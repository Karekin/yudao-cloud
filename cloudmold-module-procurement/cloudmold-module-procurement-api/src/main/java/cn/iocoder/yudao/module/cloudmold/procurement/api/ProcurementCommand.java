package cn.iocoder.yudao.module.cloudmold.procurement.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
        private String supplierId;
        private String currencyCode;
        private Integer leadTimeDays;
        private Long headerNetAmountMinor;
        private Long headerTaxAmountMinor;
        private Long headerGrossAmountMinor;
        private String taxCalculationPolicyCode;
        private String roundingPolicyCode;
        private String remark;
        private String reasonCode;
        private Long expectedVersion;
        private List<PurchaseOrderLineDefinition> lines;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PurchaseOrderLineDefinition {
        private String itemId;
        private Integer lineNumber;
        private String canonicalSkuId;
        private BigDecimal orderedQuantity;
        private String uomCode;
        private String taxCode;
        private Integer taxRateBps;
        private BigDecimal unitNetPriceMinor;
        private Long lineNetAmountMinor;
        private Long lineTaxAmountMinor;
        private Long lineGrossAmountMinor;
        private List<PurchaseOrderDeliveryScheduleDefinition> schedules;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PurchaseOrderDeliveryScheduleDefinition {
        private String scheduleId;
        private Integer scheduleNumber;
        private LocalDate requiredDeliveryDate;
        private String canonicalWarehouseId;
        private BigDecimal scheduledQuantity;
    }

}
