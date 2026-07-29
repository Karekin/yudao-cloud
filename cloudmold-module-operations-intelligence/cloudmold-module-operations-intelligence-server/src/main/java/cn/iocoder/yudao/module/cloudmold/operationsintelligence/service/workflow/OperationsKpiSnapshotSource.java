package cn.iocoder.yudao.module.cloudmold.operationsintelligence.service.workflow;

import cn.iocoder.yudao.module.cloudmold.operationsintelligence.config.OperationsIntelligenceAnalyticsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Builds a tenant-scoped KPI snapshot from the governed StarRocks role mart.
 *
 * <p>The source returns only aggregate metrics and never exposes order,
 * customer, address or payment identifiers.</p>
 */
@Component
@RequiredArgsConstructor
class OperationsKpiSnapshotSource {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String METRIC_SQL = """
            SELECT metric_id, metric_value, unit, source_row_count,
                   numerator, denominator, evidence_note, data_freshness_at
            FROM yshopping_ads.ads_ecommerce_role_metrics
            WHERE tenant_id = ? AND metric_id IN (%s)
            ORDER BY metric_id
            """;

    private final OperationsIntelligenceAnalyticsProperties properties;
    private final ObjectMapper objectMapper;

    JsonNode load(long tenantId, Collection<String> metricIds) {
        require(tenantId > 0, "tenantId must be positive");
        List<String> requested = metricIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .sorted()
                .toList();
        require(!requested.isEmpty(), "at least one KPI is required");

        ObjectNode metrics = objectMapper.createObjectNode();
        LocalDateTime oldestFreshness = null;
        try (Connection connection = DriverManager.getConnection(
                properties.getStarRocksJdbcUrl(),
                properties.getStarRocksUsername(),
                properties.getStarRocksPassword())) {
            configurePlanner(connection);
            int batchSize = Math.max(1, Math.min(properties.getQueryBatchSize(), 5));
            for (int offset = 0; offset < requested.size(); offset += batchSize) {
                List<String> batch = requested.subList(offset, Math.min(requested.size(), offset + batchSize));
                oldestFreshness = readBatch(connection, tenantId, batch, metrics, oldestFreshness);
            }
        } catch (SQLException exception) {
            throw new KpiSnapshotSourceException("STARROCKS_KPI_QUERY_FAILED", exception);
        }

        ObjectNode snapshot = objectMapper.createObjectNode();
        String freshnessToken = oldestFreshness == null
                ? "missing"
                : oldestFreshness.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        snapshot.put("schema_version", 1);
        snapshot.put("snapshot_id", "tenant-" + tenantId + "-starrocks-" + freshnessToken);
        if (oldestFreshness != null) {
            snapshot.put("generated_at", oldestFreshness.atZone(BUSINESS_ZONE).toOffsetDateTime().toString());
        }
        snapshot.put("evidence_scope", "LOCAL_RUNTIME_STARROCKS");
        snapshot.put("tenant_label", "Tenant " + tenantId);
        snapshot.put("pipeline", "MySQL Outbox / CDC → StarRocks ADS");
        snapshot.set("metrics", metrics);
        return snapshot;
    }

    private LocalDateTime readBatch(Connection connection, long tenantId, List<String> metricIds,
                                    ObjectNode metrics, LocalDateTime oldestFreshness) throws SQLException {
        String placeholders = String.join(",", Collections.nCopies(metricIds.size(), "?"));
        try (PreparedStatement statement = connection.prepareStatement(METRIC_SQL.formatted(placeholders))) {
            statement.setQueryTimeout(Math.max(1, properties.getQueryTimeoutSeconds()));
            statement.setLong(1, tenantId);
            for (int index = 0; index < metricIds.size(); index++) {
                statement.setString(index + 2, metricIds.get(index));
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String metricId = rows.getString("metric_id");
                    ObjectNode metric = objectMapper.createObjectNode();
                    BigDecimal value = rows.getBigDecimal("metric_value");
                    if (value != null) {
                        metric.put("value", value);
                    }
                    metric.put("unit", rows.getString("unit"));
                    metric.put("status", "runtime_local_test");
                    metric.put("note", evidenceNote(rows));
                    Timestamp freshness = rows.getTimestamp("data_freshness_at");
                    if (freshness != null) {
                        LocalDateTime valueFreshness = freshness.toLocalDateTime();
                        metric.put("freshness", valueFreshness.toString());
                        oldestFreshness = oldestFreshness == null || valueFreshness.isBefore(oldestFreshness)
                                ? valueFreshness : oldestFreshness;
                    }
                    metrics.set(metricId, metric);
                }
            }
        }
        return oldestFreshness;
    }

    private static void configurePlanner(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET new_planner_optimize_timeout=30000");
        }
    }

    private static String evidenceNote(ResultSet rows) throws SQLException {
        String note = Objects.toString(rows.getString("evidence_note"), "");
        long sourceCount = rows.getLong("source_row_count");
        BigDecimal numerator = rows.getBigDecimal("numerator");
        BigDecimal denominator = rows.getBigDecimal("denominator");
        if (denominator != null) {
            return note + "；样本 " + number(numerator) + "/" + number(denominator)
                    + "；来源行数 " + sourceCount;
        }
        return note + "；来源行数 " + sourceCount;
    }

    private static String number(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    static final class KpiSnapshotSourceException extends RuntimeException {
        private final String code;

        KpiSnapshotSourceException(String code, Throwable cause) {
            super(code, cause);
            this.code = code;
        }

        String code() {
            return code;
        }
    }
}
