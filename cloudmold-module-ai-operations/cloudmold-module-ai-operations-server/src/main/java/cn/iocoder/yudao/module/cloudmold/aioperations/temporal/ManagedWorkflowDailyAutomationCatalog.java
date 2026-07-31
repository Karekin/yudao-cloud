package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo.TemporalScheduleCreateReqVO;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;

import java.util.Map;
import java.util.Set;

/**
 * Daily automation policy for every managed SkillTask definition.
 *
 * <p>The Temporal Schedule always runs. Workflows that need a concrete business
 * object use a discovery envelope and finish with {@code NO_ACTION_DUE} until a
 * domain event or a future candidate scanner materializes a safe input. This is
 * deliberately different from submitting an invalid {@code {}} payload.</p>
 */
final class ManagedWorkflowDailyAutomationCatalog {

    static final String DAILY_DISCOVERY_INPUT = """
            {"_cloudmoldAutomation":{"schemaVersion":"1.0","mode":"DAILY_DISCOVERY"}}
            """.trim();

    private static final long DAILY_INTERVAL_SECONDS = 86_400L;
    private static final int DEFAULT_MAX_FAN_OUT = 100;

    private static final Set<String> INPUT_FREE_WORKFLOWS = Set.of(
            "skill.cloudmold.operations.daily-business-control.v1",
            "skill.cloudmold.operations.weekly-business-review.v1"
    );

    private static final Set<String> ROTATING_BUSINESS_SCENARIOS = Set.of(
            "skill.cloudmold.commerce.catalog-matrix.v1",
            "skill.cloudmold.commerce.aftersale-saga.v1",
            "skill.cloudmold.commerce.order-cancellation-operational.v1",
            "skill.cloudmold.commerce.product-to-listing.v1",
            "skill.cloudmold.pricing.reprice-lifecycle.v1",
            "skill.cloudmold.commerce.full-chain-hsf.v1",
            "skill.cloudmold.commerce.autonomous-day.v1",
            "skill.cloudmold.commerce.category-daily-operations.v1",
            "skill.cloudmold.commerce.product-management-lifecycle.v1",
            "skill.cloudmold.customer-experience.ticket-responsibility-lifecycle.v1",
            "skill.cloudmold.customer-experience.unfulfillable-order-compensation-lifecycle.v1",
            "skill.cloudmold.consumer.shopping-journey.v1",
            "skill.cloudmold.merchant.onboarding-lifecycle.v1",
            "skill.cloudmold.merchant-experience.rectification-lifecycle.v1",
            "skill.cloudmold.engagement.promotion-campaign-operations.v1",
            "skill.cloudmold.growth.experiment-lifecycle.v1",
            "skill.cloudmold.supplier.sourcing-lifecycle.v1",
            "skill.cloudmold.procurement.order-lifecycle.v1",
            "skill.cloudmold.wms.operations.v1",
            "skill.cloudmold.supply.replenishment-lifecycle.v1",
            "skill.cloudmold.supply.warehouse-admission-lifecycle.v1",
            "skill.cloudmold.supply-planning.sop-lifecycle.v1",
            "skill.cloudmold.customer-service.resolution-lifecycle.v1",
            "skill.cloudmold.finance.close-lifecycle.v1",
            "skill.cloudmold.finance.logistics-service-settlement-lifecycle.v1",
            "skill.cloudmold.finance.merchant-service-fee-settlement-lifecycle.v1",
            "skill.cloudmold.finance.advertising-fee-settlement-lifecycle.v1",
            "skill.cloudmold.finance.profit-loss-improvement-lifecycle.v1",
            "skill.cloudmold.risk.dispute-resolution-lifecycle.v1",
            "skill.cloudmold.data-ai-operations.data-quality-recovery-lifecycle.v1",
            "skill.cloudmold.quality.inspection-recall-lifecycle.v1",
            "skill.cloudmold.fulfillment.exception-resolution-lifecycle.v1",
            "skill.cloudmold.crossborder.fulfillment-compliance-lifecycle.v1",
            "skill.cloudmold.crossborder.bonded-customs-lifecycle.v1",
            "skill.cloudmold.partner-marketing.kol-media-operations.v1",
            "skill.cloudmold.mes.production-execution-lifecycle.v1",
            "skill.cloudmold.catalog.assortment-planning-lifecycle.v1",
            "skill.cloudmold.commerce.reuse-ready-master.v1",
            "skill.cloudmold.commerce.legacy-projection-plan.v1"
    );

