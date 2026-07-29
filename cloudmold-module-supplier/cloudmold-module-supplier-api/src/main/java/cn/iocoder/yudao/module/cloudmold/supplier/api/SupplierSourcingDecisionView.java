package cn.iocoder.yudao.module.cloudmold.supplier.api;

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
public class SupplierSourcingDecisionView {
    private String sourcingCaseId;
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
    private Integer quoteSupplierCount;
    private String awardedSupplierId;
    private String awardedSupplierName;
    private String awardedQuoteId;
    private Long awardedUnitCostMinor;
    private BigDecimal awardedMoq;
    private Integer awardedLeadTimeDays;
    private BigDecimal awardedCapacityQuantity;
    private String sampleResult;
    private Integer sampleQualityScore;
    private Integer sampleFitScore;
    private Integer sampleDeliveryScore;
    private Integer sampleRiskScore;
    private String decisionRationale;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime awardedAt;
}
