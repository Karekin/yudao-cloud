package cn.iocoder.yudao.module.cloudmold.skilltask.definition.dynamic;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.skilltask.SkillTaskProperties;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.SkillTaskDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DynamicSkillTaskDefinitionSource {

    public static final String SOURCE_ACTIVE_POINTER = "ACTIVE_POINTER";
    public static final String SOURCE_LINEAGE = "LINEAGE";

    private final ObjectMapper objectMapper;
    private final SkillTaskProperties properties;
    private final WorkflowRegistryPointerReadMapper pointerMapper;
    private final WorkflowRegistryVersionReadMapper versionMapper;
    private final SkillTaskRegistryLineageMapper lineageMapper;
    private final ConcurrentHashMap<CacheKey, CachedStableDefinition> activeCache = new ConcurrentHashMap<>();

    public DynamicSkillTaskDefinitionSource(ObjectMapper objectMapper,
                                            SkillTaskProperties properties,
                                            WorkflowRegistryPointerReadMapper pointerMapper,
                                            WorkflowRegistryVersionReadMapper versionMapper,
                                            SkillTaskRegistryLineageMapper lineageMapper) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.pointerMapper = pointerMapper;
        this.versionMapper = versionMapper;
        this.lineageMapper = lineageMapper;
    }

    public boolean isActiveAlias(String skillVersion) {
        if (skillVersion == null) {
            return false;
        }
        String alias = properties.getDynamic().getActiveAlias();
        return alias != null && !alias.isBlank() && alias.equalsIgnoreCase(skillVersion.trim());
    }

    public ResolvedDefinition resolve(String skillId, String skillVersion) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (isActiveAlias(skillVersion)) {
            requireDynamicBridgeEnabled();
            return resolveActive(tenantId, skillId);
        }
        WorkflowRegistryPointerRecord pointer = null;
        if (properties.getDynamic().isEnabled()) {
            pointer = pointerMapper.selectBySkillId(tenantId, skillId);
            if (pointer != null && Boolean.TRUE.equals(pointer.getKillSwitchEnabled())) {
                throw new IllegalStateException("SkillTask resolution is blocked by workflow registry kill_switch");
            }
        }
        SkillTaskRegistryLineageRecord lineage = lineageMapper.selectBySkillVersion(tenantId, skillId, skillVersion);
        if (lineage != null) {
            return verifyLineage(lineage);
        }
        if (!properties.getDynamic().isEnabled()) {
            return null;
        }
        if (pointer == null || pointer.getStableVersionId() == null) {
            return null;
        }
        ResolvedDefinition stable = resolveStable(tenantId, skillId, pointer);
        return skillVersion.equals(stable.skillVersion()) ? stable : null;
    }

    public void recordLineage(ResolvedDefinition resolved, SkillTaskDefinition definition) {
        Objects.requireNonNull(resolved, "resolved");
        Objects.requireNonNull(definition, "definition");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now();
        SkillTaskRegistryLineageRecord lineage = new SkillTaskRegistryLineageRecord()
                .setLineageId("stl-" + UUID.randomUUID())
                .setTenantId(tenantId)
                .setSkillId(definition.getSkillId())
                .setSkillVersion(definition.getSkillVersion())
                .setRegistryVersionId(resolved.registryVersionId())
                .setPointerVersion(resolved.pointerVersion())
                .setSourceKind(resolved.sourceKind())
                .setCanonicalDefinitionJson(resolved.canonicalDefinitionJson())
                .setRegistryPayloadSha256(resolved.registryPayloadSha256())
                .setDefinitionSha256(definition.getDefinitionSha256())
                .setDefinitionClosureSha256(definition.getDefinitionClosureSha256())
                .setCreatedAt(now)
                .setUpdatedAt(now);
        lineageMapper.upsert(lineage);
    }

    private void requireDynamicBridgeEnabled() {
        if (!properties.getDynamic().isEnabled()) {
            throw new IllegalStateException("ACTIVE SkillTask resolution is disabled by the dynamic registry");
        }
    }

    private ResolvedDefinition resolveActive(Long tenantId, String skillId) {
        WorkflowRegistryPointerRecord pointer = pointerMapper.selectBySkillId(tenantId, skillId);
        if (pointer == null || pointer.getStableVersionId() == null) {
            throw new IllegalArgumentException("No ACTIVE workflow registry pointer exists for " + skillId);
        }
        return resolveStable(tenantId, skillId, pointer);
    }

    private ResolvedDefinition resolveStable(Long tenantId, String skillId, WorkflowRegistryPointerRecord pointer) {
        if (Boolean.TRUE.equals(pointer.getKillSwitchEnabled())) {
            throw new IllegalStateException("ACTIVE SkillTask resolution is blocked by workflow registry kill_switch");
        }
        CacheKey cacheKey = new CacheKey(tenantId, skillId);
        CachedStableDefinition cached = activeCache.get(cacheKey);
        if (cached != null
                && Objects.equals(cached.pointerVersion(), pointer.getPointerVersion())
                && Objects.equals(cached.registryVersionId(), pointer.getStableVersionId())) {
            return cached.definition();
        }
        WorkflowRegistryVersionRecord version = versionMapper.selectByRegistryVersionId(tenantId, pointer.getStableVersionId());
        if (version == null) {
            throw new IllegalStateException("Stable workflow registry version is missing: " + pointer.getStableVersionId());
        }
        ResolvedDefinition resolved = verifyStableVersion(version, pointer);
        activeCache.put(cacheKey, new CachedStableDefinition(pointer.getPointerVersion(), pointer.getStableVersionId(), resolved));
        return resolved;
    }

    private ResolvedDefinition verifyStableVersion(WorkflowRegistryVersionRecord version,
                                                   WorkflowRegistryPointerRecord pointer) {
        if (!Objects.equals(version.getSkillId(), pointer.getSkillId())) {
            throw new IllegalStateException("Workflow registry pointer skill_id does not match its stable version");
        }
        if (!"ACTIVE".equals(version.getRegistryStatus())) {
            throw new IllegalStateException("Workflow registry stable version must remain ACTIVE");
        }
        VerifiedCanonicalDefinition verified = verifyCanonicalDefinition(
                version.getCanonicalDefinitionJson(),
                version.getDefinitionSha256(),
                version.getSkillId(),
                version.getSkillSemanticVersion());
        return new ResolvedDefinition(
                verified.skillId(),
                verified.skillVersion(),
                version.getRegistryVersionId(),
                pointer.getPointerVersion(),
                verified.canonicalDefinitionJson(),
                verified.registryPayloadSha256(),
                SOURCE_ACTIVE_POINTER);
    }

    private ResolvedDefinition verifyLineage(SkillTaskRegistryLineageRecord lineage) {
        VerifiedCanonicalDefinition verified = verifyCanonicalDefinition(
                lineage.getCanonicalDefinitionJson(),
                lineage.getRegistryPayloadSha256(),
                lineage.getSkillId(),
                lineage.getSkillVersion());
        return new ResolvedDefinition(
                verified.skillId(),
                verified.skillVersion(),
                lineage.getRegistryVersionId(),
                lineage.getPointerVersion(),
                verified.canonicalDefinitionJson(),
                verified.registryPayloadSha256(),
                SOURCE_LINEAGE);
    }

    private VerifiedCanonicalDefinition verifyCanonicalDefinition(String canonicalDefinitionJson,
                                                                 String expectedRegistryPayloadSha256,
                                                                 String expectedSkillId,
                                                                 String expectedSkillVersion) {
        JsonNode parsed = readJson(canonicalDefinitionJson);
        if (!parsed.isObject()) {
            throw new IllegalStateException("Workflow registry canonical definition must be a JSON object");
        }
        String normalizedCanonical = canonicalize(parsed);
        String actualRegistryPayloadSha256 = sha256Hex(normalizedCanonical);
        if (!actualRegistryPayloadSha256.equals(expectedRegistryPayloadSha256)) {
            throw new IllegalStateException("Workflow registry canonical definition hash mismatch");
        }
        String skillId = text(parsed, "skill_id");
        String skillVersion = text(parsed, "skill_version");
        if (!expectedSkillId.equals(skillId)) {
            throw new IllegalStateException("Workflow registry canonical definition skill_id drifted");
        }
        if (!expectedSkillVersion.equals(skillVersion)) {
            throw new IllegalStateException("Workflow registry canonical definition skill_version drifted");
        }
        return new VerifiedCanonicalDefinition(skillId, skillVersion, normalizedCanonical, actualRegistryPayloadSha256);
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Workflow registry canonical definition JSON is invalid", exception);
        }
    }

    private String canonicalize(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(sort(node)) + "\n";
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Workflow registry canonical definition cannot be normalized", exception);
        }
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

    private static String sha256Hex(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Workflow registry canonical definition is missing " + field);
        }
        return value.trim();
    }

    private record CacheKey(Long tenantId, String skillId) {
    }

    private record CachedStableDefinition(Long pointerVersion,
                                          String registryVersionId,
                                          ResolvedDefinition definition) {
    }

    private record VerifiedCanonicalDefinition(String skillId,
                                               String skillVersion,
                                               String canonicalDefinitionJson,
                                               String registryPayloadSha256) {
    }

    public record ResolvedDefinition(String skillId,
                                     String skillVersion,
                                     String registryVersionId,
                                     Long pointerVersion,
                                     String canonicalDefinitionJson,
                                     String registryPayloadSha256,
                                     String sourceKind) {
    }
}
