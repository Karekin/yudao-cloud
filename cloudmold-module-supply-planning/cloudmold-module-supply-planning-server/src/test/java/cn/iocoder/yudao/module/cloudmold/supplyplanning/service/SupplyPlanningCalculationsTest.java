package cn.iocoder.yudao.module.cloudmold.supplyplanning.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SupplyPlanningCalculationsTest {

    @Test
    void calculatesWapeBiasAndMaeFromSameGrainPairs() {
        SupplyPlanningCalculations.ForecastMetrics result =
                SupplyPlanningCalculations.evaluateForecast(List.of(
                        new SupplyPlanningCalculations.ForecastPair(
                                new BigDecimal("120"), new BigDecimal("100")),
                        new SupplyPlanningCalculations.ForecastPair(
                                new BigDecimal("80"), new BigDecimal("100"))));

        assertThat(result.forecastQuantity()).isEqualByComparingTo("200");
        assertThat(result.actualQuantity()).isEqualByComparingTo("200");
        assertThat(result.absoluteError()).isEqualByComparingTo("40");
        assertThat(result.signedError()).isEqualByComparingTo("0");
        assertThat(result.wapeBasisPoints()).isEqualTo(2_000);
        assertThat(result.biasBasisPoints()).isZero();
        assertThat(result.mae()).isEqualByComparingTo("20");
    }

    @Test
    void keepsUndefinedRatioMetricsNullWhenActualDemandIsZero() {
        SupplyPlanningCalculations.ForecastMetrics result =
                SupplyPlanningCalculations.evaluateForecast(List.of(
                        new SupplyPlanningCalculations.ForecastPair(
                                BigDecimal.TEN, BigDecimal.ZERO)));

        assertThat(result.wapeBasisPoints()).isNull();
        assertThat(result.biasBasisPoints()).isNull();
        assertThat(result.mae()).isEqualByComparingTo("10");
    }

    @Test
    void constrainsScenarioByCapacityBudgetAndMinimumOrderQuantity() {
        SupplyPlanningCalculations.ScenarioResult result =
                SupplyPlanningCalculations.evaluateScenario(
                        new BigDecimal("100"), new BigDecimal("10"),
                        new BigDecimal("20"), new BigDecimal("10"),
                        new BigDecimal("70"), new BigDecimal("20"),
                        100L, 5_000L);

        assertThat(result.constrainedOrderQuantity()).isEqualByComparingTo("50");
        assertThat(result.projectedShortageQuantity()).isEqualByComparingTo("30");
        assertThat(result.projectedServiceLevelBasisPoints()).isEqualTo(7_272);
        assertThat(result.projectedCostMinor()).isEqualTo(5_000L);
    }
}
