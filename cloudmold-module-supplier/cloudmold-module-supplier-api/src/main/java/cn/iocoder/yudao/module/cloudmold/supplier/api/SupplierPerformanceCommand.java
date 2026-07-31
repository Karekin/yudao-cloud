package cn.iocoder.yudao.module.cloudmold.supplier.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierPerformanceCommand {
    private SupplierPerformanceOperation operation;
    private String idempotencyKey;
    private String runId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    private MetricEvidenceDefinition metricEvidence;
    private ScorecardDefinition scorecard;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricEvidenceDefinition {
        private String metricEvidenceId;
        private String supplierId;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private String sourceSystem;
        private String sourceRecordId;
        private String metricCode;
        private BigDecimal numerator;
        private BigDecimal denominator;
        private String evidenceSha256;
        private Instant observedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScorecardDefinition {
        private String scorecardId;
        private String supplierId;
        private LocalDate periodStart;
        private LocalDate periodEnd;
    }
}
