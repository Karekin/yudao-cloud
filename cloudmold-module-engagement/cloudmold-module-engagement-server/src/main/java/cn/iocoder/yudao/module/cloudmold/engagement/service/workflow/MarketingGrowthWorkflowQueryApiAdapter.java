package cn.iocoder.yudao.module.cloudmold.engagement.service.workflow;

import cn.iocoder.yudao.module.cloudmold.engagement.api.workflow.MarketingGrowthWorkflowQueryApi;
import cn.iocoder.yudao.module.cloudmold.engagement.api.workflow.MarketingGrowthWorkflowQueryPort;
import cn.iocoder.yudao.module.cloudmold.engagement.api.workflow.MarketingGrowthWorkflowResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarketingGrowthWorkflowQueryApiAdapter implements MarketingGrowthWorkflowQueryApi {

    private final MarketingGrowthWorkflowQueryPort delegate;

    @Override
    public MarketingGrowthWorkflowResult inspectCampaignLifecycle(String campaignId, String deliveryId) {
        return delegate.inspectCampaignLifecycle(campaignId, deliveryId);
    }

    @Override
    public MarketingGrowthWorkflowResult inspectGrowthExperiment(String experimentId) {
        return delegate.inspectGrowthExperiment(experimentId);
    }
}
