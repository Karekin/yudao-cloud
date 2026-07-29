package cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class SupplierSourcingRecords {
    private SupplierSourcingRecords() {
    }

    @Data
    @Accessors(chain = true)
    public static class Operation {
        private Long operationId;
        private Long tenantId;
        private String idempotencyKey;
        private String commandType;
        private String requestHash;
        private String attemptToken;
        private Integer status;
        private String aggregateType;
        private String aggregateId;
        private String resultJson;
    }

    @Data
    @Accessors(chain = true)
    public static class SupplierProfile {
        private String supplierId;
        private Long tenantId;
        private String supplierCode;
        private String supplierName;
        private String countryCode;
        private String capabilitySummary;
        private String riskLevel;
        private String status;
        private String admissionStatus;
        private String qualificationEvidenceSha256;
        private String riskEvidenceSha256;
        private String createdByPrincipalId;
        private String submittedByPrincipalId;
        private String admittedByPrincipalId;
        private String reasonCode;
        private Long version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime admissionSubmittedAt;
        private LocalDateTime admittedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class SourcingCase {
        private String sourcingCaseId;
        private Long tenantId;
        private String rfqCode;
        private String requestRef;
        private String canonicalSkuId;
        private BigDecimal targetQuantity;
        private String uomCode;
        private String currencyCode;
        private Long maxUnitCostMinor;
        private LocalDate requiredDeliveryDate;
        private String requirements;
        private String status;
        private String awardedSupplierId;
        private String awardedQuoteId;
        private String decisionRationale;
        private String createdByPrincipalId;
        private String awardedByPrincipalId;
        private Long version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime awardedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class Quote {
        private String quoteId;
        private Long tenantId;
        private String sourcingCaseId;
        private String supplierId;
        private Integer quoteVersion;
        private Long unitCostMinor;
        private BigDecimal moq;
        private Integer leadTimeDays;
        private BigDecimal capacityQuantity;
        private LocalDate validUntil;
        private String termsSummary;
        private String status;
        private String submittedByPrincipalId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class SampleEvaluation {
        private String evaluationId;
        private Long tenantId;
        private String sourcingCaseId;
        private String supplierId;
        private String quoteId;
        private Integer qualityScore;
        private Integer fitScore;
        private Integer deliveryScore;
        private Integer riskScore;
        private String result;
        private String notes;
        private String evaluatedByPrincipalId;
        private LocalDateTime evaluatedAt;
    }
}
