package cn.iocoder.yudao.module.cloudmold.supplyplanning.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.procurement.api.PurchaseRequisitionCommandApi;
import cn.iocoder.yudao.module.cloudmold.procurement.api.PurchaseRequisitionResult;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.*;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.dataobject.SupplyPlanningRecords.*;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.mysql.SupplyPlanningMapper;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.service.actor.SupplyPlanningActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockTransferCommandApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockTransferResult;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupplyPlanningServiceImplTest {
    private static final String ACTOR = "principal-planner-01";
    private final SupplyPlanningMapper mapper = mock(SupplyPlanningMapper.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final SupplyPlanningActorPrincipalPort actorPrincipalPort =
            mock(SupplyPlanningActorPrincipalPort.class);
    private final PurchaseRequisitionCommandApi purchaseRequisitionCommandApi =
            mock(PurchaseRequisitionCommandApi.class);
    private final StockTransferCommandApi stockTransferCommandApi =
            mock(StockTransferCommandApi.class);
    private final SupplyPlanningServiceImpl service = new SupplyPlanningServiceImpl(
            mapper, outbox, actorPrincipalPort, purchaseRequisitionCommandApi, stockTransferCommandApi);
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
        SupplyPlanningResult result = execute(forecastCommand(LocalDate.of(2026, 8, 1)));

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
        verify(actorPrincipalPort).requireActive(ACTOR);
    }

    @Test
    void rejectsForecastPointOutsideSnapshotHorizonBeforeMutation() {
        assertThatThrownBy(() -> execute(forecastCommand(LocalDate.of(2026, 9, 1))))
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

        assertThatThrownBy(() -> execute(command))
                .hasMessage("an active inventory-health issue already exists for this source and type");
        verify(mapper, never()).insertInventoryIssue(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void rejectsMissingCorrelationIdAtCommandBoundary() {
        SupplyPlanningCommand command = base(SupplyPlanningOperation.CREATE_FORECAST)
                .correlationId(null).build();

        assertThatThrownBy(() -> execute(command))
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

        SupplyPlanningResult result = execute(base(SupplyPlanningOperation.RELEASE_SUPPLY_PLAN)
                .supplyPlan(SupplyPlanningCommand.SupplyPlanDefinition.builder()
                        .planId("plan-01").expectedVersion(2L)
                        .releasePrincipalId("principal-planner-01").build())
                .build());

        assertThat(result.getStatus()).isEqualTo("RELEASED");
        assertThat(result.getAggregateVersion()).isEqualTo(3L);
        assertThat(result.getBusinessObjectType()).isEqualTo("replenishment_recommendation");
        assertThat(result.getBusinessObjectId()).isNotBlank();
        assertThat(result.getBusinessStatus()).isEqualTo("PROPOSED");
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
    void convertsApprovedReplenishmentToCanonicalPurchaseRequisitionWithoutCallingYudao() {
        when(mapper.selectReplenishmentForUpdate(17L, "recommendation-01")).thenReturn(
                new Replenishment().setRecommendationId("recommendation-01").setTenantId(17L)
                        .setPlanId("plan-01").setCanonicalSkuId("sku-01")
                        .setWarehouseId("warehouse-01")
                        .setSuggestedQuantity(new BigDecimal("30")).setUomCode("EA")
                        .setNeedByDate(LocalDate.of(2026, 8, 10))
                        .setStatus("APPROVED").setVersion(2L));
        when(mapper.selectReadyReplenishmentExecutionProposalForUpdate(
                17L, "recommendation-01")).thenReturn(
                new ReplenishmentExecutionProposal()
                        .setProposalId("proposal-01").setTenantId(17L)
                        .setRecommendationId("recommendation-01")
                        .setExpectedRecommendationVersion(2L)
                        .setTargetType("PURCHASE_REQUEST")
                        .setProposedByPrincipalId(ACTOR)
                        .setPolicyCode("REPLENISHMENT_EXECUTION_V1")
                        .setPolicySha256("d".repeat(64))
                        .setStatus("READY").setVersion(1L));
        when(purchaseRequisitionCommandApi.createApproved(any(), eq(ACTOR))).thenReturn(
                PurchaseRequisitionResult.builder()
                        .aggregateType("purchase_requisition")
                        .requisitionId("purchase-requisition:conversion-01")
                        .aggregateVersion(1L)
                        .status("APPROVED")
                        .requisitionCode("PR-CM-CONVERSION01")
                        .build());
        when(mapper.insertReplenishmentConversion(any())).thenReturn(1);
        when(mapper.convertReplenishment(eq(17L), eq("recommendation-01"), eq(2L), any()))
                .thenReturn(1);
        when(mapper.consumeReplenishmentExecutionProposal(
                eq(17L), eq("proposal-01"), eq(1L), eq("conversion-01"), any()))
                .thenReturn(1);

        SupplyPlanningResult result = execute(base(SupplyPlanningOperation.CONVERT_REPLENISHMENT)
                .replenishmentConversion(
                        SupplyPlanningCommand.ReplenishmentConversionDefinition.builder()
                                .conversionId("conversion-01")
                                .recommendationId("recommendation-01").expectedVersion(2L)
                                .targetType("PURCHASE_REQUEST")
                                .convertedByPrincipalId(ACTOR).build())
                .build());

        assertThat(result.getStatus()).isEqualTo("CONVERTED");
        assertThat(result.getBusinessObjectType()).isEqualTo("purchase_requisition");
        assertThat(result.getBusinessObjectId()).isEqualTo("purchase-requisition:conversion-01");
        assertThat(result.getBusinessObjectNo()).isEqualTo("PR-CM-CONVERSION01");
        assertThat(result.getBusinessStatus()).isEqualTo("APPROVED");
        assertThat(result.getNextWaitingEventCode()).isEqualTo("PROCUREMENT_SOURCING");
        assertThat(result.getNextWaitingEventLabel()).isEqualTo("等待采购寻源与定标");
        verifyNoInteractions(stockTransferCommandApi);
        verify(purchaseRequisitionCommandApi).createApproved(argThat(command ->
                command.getSourceBusinessRef().equals("recommendation-01")
                        && command.getLines().get(0).getCanonicalSkuId().equals("sku-01")
                        && command.getLines().get(0).getRequestedQuantity().compareTo(new BigDecimal("30")) == 0
                        && command.getLines().get(0).getSchedules().get(0).getCanonicalWarehouseId()
                        .equals("warehouse-01")),
                eq(ACTOR));
        verify(mapper).insertReplenishmentConversion(argThat(row ->
                row.getTargetAggregateType().equals("purchase_requisition")
                        && row.getTargetAggregateId().equals("purchase-requisition:conversion-01")
                        && row.getTargetAggregateNo().equals("PR-CM-CONVERSION01")
                        && row.getTargetAggregateStatus().equals("APPROVED")
                        && row.getStatus().equals("CREATED")));
        verify(mapper).consumeReplenishmentExecutionProposal(
                eq(17L), eq("proposal-01"), eq(1L), eq("conversion-01"), any());
        verify(outbox).append(argThat(event ->
                event.getEventType().equals("supply_planning.replenishment.converted")
                        && "proposal-01".equals(
                        event.getPayload().get("proposal_id"))));
    }

    @Test
    void persistsGovernedExecutionProposalWithoutCreatingDownstreamDraft() {
        when(mapper.selectReplenishmentForUpdate(17L, "recommendation-01")).thenReturn(
                new Replenishment().setRecommendationId("recommendation-01").setTenantId(17L)
                        .setPlanId("plan-01").setCanonicalSkuId("sku-01")
                        .setWarehouseId("warehouse-01")
                        .setSuggestedQuantity(new BigDecimal("30")).setUomCode("EA")
                        .setNeedByDate(LocalDate.of(2026, 8, 10))
                        .setStatus("APPROVED").setVersion(2L));
        when(mapper.insertReplenishmentExecutionProposal(any())).thenReturn(1);

        SupplyPlanningResult result = execute(
                base(SupplyPlanningOperation.PROPOSE_REPLENISHMENT_EXECUTION)
                        .replenishmentExecutionProposal(
                                SupplyPlanningCommand.ReplenishmentExecutionProposalDefinition.builder()
                                        .proposalId("proposal-01")
                                        .recommendationId("recommendation-01")
                                        .expectedRecommendationVersion(2L)
                                        .targetType("PURCHASE_REQUEST")
                                        .proposedByPrincipalId("spoofed-principal")
                                        .policyCode("REPLENISHMENT_EXECUTION_V1")
                                        .policySha256("d".repeat(64)).build())
                        .build());

        assertThat(result.getAggregateType()).isEqualTo("replenishment_execution_proposal");
        assertThat(result.getAggregateId()).isEqualTo("proposal-01");
        assertThat(result.getStatus()).isEqualTo("READY");
        verify(mapper).insertReplenishmentExecutionProposal(argThat(proposal ->
                proposal.getTenantId().equals(17L)
                        && proposal.getRecommendationId().equals("recommendation-01")
                        && proposal.getExpectedRecommendationVersion().equals(2L)
                        && proposal.getTargetType().equals("PURCHASE_REQUEST")
                        && proposal.getProposedByPrincipalId().equals(ACTOR)
                        && proposal.getStatus().equals("READY")
                        && proposal.getVersion().equals(1L)));
        verify(outbox).append(argThat(event ->
                event.getEventType()
                        .equals("supply_planning.replenishment_execution.proposed")
                        && event.getAggregateId().equals("proposal-01")
                        && Boolean.FALSE.equals(
                        event.getPayload().get("execution_authorized"))));
        verifyNoInteractions(purchaseRequisitionCommandApi);
        verifyNoInteractions(stockTransferCommandApi);
    }

    @Test
    void persistsCanonicalStockTransferProposalWithoutCreatingDownstreamDocument() {
        when(mapper.selectReplenishmentForUpdate(17L, "recommendation-01")).thenReturn(
                new Replenishment().setRecommendationId("recommendation-01").setTenantId(17L)
                        .setPlanId("plan-01").setCanonicalSkuId("sku-01")
                        .setWarehouseId("warehouse-target")
                        .setSuggestedQuantity(new BigDecimal("30")).setUomCode("EA")
                        .setNeedByDate(LocalDate.of(2026, 8, 10))
                        .setStatus("APPROVED").setVersion(2L));
        when(mapper.insertReplenishmentExecutionProposal(any())).thenReturn(1);

        SupplyPlanningCommand command =
                base(SupplyPlanningOperation.PROPOSE_REPLENISHMENT_EXECUTION)
                        .replenishmentExecutionProposal(
                                SupplyPlanningCommand.ReplenishmentExecutionProposalDefinition.builder()
                                        .proposalId("proposal-02")
                                        .recommendationId("recommendation-01")
                                        .expectedRecommendationVersion(2L)
                                        .targetType("TRANSFER_REQUEST")
                                        .ownerType("MERCHANT").ownerId("merchant-01")
                                        .sourceWarehouseId("warehouse-source")
                                        .targetWarehouseId("warehouse-target")
                                        .policyCode("REPLENISHMENT_EXECUTION_V1")
                                        .policySha256("d".repeat(64)).build())
                        .build();

        SupplyPlanningResult result = execute(command);

        assertThat(result.getAggregateType()).isEqualTo("replenishment_execution_proposal");
        assertThat(result.getStatus()).isEqualTo("READY");
        verify(mapper).insertReplenishmentExecutionProposal(argThat(proposal ->
                "MERCHANT".equals(proposal.getOwnerType())
                        && "merchant-01".equals(proposal.getOwnerId())
                        && "warehouse-source".equals(proposal.getSourceWarehouseId())
                        && "warehouse-target".equals(proposal.getTargetWarehouseId())));
        verifyNoInteractions(purchaseRequisitionCommandApi, stockTransferCommandApi);
    }

    @Test
    void rejectsStockTransferDimensionsOnPurchaseRequisitionConversion() {
        SupplyPlanningCommand command =
                base(SupplyPlanningOperation.CONVERT_REPLENISHMENT)
                        .replenishmentConversion(
                                SupplyPlanningCommand.ReplenishmentConversionDefinition.builder()
                                        .conversionId("conversion-drift")
                                        .recommendationId("recommendation-01").expectedVersion(2L)
                                        .targetType("PURCHASE_REQUEST")
                                        .ownerType("MERCHANT").ownerId("merchant-01")
                                        .sourceWarehouseId("warehouse-source")
                                        .targetWarehouseId("warehouse-target").build())
                        .build();

        assertThatThrownBy(() -> execute(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("purchase requisition must not contain stock-transfer dimensions");
        verifyNoInteractions(purchaseRequisitionCommandApi, stockTransferCommandApi);
        verify(mapper, never()).selectReplenishmentForUpdate(anyLong(), anyString());
        verify(mapper, never()).insertReplenishmentConversion(any());
        verify(mapper, never()).consumeReplenishmentExecutionProposal(
                anyLong(), anyString(), anyLong(), anyString(), any());
    }

    @Test
    void convertsApprovedReplenishmentToCanonicalStockTransfer() {
        when(mapper.selectReplenishmentForUpdate(17L, "recommendation-transfer")).thenReturn(
                new Replenishment().setRecommendationId("recommendation-transfer").setTenantId(17L)
                        .setPlanId("plan-transfer").setCanonicalSkuId("sku-01")
                        .setWarehouseId("warehouse-target")
                        .setSuggestedQuantity(new BigDecimal("12.5")).setUomCode("EA")
                        .setNeedByDate(LocalDate.of(2026, 8, 10))
                        .setStatus("APPROVED").setVersion(2L));
        when(mapper.selectReadyReplenishmentExecutionProposalForUpdate(
                17L, "recommendation-transfer")).thenReturn(
                new ReplenishmentExecutionProposal()
                        .setProposalId("proposal-transfer").setTenantId(17L)
                        .setRecommendationId("recommendation-transfer")
                        .setExpectedRecommendationVersion(2L)
                        .setTargetType("TRANSFER_REQUEST")
                        .setOwnerType("MERCHANT").setOwnerId("merchant-01")
                        .setSourceWarehouseId("warehouse-source")
                        .setTargetWarehouseId("warehouse-target")
                        .setStatus("READY").setVersion(1L));
        when(stockTransferCommandApi.execute(any())).thenReturn(StockTransferResult.builder()
                .requestId("transfer-request-01").requestCode("STR-01").requestStatus("APPROVED")
                .orderId("transfer-order-01").orderCode("STO-01").orderStatus("PREPARE")
                .currentStageCode("TRANSFER_OUTBOUND").currentStageLabel("等待调拨出库")
                .aggregateVersion(1L).build());
        when(mapper.insertReplenishmentConversion(any())).thenReturn(1);
        when(mapper.convertReplenishment(eq(17L), eq("recommendation-transfer"), eq(2L), any()))
                .thenReturn(1);
        when(mapper.consumeReplenishmentExecutionProposal(
                eq(17L), eq("proposal-transfer"), eq(1L), eq("conversion-transfer"), any()))
                .thenReturn(1);

        SupplyPlanningResult result = execute(base(SupplyPlanningOperation.CONVERT_REPLENISHMENT)
                .replenishmentConversion(SupplyPlanningCommand.ReplenishmentConversionDefinition.builder()
                        .conversionId("conversion-transfer")
                        .recommendationId("recommendation-transfer").expectedVersion(2L)
                        .targetType("TRANSFER_REQUEST")
                        .ownerType("MERCHANT").ownerId("merchant-01")
                        .sourceWarehouseId("warehouse-source")
                        .targetWarehouseId("warehouse-target")
                        .convertedByPrincipalId(ACTOR).build())
                .build());

        assertThat(result.getBusinessObjectType()).isEqualTo("STOCK_TRANSFER_ORDER");
        assertThat(result.getBusinessObjectId()).isEqualTo("transfer-order-01");
        assertThat(result.getBusinessStatus()).isEqualTo("PREPARE");
        assertThat(result.getNextWaitingEventCode()).isEqualTo("TRANSFER_OUTBOUND");
        verifyNoInteractions(purchaseRequisitionCommandApi);
        verify(stockTransferCommandApi).execute(argThat(command ->
                "REPLENISHMENT".equals(command.getSourceBusinessType())
                        && "recommendation-transfer".equals(command.getSourceBusinessRef())
                        && "MERCHANT".equals(command.getOwnerType())
                        && "warehouse-source".equals(command.getSourceWarehouseId())
                        && "warehouse-target".equals(command.getTargetWarehouseId())
                        && "sku-01".equals(command.getLines().get(0).getCanonicalSkuId())
                        && command.getLines().get(0).getRequestedQuantity()
                        .compareTo(new BigDecimal("12.5")) == 0));
        verify(mapper).insertReplenishmentConversion(argThat(row ->
                "STOCK_TRANSFER_ORDER".equals(row.getTargetAggregateType())
                        && "transfer-order-01".equals(row.getTargetAggregateId())
                        && "STO-01".equals(row.getTargetAggregateNo())
                        && "PREPARE".equals(row.getTargetAggregateStatus())));
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

        assertThatThrownBy(() -> execute(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("supply plan requires exactly one selected scenario before approval");
        assertThatThrownBy(() -> execute(command))
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

        SupplyPlanningResult result = execute(
                base(SupplyPlanningOperation.APPROVE_SUPPLY_PLAN)
                        .supplyPlan(SupplyPlanningCommand.SupplyPlanDefinition.builder()
                        .planId("plan-01").expectedVersion(1L)
                        .approverPrincipalId("spoofed-principal").build())
                .build());

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        assertThat(result.getAggregateVersion()).isEqualTo(2L);
        verify(outbox).append(argThat(event ->
                event.getEventType().equals("supply_planning.plan.approved")));
    }

    @Test
    void persistsGovernedRobustRecommendationWithoutSelectingOrExecutingIt() {
        when(mapper.selectSupplyPlanForUpdate(17L, "plan-01")).thenReturn(new SupplyPlan()
                .setPlanId("plan-01").setTenantId(17L).setPlanCode("PLAN-01")
                .setBudgetAmountMinor(20_000L).setStatus("DRAFT").setVersion(1L));
        PlanScenario fragile = scenario("scenario-fragile", "FRAGILE",
                "20", "10", "100", "100", "a".repeat(64));
        PlanScenario robust = scenario("scenario-robust", "ROBUST",
                "60", "20", "100", "100", "b".repeat(64));
        when(mapper.selectPlanScenariosForRecommendation(
                eq(17L), eq("plan-01"), anyList()))
                .thenReturn(List.of(fragile, robust));
        when(mapper.insertPlanScenarioRecommendation(any())).thenReturn(1);

        SupplyPlanningResult result = execute(
                base(SupplyPlanningOperation.RECOMMEND_PLAN_SCENARIO)
                        .scenarioRecommendation(
                                SupplyPlanningCommand.ScenarioRecommendationDefinition.builder()
                                        .recommendationId("robust-recommendation-01")
                                        .planId("plan-01").expectedPlanVersion(1L)
                                        .candidateScenarioIds(List.of(
                                                "scenario-robust", "scenario-fragile"))
                                        .targetServiceLevelFloorBasisPoints(9_000)
                                        .maxProjectedCostMinor(10_000L)
                                        .demandStressBasisPoints(12_000)
                                        .supplyAvailabilityBasisPoints(8_000)
                                        .policySha256("c".repeat(64)).build())
                        .build());

        assertThat(result.getAggregateType())
                .isEqualTo("supply_plan_scenario_recommendation");
        assertThat(result.getStatus()).isEqualTo("PROPOSED");
        assertThat(result.getBusinessObjectType()).isEqualTo("supply_plan_scenario");
        assertThat(result.getBusinessObjectId()).isEqualTo("scenario-robust");
        assertThat(result.getBusinessStatus()).isEqualTo("RECOMMENDED");
        verify(mapper).insertPlanScenarioRecommendation(argThat(row ->
                row.getRecommendedScenarioId().equals("scenario-robust")
                        && row.getSolverType().equals("ROBUST_LEXICOGRAPHIC_V1")
                        && row.getCandidateSetSha256().matches("[0-9a-f]{64}")
                        && row.getStatus().equals("PROPOSED")
                        && row.getVersion().equals(1L)));
        verify(mapper, never()).selectPlanScenario(
                anyLong(), anyString(), anyLong(), anyString(), any());
        verifyNoInteractions(purchaseRequisitionCommandApi, stockTransferCommandApi);
        verify(outbox).append(argThat(event ->
                event.getEventType().equals("supply_planning.plan_scenario.recommended")
                        && event.getAggregateId().equals("robust-recommendation-01")));
    }

    @Test
    void rejectsCommandsWithoutAnAttestedActorEnvelope() {
        assertThatThrownBy(() -> service.execute(
                forecastCommand(LocalDate.of(2026, 8, 1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("attested actor Principal is required");
        verifyNoInteractions(actorPrincipalPort);
        verify(mapper, never()).insertOrResolveOperation(anyLong(), anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    void emitsTraceableIssueEventForInventoryHealthScan() {
        when(mapper.countActiveInventoryIssues(17L, "balance-01", "STOCKOUT")).thenReturn(0L);
        when(mapper.insertInventoryHealthScan(any())).thenReturn(1);

        SupplyPlanningResult result = execute(
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

    private SupplyPlanningResult execute(SupplyPlanningCommand command) {
        return service.execute(command, ACTOR);
    }

    private static PlanScenario scenario(String scenarioId, String scenarioCode,
                                         String onHand, String inbound, String capacity,
                                         String unitCost, String parametersSha256) {
        return new PlanScenario().setScenarioId(scenarioId).setTenantId(17L)
                .setPlanId("plan-01").setScenarioCode(scenarioCode)
                .setCanonicalSkuId("sku-01").setWarehouseId("warehouse-01")
                .setForecastQuantity(new BigDecimal("100"))
                .setSafetyStockQuantity(new BigDecimal("10"))
                .setOnHandQuantity(new BigDecimal(onHand))
                .setInboundQuantity(new BigDecimal(inbound))
                .setCapacityQuantity(new BigDecimal(capacity))
                .setMinimumOrderQuantity(new BigDecimal("10"))
                .setUnitCostMinor(Long.parseLong(unitCost)).setUomCode("EA")
                .setParametersSha256(parametersSha256)
                .setStatus("EVALUATED").setVersion(1L);
    }

    private static SupplyPlanningCommand.SupplyPlanningCommandBuilder base(
            SupplyPlanningOperation operation) {
        return SupplyPlanningCommand.builder().operation(operation)
                .idempotencyKey("supply-idempotency-" + operation)
                .runId("run-001").correlationId("11111111-1111-4111-8111-111111111111")
                .occurredAt(Instant.parse("2026-07-25T00:00:00Z"));
    }
}
