package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentAuthorityCommand;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentAuthorityGovernanceApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentAuthorityOperation;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlCommand;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlCommandApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlOperation;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reconciles the Agent Control identities required by Temporal-managed role workflows.
 *
 * <p>A daily schedule is not runnable merely because it exists in Temporal. Its
 * operating role, governed action policy and requester grant must exist first.
 * Keeping this reconciliation beside schedule seeding prevents newly introduced
 * business roles from failing only when their first daily occurrence fires.</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.ai-operations.temporal", name = "enabled", havingValue = "true")
class ManagedWorkflowAgentGovernanceSeeder {

    private static final Map<String, String> ROLE_NAMES = Map.ofEntries(
            Map.entry("customer-service", "客户服务运营"),
            Map.entry("finance-operations", "财务结算运营"),
            Map.entry("growth-marketing", "增长营销运营"),
            Map.entry("merchandising", "商品运营"),
            Map.entry("merchant-operations", "商家运营"),
            Map.entry("logistics-operations", "物流履约异常运营"),
            Map.entry("operations-control", "综合运营控制"),
            Map.entry("procurement", "采购与供应商运营"),
            Map.entry("quality-operations", "质量检验与召回运营"),
            Map.entry("supply-planning", "供应计划运营"),
            Map.entry("warehouse-operations", "仓储运营"),
            Map.entry("buyer", "采购会签责任人"),
            Map.entry("quality", "质量会签责任人"),
            Map.entry("operations-lead", "运营负责人"),
            Map.entry("risk", "风险会签责任人"),
            Map.entry("legal", "法务会签责任人")
    );

    private static final Map<String, List<String>> R3_RESPONSIBILITY_ROLES = Map.ofEntries(
            Map.entry("aftersale.refund", List.of("customer-service", "finance")),
            Map.entry("customer-service.compensate", List.of("customer-service", "finance")),
            Map.entry("mission.stockout.start", List.of("risk", "finance")),
            Map.entry("commerce.full-chain", List.of("customer-service", "finance")),
            Map.entry("catalog.publish", List.of("risk", "finance")),
            Map.entry("commerce.autonomous-day", List.of("customer-service", "finance")),
            Map.entry("consumer.journey", List.of("customer-service", "finance")),
            Map.entry("merchant.onboarding", List.of("risk", "legal")),
            Map.entry("supplier.award", List.of("buyer", "finance")),
            Map.entry("purchase-order.dispatch", List.of("buyer", "finance")),
            Map.entry("warehouse.physical-cycle", List.of("inventory-control", "operations-control")),
            Map.entry("replenishment.end-to-end", List.of("buyer", "finance")),
            Map.entry("finance.period-close", List.of("risk", "operations-control")),
            Map.entry("quality.inspection-recall", List.of("quality", "operations-lead")),
            Map.entry("fulfillment.exception-resolution",
                    List.of("customer-service", "operations-lead"))
    );

    private final AgentControlCommandApi agentCommands;
    private final AgentAuthorityGovernanceApi authorityGovernance;
    private final AiOperationsTemporalMapper mapper;

    ReconcileResult reconcile(Long tenantId, List<ManagedSkillTaskWorkflowView> workflows) {
        TemporalApprovalPolicyRecord approvalPolicy = mapper.selectApprovalPolicy(tenantId);
        if (approvalPolicy == null || !"ACTIVE".equals(approvalPolicy.getStatus())) {
            throw new IllegalStateException(
                    "active AI Operations approval policy is required before managed workflow governance seeding");
        }

        int createdRoles = 0;
        int createdPolicies = 0;
        int createdGrants = 0;
        Instant seedOccurredAt = seedOccurredAt(approvalPolicy);
        Set<String> reconciledRoles = new LinkedHashSet<>();
        Set<String> reconciledDefinitions = new LinkedHashSet<>();
        Set<String> reconciledGrants = new LinkedHashSet<>();
        for (ManagedSkillTaskWorkflowView workflow : workflows) {
            if (!Boolean.TRUE.equals(workflow.getApprovalRequired())) {
                continue;
            }
            ManagedWorkflowDailyAutomationCatalog.ApprovalRoute route =
                    ManagedWorkflowDailyAutomationCatalog.approvalRoute(workflow.getSkillId());
            if (route == null) {
                throw new IllegalStateException(
                        "approval route is missing for managed workflow: " + workflow.getSkillId());
            }
            if (reconciledDefinitions.add(route.roleCode())
                    && mapper.countActiveAgentRole(tenantId, route.roleCode()) == 0) {
                defineRole(route.roleCode(), approvalPolicy.getGovernanceUserId(), seedOccurredAt);
                createdRoles++;
            }
            if (mapper.countEnabledAgentActionPolicy(
                    tenantId, route.roleCode(), route.actionCode()) == 0) {
                defineActionPolicy(route, workflow, approvalPolicy.getGovernanceUserId(), seedOccurredAt);
                createdPolicies++;
            }
            if (reconciledGrants.add(approvalPolicy.getRequesterUserId() + ":" + route.roleCode())
                    && mapper.countEffectiveAgentRoleGrant(
                    tenantId, approvalPolicy.getRequesterUserId(), route.roleCode()) == 0) {
                grantRole(approvalPolicy.getRequesterUserId(), route.roleCode(), approvalPolicy, seedOccurredAt);
                createdGrants++;
            }
            if ("R3".equals(workflow.getRiskLevel())) {
                for (String responsibilityRole : responsibilityRoles(route.actionCode())) {
                    if (reconciledDefinitions.add(responsibilityRole)
                            && mapper.countActiveAgentRole(tenantId, responsibilityRole) == 0) {
                        defineRole(responsibilityRole, approvalPolicy.getGovernanceUserId(), seedOccurredAt);
                        createdRoles++;
                    }
                    Long actorUserId = responsibilityActor(tenantId, responsibilityRole, approvalPolicy);
                    if (reconciledGrants.add(actorUserId + ":" + responsibilityRole)
                            && mapper.countEffectiveAgentRoleGrant(
                            tenantId, actorUserId, responsibilityRole) == 0) {
                        grantRole(actorUserId, responsibilityRole, approvalPolicy, seedOccurredAt);
                        createdGrants++;
                    }
                }
            }
            reconciledRoles.add(route.roleCode());
        }
        return new ReconcileResult(reconciledRoles.size(), createdRoles, createdPolicies, createdGrants);
    }

