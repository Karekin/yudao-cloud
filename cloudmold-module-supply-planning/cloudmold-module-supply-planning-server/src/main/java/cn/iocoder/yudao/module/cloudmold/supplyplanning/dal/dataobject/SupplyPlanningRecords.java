package cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.dataobject;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class SupplyPlanningRecords {
    private SupplyPlanningRecords() {
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
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class Forecast {
        private String forecastId;
        private Long tenantId;
        private String forecastCode;
        private LocalDate horizonStart;
        private LocalDate horizonEnd;
        private String bucketType;
        private String modelRef;
        private String baselineSha256;
        private String status;
        private Long version;
        private LocalDateTime publishedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class ForecastPoint {
        private Long tenantId;
        private String forecastId;
        private String canonicalSkuId;
        private String warehouseId;
        private LocalDate bucketStart;
        private BigDecimal forecastQuantity;
        private BigDecimal lowerQuantity;
        private BigDecimal upperQuantity;
        private String uomCode;
        private LocalDateTime createdAt;
    }

    @Data
    @Accessors(chain = true)
    public static class ForecastEvaluation {
        private String evaluationId;
        private Long tenantId;
        private String forecastId;
        private String actualsSha256;
        private Integer pointCount;
        private BigDecimal forecastQuantity;
        private BigDecimal actualQuantity;
        private BigDecimal absoluteError;
        private BigDecimal signedError;
        private Integer wapeBasisPoints;
        private Integer biasBasisPoints;
        private BigDecimal mae;
        private String status;
        private Long version;
        private LocalDateTime evaluatedAt;
        private LocalDateTime createdAt;
    }

    @Data
    @Accessors(chain = true)
    public static class ForecastActual {
        private Long tenantId;
        private String evaluationId;
        private String canonicalSkuId;
        private String warehouseId;
        private LocalDate bucketStart;
        private BigDecimal forecastQuantity;
        private BigDecimal actualQuantity;
        private BigDecimal absoluteError;
        private BigDecimal signedError;
        private String uomCode;
        private LocalDateTime createdAt;
    }

    @Data
    @Accessors(chain = true)
    public static class SupplyPlan {
        private String planId;
        private Long tenantId;
        private String planCode;
        private String demandForecastId;
        private LocalDate horizonStart;
        private LocalDate horizonEnd;
        private Integer targetServiceLevelBasisPoints;
        private Long budgetAmountMinor;
        private String currencyCode;
        private String constraintsSha256;
        private String status;
        private String approverPrincipalId;
        private String selectedScenarioId;
        private String releasePrincipalId;
        private Long version;
        private LocalDateTime approvedAt;
        private LocalDateTime releasedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class PlanScenario {
        private String scenarioId;
        private Long tenantId;
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
        private BigDecimal constrainedOrderQuantity;
        private BigDecimal projectedShortageQuantity;
        private Integer projectedServiceLevelBasisPoints;
        private Long projectedCostMinor;
        private String uomCode;
        private String parametersSha256;
        private String solverType;
        private String status;
        private String selectedByPrincipalId;
        private Long version;
        private LocalDateTime evaluatedAt;
        private LocalDateTime selectedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class PlanScenarioRecommendation {
        private String recommendationId;
        private Long tenantId;
        private String planId;
        private String recommendedScenarioId;
        private String candidateSetSha256;
        private String candidateScenarioIdsJson;
        private String policySha256;
        private Integer targetServiceLevelFloorBasisPoints;
        private Long maxProjectedCostMinor;
        private Integer demandStressBasisPoints;
        private Integer supplyAvailabilityBasisPoints;
        private Integer worstCaseServiceLevelBasisPoints;
        private Long projectedCostMinor;
        private BigDecimal projectedShortageQuantity;
        private Integer sensitivityBasisPoints;
        private Integer violationCount;
        private String constraintViolationsJson;
        private String rationaleJson;
        private String solverType;
        private String status;
        private Long version;
        private LocalDateTime recommendedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class Replenishment {
        private String recommendationId;
        private Long tenantId;
        private String planId;
        private String canonicalSkuId;
        private String warehouseId;
        private BigDecimal suggestedQuantity;
        private String uomCode;
        private LocalDate needByDate;
        private String reasonCode;
        private String status;
        private String decisionPrincipalId;
        private LocalDateTime decisionAt;
        private Long version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class ReplenishmentConversion {
        private String conversionId;
        private Long tenantId;
        private String recommendationId;
        private String targetType;
        private String targetReference;
        private String sourceSystem;
        private String documentType;
        private String externalDocumentId;
        private String externalDocumentNo;
        private String documentStatus;
        private String nextWaitingEventCode;
        private String nextWaitingEventLabel;
        private BigDecimal requestedQuantity;
        private String uomCode;
        private String status;
        private String convertedByPrincipalId;
        private Long version;
        private LocalDateTime convertedAt;
        private LocalDateTime createdAt;
    }

    @Data
    @Accessors(chain = true)
    public static class InventoryIssue {
        private String issueId;
        private Long tenantId;
        private String sourceBalanceId;
        private String issueType;
        private String severity;
        private String status;
        private String ownerPrincipalId;
        private String resolutionCode;
        private String scanId;
        private String detectionSource;
        private Long version;
        private LocalDateTime openedAt;
        private LocalDateTime acknowledgedAt;
        private LocalDateTime resolvedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class InventoryHealthScan {
        private String scanId;
        private Long tenantId;
        private String policyCode;
        private String policySha256;
        private Integer observationCount;
        private Integer issueCount;
        private Integer skippedActiveCount;
        private String status;
        private Long version;
        private LocalDateTime evaluatedAt;
        private LocalDateTime createdAt;
    }
}