    private static final Set<String> DOMAIN_BACKLOG_WORKFLOWS = Set.of(
            "skill.cloudmold.supply-planning.prepare.v1"
    );

    private static final Map<String, ApprovalRoute> APPROVAL_ROUTES = Map.ofEntries(
            Map.entry("skill.cloudmold.commerce.catalog-matrix.v1",
                    new ApprovalRoute("merchandising", "catalog.define")),
            Map.entry("skill.cloudmold.commerce.aftersale-saga.v1",
                    new ApprovalRoute("customer-service", "aftersale.refund")),
            Map.entry("skill.cloudmold.commerce.order-cancellation-operational.v1",
                    new ApprovalRoute("customer-service", "customer-service.compensate")),
            Map.entry("skill.cloudmold.agentcontrol.mission-lifecycle-stockout.v1",
                    new ApprovalRoute("inventory-control", "mission.stockout.start")),
            Map.entry("skill.cloudmold.commerce.reuse-ready-master.v1",
                    new ApprovalRoute("merchant-operations", "merchant.activate")),
            Map.entry("skill.cloudmold.commerce.full-chain-hsf.v1",
                    new ApprovalRoute("operations-control", "commerce.full-chain")),
            Map.entry("skill.cloudmold.commerce.product-to-listing.v1",
                    new ApprovalRoute("product-listing-operator", "catalog.publish")),
            Map.entry("skill.cloudmold.pricing.reprice-lifecycle.v1",
                    new ApprovalRoute("pricing-revenue-operator", "pricing.reprice")),
            Map.entry("skill.cloudmold.commerce.autonomous-day.v1",
                    new ApprovalRoute("operations-control", "commerce.autonomous-day")),
            Map.entry("skill.cloudmold.commerce.category-daily-operations.v1",
                    new ApprovalRoute("category-operations", "category.daily-operations")),
            Map.entry("skill.cloudmold.commerce.product-management-lifecycle.v1",
                    new ApprovalRoute("product-operations", "product.management")),
            Map.entry("skill.cloudmold.customer-experience.ticket-responsibility-lifecycle.v1",
                    new ApprovalRoute("consumer-experience-operator",
                            "customer-experience.ticket-responsibility")),
            Map.entry("skill.cloudmold.customer-experience.unfulfillable-order-compensation-lifecycle.v1",
                    new ApprovalRoute("consumer-compensation-operator",
                            "customer-experience.unfulfillable-compensation")),
            Map.entry("skill.cloudmold.consumer.shopping-journey.v1",
                    new ApprovalRoute("operations-control", "consumer.journey")),
            Map.entry("skill.cloudmold.merchant.onboarding-lifecycle.v1",
                    new ApprovalRoute("merchant-operations", "merchant.onboarding")),
            Map.entry("skill.cloudmold.merchant-experience.rectification-lifecycle.v1",
                    new ApprovalRoute("merchant-experience-operator",
                            "merchant-experience.rectification")),
            Map.entry("skill.cloudmold.engagement.promotion-campaign-operations.v1",
                    new ApprovalRoute("growth-marketing", "campaign.operate")),
            Map.entry("skill.cloudmold.growth.experiment-lifecycle.v1",
                    new ApprovalRoute("growth-marketing", "experiment.conclude")),
            Map.entry("skill.cloudmold.supplier.sourcing-lifecycle.v1",
                    new ApprovalRoute("procurement", "supplier.award")),
            Map.entry("skill.cloudmold.procurement.order-lifecycle.v1",
                    new ApprovalRoute("procurement", "purchase-order.dispatch")),
            Map.entry("skill.cloudmold.wms.operations.v1",
                    new ApprovalRoute("warehouse-operations", "warehouse.physical-cycle")),
            Map.entry("skill.cloudmold.supply.replenishment-lifecycle.v1",
                    new ApprovalRoute("supply-planning", "replenishment.end-to-end")),
            Map.entry("skill.cloudmold.supply.warehouse-admission-lifecycle.v1",
                    new ApprovalRoute("supply-chain-operator", "warehouse.admission")),
            Map.entry("skill.cloudmold.supply-planning.sop-lifecycle.v1",
                    new ApprovalRoute("supply-planning", "supply-planning.sop-release")),
            Map.entry("skill.cloudmold.customer-service.resolution-lifecycle.v1",
                    new ApprovalRoute("customer-service", "ticket.resolve")),
            Map.entry("skill.cloudmold.finance.close-lifecycle.v1",
                    new ApprovalRoute("finance-operations", "finance.period-close")),
            Map.entry("skill.cloudmold.finance.logistics-service-settlement-lifecycle.v1",
                    new ApprovalRoute("logistics-settlement-operator",
                            "finance.logistics-settlement")),
            Map.entry("skill.cloudmold.finance.merchant-service-fee-settlement-lifecycle.v1",
                    new ApprovalRoute("merchant-settlement-operator",
                            "finance.merchant-service-fee-settlement")),
            Map.entry("skill.cloudmold.finance.advertising-fee-settlement-lifecycle.v1",
                    new ApprovalRoute("advertising-settlement-operator",
                            "finance.advertising-fee-settlement")),
            Map.entry("skill.cloudmold.finance.profit-loss-improvement-lifecycle.v1",
                    new ApprovalRoute("profit-loss-operator",
                            "finance.profit-loss-improvement")),
            Map.entry("skill.cloudmold.risk.dispute-resolution-lifecycle.v1",
                    new ApprovalRoute("risk-operations", "risk.dispute-resolve")),
            Map.entry("skill.cloudmold.data-ai-operations.data-quality-recovery-lifecycle.v1",
                    new ApprovalRoute("data-ai-operations", "data-quality.recover")),
            Map.entry("skill.cloudmold.quality.inspection-recall-lifecycle.v1",
                    new ApprovalRoute("quality-operations", "quality.inspection-recall")),
            Map.entry("skill.cloudmold.fulfillment.exception-resolution-lifecycle.v1",
                    new ApprovalRoute("logistics-operations", "fulfillment.exception-resolution")),
            Map.entry("skill.cloudmold.crossborder.fulfillment-compliance-lifecycle.v1",
                    new ApprovalRoute("crossborder-operations",
                            "crossborder.fulfillment-compliance")),
            Map.entry("skill.cloudmold.crossborder.bonded-customs-lifecycle.v1",
                    new ApprovalRoute("bonded-customs-operations",
                            "crossborder.bonded-customs")),
            Map.entry("skill.cloudmold.partner-marketing.kol-media-operations.v1",
                    new ApprovalRoute("partner-marketing-operations",
                            "partner-marketing.kol-media-operations")),
            Map.entry("skill.cloudmold.mes.production-execution-lifecycle.v1",
                    new ApprovalRoute("production-supervisor",
                            "production.execute")),
            Map.entry("skill.cloudmold.catalog.assortment-planning-lifecycle.v1",
                    new ApprovalRoute("assortment-manager",
                            "assortment.plan-release")),
            Map.entry("skill.cloudmold.mes.production-readiness.v1",
                    new ApprovalRoute("production-supervisor",
                            "production.prepare")),
            Map.entry("skill.cloudmold.consumer.in-transit-order-scenario.v1",
                    new ApprovalRoute("logistics-operations", "fulfillment.exception-resolution")),
            Map.entry("skill.cloudmold.consumer.paid-unshipped-order-scenario.v1",
                    new ApprovalRoute("order-exception-operator",
                            "customer-service.compensate")),
            Map.entry("skill.cloudmold.supply-planning.prepare.v1",
                    new ApprovalRoute("supply-planning", "replenishment.convert"))
    );

