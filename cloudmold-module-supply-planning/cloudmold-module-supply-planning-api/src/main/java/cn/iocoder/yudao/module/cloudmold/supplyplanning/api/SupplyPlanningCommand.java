package cn.iocoder.yudao.module.cloudmold.supplyplanning.api;

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
public class SupplyPlanningCommand {
    private SupplyPlanningOperation operation;
    private String idempotencyKey;
    private String runId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    private ForecastDefinition forecast;
    private ForecastEvaluationDefinition forecastEvaluation;
    private SupplyPlanDefinition supplyPlan;
    private PlanScenarioDefinition planScenario;
    private ReplenishmentDefinition replenishment;
    private ReplenishmentConversionDefinition replenishmentConversion;
    private InventoryIssueDefinition inventoryIssue;
    private InventoryHealthScanDefinition inventoryHealthScan;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForecastDefinition {
        private String forecastId;
        private String forecastCode;
        private LocalDate horizonStart;
        private LocalDate horizonEnd;
        private String bucketType;
        private String modelRef;
        private String baselineSha256;
        private Long expectedVersion;
        private List<ForecastPointDefinition> points;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForecastPointDefinition {
        private String canonicalSkuId;
        private String warehouseId;
        private LocalDate bucketStart;
        private BigDecimal forecastQuantity;
        private BigDecimal lowerQuantity;
        private BigDecimal upperQuantity;
        private String uomCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForecastEvaluationDefinition {
        private String evaluationId;
        private String forecastId;
        private String actualsSha256;
        private List<ForecastActualDefinition> actuals;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForecastActualDefinition {
        private String canonicalSkuId;
        private String warehouseId;
        private LocalDate bucketStart;
        private BigDecimal actualQuantity;
        private String uomCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupplyPlanDefinition {
        private String planId;
        private String planCode;
        private String demandForecastId;
        private LocalDate horizonStart;
        private LocalDate horizonEnd;
        private Integer targetServiceLevelBasisPoints;
        private Long budgetAmountMinor;
        private String currencyCode;
        private String constraintsSha256;
        private Long expectedVersion;
        private String approverPrincipalId;
        private String releasePrincipalId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanScenarioDefinition {
        private String scenarioId;
        private String planId;
        private String scenarioCode;
        private String canonicalSkuId;
        private String warehouseId;
        private BigDecimal forecastQuantity;
        private BigDecimal safetyStockQuantity;
        private BigDecimal onHandQuantity;
        private BigDecimal inboundQuantity;
        private BigDecimal capacityQuantity;
        private BigDecimal minimumOrderQuantity;
        private Long unitCostMinor;
        private String uomCode;
        private String parametersSha256;
        private Long expectedVersion;
        private String selectedByPrincipalId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReplenishmentDefinition {
        private String recommendationId;
        private String planId;
        private String canonicalSkuId;
        private String warehouseId;
        private BigDecimal suggestedQuantity;
        private String uomCode;
        private LocalDate needByDate;
        private String reasonCode;
        private Long expectedVersion;
        private String decision;
        private String decisionPrincipalId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReplenishmentConversionDefinition {
        private String conversionId;
        private String recommendationId;
        private Long expectedVersion;
        private String targetType;
        private String mappingEvidenceSha256;
        private Long supplierId;
        private Long accountId;
        private Long erpProductId;
        private Long erpProductUnitId;
        private Long unitCostMinor;
        private BigDecimal taxPercent;
        private Long sourceWarehouseId;
        private Long targetWarehouseId;
        private Long wmsSkuId;
        private String convertedByPrincipalId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryIssueDefinition {
        private String issueId;
        private String sourceBalanceId;
        private String issueType;
        private String severity;
        private String ownerPrincipalId;
        private Long expectedVersion;
        private String resolutionCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryHealthScanDefinition {
        private String scanId;
        private String policyCode;
        private String policySha256;
        private Integer agedThresholdDays;
        private List<InventoryHealthObservationDefinition> observations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryHealthObservationDefinition {
        private String sourceBalanceId;
        private BigDecimal availableQuantity;
        private BigDecimal reorderPointQuantity;
        private BigDecimal maximumStockQuantity;
        private Integer ageDays;
        private Boolean pendingQualityInspection;
    }
}
