package cn.iocoder.yudao.module.cloudmold.skilltask.definition.dynamic;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.skilltask.SkillTaskProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicSkillTaskDefinitionSourceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SkillTaskProperties properties = new SkillTaskProperties();
    private final WorkflowRegistryPointerReadMapper pointerMapper = mock(WorkflowRegistryPointerReadMapper.class);
    private final WorkflowRegistryVersionReadMapper versionMapper = mock(WorkflowRegistryVersionReadMapper.class);
    private final SkillTaskRegistryLineageMapper lineageMapper = mock(SkillTaskRegistryLineageMapper.class);
    private final DynamicSkillTaskDefinitionSource source = new DynamicSkillTaskDefinitionSource(
            objectMapper, properties, pointerMapper, versionMapper, lineageMapper);

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void resolvesActiveStableVersionAndCachesByPointerVersion() throws Exception {
        TenantContextHolder.setTenantId(162L);
        WorkflowRegistryPointerRecord pointer = new WorkflowRegistryPointerRecord()
                .setTenantId(162L)
                .setSkillId("skill.test")
                .setStableVersionId("wrv-1")
                .setPointerVersion(7L)
                .setKillSwitchEnabled(false);
        WorkflowRegistryVersionRecord version = new WorkflowRegistryVersionRecord()
                .setTenantId(162L)
                .setRegistryVersionId("wrv-1")
                .setSkillId("skill.test")
                .setSkillSemanticVersion("2.0.0")
                .setRegistryStatus("ACTIVE")
                .setCanonicalDefinitionJson(canonical("""
                        {"schema_version":"cloudmold.skill-task-definition/v1","skill_id":"skill.test",
                         "skill_version":"2.0.0","risk_level":"R1",
                         "steps":[{"step_kind":"CAPABILITY","step_code":"read","step_order":1,
                                   "capability_id":"cap.read","operation_type":"READ","arguments":[]}]}
                        """));
        version.setDefinitionSha256(sha256(version.getCanonicalDefinitionJson()));
        when(pointerMapper.selectBySkillId(162L, "skill.test")).thenReturn(pointer);
        when(versionMapper.selectByRegistryVersionId(162L, "wrv-1")).thenReturn(version);

        DynamicSkillTaskDefinitionSource.ResolvedDefinition first = source.resolve("skill.test", "ACTIVE");
        DynamicSkillTaskDefinitionSource.ResolvedDefinition second = source.resolve("skill.test", "ACTIVE");

        assertThat(first.skillVersion()).isEqualTo("2.0.0");
        assertThat(first.sourceKind()).isEqualTo(DynamicSkillTaskDefinitionSource.SOURCE_ACTIVE_POINTER);
        assertThat(second).isEqualTo(first);
        verify(versionMapper, times(1)).selectByRegistryVersionId(162L, "wrv-1");
    }

    @Test
    void rejectsActiveResolutionWhenPointerKillSwitchIsEnabled() {
        TenantContextHolder.setTenantId(162L);
        when(pointerMapper.selectBySkillId(162L, "skill.test")).thenReturn(new WorkflowRegistryPointerRecord()
                .setTenantId(162L)
                .setSkillId("skill.test")
                .setStableVersionId("wrv-1")
                .setPointerVersion(8L)
                .setKillSwitchEnabled(true));

        assertThatThrownBy(() -> source.resolve("skill.test", "ACTIVE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kill_switch");
        verify(versionMapper, never()).selectByRegistryVersionId(162L, "wrv-1");
    }

    @Test
    void fallsBackToLineageForExplicitHistoricalVersion() throws Exception {
        TenantContextHolder.setTenantId(162L);
        String canonicalDefinition = canonical("""
                {"schema_version":"cloudmold.skill-task-definition/v1","skill_id":"skill.test",
                 "skill_version":"1.9.0","risk_level":"R1",
                 "steps":[{"step_kind":"CAPABILITY","step_code":"read","step_order":1,
                           "capability_id":"cap.read","operation_type":"READ","arguments":[]}]}
                """);
        when(lineageMapper.selectBySkillVersion(162L, "skill.test", "1.9.0"))
                .thenReturn(new SkillTaskRegistryLineageRecord()
                        .setTenantId(162L)
                        .setSkillId("skill.test")
                        .setSkillVersion("1.9.0")
                        .setRegistryVersionId("wrv-old")
                        .setPointerVersion(4L)
                        .setSourceKind(DynamicSkillTaskDefinitionSource.SOURCE_LINEAGE)
                        .setCanonicalDefinitionJson(canonicalDefinition)
                        .setRegistryPayloadSha256(sha256(canonicalDefinition)));

        DynamicSkillTaskDefinitionSource.ResolvedDefinition resolved = source.resolve("skill.test", "1.9.0");

        assertThat(resolved.skillVersion()).isEqualTo("1.9.0");
        assertThat(resolved.sourceKind()).isEqualTo(DynamicSkillTaskDefinitionSource.SOURCE_LINEAGE);
        verify(pointerMapper).selectBySkillId(162L, "skill.test");
    }

    @Test
    void killSwitchAlsoBlocksExplicitLineageSubmission() {
        TenantContextHolder.setTenantId(162L);
        when(pointerMapper.selectBySkillId(162L, "skill.test")).thenReturn(new WorkflowRegistryPointerRecord()
                .setTenantId(162L)
                .setSkillId("skill.test")
                .setStableVersionId("wrv-1")
                .setPointerVersion(8L)
                .setKillSwitchEnabled(true));

        assertThatThrownBy(() -> source.resolve("skill.test", "1.9.0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("kill_switch");
        verify(lineageMapper, never()).selectBySkillVersion(162L, "skill.test", "1.9.0");
    }

    private String canonical(String json) throws Exception {
        return objectMapper.writeValueAsString(sort(objectMapper.readTree(json))) + "\n";
    }

    private JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            Map<String, JsonNode> fields = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                fields.put(entry.getKey(), sort(entry.getValue()));
            }
            fields.forEach(sorted::set);
            return sorted;
        }
        if (node.isArray()) {
            var array = objectMapper.createArrayNode();
            node.forEach(item -> array.add(sort(item)));
            return array;
        }
        return node.deepCopy();
    }

    private static String sha256(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes()));
    }
}
