package cn.iocoder.yudao.module.cloudmold.supplyplanning.api;

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
public class SafetyStockPolicyView {
    private String policyId;
    private String policyCode;
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
    private String status;
    private Long currentVersion;
    private Long approvedVersion;
    private Long publishedVersion;
    private String createdByPrincipalId;
    private String approvedByPrincipalId;
    private String publishedByPrincipalId;
    private String retiredByPrincipalId;
    private LocalDateTime approvedAt;
    private LocalDateTime publishedAt;
    private LocalDateTime retiredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<SafetyStockPolicyVersionView> history;
}