    private void defineRole(String roleCode, Long governanceUserId, Instant occurredAt) {
        String roleName = ROLE_NAMES.getOrDefault(roleCode, roleCode);
        agentCommands.execute(AgentControlCommand.builder()
                .operation(AgentControlOperation.DEFINE_ROLE)
                .idempotencyKey("aiops:managed-role:v1:" + roleCode)
                .occurredAt(occurredAt)
                .role(AgentControlCommand.RoleDefinition.builder()
                        .roleCode(roleCode)
                        .roleName(roleName)
                        .responsibilityJson("""
                                {"mission":"以岗位主流程为边界完成业务闭环并保留执行证据"}
                                """.trim())
                        .kpiJson("""
                                {"metrics":["流程完成率","业务终态达成率","人工接管率"]}
                                """.trim())
                        .approvalBoundaryJson("""
                                {"rule":"R2/R3 写操作必须经过独立审批卡口"}
                                """.trim())
                        .memoryPolicyJson("""
                                {"scope":"TENANT_ROLE","retention":"AUDITABLE"}
                                """.trim())
                        .build())
                .build(), governanceUserId);
    }

    private void defineActionPolicy(
            ManagedWorkflowDailyAutomationCatalog.ApprovalRoute route,
            ManagedSkillTaskWorkflowView workflow,
            Long governanceUserId,
            Instant occurredAt) {
        agentCommands.execute(AgentControlCommand.builder()
                .operation(AgentControlOperation.SET_ACTION_POLICY)
                .idempotencyKey("aiops:managed-action:v1:" + workflow.getSkillId())
                .occurredAt(occurredAt)
                .actionPolicy(AgentControlCommand.RoleActionPolicyDefinition.builder()
                        .roleCode(route.roleCode())
                        .actionCode(route.actionCode())
                        .riskLevel(workflow.getRiskLevel())
                        .approvalRequired(true)
                        .executionRequired(true)
                        .skillId(workflow.getSkillId())
                        .skillVersion(workflow.getSkillVersion())
                        .skillDefinitionClosureSha256(workflow.getDefinitionClosureSha256())
                        .build())
                .build(), governanceUserId);
    }

    private void grantRole(Long actorUserId, String roleCode, TemporalApprovalPolicyRecord approvalPolicy,
                           Instant occurredAt) {
        Instant validFrom = occurredAt.minus(1, ChronoUnit.MINUTES);
        Long grantingUserId = actorUserId.equals(approvalPolicy.getGovernanceUserId())
                ? approvalPolicy.getApproverUserId()
                : approvalPolicy.getGovernanceUserId();
        authorityGovernance.executeAuthorityGovernance(AgentAuthorityCommand.builder()
                .operation(AgentAuthorityOperation.GRANT_ROLE)
                .idempotencyKey("aiops:managed-role-grant:v1:" + actorUserId + ":" + roleCode)
                .occurredAt(occurredAt)
                .roleGrant(AgentAuthorityCommand.RoleGrantDefinition.builder()
                        .actorUserId(actorUserId)
                        .roleCode(roleCode)
                        .validFrom(validFrom)
                        .validUntil(validFrom.plus(3650, ChronoUnit.DAYS))
                        .build())
                .build(), grantingUserId);
    }

    private List<String> responsibilityRoles(String actionCode) {
        List<String> roles = R3_RESPONSIBILITY_ROLES.get(actionCode);
        if (roles == null || roles.isEmpty()) {
            throw new IllegalStateException(
                    "R3 responsibility roles are missing for managed workflow action: " + actionCode);
        }
        return roles;
    }

    private Long responsibilityActor(
            Long tenantId, String roleCode, TemporalApprovalPolicyRecord approvalPolicy) {
        return switch (roleCode) {
            case "customer-service", "buyer", "quality", "risk", "inventory-control" ->
                    approvalPolicy.getGovernanceUserId();
            case "finance", "legal", "operations-control", "operations-lead" ->
                    requireFinancialController(tenantId);
            default -> throw new IllegalStateException(
                    "managed workflow responsibility actor is not configured for role: " + roleCode);
        };
    }

    private Long requireFinancialController(Long tenantId) {
        Long actorUserId = mapper.selectFirstEffectiveAgentRoleActor(tenantId, "finance");
        if (actorUserId == null || actorUserId <= 0) {
            throw new IllegalStateException(
                    "an effective finance responsibility member is required for managed R3 workflows");
        }
        return actorUserId;
    }

    private Instant seedOccurredAt(TemporalApprovalPolicyRecord approvalPolicy) {
        if (approvalPolicy.getCreatedAt() != null) {
            return approvalPolicy.getCreatedAt().toInstant(ZoneOffset.UTC);
        }
        return Instant.parse("2026-01-01T00:00:00Z");
    }

    record ReconcileResult(int roleCount, int createdRoles, int createdPolicies, int createdGrants) {
    }
}
