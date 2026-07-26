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

    @Test
    void recommendsFeasibleScenarioUsingWorstCaseServiceThenShortageAndCost() {
        SupplyPlanningCalculations.RecommendationResult result =
                SupplyPlanningCalculations.recommendScenario(List.of(
                                candidate("scenario-costly", "100", "10", "20", "10",
                                        "100", "10", 100L),
                                candidate("scenario-robust", "100", "10", "60", "20",
                                        "100", "10", 100L)),
                        20_000L, 9_000, 10_000L, 12_000, 8_000);

        assertThat(result.recommended().scenarioId()).isEqualTo("scenario-robust");
        assertThat(result.recommended().constraintViolations()).isEmpty();
        assertThat(result.rankedAssessments()).extracting(
                        SupplyPlanningCalculations.ScenarioAssessment::scenarioId)
                .containsExactly("scenario-robust", "scenario-costly");
    }

    @Test
    void ranksAllInfeasibleScenariosDeterministicallyAndReportsPolicyViolations() {
        SupplyPlanningCalculations.ScenarioCandidate alpha =
                candidate("scenario-alpha", "100", "10", "0", "0", "10", "1", 100L);
        SupplyPlanningCalculations.ScenarioCandidate beta =
                candidate("scenario-beta", "100", "10", "0", "0", "10", "1", 100L);

        SupplyPlanningCalculations.RecommendationResult result =
                SupplyPlanningCalculations.recommendScenario(
                        List.of(beta, alpha), 10_000L, 9_900, 499L, 15_000, 5_000);

        assertThat(result.recommended().scenarioId()).isEqualTo("scenario-alpha");
        assertThat(result.recommended().constraintViolations())
                .containsExactly("SERVICE_LEVEL_FLOOR", "COST_CEILING");
    }

    private static SupplyPlanningCalculations.ScenarioCandidate candidate(
            String id, String forecast, String safetyStock, String onHand, String inbound,
            String capacity, String minimumOrder, long unitCostMinor) {
        return new SupplyPlanningCalculations.ScenarioCandidate(
                id, new BigDecimal(forecast), new BigDecimal(safetyStock),
                new BigDecimal(onHand), new BigDecimal(inbound), new BigDecimal(capacity),
                new BigDecimal(minimumOrder), unitCostMinor);
    }
}
