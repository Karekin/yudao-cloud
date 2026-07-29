package cn.iocoder.yudao.module.cloudmold.engagement.service.workflow;

import cn.iocoder.yudao.module.cloudmold.engagement.api.EngagementQueryApi;
import cn.iocoder.yudao.module.cloudmold.engagement.api.EngagementQueryApi.NotificationCampaignView;
import cn.iocoder.yudao.module.cloudmold.engagement.api.EngagementQueryApi.NotificationDeliveryView;
import cn.iocoder.yudao.module.cloudmold.engagement.api.workflow.MarketingGrowthWorkflowResult.Status;
import cn.iocoder.yudao.module.cloudmold.promotion.api.PromotionAggregateView;
import cn.iocoder.yudao.module.cloudmold.promotion.api.PromotionQueryApi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MarketingGrowthWorkflowQueryServiceTest {

    private final EngagementQueryApi queryApi = mock(EngagementQueryApi.class);
    private final PromotionQueryApi promotionQueryApi = mock(PromotionQueryApi.class);
    private final MarketingGrowthWorkflowQueryService service =
            new MarketingGrowthWorkflowQueryService(queryApi, promotionQueryApi);

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
    void runningGrowthExperimentDoesNotInventSuccessWithoutEvidence() {
        when(promotionQueryApi.get("PROMOTION_GROWTH_EXPERIMENT", "experiment-1"))
                .thenReturn(experiment("RUNNING", Map.of(
                        "minimum_sample_size_per_variant", 30,
                        "variants", List.of(Map.of(
                                "variant_code", "CONTROL",
                                "exposure_count", 12
                        ), Map.of(
                                "variant_code", "TREATMENT",
                                "exposure_count", 10
                        ))
                )));

        assertThat(service.inspectGrowthExperiment("experiment-1")).satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo(Status.RUNNING);
            assertThat(result.getTerminal()).isFalse();
            assertThat(result.getBlockers()).anyMatch(value -> value.contains("最小曝光样本"));
            assertThat(result.getArtifacts()).hasSize(3);
        });
    }

    @Test
    void concludedGrowthExperimentRequiresAndReportsAuditableEvidence() {
        when(promotionQueryApi.get("PROMOTION_GROWTH_EXPERIMENT", "experiment-1"))
                .thenReturn(experiment("CONCLUDED", Map.of(
                        "minimum_sample_size_per_variant", 30,
                        "confidence_basis_points", 9700,
                        "guardrail_status", "PASSED",
                        "conclusion_evidence_ref", "evidence://experiment/conclusion",
                        "decision", "TREATMENT",
                        "variants", List.of(variantEvidence("CONTROL", 30, 30),
                                variantEvidence("TREATMENT", 30, 30))
                )));

        assertThat(service.inspectGrowthExperiment("experiment-1")).satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo(Status.SUCCEEDED);
            assertThat(result.getTerminal()).isTrue();
            assertThat(result.getSummary()).contains("95%").contains("TREATMENT");
            assertThat(result.getBlockers()).isEmpty();
        });
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

    private static PromotionAggregateView experiment(String status, Map<String, Object> attributes) {
        return PromotionAggregateView.builder().aggregateType("promotion_growth_experiment")
                .aggregateId("experiment-1").businessCode("EXP-1").status(status)
                .version(3L).attributes(attributes).build();
    }

    private static Map<String, Object> variantEvidence(String code, int exposureCount, int sampleCount) {
        return Map.of("variant_code", code, "exposure_count", exposureCount,
                "latest_sample_count", sampleCount,
                "metric_evidence_ref", "evidence://experiment/" + code.toLowerCase());
    }
}
