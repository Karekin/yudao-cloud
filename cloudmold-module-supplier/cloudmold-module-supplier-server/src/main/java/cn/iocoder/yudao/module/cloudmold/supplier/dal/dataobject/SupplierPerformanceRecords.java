package cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class SupplierPerformanceRecords {
    private SupplierPerformanceRecords() {
    }

    @Data
    @Accessors(chain = true)
    public static class Operation {
        private Long operationId;
        private Long tenantId;
        private String requestHash;
        private String attemptToken;
        private Integer status;
        private String resultJson;
    }

    @Data
    @Accessors(chain = true)
    public static class MetricEvidence {
        private String metricEvidenceId;
        private Long tenantId;
        private String supplierId;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private String sourceSystem;
        private String sourceRecordId;
        private String metricCode;
        private BigDecimal numerator;
        private BigDecimal denominator;
        private String evidenceSha256;
        private LocalDateTime observedAt;
        private String recordedByPrincipalId;
        private LocalDateTime createdAt;
    }

    @Data
    @Accessors(chain = true)
    public static class Scorecard {
        private String scorecardId;
        private Long tenantId;
        private String supplierId;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private Integer scorecardVersion;
        private Integer otifBps;
        private Integer qualityBps;
        private Integer capacityBps;
        private Integer capaBps;
        private Integer overallBps;
        private String assessment;
        private String evidenceSnapshotSha256;
        private String generatedByPrincipalId;
        private LocalDateTime generatedAt;
    }
}
