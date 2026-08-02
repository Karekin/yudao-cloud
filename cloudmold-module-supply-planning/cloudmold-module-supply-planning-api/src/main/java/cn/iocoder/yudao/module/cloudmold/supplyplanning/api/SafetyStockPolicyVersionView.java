package cn.iocoder.yudao.module.cloudmold.supplyplanning.api;

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
public class SafetyStockPolicyVersionView {
    private String policyVersionId;
    private Long version;
    private String status;
    private String ownerType;
    private String ownerId;
    private String canonicalSkuId;
    private String warehouseNetworkId;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Integer targetServiceLevelBasisPoints;
    private BigDecimal safetyStockQuantity;
    private BigDecimal reorderPointQuantity;
    private BigDecimal maximumStockQuantity;
    private Integer replenishmentCycleDays;
    private Integer leadTimeDays;
    private String policyBasisCode;
    private String policySha256;
    private String evidenceRef;
    private String actorPrincipalId;
    private LocalDateTime createdAt;
}
