package cn.iocoder.yudao.module.cloudmold.procurement.api;

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
public class ProcurementCommand {
    private ProcurementOperation operation;
    private String idempotencyKey;
    private String runId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    private PurchaseOrderDefinition purchaseOrder;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PurchaseOrderDefinition {
        private String orderId;
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
        private String remark;
        private String reasonCode;
        private Long expectedVersion;
        private ProjectionDefinition projection;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectionDefinition {
        private String sourceSystem;
        private String documentType;
        private String externalDocumentId;
        private String externalDocumentNo;
        private String documentStatus;
        private String evidenceSha256;
    }
}
