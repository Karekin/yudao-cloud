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
        private String supplierRef;
        private String canonicalSkuId;
        private String canonicalWarehouseId;
        private BigDecimal orderedQuantity;
        private String uomCode;
        private Long unitCostMinor;
        private Long totalAmountMinor;
        private String currencyCode;
        private Integer leadTimeDays;
        private LocalDate requiredDeliveryDate;
        private String status;
        private String createdByPrincipalId;
        private String dispatchedByPrincipalId;
        private String supplierConfirmedByPrincipalId;
        private String cancelledByPrincipalId;
        private String closedByPrincipalId;
        private String reasonCode;
        private String remark;
        private String projectionSourceSystem;
        private String projectionDocumentType;
        private String projectionExternalDocumentId;
        private String projectionExternalDocumentNo;
        private String projectionDocumentStatus;
        private String projectionEvidenceSha256;
        private Long version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime dispatchedAt;
        private LocalDateTime supplierConfirmedAt;
        private LocalDateTime cancelledAt;
        private LocalDateTime closedAt;
    }
}
