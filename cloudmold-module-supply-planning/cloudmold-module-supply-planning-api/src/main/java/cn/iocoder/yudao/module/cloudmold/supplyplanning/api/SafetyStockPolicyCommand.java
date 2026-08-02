package cn.iocoder.yudao.module.cloudmold.supplyplanning.api;

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
public class SafetyStockPolicyCommand {
    private SafetyStockPolicyOperation operation;
    private String idempotencyKey;
    private String runId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    private PolicyDefinition policy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyDefinition {
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
        private Long expectedVersion;
    }
}
