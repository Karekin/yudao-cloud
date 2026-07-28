package cn.iocoder.yudao.module.cloudmold.engagement.service.workflow;

import cn.iocoder.yudao.module.cloudmold.engagement.api.EngagementQueryApi;
import cn.iocoder.yudao.module.cloudmold.engagement.api.EngagementQueryApi.NotificationCampaignView;
import cn.iocoder.yudao.module.cloudmold.engagement.api.EngagementQueryApi.NotificationDeliveryView;
import cn.iocoder.yudao.module.cloudmold.engagement.api.workflow.MarketingGrowthWorkflowQueryPort;
import cn.iocoder.yudao.module.cloudmold.engagement.api.workflow.MarketingGrowthWorkflowResult;
import cn.iocoder.yudao.module.cloudmold.engagement.api.workflow.MarketingGrowthWorkflowResult.Artifact;
import cn.iocoder.yudao.module.cloudmold.engagement.api.workflow.MarketingGrowthWorkflowResult.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MarketingGrowthWorkflowQueryService implements MarketingGrowthWorkflowQueryPort {

    private static final String CAMPAIGN_LIFECYCLE = "CampaignLifecycleWorkflow";
    private static final String GROWTH_EXPERIMENT = "GrowthExperimentWorkflow";

    private final EngagementQueryApi engagementQueryApi;

    @Override
    public MarketingGrowthWorkflowResult inspectCampaignLifecycle(String campaignId, String deliveryId) {
        requireText(campaignId, "campaignId");
        String key = campaignId.trim();
        NotificationCampaignView campaign = engagementQueryApi.getNotificationCampaign(key);
        List<Artifact> artifacts = new ArrayList<>();
        artifacts.add(artifact("NOTIFICATION_CAMPAIGN", campaign.getCampaignId(), campaign.getStatus(),
                campaign.getVersion(), "营销活动 " + campaign.getCampaignName()));

        if (!StringUtils.hasText(deliveryId)) {
            return withoutDelivery(campaign, artifacts);
        }

        NotificationDeliveryView delivery = engagementQueryApi.getNotificationDelivery(deliveryId.trim());
        if (!Objects.equals(campaign.getCampaignId(), delivery.getCampaignId())) {
            return result(CAMPAIGN_LIFECYCLE, key, Status.FAILED, "投放归属校验", true, true,
                    "投放实例不属于当前营销活动", campaign.getVersion(),
                    List.of("投放 " + delivery.getDeliveryId() + " 关联活动 " + delivery.getCampaignId()),
                    List.of("使用当前活动的投放实例重新检查"), artifacts);
        }
        artifacts.add(artifact("NOTIFICATION_DELIVERY", delivery.getDeliveryId(), delivery.getStatus(),
                delivery.getVersion(), "营销触达 " + delivery.getDeliveryKey()));

        if ("PAUSED".equals(campaign.getStatus())) {
            return result(CAMPAIGN_LIFECYCLE, key, Status.WAITING, "活动暂停", false, true,
                    "营销活动已暂停，Temporal 不会继续创建新触达", campaign.getVersion(),
                    List.of("活动状态为 PAUSED"), List.of("运营主体确认后恢复或结束活动"), artifacts);
        }
        if ("DRAFT".equals(campaign.getStatus())) {
            return result(CAMPAIGN_LIFECYCLE, key, Status.WAITING, "活动启用", false, true,
                    "营销活动仍为草稿，现有投放证据与活动状态不一致", campaign.getVersion(),
                    List.of("活动尚未启用"), List.of("核对投放来源并启用活动"), artifacts);
        }

        return switch (delivery.getStatus()) {
            case "QUEUED" -> result(CAMPAIGN_LIFECYCLE, key, Status.RUNNING, "等待渠道发送", false, false,
                    "营销触达已进入发送队列", delivery.getVersion(), List.of(),
                    List.of("等待渠道发送回执"), artifacts);
            case "SENT" -> result(CAMPAIGN_LIFECYCLE, key, Status.RUNNING, "等待用户回执", false, false,
                    "营销触达已发送，等待送达或互动回执", delivery.getVersion(), List.of(),
                    List.of("等待渠道送达、打开或点击回执"), artifacts);
            case "FAILED" -> result(CAMPAIGN_LIFECYCLE, key, Status.FAILED, "触达失败", true, true,
                    "营销触达失败，未形成有效用户触达", delivery.getVersion(),
                    List.of("投放状态为 FAILED"), List.of("核对渠道错误并决定是否重试"), artifacts);
            case "DELIVERED", "OPENED", "CLICKED" -> completedDelivery(campaign, delivery, artifacts);
            default -> result(CAMPAIGN_LIFECYCLE, key, Status.WAITING, "状态核对", false, true,
                    "营销触达处于未识别状态", delivery.getVersion(),
                    List.of("未知投放状态：" + delivery.getStatus()), List.of("人工核对渠道回执"), artifacts);
        };
    }

    @Override
    public MarketingGrowthWorkflowResult inspectGrowthExperiment(String experimentId) {
        requireText(experimentId, "experimentId");
        String key = experimentId.trim();
        return result(GROWTH_EXPERIMENT, key, Status.PREPARE, "实验权威准备", false, true,
                "增长实验尚不能自动执行：当前没有可审计的实验权威模型", 0L,
                List.of("缺少实验、分流、曝光、对照组和结论的规范 System of Record"),
                List.of("建立增长实验权威模型", "固化分流与曝光证据", "接入指标新鲜度和显著性门禁"),
                List.of());
    }

    private static MarketingGrowthWorkflowResult withoutDelivery(NotificationCampaignView campaign,
                                                                  List<Artifact> artifacts) {
        String status = campaign.getStatus();
        if ("DRAFT".equals(status)) {
            return result(CAMPAIGN_LIFECYCLE, campaign.getCampaignId(), Status.PREPARE, "活动准备",
                    false, true, "营销活动仍为草稿，尚未产生真实触达", campaign.getVersion(),
                    List.of("活动尚未启用", "缺少投放实例"), List.of("完成活动配置并启用", "创建首个投放实例"), artifacts);
        }
        if ("PAUSED".equals(status)) {
            return result(CAMPAIGN_LIFECYCLE, campaign.getCampaignId(), Status.WAITING, "活动暂停",
                    false, true, "营销活动已暂停，且未提供可核验的投放实例", campaign.getVersion(),
                    List.of("活动状态为 PAUSED", "缺少投放实例"), List.of("运营主体决定恢复或结束活动"), artifacts);
        }
        return result(CAMPAIGN_LIFECYCLE, campaign.getCampaignId(), Status.WAITING, "等待投放证据",
                false, true, "营销活动已建立，但不能在缺少投放实例时宣称完成", campaign.getVersion(),
                List.of("缺少投放实例及渠道回执"), List.of("提供投放实例并等待真实渠道回执"), artifacts);
    }

    private static MarketingGrowthWorkflowResult completedDelivery(NotificationCampaignView campaign,
                                                                    NotificationDeliveryView delivery,
                                                                    List<Artifact> artifacts) {
        if (!"COMPLETED".equals(campaign.getStatus())) {
            return result(CAMPAIGN_LIFECYCLE, campaign.getCampaignId(), Status.RUNNING, "活动持续运行",
                    false, false, "触达 " + delivery.getDeliveryKey() + " 已" + deliveryLabel(delivery.getStatus())
                            + "，活动仍在运行", delivery.getVersion(), List.of(),
                    List.of("继续观察活动并在运营目标达成后结束"), artifacts);
        }
        return result(CAMPAIGN_LIFECYCLE, campaign.getCampaignId(), Status.SUCCEEDED, "营销活动完成",
                true, false, "活动 " + campaign.getCampaignName() + " 已结束；触达 "
                        + delivery.getDeliveryKey() + " 已" + deliveryLabel(delivery.getStatus()),
                Math.max(campaign.getVersion(), delivery.getVersion()), List.of(), List.of(), artifacts);
    }

    private static String deliveryLabel(String status) {
        return switch (status) {
            case "DELIVERED" -> "送达";
            case "OPENED" -> "打开";
            case "CLICKED" -> "点击";
            default -> status;
        };
    }

    private static MarketingGrowthWorkflowResult result(String workflowType, String key, Status status,
                                                         String phase, boolean terminal, boolean actionRequired,
                                                         String summary, Long version, List<String> blockers,
                                                         List<String> nextActions, List<Artifact> artifacts) {
        return MarketingGrowthWorkflowResult.builder().workflowType(workflowType)
                .workflowInstanceKey(workflowType + ":" + key).businessKey(key).status(status).phase(phase)
                .terminal(terminal).actionRequired(actionRequired).summary(summary).aggregateVersion(version)
                .blockers(blockers).nextActions(nextActions).artifacts(artifacts).build();
    }

    private static Artifact artifact(String type, String id, String status, Long version, String label) {
        return Artifact.builder().type(type).id(id).status(status).version(version).label(label).build();
    }

    private static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
