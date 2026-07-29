package cn.iocoder.yudao.module.cloudmold.operationsintelligence.service.workflow;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.api.workflow.BusinessControlWorkflowResult;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.api.workflow.BusinessControlWorkflowResult.*;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.api.workflow.DailyBusinessControlQueryPort;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.api.workflow.WeeklyBusinessReviewQueryPort;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.config.OperationsIntelligenceAnalyticsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class OperationsBusinessControlWorkflowQueryService
        implements DailyBusinessControlQueryPort, WeeklyBusinessReviewQueryPort {

    private static final String DAILY = "DailyBusinessControlWorkflow";
    private static final String WEEKLY = "WeeklyBusinessReviewWorkflow";
    private static final List<String> DAILY_METRICS = List.of(
            "finance.net_revenue_yuan",
            "commerce.order_count",
            "commerce.reconciled_rate",
            "inventory.low_stock_balance_count",
            "inventory.stockout_rate",
            "service.resolution_sla_rate");
    private static final List<String> WEEKLY_METRICS = List.of(
            "finance.contribution_margin_rate",
            "commerce.reconciled_rate",
            "inventory.turnover_days",
            "inventory.sell_through_rate_30d",
            "procurement.otif_rate",
            "service.first_contact_resolution_rate",
            "service.resolution_sla_rate");

    private final OperationsIntelligenceAnalyticsProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final OperationsKpiSnapshotSource liveSnapshotSource;

    @Autowired
    public OperationsBusinessControlWorkflowQueryService(
            OperationsIntelligenceAnalyticsProperties properties,
            ObjectMapper objectMapper,
            ObjectProvider<Clock> clockProvider,
            OperationsKpiSnapshotSource liveSnapshotSource) {
        this(properties, objectMapper, clockProvider.getIfAvailable(Clock::systemUTC), liveSnapshotSource);
    }

    OperationsBusinessControlWorkflowQueryService(
            OperationsIntelligenceAnalyticsProperties properties,
            ObjectMapper objectMapper,
            Clock clock) {
        this(properties, objectMapper, clock, null);
    }

    OperationsBusinessControlWorkflowQueryService(
            OperationsIntelligenceAnalyticsProperties properties,
            ObjectMapper objectMapper,
            Clock clock,
            OperationsKpiSnapshotSource liveSnapshotSource) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.liveSnapshotSource = liveSnapshotSource;
    }

    @Override
    public BusinessControlWorkflowResult inspectDaily() {
        SnapshotContext snapshot = loadSnapshot(DAILY_METRICS);
        if (snapshot.error != null) {
            return missingData(DAILY, "daily-business-control", "WAITING_KPI_SNAPSHOT",
                    "尚未接入权威 KPI 快照，无法生成每日经营总控结果。", List.of(snapshot.error));
        }
        List<KpiReading> readings = List.of(
                reading(snapshot, "finance.net_revenue_yuan"),
                reading(snapshot, "commerce.order_count"),
                reading(snapshot, "commerce.reconciled_rate"),
                reading(snapshot, "inventory.low_stock_balance_count"),
                reading(snapshot, "inventory.stockout_rate"),
                reading(snapshot, "service.resolution_sla_rate"));
        List<TargetDeviation> deviations = new ArrayList<>();
        List<Anomaly> anomalies = new ArrayList<>();
        List<SuggestedWorkOrder> workOrders = new ArrayList<>();
        deviations.add(rule(snapshot, anomalies, workOrders, "commerce.reconciled_rate", "交易对账率",
                "GTE", 95D, 80D, "交易与财务",
                "今日交易对账率偏低，存在未闭合的跨域交易事实。",
                "核对订单、支付、履约与售后链路，补齐失败事件或人工结案。"));
        deviations.add(rule(snapshot, anomalies, workOrders, "inventory.low_stock_balance_count", "低库存余额数",
                "LTE", 0D, 3D, "库控与供应计划",
                "低库存 SKU 已经出现，存在缺断码或即将缺货风险。",
                "检查销量预测与补货建议，必要时发起补货或调拨。"));
        deviations.add(rule(snapshot, anomalies, workOrders, "inventory.stockout_rate", "缺货率",
                "LTE", 3D, 8D, "商品与库控",
                "可售商品缺货率偏高，影响转化与 GMV。",
                "排查待上架商品、可售映射与补货节奏。"));
        deviations.add(rule(snapshot, anomalies, workOrders, "service.resolution_sla_rate", "客服解决 SLA 达成率",
                "GTE", 90D, 75D, "客服运营",
                "客服解决 SLA 未达标，存在待办积压或跨岗阻塞。",
                "清理超时工单，补充责任人并升级跨团队阻塞。"));
        List<String> blockers = new ArrayList<>();
        blockers.addAll(missingMetricBlockers(readings));
        List<String> nextActions = new ArrayList<>();
        if (isStale(snapshot, Duration.ofHours(36))) {
            blockers.add("KPI_SNAPSHOT_STALE:" + snapshot.generatedAt);
            nextActions.add("刷新当日 StarRocks KPI 快照后再派发每日经营处置单");
        }
        if (anomalies.isEmpty()) {
            nextActions.add("继续监控交易、库存和客服 SLA，无需新增经营派单");
        } else {
            workOrders.forEach(item -> nextActions.add(item.getRecommendedAction()));
        }
        Status status = snapshot.error != null ? Status.NEEDS_DATA
                : !blockers.isEmpty() ? Status.WAITING
                : anomalies.isEmpty() ? Status.SUCCEEDED : Status.RUNNING;
        String summary = dailySummary(snapshot, anomalies, blockers);
        return baseResult(DAILY, "daily-business-control", status,
                !blockers.isEmpty() ? "WAITING_KPI_REFRESH" : anomalies.isEmpty() ? "DAILY_HEALTHY" : "DAILY_DISPATCH_REQUIRED",
                anomalies.isEmpty() && blockers.isEmpty(), !anomalies.isEmpty() || !blockers.isEmpty(), summary, snapshot,
                blockers, dedupe(nextActions), readings, deviations, anomalies, workOrders);
    }

    @Override
    public BusinessControlWorkflowResult inspectWeekly() {
        SnapshotContext snapshot = loadSnapshot(WEEKLY_METRICS);
        if (snapshot.error != null) {
            return missingData(WEEKLY, "weekly-business-review", "WAITING_KPI_SNAPSHOT",
                    "尚未接入权威 KPI 快照，无法生成周经营复盘。", List.of(snapshot.error));
        }
        List<KpiReading> readings = List.of(
                reading(snapshot, "finance.contribution_margin_rate"),
                reading(snapshot, "commerce.reconciled_rate"),
                reading(snapshot, "inventory.turnover_days"),
                reading(snapshot, "inventory.sell_through_rate_30d"),
                reading(snapshot, "procurement.otif_rate"),
                reading(snapshot, "service.first_contact_resolution_rate"),
                reading(snapshot, "service.resolution_sla_rate"));
        List<TargetDeviation> deviations = new ArrayList<>();
        List<Anomaly> anomalies = new ArrayList<>();
        List<SuggestedWorkOrder> workOrders = new ArrayList<>();
        deviations.add(rule(snapshot, anomalies, workOrders, "finance.contribution_margin_rate", "贡献利润率",
                "GTE", 35D, 25D, "经营总控",
                "贡献利润率已经跌破周经营基线，需要复核定价、补贴和售后损失。",
                "复核价格带、折扣资金与售后损失，必要时调整经营策略。"));
        deviations.add(rule(snapshot, anomalies, workOrders, "commerce.reconciled_rate", "交易对账率",
                "GTE", 95D, 80D, "数据与财务",
                "周度交易对账率偏低，复盘基线不可信。",
                "优先修复跨域对账差异，再更新周复盘结论。"));
        deviations.add(rule(snapshot, anomalies, workOrders, "inventory.turnover_days", "库存周转天数",
                "LTE", 120D, 180D, "商品与供应计划",
                "库存周转过慢，存在积压和结构性库存风险。",
                "按款色码梳理滞销库存，调整补货和清货计划。"));
        deviations.add(rule(snapshot, anomalies, workOrders, "inventory.sell_through_rate_30d", "30 天售罄率",
                "GTE", 15D, 8D, "商品企划",
                "30 天售罄率偏低，新品或库存结构需要重排。",
                "复盘选品池、价格带和渠道投放，重排下周上新与清仓动作。"));
        deviations.add(rule(snapshot, anomalies, workOrders, "procurement.otif_rate", "采购 OTIF",
                "GTE", 95D, 85D, "采购",
                "供应商交付 OTIF 偏低，影响补货兑现。",
                "复核供应商承诺与交期，必要时切换备选供应商。"));
        deviations.add(rule(snapshot, anomalies, workOrders, "service.first_contact_resolution_rate", "一次解决率",
                "GTE", 80D, 60D, "客服运营",
                "客服一次解决率偏低，说明知识库或流程交接存在问题。",
                "复盘高频问题与客服 SOP，补齐跨岗升级路径。"));
        deviations.add(rule(snapshot, anomalies, workOrders, "service.resolution_sla_rate", "客服解决 SLA 达成率",
                "GTE", 90D, 75D, "客服运营",
                "客服 SLA 周达成率偏低，需压缩积压。",
                "清理积压工单并明确责任人和时限。"));
        List<String> blockers = new ArrayList<>();
        blockers.addAll(missingMetricBlockers(readings));
        List<String> nextActions = new ArrayList<>();
        if (isStale(snapshot, Duration.ofDays(8))) {
            blockers.add("KPI_SNAPSHOT_STALE:" + snapshot.generatedAt);
            nextActions.add("刷新周 KPI 快照后再确认周复盘结论与下周工作图");
        }
        if (anomalies.isEmpty()) {
            nextActions.add("周指标整体健康，可继续执行既定经营节奏");
        } else {
            workOrders.forEach(item -> nextActions.add(item.getRecommendedAction()));
        }
        Status status = !blockers.isEmpty() ? Status.WAITING
                : anomalies.isEmpty() ? Status.SUCCEEDED : Status.RUNNING;
        String summary = weeklySummary(snapshot, anomalies, blockers);
        return baseResult(WEEKLY, "weekly-business-review", status,
                !blockers.isEmpty() ? "WAITING_WEEKLY_REFRESH" : anomalies.isEmpty() ? "WEEKLY_HEALTHY" : "REPLAN_REQUIRED",
                anomalies.isEmpty() && blockers.isEmpty(), !anomalies.isEmpty() || !blockers.isEmpty(), summary, snapshot,
                blockers, dedupe(nextActions), readings, deviations, anomalies, workOrders);
    }

    private BusinessControlWorkflowResult baseResult(String workflowType, String key, Status status, String phase,
                                                     boolean terminal, boolean actionRequired, String summary,
                                                     SnapshotContext snapshot, List<String> blockers,
                                                     List<String> nextActions, List<KpiReading> readings,
                                                     List<TargetDeviation> deviations, List<Anomaly> anomalies,
                                                     List<SuggestedWorkOrder> workOrders) {
        return BusinessControlWorkflowResult.builder()
                .workflowType(workflowType)
                .workflowInstanceKey(key + ":" + Objects.toString(snapshot.snapshotId, "missing"))
                .businessKey(key)
                .status(status)
                .phase(phase)
                .terminal(terminal)
                .actionRequired(actionRequired)
                .summary(summary)
                .snapshotId(snapshot.snapshotId)
                .generatedAt(snapshot.generatedAt)
                .evidenceScope(snapshot.evidenceScope)
                .tenantLabel(snapshot.tenantLabel)
                .blockers(List.copyOf(blockers))
                .nextActions(nextActions)
                .readings(readings)
                .targetDeviations(compact(deviations))
                .anomalies(anomalies)
                .suggestedWorkOrders(workOrders)
                .artifacts(List.of(Artifact.builder()
                        .type("KPI_SNAPSHOT")
                        .id(Objects.toString(snapshot.snapshotId, "missing"))
                        .status(Objects.toString(snapshot.evidenceScope, "UNKNOWN"))
                        .label("经营快照 " + Objects.toString(snapshot.generatedAt, "未知时间"))
                        .build()))
                .build();
    }

    private BusinessControlWorkflowResult missingData(String workflowType, String key, String phase, String summary,
                                                      List<String> blockers) {
        return BusinessControlWorkflowResult.builder()
                .workflowType(workflowType)
                .workflowInstanceKey(key + ":missing")
                .businessKey(key)
                .status(Status.NEEDS_DATA)
                .phase(phase)
                .terminal(false)
                .actionRequired(true)
                .summary(summary)
                .blockers(blockers)
                .nextActions(List.of("接入并刷新权威 StarRocks / KPI 快照"))
                .readings(List.of())
                .targetDeviations(List.of())
                .anomalies(List.of())
                .suggestedWorkOrders(List.of())
                .artifacts(List.of())
                .build();
    }

    private TargetDeviation rule(SnapshotContext snapshot, List<Anomaly> anomalies,
                                 List<SuggestedWorkOrder> workOrders, String metricId, String metricName,
                                 String direction, double targetValue, double criticalValue, String ownerRole,
                                 String anomalySummary, String workAction) {
        JsonNode metric = snapshot.metric(metricId);
        if (metric == null || !metric.path("value").isNumber()) {
            return null;
        }
        double actual = metric.path("value").asDouble();
        double delta = actual - targetValue;
        boolean breach = "GTE".equals(direction) ? actual < targetValue : actual > targetValue;
        boolean critical = "GTE".equals(direction) ? actual < criticalValue : actual > criticalValue;
        String severity = critical ? "CRITICAL" : breach ? "WARNING" : "NORMAL";
        if (breach) {
            anomalies.add(Anomaly.builder()
                    .code(metricId.toUpperCase(Locale.ROOT).replace('.', '_') + "_BREACH")
                    .title(metricName + "异常")
                    .severity(severity)
                    .summary(anomalySummary + " 当前值 " + format(actual) + unit(metric) + "，目标 "
                            + format(targetValue) + unit(metric) + "。")
                    .build());
            workOrders.add(SuggestedWorkOrder.builder()
                    .code(metricId.replace('.', '-'))
                    .title(metricName + "治理")
                    .ownerRole(ownerRole)
                    .reason(anomalySummary)
                    .recommendedAction(workAction)
                    .build());
        }
        return TargetDeviation.builder()
                .metricId(metricId)
                .metricName(metricName)
                .direction(direction)
                .severity(severity)
                .targetValue(targetValue)
                .actualValue(actual)
                .deltaValue(delta)
                .unit(text(metric, "unit"))
                .explanation("按内置经营守护阈值对比，不写 Mission 目标。")
                .build();
    }

    private KpiReading reading(SnapshotContext snapshot, String metricId) {
        JsonNode metric = snapshot.metric(metricId);
        if (metric == null) {
            return KpiReading.builder().metricId(metricId).metricName(snapshot.metricName(metricId))
                    .status("MISSING").note("快照中缺少该指标").build();
        }
        return KpiReading.builder()
                .metricId(metricId)
                .metricName(snapshot.metricName(metricId))
                .status(text(metric, "status"))
                .value(metric.path("value").isNumber() ? metric.path("value").asDouble() : null)
                .unit(text(metric, "unit"))
                .freshness(text(metric, "freshness"))
                .note(text(metric, "note"))
                .build();
    }

    private SnapshotContext loadSnapshot(Collection<String> metricIds) {
        if (properties.isLiveQueryEnabled()) {
            Long tenantId = TenantContextHolder.getTenantId();
            if (tenantId == null) {
                return SnapshotContext.error("TENANT_CONTEXT_MISSING");
            }
            if (liveSnapshotSource == null) {
                return SnapshotContext.error("STARROCKS_KPI_SOURCE_UNAVAILABLE");
            }
            try {
                return new SnapshotContext(liveSnapshotSource.load(tenantId, metricIds), loadCatalog(), null);
            } catch (OperationsKpiSnapshotSource.KpiSnapshotSourceException exception) {
                return SnapshotContext.error(exception.code());
            } catch (IOException exception) {
                return SnapshotContext.error("KPI_CATALOG_UNREADABLE");
            }
        }
        if (!Files.isRegularFile(properties.snapshotPath())) {
            return SnapshotContext.error("KPI_SNAPSHOT_NOT_FOUND");
        }
        try {
            JsonNode snapshot = objectMapper.readTree(Files.readString(properties.snapshotPath()));
            return new SnapshotContext(snapshot, loadCatalog(), null);
        } catch (IOException ex) {
            return SnapshotContext.error("KPI_SNAPSHOT_UNREADABLE");
        }
    }

    private JsonNode loadCatalog() throws IOException {
        return Files.isRegularFile(properties.catalogPath())
                ? objectMapper.readTree(Files.readString(properties.catalogPath())) : null;
    }

    private static List<String> missingMetricBlockers(List<KpiReading> readings) {
        return readings.stream()
                .filter(reading -> "MISSING".equals(reading.getStatus()))
                .map(reading -> "KPI_METRIC_MISSING:" + reading.getMetricId())
                .toList();
    }

    private boolean isStale(SnapshotContext snapshot, Duration threshold) {
        if (snapshot.generatedAt == null) {
            return true;
        }
        try {
            return Duration.between(OffsetDateTime.parse(snapshot.generatedAt), OffsetDateTime.now(clock)).compareTo(threshold) > 0;
        } catch (RuntimeException ex) {
            return true;
        }
    }

    private String dailySummary(SnapshotContext snapshot, List<Anomaly> anomalies, List<String> blockers) {
        if (!blockers.isEmpty()) {
            return "每日经营快照已存在，但数据已过期，暂不应自动派发新的经营处置单。";
        }
        if (anomalies.isEmpty()) {
            return "每日经营指标整体健康，交易、库存与客服 SLA 暂未发现需要升级的异常。";
        }
        return "每日经营总控发现 " + anomalies.size()
                + " 项重点异常，建议立即派发对账、库存或客服治理工作单。";
    }

    private String weeklySummary(SnapshotContext snapshot, List<Anomaly> anomalies, List<String> blockers) {
        if (!blockers.isEmpty()) {
            return "周经营复盘快照已存在，但已经过期，当前结论只能作为历史参考，不能自动重规划。";
        }
        if (anomalies.isEmpty()) {
            return "周经营复盘未发现明显偏差，当前经营节奏可以延续。";
        }
        return "周经营复盘识别出 " + anomalies.size()
                + " 项偏差，建议围绕库存、对账、采购和客服能力重排下周工作图。";
    }

    private static List<String> dedupe(List<String> values) {
        return values.stream().filter(Objects::nonNull).map(String::trim).filter(v -> !v.isEmpty()).distinct().toList();
    }

    private static <T> List<T> compact(List<T> values) {
        return values.stream().filter(Objects::nonNull).toList();
    }

    private static String text(JsonNode node, String field) {
        return node == null || node.path(field).isMissingNode() || node.path(field).isNull() ? null : node.path(field).asText();
    }

    private static String unit(JsonNode metric) {
        String unit = text(metric, "unit");
        return unit == null ? "" : " " + unit;
    }

    private static String format(double value) {
        if (Math.rint(value) == value) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static final class SnapshotContext {
        private final JsonNode snapshot;
        private final Map<String, String> names;
        private final String error;
        private final String snapshotId;
        private final String generatedAt;
        private final String evidenceScope;
        private final String tenantLabel;

        private SnapshotContext(JsonNode snapshot, JsonNode catalog, String error) {
            this.snapshot = snapshot;
            this.error = error;
            this.snapshotId = text(snapshot, "snapshot_id");
            this.generatedAt = text(snapshot, "generated_at");
            this.evidenceScope = text(snapshot, "evidence_scope");
            this.tenantLabel = text(snapshot, "tenant_label");
            Map<String, String> nameMap = new LinkedHashMap<>();
            if (catalog != null && catalog.path("metrics").isArray()) {
                for (JsonNode metric : catalog.path("metrics")) {
                    String id = text(metric, "id");
                    if (id != null) {
                        nameMap.put(id, Objects.requireNonNullElse(text(metric, "name"), id));
                    }
                }
            }
            this.names = Map.copyOf(nameMap);
        }

        private static SnapshotContext error(String error) {
            return new SnapshotContext(null, null, error);
        }

        private JsonNode metric(String metricId) {
            if (snapshot == null) {
                return null;
            }
            JsonNode metric = snapshot.path("metrics").path(metricId);
            return metric.isMissingNode() || metric.isNull() ? null : metric;
        }

        private String metricName(String metricId) {
            return names.getOrDefault(metricId, metricId);
        }
    }
}
