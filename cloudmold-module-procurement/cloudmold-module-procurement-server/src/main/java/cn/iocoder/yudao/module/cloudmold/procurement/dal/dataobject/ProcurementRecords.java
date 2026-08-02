package cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ProcurementRecords {
    private ProcurementRecords() {
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
    public static class ProcurementOrder {
        private String orderId;
        private Long tenantId;
        private String orderCode;
        private String sourceBusinessType;
        private String sourceBusinessRef;
        private String awardId;
        private Long awardVersion;
        private String legalEntityId;
        private String supplierId;
        private String currencyCode;
        private Integer leadTimeDays;
        private Long headerNetAmountMinor;
        private Long headerTaxAmountMinor;
        private Long headerGrossAmountMinor;
        private String taxCalculationPolicyCode;
        private String roundingPolicyCode;
        private String status;
        private String createdByPrincipalId;
        private String submittedByPrincipalId;
        private String approvedByPrincipalId;
        private String releasedByPrincipalId;
        private String dispatchedByPrincipalId;
        private String supplierConfirmedByPrincipalId;
        private String cancelledByPrincipalId;
        private String closedByPrincipalId;
        private String reasonCode;
        private String remark;
        private Long version;
        private LocalDateTime createdAt;
        private LocalDateTime submittedAt;
        private LocalDateTime approvedAt;
        private LocalDateTime releasedAt;
        private LocalDateTime updatedAt;
        private LocalDateTime dispatchedAt;
        private LocalDateTime supplierConfirmedAt;
        private LocalDateTime cancelledAt;
        private LocalDateTime closedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class PurchaseOrderItem {
        private String itemId;
        private Long tenantId;
        private String orderId;
        private Integer lineNumber;
        private String awardLineId;
        private String canonicalSkuId;
        private BigDecimal orderedQuantity;
        private String uomCode;
        private String taxCode;
        private Integer taxRateBps;
        private BigDecimal unitNetPriceMinor;
        private String valuationPolicyId;
        private String valuationPolicyVersion;
        private String valuationPolicyHash;
        private Long lineNetAmountMinor;
        private Long lineTaxAmountMinor;
        private Long lineGrossAmountMinor;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class PurchaseOrderAwardSource {
        private Long tenantId;
        private String orderId;
        private String awardId;
        private Long awardVersion;
        private String awardLineId;
        private String itemId;
        private String sourceSnapshotId;
        private LocalDateTime createdAt;
    }

    @Data
    @Accessors(chain = true)
    public static class PurchaseOrderDeliverySchedule {
        private String scheduleId;
        private Long tenantId;
        private String orderId;
        private String itemId;
        private Integer scheduleNumber;
        private LocalDate requiredDeliveryDate;
        private String canonicalWarehouseId;
        private BigDecimal scheduledQuantity;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class OrderStatusHistory {
        private Long historyId;
        private Long tenantId;
        private String orderId;
        private Long operationId;
        private Long aggregateVersion;
        private String status;
        private String actorPrincipalId;
        private String reasonCode;
        private LocalDateTime occurredAt;
        private LocalDateTime createdAt;
    }

    @Data
    @Accessors(chain = true)
    public static class PurchaseRequisition {
        private String requisitionId;
        private Long tenantId;
        private String requisitionCode;
        private String sourceBusinessType;
        private String sourceBusinessRef;
        private String legalEntityId;
        private String taxCalculationPolicyCode;
        private String roundingPolicyCode;
        private String status;
        private String requestedByPrincipalId;
        private String approvedByPrincipalId;
        private String reasonCode;
        private String remark;
        private Long version;
        private LocalDateTime requestedAt;
        private LocalDateTime approvedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class PurchaseRequisitionLine {
        private String lineId;
        private Long tenantId;
        private String requisitionId;
        private Integer lineNumber;
        private String canonicalSkuId;
        private BigDecimal requestedQuantity;
        private String uomCode;
        private String valuationPolicyId;
        private String valuationPolicyVersion;
        private String valuationPolicyHash;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class PurchaseRequisitionDeliverySchedule {
        private String scheduleId;
        private Long tenantId;
        private String requisitionId;
        private String lineId;
        private Integer scheduleNumber;
        private String canonicalWarehouseId;
        private LocalDate requiredDeliveryDate;
        private BigDecimal scheduledQuantity;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class PurchaseRequisitionStatusHistory {
        private Long historyId;
        private Long tenantId;
        private String requisitionId;
        private Long operationId;
        private Long aggregateVersion;
        private String status;
        private String actorPrincipalId;
        private String reasonCode;
        private LocalDateTime occurredAt;
        private LocalDateTime createdAt;
    }
}
