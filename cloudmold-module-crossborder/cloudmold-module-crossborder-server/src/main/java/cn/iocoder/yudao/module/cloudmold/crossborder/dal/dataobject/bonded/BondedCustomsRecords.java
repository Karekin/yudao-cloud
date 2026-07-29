package cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.bonded;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class BondedCustomsRecords {
    private BondedCustomsRecords() {
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
        private String mode;
        private String canonicalOrderId;
        private String status;
        private String tripleMatchStatus;
        private String customsStatus;
        private String bondedReleaseStatus;
        private String deliveryStatus;
        private String orderRef;
        private String paymentRef;
        private String logisticsRef;
        private Long orderAmountMinor;
        private Long paymentAmountMinor;
        private Long logisticsAmountMinor;
        private String currency;
        private String buyerIdentityHash;
        private String receiverIdentityHash;
        private String declarantIdentityHash;
        private String orderSnapshotRef;
        private String paymentSnapshotRef;
        private String logisticsSnapshotRef;
        private String assessmentId;
        private String hsCode;
        private String positiveListCode;
        private String goodsName;
        private String goodsEvidenceRef;
        private Long dutiableAmountMinor;
        private Long consumptionTaxMinor;
        private Long valueAddedTaxMinor;
        private Long totalTaxMinor;
        private String taxCurrency;
        private String taxEvidenceRef;
        private String approvalRef;
        private String declarationRef;
        private String customsAcceptanceRef;
        private String bondedReleaseRef;
        private String deliveryConfirmationRef;
        private String closeReason;
        private String createdByPrincipalId;
        private Long version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime closedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class EligibilityAssessmentRecord {
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
