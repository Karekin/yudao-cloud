package cn.iocoder.yudao.module.cloudmold.engagement.api.workflow;

/**
 * Governed, repeatable read contracts for campaign and experiment workflows.
 */
public interface MarketingGrowthWorkflowQueryApi {

    MarketingGrowthWorkflowResult inspectCampaignLifecycle(String campaignId, String deliveryId);

    MarketingGrowthWorkflowResult inspectGrowthExperiment(String experimentId);
}
