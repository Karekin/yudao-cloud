package cn.iocoder.yudao.module.cloudmold.engagement.api.workflow;

public interface MarketingGrowthWorkflowQueryPort {

    MarketingGrowthWorkflowResult inspectCampaignLifecycle(String campaignId, String deliveryId);

    MarketingGrowthWorkflowResult inspectGrowthExperiment(String experimentId);
}