    private ManagedWorkflowDailyAutomationCatalog() {
    }

    static TemporalScheduleCreateReqVO dailyRequest(ManagedSkillTaskWorkflowView workflow,
                                                    AiOperationsTemporalSeedProperties properties) {
        TemporalScheduleCreateReqVO request = new TemporalScheduleCreateReqVO();
        request.setScheduleId(scheduleCode(workflow.getSkillId()));
        request.setDisplayName(workflow.getDisplayName() + "（每日自动）");
        request.setDescription("每天由 Temporal 执行到期工作发现；无可物化业务输入时安全返回 NO_ACTION_DUE，"
                + "不发起审批、不写业务数据。");
        request.setSkillId(workflow.getSkillId());
        request.setSkillVersion(workflow.getSkillVersion());
        request.setInputJson(DAILY_DISCOVERY_INPUT);
        request.setIntervalSeconds(DAILY_INTERVAL_SECONDS);
        request.setTimeZone(properties.getTimeZone());
        request.setPaused(properties.isPaused());
        if (Boolean.TRUE.equals(workflow.getApprovalRequired())) {
            ApprovalRoute route = APPROVAL_ROUTES.get(workflow.getSkillId());
            if (route == null) {
                throw new IllegalStateException(
                        "Approval route is missing for managed daily workflow: " + workflow.getSkillId());
            }
            request.setRoleCode(route.roleCode());
            request.setActionCode(route.actionCode());
        }
        return request;
    }

