package cn.iocoder.yudao.module.cloudmold.engagement.service.workflow;

import cn.iocoder.yudao.module.cloudmold.engagement.api.EngagementQueryApi;
import cn.iocoder.yudao.module.cloudmold.engagement.api.EngagementQueryApi.NotificationCampaignView;
import cn.iocoder.yudao.module.cloudmold.engagement.api.EngagementQueryApi.NotificationDeliveryView;
import cn.iocoder.yudao.module.cloudmold.engagement.api.workflow.MarketingGrowthWorkflowResult.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MarketingGrowthWorkflowQueryServiceTest {

    private final EngagementQueryApi queryApi = mock(EngagementQueryApi.class);
    private final MarketingGrowthWorkflowQueryService service = new MarketingGrowthWorkflowQueryService(queryApi);

    @Test
    void activeCampaignWithoutDeliveryRemainsWaiting() {
        when(queryApi.getNotificationCampaign("campaign-1")).thenReturn(campaign("ACTIVE", 2L));

        assertThat(service.inspectCampaignLifecycle("campaign-1", null)).satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo(Status.WAITING);
            assertThat(result.getSummary()).contains("不能在缺少投放实例时宣称完成");
            assertThat(result.getArtifacts()).hasSize(1);
        });
    }

    @Test
    void completedCampaignWithClickedDeliverySucceeds() {
        when(queryApi.getNotificationCampaign("campaign-1")).thenReturn(campaign("COMPLETED", 3L));
        when(queryApi.getNotificationDelivery("delivery-1")).thenReturn(delivery("campaign-1", "CLICKED", 4L));

        assertThat(service.inspectCampaignLifecycle("campaign-1", "delivery-1")).satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo(Status.SUCCEEDED);
            assertThat(result.getTerminal()).isTrue();
            assertThat(result.getSummary()).contains("已点击");
            assertThat(result.getArtifacts()).hasSize(2);
        });
    }

    @Test
    void deliveryFromAnotherCampaignFailsClosed() {
        when(queryApi.getNotificationCampaign("campaign-1")).thenReturn(campaign("ACTIVE", 2L));
        when(queryApi.getNotificationDelivery("delivery-1")).thenReturn(delivery("campaign-2", "SENT", 2L));

        assertThat(service.inspectCampaignLifecycle("campaign-1", "delivery-1")).satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo(Status.FAILED);
            assertThat(result.getBlockers()).containsExactly("投放 delivery-1 关联活动 campaign-2");
        });
    }

    @Test
    void growthExperimentDoesNotInventSuccessWithoutAuthority() {
        assertThat(service.inspectGrowthExperiment("experiment-1")).satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo(Status.PREPARE);
            assertThat(result.getTerminal()).isFalse();
            assertThat(result.getBlockers()).anyMatch(value -> value.contains("System of Record"));
            assertThat(result.getArtifacts()).isEmpty();
        });
        verifyNoInteractions(queryApi);
    }

    private static NotificationCampaignView campaign(String status, long version) {
        return NotificationCampaignView.builder().campaignId("campaign-1").campaignCode("CMP-1")
                .campaignName("夏季新品召回").channel("APP_PUSH").status(status).version(version).build();
    }

    private static NotificationDeliveryView delivery(String campaignId, String status, long version) {
        return NotificationDeliveryView.builder().deliveryId("delivery-1").deliveryKey("D-1")
                .campaignId(campaignId).principalId("principal-1").channel("APP_PUSH")
                .status(status).attemptCount(1).receiptCount(1).version(version).build();
    }
}
