package cn.iocoder.yudao.module.cloudmold.procurement.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcurementOrderView {
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
    private String status;
    private Long version;
    private String createdByPrincipalId;
    private String dispatchedByPrincipalId;
    private String supplierConfirmedByPrincipalId;
    private String cancelledByPrincipalId;
    private String closedByPrincipalId;
    private String reasonCode;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime dispatchedAt;
    private LocalDateTime supplierConfirmedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime closedAt;
    private List<PurchaseOrderItemView> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PurchaseOrderItemView {
        private String itemId;
        private Integer lineNumber;
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
        private List<PurchaseOrderDeliveryScheduleView> schedules;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PurchaseOrderDeliveryScheduleView {
        private String scheduleId;
        private Integer scheduleNumber;
        private LocalDate requiredDeliveryDate;
        private String canonicalWarehouseId;
        private BigDecimal scheduledQuantity;
    }

}
