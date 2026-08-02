package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ActorRoleGrant;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ApprovalWorkflowBinding;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ApprovalWorkflowStartCandidate;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.WorkOrder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentApprovalResponsibilityResolverTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 28, 1, 0);
    private final AgentControlStoreMapper mapper = mock(AgentControlStoreMapper.class);
    private final AgentApprovalResponsibilityResolver resolver =
            new AgentApprovalResponsibilityResolver(mapper);

    @Test
    void resolvesR3PurchaseResponsibilityFromEffectiveTenantRoleGrants() {
        when(mapper.selectEffectiveActorRoleGrantsByRole(17L, "buyer", NOW))
                .thenReturn(List.of(grant(17L, "buyer", 210L, "grant-buyer", 1L)));
        when(mapper.selectEffectiveActorRoleGrantsByRole(17L, "finance", NOW))
                .thenReturn(List.of(grant(17L, "finance", 220L, "grant-finance", 3L)));

        AgentApprovalResponsibilityResolver.Resolution resolution =
                resolver.resolve(candidate(), NOW);

        assertThat(resolution.roleCodes()).containsExactly("buyer", "finance");
        assertThat(resolution.approverUserIds()).containsExactly(210L, 220L);
        assertThat(resolution.authoritySha256()).matches("[0-9a-f]{64}");
    }

    @Test
    void rejectsMissingResponsibilityGroup() {
        when(mapper.selectEffectiveActorRoleGrantsByRole(17L, "buyer", NOW))
                .thenReturn(List.of(grant(17L, "buyer", 210L, "grant-buyer", 1L)));
        when(mapper.selectEffectiveActorRoleGrantsByRole(17L, "finance", NOW))
                .thenReturn(List.of());

        assertThatThrownBy(() -> resolver.resolve(candidate(), NOW))
                .hasMessage("R3 responsibility group has no effective tenant member: finance");
    }

    @Test
    void rejectsSameUserAcrossResponsibilityGroupsOrMissingIndependentMember() {
        when(mapper.selectEffectiveActorRoleGrantsByRole(17L, "buyer", NOW))
                .thenReturn(List.of(grant(17L, "buyer", 210L, "grant-buyer", 1L)));
        when(mapper.selectEffectiveActorRoleGrantsByRole(17L, "finance", NOW))
                .thenReturn(List.of(grant(17L, "finance", 210L, "grant-finance", 1L)));

        assertThatThrownBy(() -> resolver.resolve(candidate(), NOW))
                .hasMessage("R3 responsibility groups must not resolve to the same user");

        when(mapper.selectEffectiveActorRoleGrantsByRole(17L, "finance", NOW))
                .thenReturn(List.of(grant(17L, "finance", 200L, "grant-finance", 1L)));
        assertThatThrownBy(() -> resolver.resolve(candidate(), NOW))
                .hasMessage("R3 responsibility group has no independent effective tenant member: finance");
    }

    @Test
    void excludesRequesterExecutorAndOperatingPrincipalWhenIndependentMembersExist() {
        when(mapper.selectEffectiveActorRoleGrantsByRole(17L, "buyer", NOW))
                .thenReturn(List.of(
                        grant(17L, "buyer", 100L, "grant-buyer-requester", 1L),
                        grant(17L, "buyer", 210L, "grant-buyer-independent", 1L)));
        when(mapper.selectEffectiveActorRoleGrantsByRole(17L, "finance", NOW))
                .thenReturn(List.of(
                        grant(17L, "finance", 200L, "grant-finance-principal", 1L),
                        grant(17L, "finance", 220L, "grant-finance-independent", 1L)));

        AgentApprovalResponsibilityResolver.Resolution resolution =
                resolver.resolve(candidate(), NOW);

        assertThat(resolution.roleCodes()).containsExactly("buyer", "finance");
        assertThat(resolution.approverUserIds()).containsExactly(210L, 220L);
    }

    @Test
    void rejectsCrossTenantGrant() {
        when(mapper.selectEffectiveActorRoleGrantsByRole(17L, "buyer", NOW))
                .thenReturn(List.of(grant(18L, "buyer", 210L, "grant-buyer", 1L)));

        assertThatThrownBy(() -> resolver.resolve(candidate(), NOW))
                .hasMessage("R3 responsibility grant crosses a tenant or role boundary");
    }

    @Test
    void rejectsRevokedOrChangedAuthorityAfterBpmStart() {
        ActorRoleGrant buyer = grant(17L, "buyer", 210L, "grant-buyer", 1L);
        ActorRoleGrant finance = grant(17L, "finance", 220L, "grant-finance", 1L);
        when(mapper.selectEffectiveActorRoleGrantsByRole(17L, "buyer", NOW))
                .thenReturn(List.of(buyer));
        when(mapper.selectEffectiveActorRoleGrantsByRole(17L, "finance", NOW))
                .thenReturn(List.of(finance));
        AgentApprovalResponsibilityResolver.Resolution frozen = resolver.resolve(candidate(), NOW);
        ApprovalWorkflowBinding binding = binding(frozen);
        WorkOrder workOrder = workOrder();

        when(mapper.selectEffectiveActorRoleGrantsByRole(17L, "finance", NOW.plusMinutes(1)))
                .thenReturn(List.of(grant(17L, "finance", 221L, "grant-finance-new", 1L)));
        when(mapper.selectEffectiveActorRoleGrantsByRole(17L, "buyer", NOW.plusMinutes(1)))
                .thenReturn(List.of(buyer));

        assertThatThrownBy(() -> resolver.assertCurrent(binding, workOrder, 210L,
                YudaoBpmApprovalWorkflowAdapter.RESPONSIBILITY_TASK_KEY,
                "APPROVE", NOW.plusMinutes(1)))
                .hasMessage("R3 responsibility authorization snapshot is stale or drifted");
    }

    @Test
    void acceptsCurrentR3CountersignApprovalAndAuthorizedRejection() {
        stubCurrentPurchaseResponsibility(NOW);
        AgentApprovalResponsibilityResolver.Resolution frozen = resolver.resolve(candidate(), NOW);
        ApprovalWorkflowBinding binding = binding(frozen);
        WorkOrder workOrder = workOrder();

        assertThat(resolver.assertCurrent(binding, workOrder, 210L,
                YudaoBpmApprovalWorkflowAdapter.RESPONSIBILITY_TASK_KEY,
                "APPROVE", NOW)).isEqualTo(frozen);
        assertThat(resolver.assertCurrent(binding, workOrder, 200L,
                YudaoBpmApprovalWorkflowAdapter.APPROVAL_TASK_KEY,
                "REJECT", NOW)).isEqualTo(frozen);
        assertThat(resolver.assertCurrent(binding, workOrder, 220L,
                YudaoBpmApprovalWorkflowAdapter.RESPONSIBILITY_TASK_KEY,
                "REJECT", NOW)).isEqualTo(frozen);

        assertThatThrownBy(() -> resolver.assertCurrent(binding, workOrder, 999L,
                YudaoBpmApprovalWorkflowAdapter.RESPONSIBILITY_TASK_KEY,
                "REJECT", NOW))
                .hasMessage("R3 rejection must come from an authorized operating or responsibility approver");
    }

    @Test
    void mapsEveryGovernedR3ActionDomain() {
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("purchase.commit"))
                .containsExactly("buyer", "finance");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("purchase-order.dispatch"))
                .containsExactly("buyer", "finance");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("buyer.execute-replenishment"))
                .containsExactly("buyer", "finance");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("listing.batch-publish"))
                .containsExactly("risk", "finance");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("assortment.plan-release"))
                .containsExactly("risk", "finance");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("merchant.penalty"))
                .containsExactly("risk", "legal");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("merchant-experience.rectification"))
                .containsExactly("customer-service", "operations-lead");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("customer-experience.ticket-responsibility"))
                .containsExactly("customer-service", "operations-lead");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("customer-service.compensate"))
                .containsExactly("customer-service", "finance");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("quality.recall"))
                .containsExactly("quality", "risk", "operations-lead");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("quality.inspection-recall"))
                .containsExactly("quality", "operations-lead");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("production.execute"))
                .containsExactly("quality", "operations-lead");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("fulfillment.exception-resolution"))
                .containsExactly("customer-service", "operations-lead");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("crossborder.fulfillment-compliance"))
                .containsExactly("risk", "legal");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("crossborder.bonded-customs"))
                .containsExactly("risk", "legal");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes(
                "partner-marketing.kol-media-operations"))
                .containsExactly("risk", "legal");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("commerce.autonomous-day"))
                .containsExactly("customer-service", "finance");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("commerce-full-chain"))
                .containsExactly("customer-service", "finance");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("category.daily-operations"))
                .containsExactly("risk", "operations-lead");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("consumer.journey"))
                .containsExactly("customer-service", "finance");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("supplier.award"))
                .containsExactly("buyer", "finance");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("supply-planning.sop-release"))
                .containsExactly("buyer", "finance");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("warehouse.physical-cycle"))
                .containsExactly("inventory-control", "operations-control");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("inventory.stock-count"))
                .containsExactly("risk", "finance");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("supplier.admission"))
                .containsExactly("risk", "legal");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("inventory.scrap"))
                .containsExactly("quality", "finance");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("finance.period-close"))
                .containsExactly("risk", "operations-control");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("risk.dispute-resolve"))
                .containsExactly("risk", "legal");
        assertThat(AgentApprovalR3Policy.requiredRoleCodes("mission.stockout.start"))
                .containsExactly("risk", "finance");
        assertThatThrownBy(() -> AgentApprovalR3Policy.requiredRoleCodes("unknown.write"))
                .hasMessageContaining("no responsibility policy");
    }

    private void stubCurrentPurchaseResponsibility(LocalDateTime now) {
        when(mapper.selectEffectiveActorRoleGrantsByRole(17L, "buyer", now))
                .thenReturn(List.of(grant(17L, "buyer", 210L, "grant-buyer", 1L)));
        when(mapper.selectEffectiveActorRoleGrantsByRole(17L, "finance", now))
                .thenReturn(List.of(grant(17L, "finance", 220L, "grant-finance", 1L)));
    }

    private static ApprovalWorkflowStartCandidate candidate() {
        return new ApprovalWorkflowStartCandidate().setTenantId(17L).setApprovalId("approval-1")
                .setWorkOrderId("work-1").setActionCode("purchase.commit").setRoleCode("buyer")
                .setRiskLevel("R3").setRequesterUserId(100L).setExecutorUserId(300L)
                .setApproverUserId(200L).setScopeHash("a".repeat(64));
    }

    private static ApprovalWorkflowBinding binding(
            AgentApprovalResponsibilityResolver.Resolution resolution) {
        return new ApprovalWorkflowBinding().setTenantId(17L).setApprovalId("approval-1")
                .setWorkOrderId("work-1").setActionCode("purchase.commit").setRoleCode("buyer")
                .setRiskLevel("R3").setRequesterUserId(100L).setApproverUserId(200L)
                .setScopeHash("a".repeat(64))
                .setResponsibilityRoleCodesJson(JsonUtils.toJsonString(resolution.roleCodes()))
                .setResponsibilityApproverUserIdsJson(JsonUtils.toJsonString(resolution.approverUserIds()))
                .setResponsibilityAuthoritySha256(resolution.authoritySha256());
    }

    private static WorkOrder workOrder() {
        return new WorkOrder().setTenantId(17L).setWorkOrderId("work-1").setRoleCode("buyer")
                .setActionCode("purchase.commit").setRiskLevel("R3").setAssigneeUserId(300L);
    }

    private static ActorRoleGrant grant(Long tenantId, String roleCode, Long userId,
                                        String grantId, Long version) {
        return new ActorRoleGrant().setTenantId(tenantId).setRoleCode(roleCode)
                .setActorUserId(userId).setGrantId(grantId).setStatus("ACTIVE")
                .setVersion(version).setValidFrom(NOW.minusDays(1)).setValidUntil(NOW.plusDays(1));
    }
}
