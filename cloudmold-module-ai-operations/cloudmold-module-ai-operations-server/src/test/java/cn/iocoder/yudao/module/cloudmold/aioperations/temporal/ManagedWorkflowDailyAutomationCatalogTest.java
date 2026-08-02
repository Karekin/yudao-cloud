package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo.TemporalScheduleCreateReqVO;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManagedWorkflowDailyAutomationCatalogTest {

    @Test
    void shouldBuildUniqueDailyPolicyForAllManagedDefinitions() {
        AiOperationsTemporalSeedProperties properties = new AiOperationsTemporalSeedProperties();
        Set<String> scheduleIds = new HashSet<>();
        Set<String> policyHashes = new HashSet<>();
        ZoneId expectedZone = ZoneId.of("Asia/Shanghai");

        for (ManagedSkillTaskWorkflowView workflow :
                ManagedWorkflowDailyAutomationFixtures.workflows()) {
            String skillId = workflow.getSkillId();
            TemporalScheduleCreateReqVO request =
                    ManagedWorkflowDailyAutomationCatalog.dailyRequest(workflow, properties);
            TemporalDailyDispatchRequest dispatch =
                    ManagedWorkflowDailyAutomationCatalog.dispatchRequest(
                            162L, request.getScheduleId(), workflow, properties);
            String[] cronFields =
                    ManagedWorkflowDailyAutomationCatalog.cronExpression(skillId).split(" ");
            boolean highFrequencySupplyChain =
                    ManagedWorkflowDailyAutomationCatalog.isTenMinuteSupplyChainWorkflow(skillId);

            assertThat(scheduleIds.add(request.getScheduleId())).isTrue();
            assertThat(request.getIntervalSeconds()).isEqualTo(
                    highFrequencySupplyChain ? 600L : 86_400L);
            assertThat(ZoneId.of(request.getTimeZone())).isEqualTo(expectedZone);
            if (highFrequencySupplyChain) {
                assertThat(cronFields).containsExactly("*/10", "*", "*", "*", "*");
                assertThat(dispatch.getApprovalTimeoutSeconds()).isEqualTo(600L);
            } else {
                int minute = Integer.parseInt(cronFields[0]);
                int hour = Integer.parseInt(cronFields[1]);
                assertThat(cronFields).containsExactly(
                        Integer.toString(minute), Integer.toString(hour), "*", "*", "*");
                assertThat(minute).isBetween(0, 59);
                assertThat(hour).isBetween(2, 4);
                assertThat(dispatch.getApprovalTimeoutSeconds()).isEqualTo(86_400L);
            }
            assertThat(ManagedWorkflowDailyAutomationCatalog.inputStrategy(skillId))
                    .isIn("TENANT_AGGREGATE", "ROTATING_BUSINESS_SCENARIO",
                            "DOMAIN_BACKLOG", "EVENT_BACKLOG");
            assertThat(dispatch.getTenantId()).isEqualTo(162L);
            assertThat(dispatch.getSkillId()).isEqualTo(skillId);
            assertThat(dispatch.getSkillVersion()).isEqualTo(workflow.getSkillVersion());
            assertThat(dispatch.getTimeZone()).isEqualTo("Asia/Shanghai");
            assertThat(dispatch.getMaxFanOut()).isEqualTo(100);
            assertThat(policyHashes.add(
                    ManagedWorkflowDailyAutomationCatalog.desiredPolicySha256(
                            workflow, properties))).isTrue();
            if (ManagedWorkflowDailyAutomationFixtures.APPROVAL_SKILL_IDS.contains(skillId)) {
                assertThat(request.getRoleCode()).isNotBlank();
                assertThat(request.getActionCode()).isNotBlank();
                assertThat(dispatch.getRoleCode()).isEqualTo(request.getRoleCode());
                assertThat(dispatch.getActionCode()).isEqualTo(request.getActionCode());
            } else {
                assertThat(request.getRoleCode()).isNull();
                assertThat(request.getActionCode()).isNull();
                assertThat(dispatch.getRoleCode()).isNull();
                assertThat(dispatch.getActionCode()).isNull();
            }
        }

        int workflowCount = ManagedWorkflowDailyAutomationFixtures.workflows().size();
        assertThat(scheduleIds).hasSize(workflowCount);
        assertThat(policyHashes).hasSize(workflowCount)
                .allMatch(hash -> hash.matches("[0-9a-f]{64}"));
        assertThat(ManagedWorkflowDailyAutomationFixtures.workflows())
                .filteredOn(workflow -> "R1".equals(workflow.getRiskLevel()))
                .hasSize(24);
        assertThat(ManagedWorkflowDailyAutomationFixtures.workflows())
                .filteredOn(workflow -> "R2".equals(workflow.getRiskLevel()))
                .hasSize(8);
        assertThat(ManagedWorkflowDailyAutomationFixtures.workflows())
                .filteredOn(workflow -> "R3".equals(workflow.getRiskLevel()))
                .hasSize(34);
        assertThat(ManagedWorkflowDailyAutomationFixtures.MANAGED_SKILL_IDS.stream()
                .filter(ManagedWorkflowDailyAutomationCatalog::canRunWithoutBusinessInput))
                .containsExactly(
                "skill.cloudmold.operations.daily-business-control.v1",
                "skill.cloudmold.operations.weekly-business-review.v1");
        assertThat(ManagedWorkflowDailyAutomationFixtures.MANAGED_SKILL_IDS.stream()
                .map(ManagedWorkflowDailyAutomationCatalog::inputStrategy))
                .filteredOn("TENANT_AGGREGATE"::equals)
                .hasSize(2);
        assertThat(ManagedWorkflowDailyAutomationFixtures.MANAGED_SKILL_IDS.stream()
                .map(ManagedWorkflowDailyAutomationCatalog::inputStrategy))
                .filteredOn("ROTATING_BUSINESS_SCENARIO"::equals)
                .hasSize(37);
        assertThat(ManagedWorkflowDailyAutomationFixtures.MANAGED_SKILL_IDS.stream()
                .map(ManagedWorkflowDailyAutomationCatalog::inputStrategy))
                .filteredOn("DOMAIN_BACKLOG"::equals)
                .containsExactly("DOMAIN_BACKLOG");
        assertThat(ManagedWorkflowDailyAutomationFixtures.MANAGED_SKILL_IDS.stream()
                .map(ManagedWorkflowDailyAutomationCatalog::inputStrategy))
                .filteredOn("EVENT_BACKLOG"::equals)
                .hasSize(26);
        assertThat(ManagedWorkflowDailyAutomationCatalog.isDailyEligible(
                "skill.cloudmold.commerce.legacy-projection-plan.v1")).isFalse();
        assertThat(ManagedWorkflowDailyAutomationCatalog.isDailyEligible(
                "skill.cloudmold.procurement.order-lifecycle.v1")).isTrue();
        assertThat(ManagedWorkflowDailyAutomationCatalog.inputStrategy(
                "skill.cloudmold.supply-planning.prepare.v1"))
                .isEqualTo("DOMAIN_BACKLOG");
        assertThat(ManagedWorkflowDailyAutomationCatalog.isTenMinuteSupplyChainWorkflow(
                "skill.cloudmold.supply.supplier-return-lifecycle.v1")).isTrue();
        assertThat(ManagedWorkflowDailyAutomationCatalog.isTenMinuteSupplyChainWorkflow(
                "skill.cloudmold.commerce.full-chain-hsf.v1")).isFalse();
        assertThat(ManagedWorkflowDailyAutomationCatalog.approvalRoute(
                "skill.cloudmold.supply-planning.sop-lifecycle.v1"))
                .isEqualTo(new ManagedWorkflowDailyAutomationCatalog.ApprovalRoute(
                        "supply-planning", "supply-planning.sop-release"));
        assertThat(ManagedWorkflowDailyAutomationCatalog.approvalRoute(
                "skill.cloudmold.commerce.product-to-listing.v1"))
                .isEqualTo(new ManagedWorkflowDailyAutomationCatalog.ApprovalRoute(
                        "product-listing-operator", "catalog.publish"));
        assertThat(ManagedWorkflowDailyAutomationCatalog.approvalRoute(
                "skill.cloudmold.merchant.managed-growth-lifecycle.v1"))
                .isEqualTo(new ManagedWorkflowDailyAutomationCatalog.ApprovalRoute(
                        "merchant-managed-growth-operator", "merchant.managed-growth.lifecycle"));
        assertThat(ManagedWorkflowDailyAutomationCatalog.approvalRoute(
                "skill.cloudmold.risk.dispute-resolution-lifecycle.v1"))
                .isEqualTo(new ManagedWorkflowDailyAutomationCatalog.ApprovalRoute(
                        "risk-operations", "risk.dispute-resolve"));
        assertThat(ManagedWorkflowDailyAutomationCatalog.approvalRoute(
                "skill.cloudmold.data-ai-operations.data-quality-recovery-lifecycle.v1"))
                .isEqualTo(new ManagedWorkflowDailyAutomationCatalog.ApprovalRoute(
                        "data-ai-operations", "data-quality.recover"));
        assertThat(ManagedWorkflowDailyAutomationCatalog.approvalRoute(
                "skill.cloudmold.crossborder.fulfillment-compliance-lifecycle.v1"))
                .isEqualTo(new ManagedWorkflowDailyAutomationCatalog.ApprovalRoute(
                        "crossborder-operations", "crossborder.fulfillment-compliance"));
        assertThat(ManagedWorkflowDailyAutomationCatalog.approvalRoute(
                "skill.cloudmold.crossborder.bonded-customs-lifecycle.v1"))
                .isEqualTo(new ManagedWorkflowDailyAutomationCatalog.ApprovalRoute(
                        "bonded-customs-operations", "crossborder.bonded-customs"));
        assertThat(ManagedWorkflowDailyAutomationCatalog.approvalRoute(
                "skill.cloudmold.commerce.category-daily-operations.v1"))
                .isEqualTo(new ManagedWorkflowDailyAutomationCatalog.ApprovalRoute(
                        "category-operations", "category.daily-operations"));
        assertThat(ManagedWorkflowDailyAutomationCatalog.approvalRoute(
                "skill.cloudmold.partner-marketing.kol-media-operations.v1"))
                .isEqualTo(new ManagedWorkflowDailyAutomationCatalog.ApprovalRoute(
                        "partner-marketing-operations",
                        "partner-marketing.kol-media-operations"));
        assertThat(ManagedWorkflowDailyAutomationCatalog.approvalRoute(
                "skill.cloudmold.mes.production-execution-lifecycle.v1"))
                .isEqualTo(new ManagedWorkflowDailyAutomationCatalog.ApprovalRoute(
                        "production-supervisor", "production.execute"));
        assertThat(ManagedWorkflowDailyAutomationCatalog.approvalRoute(
                "skill.cloudmold.commerce.product-management-lifecycle.v1"))
                .isEqualTo(new ManagedWorkflowDailyAutomationCatalog.ApprovalRoute(
                        "product-operations", "product.management"));
        assertThat(ManagedWorkflowDailyAutomationCatalog.approvalRoute(
                "skill.cloudmold.supply.warehouse-admission-lifecycle.v1"))
                .isEqualTo(new ManagedWorkflowDailyAutomationCatalog.ApprovalRoute(
                        "supply-chain-operator", "warehouse.admission"));
        assertThat(ManagedWorkflowDailyAutomationCatalog.approvalRoute(
                "skill.cloudmold.customer-experience.ticket-responsibility-lifecycle.v1"))
                .isEqualTo(new ManagedWorkflowDailyAutomationCatalog.ApprovalRoute(
                        "consumer-experience-operator",
                        "customer-experience.ticket-responsibility"));
        assertThat(ManagedWorkflowDailyAutomationCatalog.approvalRoute(
                "skill.cloudmold.merchant.managed-growth-lifecycle.v1"))
                .isEqualTo(new ManagedWorkflowDailyAutomationCatalog.ApprovalRoute(
                        "merchant-managed-growth-operator",
                        "merchant.managed-growth.lifecycle"));
        assertThat(ManagedWorkflowDailyAutomationCatalog.approvalRoute(
                "skill.cloudmold.merchant-experience.rectification-lifecycle.v1"))
                .isEqualTo(new ManagedWorkflowDailyAutomationCatalog.ApprovalRoute(
                        "merchant-experience-operator",
                        "merchant-experience.rectification"));
        assertThat(ManagedWorkflowDailyAutomationCatalog.approvalRoute(
                "skill.cloudmold.customer-experience.unfulfillable-order-compensation-lifecycle.v1"))
                .isEqualTo(new ManagedWorkflowDailyAutomationCatalog.ApprovalRoute(
                        "consumer-compensation-operator",
                        "customer-experience.unfulfillable-compensation"));
        assertThat(ManagedWorkflowDailyAutomationCatalog.approvalRoute(
                "skill.cloudmold.finance.logistics-service-settlement-lifecycle.v1"))
                .isEqualTo(new ManagedWorkflowDailyAutomationCatalog.ApprovalRoute(
                        "logistics-settlement-operator",
                        "finance.logistics-settlement"));
        assertThat(ManagedWorkflowDailyAutomationCatalog.approvalRoute(
                "skill.cloudmold.finance.merchant-service-fee-settlement-lifecycle.v1"))
                .isEqualTo(new ManagedWorkflowDailyAutomationCatalog.ApprovalRoute(
                        "merchant-settlement-operator",
                        "finance.merchant-service-fee-settlement"));
        assertThat(ManagedWorkflowDailyAutomationCatalog.approvalRoute(
                "skill.cloudmold.finance.advertising-fee-settlement-lifecycle.v1"))
                .isEqualTo(new ManagedWorkflowDailyAutomationCatalog.ApprovalRoute(
                        "advertising-settlement-operator",
                        "finance.advertising-fee-settlement"));
        assertThat(ManagedWorkflowDailyAutomationCatalog.approvalRoute(
                "skill.cloudmold.finance.profit-loss-improvement-lifecycle.v1"))
                .isEqualTo(new ManagedWorkflowDailyAutomationCatalog.ApprovalRoute(
                        "profit-loss-operator",
                        "finance.profit-loss-improvement"));
        assertThat(ManagedWorkflowDailyAutomationCatalog.approvalRoute(
                "skill.cloudmold.inventory.stock-transfer-lifecycle.v1"))
                .isEqualTo(new ManagedWorkflowDailyAutomationCatalog.ApprovalRoute(
                        "inventory-transfer-operator", "inventory.stock-transfer"));
        assertThat(ManagedWorkflowDailyAutomationCatalog.approvalRoute(
                "skill.cloudmold.warehouse.stock-transfer-lifecycle.v1"))
                .isEqualTo(new ManagedWorkflowDailyAutomationCatalog.ApprovalRoute(
                        "inventory-transfer-operator", "warehouse.stock-transfer"));
        assertThat(ManagedWorkflowDailyAutomationCatalog.approvalRoute(
                "skill.cloudmold.finance.supplier-return-finalization-lifecycle.v1"))
                .isEqualTo(new ManagedWorkflowDailyAutomationCatalog.ApprovalRoute(
                        "supplier-return-accountant", "finance.supplier-return-finalization"));
    }

    @Test
    void shouldFailClosedWhenApprovalWorkflowHasNoGovernanceRoute() {
        AiOperationsTemporalSeedProperties properties = new AiOperationsTemporalSeedProperties();
        ManagedSkillTaskWorkflowView ungoverned = ManagedSkillTaskWorkflowView.builder()
                .skillId("skill.cloudmold.fixture.unmapped-write.v1")
                .skillVersion("1.0.0")
                .displayName("未配置审批路由")
                .definitionClosureSha256("b".repeat(64))
                .approvalRequired(true)
                .build();

        assertThatThrownBy(() ->
                ManagedWorkflowDailyAutomationCatalog.dailyRequest(ungoverned, properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Approval route is missing")
                .hasMessageContaining(ungoverned.getSkillId());
    }
}
