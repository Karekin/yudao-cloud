package cn.iocoder.yudao.module.cloudmold.supplyplanning.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementCommandApi;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementResult;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.*;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.dataobject.SupplyPlanningRecords.*;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.mysql.SupplyPlanningMapper;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.service.actor.SupplyPlanningActorPrincipalPort;
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
    private final ReplenishmentExecutionPort replenishmentExecutionPort =
            mock(ReplenishmentExecutionPort.class);
    private final ProcurementCommandApi procurementCommandApi =
            mock(ProcurementCommandApi.class);
    private final SupplyPlanningServiceImpl service = new SupplyPlanningServiceImpl(
            mapper, outbox, actorPrincipalPort, replenishmentExecutionPort, procurementCommandApi);
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
    void convertsApprovedReplenishmentOnlyAfterCreatingRealPrepareDraft() {
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
                        .setMappingEvidenceSha256("c".repeat(64))
                        .setSupplierId(11L).setAccountId(12L).setErpProductId(13L)
                        .setErpProductUnitId(14L).setUnitCostMinor(1299L)
                        .setTaxPercent(new BigDecimal("13"))
                        .setProposedByPrincipalId(ACTOR)
                        .setPolicyCode("REPLENISHMENT_EXECUTION_V1")
                        .setPolicySha256("d".repeat(64))
                        .setStatus("READY").setVersion(1L));
        when(replenishmentExecutionPort.createDraft(any())).thenReturn(
                new ReplenishmentExecutionPort.ExecutionResult(
                        "YUDAO_ERP", "PURCHASE_ORDER", "781", "PO-20260725-01", "PREPARE",
                        "SUPPLIER_CONFIRMATION", "等待供应商确认采购单"));
        when(procurementCommandApi.execute(any(), eq(ACTOR))).thenReturn(
                ProcurementResult.builder()
                        .aggregateType("procurement_order")
                        .aggregateId("procurement-order:conversion-01")
                        .aggregateVersion(1L)
                        .status("CREATED")
                        .orderCode("PO-CM-CONVERSION01")
                        .projectionSourceSystem("YUDAO_ERP")
                        .projectionDocumentType("PURCHASE_ORDER")
                        .projectionExternalDocumentId("781")
                        .projectionExternalDocumentNo("PO-20260725-01")
                        .projectionDocumentStatus("PREPARE")
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
                                .mappingEvidenceSha256("c".repeat(64))
                                .supplierId(11L).accountId(12L).erpProductId(13L)
                                .erpProductUnitId(14L).unitCostMinor(1299L)
                                .taxPercent(new BigDecimal("13"))
                                .convertedByPrincipalId(ACTOR).build())
                .build());

        assertThat(result.getStatus()).isEqualTo("CONVERTED");
        assertThat(result.getBusinessObjectType()).isEqualTo("procurement_order");
        assertThat(result.getBusinessObjectId()).isEqualTo("procurement-order:conversion-01");
        assertThat(result.getBusinessObjectNo()).isEqualTo("PO-CM-CONVERSION01");
        assertThat(result.getBusinessStatus()).isEqualTo("CREATED");
        assertThat(result.getProjectionSourceSystem()).isEqualTo("YUDAO_ERP");
        assertThat(result.getProjectionDocumentType()).isEqualTo("PURCHASE_ORDER");
        assertThat(result.getProjectionExternalDocumentId()).isEqualTo("781");
        assertThat(result.getProjectionExternalDocumentNo()).isEqualTo("PO-20260725-01");
        assertThat(result.getProjectionDocumentStatus()).isEqualTo("PREPARE");
        assertThat(result.getNextWaitingEventCode()).isEqualTo("SUPPLIER_CONFIRMATION");
        assertThat(result.getNextWaitingEventLabel()).isEqualTo("等待供应商确认采购单");
        verify(replenishmentExecutionPort).createDraft(argThat(command ->
                command.targetType().equals("PURCHASE_REQUEST")
                        && command.quantity().compareTo(new BigDecimal("30")) == 0
                        && command.mappingEvidenceSha256().equals("c".repeat(64))));
        verify(procurementCommandApi).execute(argThat(command ->
                command.getOperation() == cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementOperation.CREATE_PURCHASE_ORDER
                        && command.getPurchaseOrder().getSourceBusinessRef().equals("recommendation-01")
                        && command.getPurchaseOrder().getProjection().getExternalDocumentId().equals("781")),
                eq(ACTOR));
        verify(mapper).insertReplenishmentConversion(argThat(row ->
                row.getTargetReference().equals("YUDAO_ERP:PURCHASE_ORDER:781")
                        && row.getSourceSystem().equals("YUDAO_ERP")
                        && row.getDocumentType().equals("PURCHASE_ORDER")
                        && row.getExternalDocumentId().equals("781")
                        && row.getExternalDocumentNo().equals("PO-20260725-01")
                        && row.getDocumentStatus().equals("PREPARE")
                        && row.getNextWaitingEventCode().equals("SUPPLIER_CONFIRMATION")
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
                                        .mappingEvidenceSha256("c".repeat(64))
                                        .supplierId(11L).accountId(12L).erpProductId(13L)
                                        .erpProductUnitId(14L).unitCostMinor(1299L)
                                        .taxPercent(new BigDecimal("13"))
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
        verifyNoInteractions(replenishmentExecutionPort);
        verifyNoInteractions(procurementCommandApi);
    }

    @Test
    void rejectsTransferProposalContainingPurchaseOnlyReferences() {
        SupplyPlanningCommand command =
                base(SupplyPlanningOperation.PROPOSE_REPLENISHMENT_EXECUTION)
                        .replenishmentExecutionProposal(
                                SupplyPlanningCommand.ReplenishmentExecutionProposalDefinition.builder()
                                        .proposalId("proposal-02")
                                        .recommendationId("recommendation-01")
                                        .expectedRecommendationVersion(2L)
                                        .targetType("TRANSFER_REQUEST")
                                        .mappingEvidenceSha256("c".repeat(64))
                                        .supplierId(11L).unitCostMinor(100L)
                                        .sourceWarehouseId(31L).targetWarehouseId(32L)
                                        .wmsSkuId(41L)
                                        .policyCode("REPLENISHMENT_EXECUTION_V1")
                                        .policySha256("d".repeat(64)).build())
                        .build();

        assertThatThrownBy(() -> execute(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("transfer execution must not contain purchase-only references");
        verify(mapper, never()).selectReplenishmentForUpdate(anyLong(), anyString());
        verify(mapper, never()).insertReplenishmentExecutionProposal(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void rejectsConversionThatDriftsFromReadyProposal() {
        when(mapper.selectReplenishmentForUpdate(17L, "recommendation-01")).thenReturn(
                new Replenishment().setRecommendationId("recommendation-01").setTenantId(17L)
                        .setStatus("APPROVED").setVersion(2L));
        when(mapper.selectReadyReplenishmentExecutionProposalForUpdate(
                17L, "recommendation-01")).thenReturn(
                new ReplenishmentExecutionProposal()
                        .setProposalId("proposal-01").setRecommendationId("recommendation-01")
                        .setExpectedRecommendationVersion(2L)
                        .setTargetType("PURCHASE_REQUEST")
                        .setMappingEvidenceSha256("c".repeat(64))
                        .setSupplierId(11L).setAccountId(12L).setErpProductId(13L)
                        .setErpProductUnitId(14L).setUnitCostMinor(1299L)
                        .setTaxPercent(new BigDecimal("13")).setStatus("READY").setVersion(1L));

        SupplyPlanningCommand command =
                base(SupplyPlanningOperation.CONVERT_REPLENISHMENT)
                        .replenishmentConversion(
                                SupplyPlanningCommand.ReplenishmentConversionDefinition.builder()
                                        .conversionId("conversion-drift")
                                        .recommendationId("recommendation-01").expectedVersion(2L)
                                        .targetType("PURCHASE_REQUEST")
                                        .mappingEvidenceSha256("c".repeat(64))
                                        .supplierId(11L).accountId(12L).erpProductId(13L)
                                        .erpProductUnitId(14L).unitCostMinor(1300L)
                                        .taxPercent(new BigDecimal("13")).build())
                        .build();

        assertThatThrownBy(() -> execute(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("conversion fields do not match the ready execution proposal");
        verifyNoInteractions(replenishmentExecutionPort);
        verify(mapper, never()).insertReplenishmentConversion(any());
        verify(mapper, never()).consumeReplenishmentExecutionProposal(
                anyLong(), anyString(), anyLong(), anyString(), any());
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
        verify(mapper).insertPlanScenarioRecommendation(argThat(row ->
                row.getRecommendedScenarioId().equals("scenario-robust")
                        && row.getSolverType().equals("ROBUST_LEXICOGRAPHIC_V1")
                        && row.getCandidateSetSha256().matches("[0-9a-f]{64}")
                        && row.getStatus().equals("PROPOSED")
                        && row.getVersion().equals(1L)));
        verify(mapper, never()).selectPlanScenario(
                anyLong(), anyString(), anyLong(), anyString(), any());
        verifyNoInteractions(replenishmentExecutionPort);
        verifyNoInteractions(procurementCommandApi);
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
