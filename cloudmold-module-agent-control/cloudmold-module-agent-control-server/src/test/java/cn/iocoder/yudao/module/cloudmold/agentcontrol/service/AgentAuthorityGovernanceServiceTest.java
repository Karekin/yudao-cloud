package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.*;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.*;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import org.junit.jupiter.api.*;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentAuthorityGovernanceServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-19T10:00:00Z");
    private final AgentControlStoreMapper mapper = mock(AgentControlStoreMapper.class);
    private final AgentAuthorityGovernanceService service = new AgentAuthorityGovernanceService(mapper,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final AtomicReference<String> requestHash = new AtomicReference<>();
    private final AtomicReference<String> attemptToken = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(17L);
        when(mapper.insertOrResolveOperation(eq(17L), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    requestHash.set(invocation.getArgument(3));
                    attemptToken.set(invocation.getArgument(4));
                    return 1;
                });
        when(mapper.selectLastInsertId()).thenReturn(9L);
        when(mapper.selectOperationForUpdate(9L, 17L)).thenAnswer(invocation -> new Operation()
                .setOperationId(9L).setTenantId(17L).setRequestHash(requestHash.get())
                .setAttemptToken(attemptToken.get()).setStatus(0));
        when(mapper.markOperationSucceeded(eq(9L), eq(17L), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(mapper.insertAuditEvent(any())).thenReturn(1);
        when(mapper.selectRole(17L, "buyer")).thenReturn(new RoleDefinition().setTenantId(17L)
                .setRoleCode("buyer").setStatus("ACTIVE").setVersion(1L));
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), anyLong(), anyString(), any()))
                .thenAnswer(invocation -> new ActorRoleGrant().setTenantId(17L)
                        .setActorUserId(invocation.getArgument(1)).setRoleCode(invocation.getArgument(2))
                        .setStatus("ACTIVE").setVersion(1L));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void roleGrantRejectsSelfGrantAndPersistsExactValidity() {
        AgentAuthorityCommand selfGrant = base(AgentAuthorityOperation.GRANT_ROLE, "self-grant")
                .roleGrant(AgentAuthorityCommand.RoleGrantDefinition.builder().grantId("grant-role-self")
                        .actorUserId(900L).roleCode("buyer").validFrom(NOW)
                        .validUntil(NOW.plus(Duration.ofHours(8))).build()).build();
        assertThatThrownBy(() -> service.executeAuthorityGovernance(selfGrant, 900L))
                .hasMessage("governance actor cannot grant authority to itself");

        when(mapper.insertActorRoleGrant(any())).thenReturn(1);
        AgentControlResult result = service.executeAuthorityGovernance(
                base(AgentAuthorityOperation.GRANT_ROLE, "role-grant")
                        .roleGrant(AgentAuthorityCommand.RoleGrantDefinition.builder().grantId("grant-role-1")
                                .actorUserId(901L).roleCode("buyer").validFrom(NOW.minusSeconds(60))
                                .validUntil(NOW.plus(Duration.ofHours(8))).build()).build(), 900L);

        assertThat(result).extracting(AgentControlResult::getAggregateType, AgentControlResult::getAggregateId,
                AgentControlResult::getStatus).containsExactly("actor_role_grant", "grant-role-1", "ACTIVE");
        verify(mapper).insertActorRoleGrant(argThat(grant -> grant.getTenantId().equals(17L)
                && grant.getActorUserId().equals(901L) && grant.getGrantedByUserId().equals(900L)
                && grant.getValidFrom().equals(LocalDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC))
                && grant.getValidUntil().equals(LocalDateTime.ofInstant(NOW.plus(Duration.ofHours(8)), ZoneOffset.UTC))));
    }

    @Test
    void roleGrantRejectsInvalidValidityWindow() {
        assertThatThrownBy(() -> service.executeAuthorityGovernance(
                base(AgentAuthorityOperation.GRANT_ROLE, "role-window")
                        .roleGrant(AgentAuthorityCommand.RoleGrantDefinition.builder().grantId("grant-role-window")
                                .actorUserId(901L).roleCode("buyer").validFrom(NOW.plusSeconds(2))
                                .validUntil(NOW.plusSeconds(1)).build()).build(), 900L))
                .hasMessage("authority validUntil must be in the future and validFrom must precede validUntil");
        verify(mapper, never()).insertActorRoleGrant(any());
    }

    @Test
    void approvalGrantMustMatchPendingApprovalRoleActionRiskAndScopeExactly() {
        RoleActionPolicy actionPolicy = new RoleActionPolicy().setPolicyId("policy-purchase-commit")
                .setTenantId(17L).setRoleCode("buyer").setActionCode("purchase.commit").setRiskLevel("R3")
                .setPermissionMode("ALLOW").setApprovalRequired(true).setExecutionRequired(false)
                .setEnabled(true).setVersion(1L);
        WorkOrder workOrder = new WorkOrder().setWorkOrderId("wo-approval").setTenantId(17L)
                .setRoleCode("buyer").setActionCode("purchase.commit").setBusinessContextJson("{\"sku\":\"A\"}")
                .setRequesterUserId(100L).setAssigneeUserId(300L)
                .setApprovalId("approval-1").setStatus("WAITING_APPROVAL").setVersion(2L)
                .setActionPolicyId(actionPolicy.getPolicyId()).setActionPolicyVersion(actionPolicy.getVersion())
                .setRiskLevel(actionPolicy.getRiskLevel()).setExecutionRequired(false)
                .setExecutionInputSha256(cn.hutool.crypto.digest.DigestUtil.sha256Hex("{\"sku\":\"A\"}"));
        Approval approval = new Approval().setApprovalId("approval-1").setTenantId(17L)
                .setWorkOrderId("wo-approval").setActionCode("purchase.commit").setRequesterUserId(100L)
                .setScopeHash(scopeHash(workOrder)).setStatus("PENDING").setVersion(1L);
        when(mapper.selectApprovalForUpdate(17L, "approval-1")).thenReturn(approval);
        when(mapper.selectWorkOrderForUpdate(17L, "wo-approval")).thenReturn(workOrder);
        when(mapper.selectActionPolicy(17L, "buyer", "purchase.commit")).thenReturn(actionPolicy);

        AgentAuthorityCommand selfGrant = base(AgentAuthorityOperation.GRANT_APPROVER, "approval-self-grant")
                .approvalGrant(AgentAuthorityCommand.ApprovalGrantDefinition.builder()
                        .grantId("approval-grant-self").approverUserId(900L).approvalId("approval-1")
                        .roleCode("buyer").actionCode("purchase.commit").riskLevel("R3")
                        .scopeHash(approval.getScopeHash()).validFrom(NOW.minusSeconds(1))
                        .validUntil(NOW.plus(Duration.ofHours(2))).build()).build();
        assertThatThrownBy(() -> service.executeAuthorityGovernance(selfGrant, 900L))
                .hasMessage("governance actor cannot grant authority to itself");

        AgentAuthorityCommand mismatched = base(AgentAuthorityOperation.GRANT_APPROVER, "approval-mismatch")
                .approvalGrant(AgentAuthorityCommand.ApprovalGrantDefinition.builder().grantId("approval-grant-bad")
                        .approverUserId(200L).approvalId("approval-1").roleCode("buyer")
                        .actionCode("purchase.commit").riskLevel("R2").scopeHash(approval.getScopeHash())
                        .validFrom(NOW.minusSeconds(1)).validUntil(NOW.plus(Duration.ofHours(2))).build()).build();
        assertThatThrownBy(() -> service.executeAuthorityGovernance(mismatched, 900L))
                .hasMessage("approver grant riskLevel does not match the frozen action policy");

        AgentAuthorityCommand executorSelfApproval = base(AgentAuthorityOperation.GRANT_APPROVER,
                "approval-executor-self-grant")
                .approvalGrant(AgentAuthorityCommand.ApprovalGrantDefinition.builder()
                        .grantId("approval-grant-executor").approverUserId(300L).approvalId("approval-1")
                        .roleCode("buyer").actionCode("purchase.commit").riskLevel("R3")
                        .scopeHash(approval.getScopeHash()).validFrom(NOW.minusSeconds(1))
                        .validUntil(NOW.plus(Duration.ofHours(2))).build()).build();
        assertThatThrownBy(() -> service.executeAuthorityGovernance(executorSelfApproval, 900L))
                .hasMessage("work-order executor cannot be granted approver authority");

        when(mapper.selectEffectiveActorRoleGrant(eq(17L), eq(200L), eq("buyer"), any())).thenReturn(null);
        when(mapper.insertApprovalAuthorityGrant(any())).thenReturn(1);
        AgentControlResult result = service.executeAuthorityGovernance(
                base(AgentAuthorityOperation.GRANT_APPROVER, "approval-grant")
                        .approvalGrant(AgentAuthorityCommand.ApprovalGrantDefinition.builder()
                                .grantId("approval-grant-1").approverUserId(200L).approvalId("approval-1")
                                .roleCode("buyer").actionCode("purchase.commit").riskLevel("R3")
                                .scopeHash(approval.getScopeHash()).validFrom(NOW.minusSeconds(1))
                                .validUntil(NOW.plus(Duration.ofHours(2))).build()).build(), 900L);

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        verify(mapper, never()).selectEffectiveActorRoleGrant(eq(17L), eq(200L), eq("buyer"), any());
        verify(mapper).insertApprovalAuthorityGrant(argThat(grant -> grant.getApprovalId().equals("approval-1")
                && grant.getApproverUserId().equals(200L) && grant.getRoleCode().equals("buyer")
                && grant.getActionCode().equals("purchase.commit") && grant.getRiskLevel().equals("R3")
                && grant.getScopeHash().equals(approval.getScopeHash())));
    }

    @Test
    void revocationRejectsSelfRevokeAndRequiresExactVersion() {
        ActorRoleGrant grant = new ActorRoleGrant().setGrantId("grant-role-1").setTenantId(17L)
                .setActorUserId(901L).setRoleCode("buyer").setStatus("ACTIVE").setVersion(3L);
        when(mapper.selectActorRoleGrantForUpdate(17L, "grant-role-1")).thenReturn(grant);

        assertThatThrownBy(() -> service.executeAuthorityGovernance(
                base(AgentAuthorityOperation.REVOKE_ROLE, "self-revoke")
                        .roleGrant(AgentAuthorityCommand.RoleGrantDefinition.builder().grantId("grant-role-1")
                                .actorUserId(901L).roleCode("buyer").expectedVersion(3L).build()).build(), 901L))
                .hasMessage("governance actor cannot revoke its own authority grant");

        assertThatThrownBy(() -> service.executeAuthorityGovernance(
                base(AgentAuthorityOperation.REVOKE_ROLE, "stale-revoke")
                        .roleGrant(AgentAuthorityCommand.RoleGrantDefinition.builder().grantId("grant-role-1")
                                .actorUserId(901L).roleCode("buyer").expectedVersion(2L).build()).build(), 900L))
                .hasMessage("expectedVersion is stale");

        when(mapper.revokeActorRoleGrant(17L, "grant-role-1", 3L, 900L, "GOVERNANCE_REVOKE",
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC))).thenReturn(1);
        assertThat(service.executeAuthorityGovernance(
                base(AgentAuthorityOperation.REVOKE_ROLE, "role-revoke")
                        .roleGrant(AgentAuthorityCommand.RoleGrantDefinition.builder().grantId("grant-role-1")
                                .actorUserId(901L).roleCode("buyer").expectedVersion(3L).build()).build(), 900L)
                .getStatus()).isEqualTo("REVOKED");
    }

    private AgentAuthorityCommand.AgentAuthorityCommandBuilder base(AgentAuthorityOperation operation, String key) {
        return AgentAuthorityCommand.builder().operation(operation).idempotencyKey(key).occurredAt(NOW);
    }

    private String scopeHash(WorkOrder workOrder) {
        return cn.hutool.crypto.digest.DigestUtil.sha256Hex(String.join("\n",
                String.valueOf(workOrder.getTenantId()), workOrder.getWorkOrderId(), workOrder.getRoleCode(),
                workOrder.getActionCode(), java.util.Objects.toString(workOrder.getActionPolicyId(), "-"),
                java.util.Objects.toString(workOrder.getActionPolicyVersion(), "-"),
                java.util.Objects.toString(workOrder.getSkillId(), "-"),
                java.util.Objects.toString(workOrder.getSkillVersion(), "-"),
                java.util.Objects.toString(workOrder.getSkillDefinitionClosureSha256(), "-"),
                java.util.Objects.toString(workOrder.getExecutionInputSha256(), "-"),
                java.util.Objects.toString(workOrder.getRiskLevel(), "-")));
    }
}
