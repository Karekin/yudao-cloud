package cn.iocoder.yudao.module.cloudmold.supplyplanning.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
}
