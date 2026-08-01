package cn.iocoder.yudao.module.cloudmold.skilltask.workflowregistry;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.SkillTaskDefinition;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.SkillTaskDefinitionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowRegistryQueryServiceTest {

    private final SkillTaskDefinitionRegistry definitionRegistry = mock(SkillTaskDefinitionRegistry.class);
    private final WorkflowRegistryAttestationService attestationService = new WorkflowRegistryAttestationService(
            new ObjectMapper(),
            "registry-secret",
            Clock.fixed(Instant.ofEpochSecond(1780000000L), ZoneOffset.UTC));
    private final WorkflowRegistryQueryService queryService =
            new WorkflowRegistryQueryService(definitionRegistry, attestationService, new ObjectMapper());

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void bindsAttestationOwnerToCurrentLoginSubject() {
        TenantContextHolder.setTenantId(162L);
        authenticate(162L, 162L, 227L);
        when(definitionRegistry.require("skill.test", "1.0.0")).thenReturn(definition());

        WorkflowRegistryDefinitionView result = queryService.getDefinition("skill.test", "1.0.0");

        assertThat(result.attestation().ownerUserId()).isEqualTo("227");
        assertThat(result.attestation().workflowId()).isEqualTo("skill.test");
        assertThat(result.definition().path("skill_id").asText()).isEqualTo("skill.test");
    }

    @Test
    void exposesBearerAuthenticatedSubjectForBridgeVerification() {
        TenantContextHolder.setTenantId(162L);
        authenticate(162L, 162L, 227L);

        WorkflowRegistrySubjectView result = queryService.getCurrentSubject();

        assertThat(result.ownerUserId()).isEqualTo("227");
        assertThat(result.tenantId()).isEqualTo(162L);
    }

    @Test
    void requiresTenantBoundLoginSubject() {
        TenantContextHolder.setTenantId(162L);
        authenticate(161L, 161L, 227L);
        when(definitionRegistry.require("skill.test", "1.0.0")).thenReturn(definition());

        assertThatThrownBy(() -> queryService.getDefinition("skill.test", "1.0.0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not bound to the current tenant context");
    }

    private void authenticate(Long tenantId, Long visitTenantId, Long userId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(userId);
        loginUser.setTenantId(tenantId);
        loginUser.setVisitTenantId(visitTenantId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
    }

    private SkillTaskDefinition definition() {
        return SkillTaskDefinition.builder()
                .schemaVersion(SkillTaskDefinitionRegistry.SCHEMA_VERSION)
                .skillId("skill.test")
                .skillVersion("1.0.0")
                .riskLevel("R1")
                .steps(List.of(SkillTaskDefinition.Step.builder()
                        .stepKind("CAPABILITY")
                        .stepCode("read")
                        .stepOrder(1)
                        .capabilityId("cap.read")
                        .operationType("READ")
                        .build()))
                .build();
    }
}
