package cn.iocoder.yudao.module.cloudmold.supplyplanning.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.*;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.dataobject.SupplyPlanningRecords.*;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.mysql.SupplyPlanningMapper;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupplyPlanningServiceImplTest {
    private final SupplyPlanningMapper mapper = mock(SupplyPlanningMapper.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final SupplyPlanningServiceImpl service = new SupplyPlanningServiceImpl(mapper, outbox);
    private final AtomicReference<String> requestHash = new AtomicReference<>();
    private final AtomicReference<String> attemptToken = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(17L);
        when(mapper.insertOrResolveOperation(eq(17L), anyString(), anyString(), anyString(),
                anyString(), any())).thenAnswer(invocation -> {
                    requestHash.set(invocation.getArgument(3));
                    attemptToken.set(invocation.getArgument(4));
                    return 1;
                });
        when(mapper.selectLastInsertId()).thenReturn(101L);
        when(mapper.selectOperationForUpdate(101L, 17L)).thenAnswer(invocation ->
                new Operation().setOperationId(101L).setTenantId(17L)
                        .setRequestHash(requestHash.get()).setAttemptToken(attemptToken.get()).setStatus(0));
        when(mapper.insertForecast(any())).thenReturn(1);
        when(mapper.insertForecastPoint(any())).thenReturn(1);
        when(mapper.insertInventoryIssue(any())).thenReturn(1);
        when(mapper.markOperationSucceeded(eq(101L), eq(17L), anyString(), anyString(),
                anyString(), any())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createsTenantScopedForecastSnapshotAndOutboxEvent() {
        SupplyPlanningResult result = service.execute(forecastCommand(LocalDate.of(2026, 8, 1)));

        assertThat(result.getAggregateType()).isEqualTo("demand_forecast");
        assertThat(result.getAggregateVersion()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo("DRAFT");
        verify(mapper).insertForecast(argThat(row -> row.getTenantId().equals(17L)
                && row.getForecastCode().equals("FC-2026-08")
                && row.getBaselineSha256().equals("a".repeat(64))));
        verify(mapper).insertForecastPoint(argThat(row -> row.getTenantId().equals(17L)
                && row.getCanonicalSkuId().equals("sku-01")
                && row.getForecastQuantity().compareTo(new BigDecimal("12.5")) == 0));
        verify(outbox).append(argThat(event -> event.getEventType().equals("supply_planning.forecast.created")
                && event.getTenantId().equals(17L)
                && event.getAggregateId().equals(result.getAggregateId())));
    }

    @Test
    void rejectsForecastPointOutsideSnapshotHorizonBeforeMutation() {
        assertThatThrownBy(() -> service.execute(forecastCommand(LocalDate.of(2026, 9, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("forecast point bucket is outside the horizon");
        verify(mapper).insertForecast(any());
        verify(mapper, never()).insertForecastPoint(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void rejectsSecondActiveInventoryHealthIssueForSameSourceAndType() {
        when(mapper.countActiveInventoryIssues(17L, "balance-01", "STOCKOUT")).thenReturn(1L);
        SupplyPlanningCommand command = base(SupplyPlanningOperation.OPEN_INVENTORY_ISSUE)
                .inventoryIssue(SupplyPlanningCommand.InventoryIssueDefinition.builder()
                        .sourceBalanceId("balance-01").issueType("STOCKOUT")
                        .severity("CRITICAL").build())
                .build();

        assertThatThrownBy(() -> service.execute(command))
                .hasMessage("an active inventory-health issue already exists for this source and type");
        verify(mapper, never()).insertInventoryIssue(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void rejectsMissingCorrelationIdAtCommandBoundary() {
        SupplyPlanningCommand command = base(SupplyPlanningOperation.CREATE_FORECAST)
                .correlationId(null).build();

        assertThatThrownBy(() -> service.execute(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("correlationId must be a UUID");
        verify(mapper, never()).insertOrResolveOperation(anyLong(), anyString(), anyString(),
                anyString(), anyString(), any());
        verifyNoInteractions(outbox);
    }

    @Test
    void releasesSelectedScenarioIntoActionableReplenishment() {
        when(mapper.selectSupplyPlanForUpdate(17L, "plan-01")).thenReturn(new SupplyPlan()
                .setPlanId("plan-01").setTenantId(17L).setPlanCode("PLAN-01")
                .setHorizonStart(LocalDate.of(2026, 8, 10))
                .setStatus("APPROVED").setVersion(2L));
        when(mapper.selectSelectedPlanScenarioForUpdate(17L, "plan-01")).thenReturn(
                new PlanScenario().setScenarioId("scenario-01").setTenantId(17L)
                        .setPlanId("plan-01").setCanonicalSkuId("sku-01")
                        .setWarehouseId("warehouse-01")
                        .setConstrainedOrderQuantity(new BigDecimal("30"))
                        .setProjectedShortageQuantity(BigDecimal.ZERO).setUomCode("EA")
                        .setStatus("SELECTED").setVersion(2L));
        when(mapper.insertReplenishment(any())).thenReturn(1);
        when(mapper.releaseSupplyPlan(eq(17L), eq("plan-01"), eq(2L),
                eq("scenario-01"), eq("principal-planner-01"), any())).thenReturn(1);

        SupplyPlanningResult result = service.execute(base(SupplyPlanningOperation.RELEASE_SUPPLY_PLAN)
                .supplyPlan(SupplyPlanningCommand.SupplyPlanDefinition.builder()
                        .planId("plan-01").expectedVersion(2L)
                        .releasePrincipalId("principal-planner-01").build())
                .build());

        assertThat(result.getStatus()).isEqualTo("RELEASED");
        assertThat(result.getAggregateVersion()).isEqualTo(3L);
        verify(mapper).insertReplenishment(argThat(row ->
                row.getPlanId().equals("plan-01")
                        && row.getSuggestedQuantity().compareTo(new BigDecimal("30")) == 0
                        && row.getNeedByDate().equals(LocalDate.of(2026, 8, 10))
                        && row.getReasonCode().equals("SELECTED_SCENARIO")));
        verify(outbox).append(argThat(event ->
                event.getEventType().equals("supply_planning.replenishment.created")
                        && event.getAggregateType().equals("replenishment_recommendation")));
        verify(outbox).append(argThat(event ->
                event.getEventType().equals("supply_planning.plan.released")));
    }

    @Test
    void rejectsApprovalUntilExactlyOneScenarioIsSelected() {
        when(mapper.selectSupplyPlanForUpdate(17L, "plan-01")).thenReturn(new SupplyPlan()
                .setPlanId("plan-01").setTenantId(17L).setPlanCode("PLAN-01")
                .setStatus("DRAFT").setVersion(1L));
        when(mapper.countSelectedPlanScenarios(17L, "plan-01")).thenReturn(0L, 2L);

        SupplyPlanningCommand command = base(SupplyPlanningOperation.APPROVE_SUPPLY_PLAN)
                .supplyPlan(SupplyPlanningCommand.SupplyPlanDefinition.builder()
                        .planId("plan-01").expectedVersion(1L)
                        .approverPrincipalId("principal-planner-01").build())
                .build();

        assertThatThrownBy(() -> service.execute(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("supply plan requires exactly one selected scenario before approval");
        assertThatThrownBy(() -> service.execute(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("supply plan requires exactly one selected scenario before approval");
        verify(mapper, never()).approveSupplyPlan(anyLong(), anyString(), anyLong(), anyString(), any());
        verifyNoInteractions(outbox);
    }

    @Test
    void approvesPlanAfterOneScenarioIsSelected() {
        when(mapper.selectSupplyPlanForUpdate(17L, "plan-01")).thenReturn(new SupplyPlan()
                .setPlanId("plan-01").setTenantId(17L).setPlanCode("PLAN-01")
                .setDemandForecastId("forecast-01").setStatus("DRAFT").setVersion(1L));
        when(mapper.countSelectedPlanScenarios(17L, "plan-01")).thenReturn(1L);
        when(mapper.approveSupplyPlan(eq(17L), eq("plan-01"), eq(1L),
                eq("principal-planner-01"), any())).thenReturn(1);

        SupplyPlanningResult result = service.execute(
                base(SupplyPlanningOperation.APPROVE_SUPPLY_PLAN)
                        .supplyPlan(SupplyPlanningCommand.SupplyPlanDefinition.builder()
                                .planId("plan-01").expectedVersion(1L)
                                .approverPrincipalId("principal-planner-01").build())
                        .build());

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        assertThat(result.getAggregateVersion()).isEqualTo(2L);
        verify(outbox).append(argThat(event ->
                event.getEventType().equals("supply_planning.plan.approved")));
    }

    @Test
    void emitsTraceableIssueEventForInventoryHealthScan() {
        when(mapper.countActiveInventoryIssues(17L, "balance-01", "STOCKOUT")).thenReturn(0L);
        when(mapper.insertInventoryHealthScan(any())).thenReturn(1);

        SupplyPlanningResult result = service.execute(
                base(SupplyPlanningOperation.RUN_INVENTORY_HEALTH_SCAN)
                        .inventoryHealthScan(
                                SupplyPlanningCommand.InventoryHealthScanDefinition.builder()
                                        .scanId("scan-01").policyCode("INVENTORY_HEALTH_V1")
                                        .policySha256("b".repeat(64)).agedThresholdDays(90)
                                        .observations(List.of(
                                                SupplyPlanningCommand
                                                        .InventoryHealthObservationDefinition
                                                        .builder()
                                                        .sourceBalanceId("balance-01")
                                                        .availableQuantity(BigDecimal.ZERO)
                                                        .reorderPointQuantity(BigDecimal.TEN)
                                                        .maximumStockQuantity(new BigDecimal("100"))
                                                        .ageDays(12)
                                                        .pendingQualityInspection(false)
                                                        .build()))
                                        .build())
                        .build());

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        verify(mapper).insertInventoryIssue(argThat(row ->
                row.getScanId().equals("scan-01")
                        && row.getDetectionSource().equals("RULE_SCAN")
                        && row.getIssueType().equals("STOCKOUT")));
        verify(outbox).append(argThat(event ->
                event.getEventType().equals("supply_planning.inventory_issue.opened")
                        && event.getAggregateType().equals("inventory_health_issue")));
        verify(outbox).append(argThat(event ->
                event.getEventType().equals("supply_planning.inventory_health.scanned")
                        && event.getAggregateId().equals("scan-01")));
    }

    private static SupplyPlanningCommand forecastCommand(LocalDate bucketStart) {
        SupplyPlanningCommand.ForecastPointDefinition point =
                SupplyPlanningCommand.ForecastPointDefinition.builder()
                        .canonicalSkuId("sku-01").warehouseId("warehouse-01")
                        .bucketStart(bucketStart).forecastQuantity(new BigDecimal("12.5"))
                        .lowerQuantity(BigDecimal.TEN).upperQuantity(new BigDecimal("15"))
                        .uomCode("EA").build();
        return base(SupplyPlanningOperation.CREATE_FORECAST)
                .forecast(SupplyPlanningCommand.ForecastDefinition.builder()
                        .forecastCode("FC-2026-08")
                        .horizonStart(LocalDate.of(2026, 8, 1))
                        .horizonEnd(LocalDate.of(2026, 8, 31))
                        .bucketType("DAY").modelRef("forecast-model:v1")
                        .baselineSha256("a".repeat(64)).points(List.of(point)).build())
                .build();
    }

    private static SupplyPlanningCommand.SupplyPlanningCommandBuilder base(
            SupplyPlanningOperation operation) {
        return SupplyPlanningCommand.builder().operation(operation)
                .idempotencyKey("supply-idempotency-" + operation)
                .runId("run-001").correlationId("11111111-1111-4111-8111-111111111111")
                .occurredAt(Instant.parse("2026-07-25T00:00:00Z"));
    }
}
