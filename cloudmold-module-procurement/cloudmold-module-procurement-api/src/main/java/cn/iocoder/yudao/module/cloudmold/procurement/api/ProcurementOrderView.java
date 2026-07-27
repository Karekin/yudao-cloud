package cn.iocoder.yudao.module.cloudmold.procurement.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcurementOrderView {
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
    private String status;
    private Long version;
    private String createdByPrincipalId;
    private String dispatchedByPrincipalId;
    private String supplierConfirmedByPrincipalId;
    private String cancelledByPrincipalId;
    private String closedByPrincipalId;
    private String reasonCode;
    private String projectionSourceSystem;
    private String projectionDocumentType;
    private String projectionExternalDocumentId;
    private String projectionExternalDocumentNo;
    private String projectionDocumentStatus;
    private String projectionEvidenceSha256;
    private LocalDateTime createdAt;
    private LocalDateTime dispatchedAt;
    private LocalDateTime supplierConfirmedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime closedAt;
}
