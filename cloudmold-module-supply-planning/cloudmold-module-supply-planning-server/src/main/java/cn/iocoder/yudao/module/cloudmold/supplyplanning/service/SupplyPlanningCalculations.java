package cn.iocoder.yudao.module.cloudmold.supplyplanning.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class SupplyPlanningCalculations {
    private static final BigDecimal TEN_THOUSAND = BigDecimal.valueOf(10_000);
    private static final int SCALE = 6;

    private SupplyPlanningCalculations() {
    }

    static ForecastMetrics evaluateForecast(List<ForecastPair> pairs) {
        BigDecimal forecast = BigDecimal.ZERO;
        BigDecimal actual = BigDecimal.ZERO;
        BigDecimal absoluteError = BigDecimal.ZERO;
        BigDecimal signedError = BigDecimal.ZERO;
        for (ForecastPair pair : pairs) {
            forecast = forecast.add(pair.forecast());
            actual = actual.add(pair.actual());
            BigDecimal error = pair.forecast().subtract(pair.actual());
            signedError = signedError.add(error);
            absoluteError = absoluteError.add(error.abs());
        }
        Integer wape = actual.signum() == 0 ? null
                : absoluteError.multiply(TEN_THOUSAND).divide(actual, 0, RoundingMode.HALF_UP).intValueExact();
        Integer bias = actual.signum() == 0 ? null
                : signedError.multiply(TEN_THOUSAND).divide(actual, 0, RoundingMode.HALF_UP).intValueExact();
        BigDecimal mae = absoluteError.divide(BigDecimal.valueOf(pairs.size()), SCALE, RoundingMode.HALF_UP);
        return new ForecastMetrics(forecast, actual, absoluteError, signedError, wape, bias, mae);
    }

    static ScenarioResult evaluateScenario(BigDecimal forecastQuantity,
                                           BigDecimal safetyStockQuantity,
                                           BigDecimal onHandQuantity,
                                           BigDecimal inboundQuantity,
                                           BigDecimal capacityQuantity,
                                           BigDecimal minimumOrderQuantity,
                                           long unitCostMinor,
                                           long budgetAmountMinor) {
        BigDecimal demand = forecastQuantity.add(safetyStockQuantity);
        BigDecimal available = onHandQuantity.add(inboundQuantity);
        BigDecimal needed = demand.subtract(available).max(BigDecimal.ZERO);
        BigDecimal affordable = unitCostMinor == 0
                ? capacityQuantity
                : BigDecimal.valueOf(budgetAmountMinor)
                .divide(BigDecimal.valueOf(unitCostMinor), SCALE, RoundingMode.DOWN);
        BigDecimal constrained = needed.min(capacityQuantity).min(affordable);
        if (constrained.signum() > 0 && constrained.compareTo(minimumOrderQuantity) < 0) {
            BigDecimal minimumFeasible = minimumOrderQuantity.min(capacityQuantity).min(affordable);
            constrained = minimumFeasible.compareTo(minimumOrderQuantity) < 0
                    ? BigDecimal.ZERO : minimumFeasible;
        }
        BigDecimal fulfilled = available.add(constrained).min(demand);
        BigDecimal shortage = demand.subtract(fulfilled).max(BigDecimal.ZERO);
        int serviceLevel = demand.signum() == 0 ? 10_000
                : fulfilled.multiply(TEN_THOUSAND).divide(demand, 0, RoundingMode.DOWN).intValue();
        long projectedCost = constrained.multiply(BigDecimal.valueOf(unitCostMinor))
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
        return new ScenarioResult(constrained, shortage, Math.min(10_000, serviceLevel), projectedCost);
    }

    static RecommendationResult recommendScenario(List<ScenarioCandidate> candidates,
                                                   long budgetAmountMinor,
                                                   int serviceLevelFloorBasisPoints,
                                                   long maxProjectedCostMinor,
                                                   int demandStressBasisPoints,
                                                   int supplyAvailabilityBasisPoints) {
        List<ScenarioAssessment> assessments = new ArrayList<>();
        for (ScenarioCandidate candidate : candidates) {
            ScenarioResult baseline = evaluateScenario(
                    candidate.forecastQuantity(), candidate.safetyStockQuantity(),
                    candidate.onHandQuantity(), candidate.inboundQuantity(),
                    candidate.capacityQuantity(), candidate.minimumOrderQuantity(),
                    candidate.unitCostMinor(), budgetAmountMinor);
            ScenarioResult stressed = evaluateScenario(
                    applyBasisPoints(candidate.forecastQuantity(), demandStressBasisPoints),
                    applyBasisPoints(candidate.safetyStockQuantity(), demandStressBasisPoints),
                    candidate.onHandQuantity(),
                    applyBasisPoints(candidate.inboundQuantity(), supplyAvailabilityBasisPoints),
                    applyBasisPoints(candidate.capacityQuantity(), supplyAvailabilityBasisPoints),
                    candidate.minimumOrderQuantity(), candidate.unitCostMinor(), budgetAmountMinor);
            List<String> violations = new ArrayList<>();
            if (stressed.projectedServiceLevelBasisPoints() < serviceLevelFloorBasisPoints) {
                violations.add("SERVICE_LEVEL_FLOOR");
            }
            if (stressed.projectedCostMinor() > maxProjectedCostMinor) {
                violations.add("COST_CEILING");
            }
            assessments.add(new ScenarioAssessment(
                    candidate.scenarioId(), baseline, stressed,
                    Math.max(0, baseline.projectedServiceLevelBasisPoints()
                            - stressed.projectedServiceLevelBasisPoints()),
                    List.copyOf(violations)));
        }
        Comparator<ScenarioAssessment> comparator = Comparator
                .comparingInt((ScenarioAssessment value) -> value.constraintViolations().size())
                .thenComparing((ScenarioAssessment value) ->
                        value.stressed().projectedServiceLevelBasisPoints(), Comparator.reverseOrder())
                .thenComparing(value -> value.stressed().projectedShortageQuantity())
                .thenComparing(value -> value.stressed().projectedCostMinor())
                .thenComparing(ScenarioAssessment::scenarioId);
        List<ScenarioAssessment> ranked = assessments.stream().sorted(comparator).toList();
        return new RecommendationResult(ranked.get(0), ranked);
    }

    private static BigDecimal applyBasisPoints(BigDecimal value, int basisPoints) {
        return value.multiply(BigDecimal.valueOf(basisPoints))
                .divide(TEN_THOUSAND, SCALE, RoundingMode.HALF_UP);
    }

    record ForecastPair(BigDecimal forecast, BigDecimal actual) {
    }

    record ForecastMetrics(BigDecimal forecastQuantity, BigDecimal actualQuantity,
                           BigDecimal absoluteError, BigDecimal signedError,
                           Integer wapeBasisPoints, Integer biasBasisPoints, BigDecimal mae) {
    }

    record ScenarioResult(BigDecimal constrainedOrderQuantity,
                          BigDecimal projectedShortageQuantity,
                          Integer projectedServiceLevelBasisPoints,
                          Long projectedCostMinor) {
    }

    record ScenarioCandidate(String scenarioId,
                             BigDecimal forecastQuantity,
                             BigDecimal safetyStockQuantity,
                             BigDecimal onHandQuantity,
                             BigDecimal inboundQuantity,
                             BigDecimal capacityQuantity,
                             BigDecimal minimumOrderQuantity,
                             long unitCostMinor) {
    }

    record ScenarioAssessment(String scenarioId,
                              ScenarioResult baseline,
                              ScenarioResult stressed,
                              int sensitivityBasisPoints,
                              List<String> constraintViolations) {
    }

    record RecommendationResult(ScenarioAssessment recommended,
                                List<ScenarioAssessment> rankedAssessments) {
    }
}
