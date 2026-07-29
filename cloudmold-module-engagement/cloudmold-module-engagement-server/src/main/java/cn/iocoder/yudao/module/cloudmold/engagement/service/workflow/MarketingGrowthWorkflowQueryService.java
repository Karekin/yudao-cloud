package cn.iocoder.yudao.module.cloudmold.engagement.service.workflow;

import cn.iocoder.yudao.module.cloudmold.engagement.api.EngagementQueryApi;
import cn.iocoder.yudao.module.cloudmold.engagement.api.EngagementQueryApi.NotificationCampaignView;
import cn.iocoder.yudao.module.cloudmold.engagement.api.EngagementQueryApi.NotificationDeliveryView;
import cn.iocoder.yudao.module.cloudmold.engagement.api.workflow.MarketingGrowthWorkflowQueryPort;
import cn.iocoder.yudao.module.cloudmold.engagement.api.workflow.MarketingGrowthWorkflowResult;
import cn.iocoder.yudao.module.cloudmold.engagement.api.workflow.MarketingGrowthWorkflowResult.Artifact;
import cn.iocoder.yudao.module.cloudmold.engagement.api.workflow.MarketingGrowthWorkflowResult.Status;
import cn.iocoder.yudao.module.cloudmold.promotion.api.PromotionAggregateView;
import cn.iocoder.yudao.module.cloudmold.promotion.api.PromotionQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MarketingGrowthWorkflowQueryService implements MarketingGrowthWorkflowQueryPort {

    private static final String CAMPAIGN_LIFECYCLE = "CampaignLifecycleWorkflow";
    private static final String GROWTH_EXPERIMENT = "GrowthExperimentWorkflow";

    private final EngagementQueryApi engagementQueryApi;
    private final PromotionQueryApi promotionQueryApi;

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
        PromotionAggregateView experiment = promotionQueryApi.get("PROMOTION_GROWTH_EXPERIMENT", key);
        Map<String, Object> attributes = experiment.getAttributes();
        List<Map<String, Object>> variants = maps(attributes.get("variants"));
        List<Artifact> artifacts = new ArrayList<>();
        artifacts.add(artifact("GROWTH_EXPERIMENT", experiment.getAggregateId(), experiment.getStatus(),
                experiment.getVersion(), "增长实验 " + experiment.getBusinessCode()));
        for (Map<String, Object> variant : variants) {
            String variantCode = text(variant.get("variant_code"));
            artifacts.add(artifact("GROWTH_EXPERIMENT_VARIANT",
                    experiment.getAggregateId() + ":" + variantCode,
                    variant.get("latest_sample_count") == null ? "EXPOSING" : "MEASURED", 1L,
                    variantCode + "，曝光 " + number(variant.get("exposure_count")) + " 次"));
        }
        return switch (experiment.getStatus()) {
            case "DRAFT" -> result(GROWTH_EXPERIMENT, key, Status.PREPARE, "实验准备", false, true,
                    "增长实验已建立权威记录，尚未开始分流", experiment.getVersion(),
                    List.of("实验状态为 DRAFT"), List.of("确认活动已启用并启动实验"), artifacts);
            case "RUNNING" -> runningExperiment(experiment, variants, artifacts);
            case "CONCLUDED" -> concludedExperiment(experiment, variants, artifacts);
            case "CANCELLED" -> result(GROWTH_EXPERIMENT, key, Status.FAILED, "实验取消", true, true,
                    "增长实验已取消，不能形成上线决策", experiment.getVersion(),
                    List.of(text(attributes.get("conclusion_reason"))),
                    List.of("复盘取消原因后创建新实验"), artifacts);
            default -> result(GROWTH_EXPERIMENT, key, Status.WAITING, "状态核对", false, true,
                    "增长实验处于未识别状态", experiment.getVersion(),
                    List.of("未知实验状态：" + experiment.getStatus()), List.of("人工核对权威记录"), artifacts);
        };
    }

    private static MarketingGrowthWorkflowResult runningExperiment(
            PromotionAggregateView experiment, List<Map<String, Object>> variants, List<Artifact> artifacts) {
        int minimum = number(experiment.getAttributes().get("minimum_sample_size_per_variant"));
        List<String> blockers = new ArrayList<>();
        for (Map<String, Object> variant : variants) {
            String code = text(variant.get("variant_code"));
            if (number(variant.get("exposure_count")) < minimum) {
                blockers.add(code + " 尚未达到最小曝光样本 " + minimum);
            } else if (number(variant.get("latest_sample_count")) < minimum) {
                blockers.add(code + " 尚未形成足量主指标快照");
            } else if (!StringUtils.hasText(text(variant.get("metric_evidence_ref")))) {
                blockers.add(code + " 缺少指标证据");
            }
        }
        String summary = blockers.isEmpty()
                ? "各实验组已达到样本门槛，等待实验窗口结束和统计结论"
                : "增长实验正在分流和积累可审计样本";
        return result(GROWTH_EXPERIMENT, experiment.getAggregateId(), Status.RUNNING, "实验运行",
                false, false, summary, experiment.getVersion(), blockers,
                blockers.isEmpty() ? List.of("等待实验窗口结束后执行显著性与护栏门禁")
                        : List.of("继续记录真实曝光并刷新主指标快照"), artifacts);
    }

    private static MarketingGrowthWorkflowResult concludedExperiment(
            PromotionAggregateView experiment, List<Map<String, Object>> variants, List<Artifact> artifacts) {
        Map<String, Object> attributes = experiment.getAttributes();
        int minimum = number(attributes.get("minimum_sample_size_per_variant"));
        List<String> blockers = new ArrayList<>();
        if (number(attributes.get("confidence_basis_points")) < 9500) {
            blockers.add("统计置信度低于 95%");
        }
        if (!"PASSED".equals(text(attributes.get("guardrail_status")))) {
            blockers.add("实验护栏未全部通过");
        }
        if (!StringUtils.hasText(text(attributes.get("conclusion_evidence_ref")))) {
            blockers.add("缺少结论证据");
        }
        for (Map<String, Object> variant : variants) {
            String code = text(variant.get("variant_code"));
            if (number(variant.get("exposure_count")) < minimum
                    || number(variant.get("latest_sample_count")) < minimum
                    || !StringUtils.hasText(text(variant.get("metric_evidence_ref")))) {
                blockers.add(code + " 的样本或指标证据不完整");
            }
        }
        if (!blockers.isEmpty()) {
            return result(GROWTH_EXPERIMENT, experiment.getAggregateId(), Status.FAILED,
                    "结论证据校验", true, true, "实验虽标记已结束，但权威证据未通过门禁",
                    experiment.getVersion(), blockers, List.of("修复权威记录后重新生成实验结论"), artifacts);
        }
        return result(GROWTH_EXPERIMENT, experiment.getAggregateId(), Status.SUCCEEDED,
                "实验结论完成", true, false,
                "增长实验已通过样本量、主指标、95% 置信度和护栏门禁；决策为 "
                        + text(attributes.get("decision")),
                experiment.getVersion(), List.of(), List.of(), artifacts);
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

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
