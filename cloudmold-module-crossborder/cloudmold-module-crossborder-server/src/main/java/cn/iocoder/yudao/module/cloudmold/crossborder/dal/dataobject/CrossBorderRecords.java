package cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class CrossBorderRecords {
    private CrossBorderRecords() {
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
    public static class CaseRecord {
        private String caseId;
        private Long tenantId;
        private String caseNo;
        private String orderId;
        private String fulfillmentId;
        private String tradeMode;
        private String originCountry;
        private String destinationCountry;
        private String status;
        private String assessmentId;
        private String routeCode;
        private String routeCarrierCode;
        private String routeServiceLevel;
        private Integer routeSlaDays;
        private String approvalRef;
        private String declarationId;
        private String hsCode;
        private String goodsDescription;
        private BigDecimal quantity;
        private Long declaredAmountMinor;
        private String currency;
        private String declarationEvidenceRef;
        private String documentOrderRef;
        private String documentPaymentRef;
        private String documentLogisticsRef;
        private String documentValidationEvidenceRef;
        private String bookingRef;
        private String bookingCarrierCode;
        private String bookingServiceLevel;
        private String labelRef;
        private String trackingNumber;
        private String handoverRef;
        private String customsDeclarationRef;
        private String customsReleaseRef;
        private String deliveryEvidenceRef;
        private String closeReason;
        private String createdByPrincipalId;
        private Long version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime closedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class ComplianceAssessmentRecord {
        private String assessmentId;
        private Long tenantId;
        private String caseId;
        private String factsJson;
        private String optionsJson;
        private String recommendation;
        private String risksJson;
        private BigDecimal confidence;
        private String missingFactsJson;
        private String evidenceRef;
        private String assessedByPrincipalId;
        private LocalDateTime assessedAt;
        private LocalDateTime createdAt;
    }

    @Data
    @Accessors(chain = true)
    public static class StatusHistoryRecord {
        private Long historyId;
        private Long tenantId;
        private String caseId;
        private Long aggregateVersion;
        private String operation;
        private String previousStatus;
        private String currentStatus;
        private String actorPrincipalId;
        private LocalDateTime occurredAt;
        private String detailJson;
        private LocalDateTime createdAt;
    }
}
