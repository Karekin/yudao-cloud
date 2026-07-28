package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ApprovalWorkflowStartCandidate;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Produces a bounded, business-readable BPM projection. Raw work-order JSON never enters BPM variables.
 */
final class AgentApprovalBusinessContextPresenter {

    private static final int VALUE_LIMIT = 512;
    private static final Set<String> OBJECT_HINTS = Set.of(
            "spu", "sku", "style", "product", "listing", "order", "merchant", "shop",
            "warehouse", "supplier", "purchase", "campaign", "customer", "lot");
    private static final Set<String> METRIC_HINTS = Set.of(
            "quantity", "qty", "count", "amount", "money", "price", "currency", "budget", "cost");
    private static final Set<String> EVIDENCE_HINTS = Set.of(
            "evidence", "reason", "forecast", "plan", "policy", "report", "reference", "ref");

    private AgentApprovalBusinessContextPresenter() {
    }

    static Presentation present(ApprovalWorkflowStartCandidate candidate) {
        JsonNode context = parseObject(candidate.getBusinessContextJson());
        String action = StrUtil.blankToDefault(candidate.getWorkOrderTitle(), candidate.getActionCode())
                + "（" + candidate.getActionCode() + "）";
        String objects = summarize(context, OBJECT_HINTS, "未提供明确影响对象，请审批人退回补充");
        String metrics = summarize(context, METRIC_HINTS, "未提供数量或金额影响");
        String recommendation = preferred(context, List.of("recommendation", "suggestion", "suggestedAction"),
                "建议由租户运营主体根据影响范围与证据决定是否放行");
        String evidence = preferred(context, List.of("evidenceSummary", "evidence", "reason", "riskReason"),
                summarize(context, EVIDENCE_HINTS, "仅有冻结审批范围摘要：" + candidate.getScopeHash()));
        String consequence = preferred(context,
                List.of("nonExecutionConsequence", "consequence", "notExecuteConsequence"),
                "不批准时，本次动作不会执行，Temporal 将保持阻断并记录拒绝结果");
        String steps = preferred(context, List.of("plannedSteps", "executionSteps", "steps"),
                "审批通过 → 服务端复核审批人身份与冻结范围 → Temporal 恢复运行 → SkillTask 执行 "
                        + candidate.getActionCode() + " → 回写业务结果与证据");
        return new Presentation(action, objects, metrics, recommendation, evidence, consequence, steps);
    }

    private static JsonNode parseObject(String value) {
        if (StrUtil.isBlank(value)) {
            return JsonUtils.getObjectMapper().createObjectNode();
        }
        try {
            JsonNode node = JsonUtils.getObjectMapper().readTree(value);
            return node != null && node.isObject() ? node : JsonUtils.getObjectMapper().createObjectNode();
        } catch (Exception ignored) {
            return JsonUtils.getObjectMapper().createObjectNode();
        }
    }

    private static String preferred(JsonNode context, List<String> keys, String fallback) {
        for (String key : keys) {
            JsonNode value = context.get(key);
            if (value != null && !value.isNull()) {
                String rendered = render(value);
                if (StrUtil.isNotBlank(rendered)) {
                    return bounded(rendered);
                }
            }
        }
        return bounded(fallback);
    }

    private static String summarize(JsonNode context, Set<String> hints, String fallback) {
        List<String> values = new ArrayList<>();
        collect(context, "", hints, values);
        return values.isEmpty() ? bounded(fallback) : bounded(String.join("；", values));
    }

    private static void collect(JsonNode node, String path, Set<String> hints, List<String> values) {
        if (values.size() >= 8 || node == null || !node.isObject()) {
            return;
        }
        var fieldNames = node.fieldNames();
        while (fieldNames.hasNext() && values.size() < 8) {
            String fieldName = fieldNames.next();
            String currentPath = path.isEmpty() ? fieldName : path + "." + fieldName;
            JsonNode value = node.get(fieldName);
            String normalized = fieldName.replace("_", "").replace("-", "")
                    .toLowerCase(Locale.ROOT);
            if (matches(normalized, hints) && (value.isValueNode() || value.isArray())) {
                values.add(currentPath + "=" + render(value));
            } else if (value.isObject()) {
                collect(value, currentPath, hints, values);
            }
        }
    }

    private static boolean matches(String key, Set<String> hints) {
        return hints.stream().anyMatch(key::contains);
    }

    private static String render(JsonNode value) {
        if (value.isTextual()) {
            return value.asText();
        }
        if (value.isArray()) {
            List<String> parts = new ArrayList<>();
            value.forEach(item -> {
                if (parts.size() < 6 && item.isValueNode()) {
                    parts.add(item.asText());
                }
            });
            return String.join(", ", parts);
        }
        return value.isValueNode() ? value.asText() : "";
    }

    private static String bounded(String value) {
        return StrUtil.maxLength(StrUtil.nullToEmpty(value), VALUE_LIMIT);
    }

    record Presentation(String businessAction, String impactObjects, String impactMetrics,
                        String recommendation, String evidence, String nonExecutionConsequence,
                        String executionSteps) {
    }
}