    static TemporalDailyDispatchRequest dispatchRequest(
            Long tenantId, String scheduleId, ManagedSkillTaskWorkflowView workflow,
            AiOperationsTemporalSeedProperties properties) {
        TemporalScheduleCreateReqVO schedule = dailyRequest(workflow, properties);
        return TemporalDailyDispatchRequest.builder()
                .tenantId(tenantId)
                .scheduleId(scheduleId)
                .skillId(workflow.getSkillId())
                .skillVersion(workflow.getSkillVersion())
                .inputStrategy(inputStrategy(workflow.getSkillId()))
                .maxFanOut(DEFAULT_MAX_FAN_OUT)
                .operatorUserId(properties.getOperatorUserId())
                .operatorUserType(properties.getOperatorUserType())
                .roleCode(schedule.getRoleCode())
                .actionCode(schedule.getActionCode())
                .approvalTimeoutSeconds(86_400L)
                .businessEventTimeoutSeconds(300L)
                .timeZone(properties.getTimeZone())
                .build();
    }

    static String cronExpression(String skillId) {
        int hash = Math.floorMod(skillId.hashCode(), 180);
        int hour = 2 + hash / 60;
        int minute = hash % 60;
        return minute + " " + hour + " * * *";
    }

    static String inputStrategy(String skillId) {
        if (INPUT_FREE_WORKFLOWS.contains(skillId)) {
            return "TENANT_AGGREGATE";
        }
        if (ROTATING_BUSINESS_SCENARIOS.contains(skillId)) {
            return "ROTATING_BUSINESS_SCENARIO";
        }
        if (DOMAIN_BACKLOG_WORKFLOWS.contains(skillId)) {
            return "DOMAIN_BACKLOG";
        }
        return "EVENT_BACKLOG";
    }

    static String desiredPolicySha256(ManagedSkillTaskWorkflowView workflow,
                                      AiOperationsTemporalSeedProperties properties) {
        TemporalScheduleCreateReqVO request = dailyRequest(workflow, properties);
        String canonical = workflow.getSkillId() + "|" + workflow.getSkillVersion()
                + "|" + workflow.getDefinitionClosureSha256()
                + "|" + cronExpression(workflow.getSkillId())
                + "|" + inputStrategy(workflow.getSkillId())
                + "|" + properties.getTimeZone()
                + "|" + request.getRoleCode()
                + "|" + request.getActionCode()
                + "|" + DEFAULT_MAX_FAN_OUT;
        return DigestUtil.sha256Hex(canonical);
    }

    static boolean isDailyDiscovery(String inputJson) {
        return DAILY_DISCOVERY_INPUT.equals(inputJson);
    }

    static boolean canRunWithoutBusinessInput(String skillId) {
        return INPUT_FREE_WORKFLOWS.contains(skillId);
    }

    static boolean isRotatingBusinessScenario(String skillId) {
        return ROTATING_BUSINESS_SCENARIOS.contains(skillId);
    }

    static boolean isDomainBacklog(String skillId) {
        return DOMAIN_BACKLOG_WORKFLOWS.contains(skillId);
    }

    static ApprovalRoute approvalRoute(String skillId) {
        return APPROVAL_ROUTES.get(skillId);
    }

    private static String scheduleCode(String skillId) {
        String code = skillId;
        if (code.startsWith("skill.cloudmold.")) {
            code = code.substring("skill.cloudmold.".length());
        }
        code = code.replaceAll("\\.v\\d+$", "")
                .replaceAll("[^a-zA-Z0-9._-]", "-")
                .replace('.', '-')
                .toLowerCase();
        return "managed-daily-" + code;
    }

    record ApprovalRoute(String roleCode, String actionCode) {
    }
}
