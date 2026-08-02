package cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public final class ProcureInventoryFinanceReconciliationAdminVOs {
    private ProcureInventoryFinanceReconciliationAdminVOs() {
    }

    @Data
    public static class CreateRunRequest {
        private String idempotencyKey;
        private String runId;
        private String correlationId;
        private String causationId;
        private Instant occurredAt;
        private String legalEntityId;
        private String currencyCode;
    }

    @Data
    public static class RunCommandResult {
        private String operationId;
        private Boolean duplicate;
        private String aggregateType;
        private String aggregateId;
        private String runCode;
        private String status;
        private Integer lineCount;
        private Integer matchedCount;
        private Integer differentCount;
        private Integer missingCount;
        private Integer uncomparableCount;
    }

    @Data
    public static class RunPageRequest {
        private Integer pageNo = 1;
        private Integer pageSize = 20;
        private String keyword;
        private String status;
        private String legalEntityId;
        private String currencyCode;
    }

    @Data
    public static class RunPageItem {
        private String runId;
        private String runCode;
        private String legalEntityId;
        private String currencyCode;
        private String status;
        private Integer lineCount;
        private Integer matchedCount;
        private Integer differentCount;
        private Integer missingCount;
        private Integer uncomparableCount;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
    }

    @Data
    public static class RunDetail {
        private String runId;
        private String runCode;
        private String legalEntityId;
        private String currencyCode;
        private String reconciliationPolicyVersion;
        private String status;
        private Integer lineCount;
        private Integer matchedCount;
        private Integer differentCount;
        private Integer missingCount;
        private Integer uncomparableCount;
        private String requestedByPrincipalId;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private List<WatermarkItem> watermarks;
    }

    @Data
    public static class WatermarkItem {
        private String domainCode;
        private String sourceTable;
        private Long maxAggregateVersion;
        private LocalDateTime maxObservedAt;
        private Integer recordCount;
    }

    @Data
    public static class LinePageRequest {
        private Integer pageNo = 1;
        private Integer pageSize = 20;
        private String runId;
        private String keyword;
        private String matchStatus;
        private String lineType;
    }

    @Data
    public static class LinePageItem {
        private String lineId;
        private String lineType;
        private String lineKey;
        private String legalEntityId;
        private String currencyCode;
        private String purchaseOrderId;
        private String purchaseOrderItemId;
        private String receiptLineId;
        private String inventoryMovementId;
        private String supplierReturnLineId;
        private String supplierInvoiceLineId;
        private String responsibilityDomain;
        private String matchStatus;
        private String primaryDifferenceCode;
        private Integer differenceCount;
        private String procurementQuantity;
        private String inventoryQuantity;
        private String financeQuantity;
        private String quantityDifference;
        private String procurementAmountMinor;
        private String inventoryAmountMinor;
        private String financeAmountMinor;
        private String amountDifferenceMinor;
        private Long procurementVersion;
        private Long inventoryVersion;
        private Long financeVersion;
    }

    @Data
    public static class LineDetail extends LinePageItem {
        private String deliveryScheduleId;
        private String supplierReturnId;
        private String supplierInvoiceId;
        private String apOpenItemId;
        private String journalEntryId;
        private List<DifferenceItem> differences;
    }

    @Data
    public static class DifferenceItem {
        private String differenceId;
        private String differenceCode;
        private String sourceDomain;
        private String expectedValue;
        private String actualValue;
        private Boolean blocking;
    }
}
