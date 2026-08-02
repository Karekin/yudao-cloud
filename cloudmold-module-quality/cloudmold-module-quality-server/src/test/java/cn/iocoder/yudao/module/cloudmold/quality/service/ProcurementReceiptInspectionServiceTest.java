package cn.iocoder.yudao.module.cloudmold.quality.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.P2pEvidenceCommands;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.P2pEvidenceIngestionApi;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.ProcureToPayResult;
import cn.iocoder.yudao.module.cloudmold.inventory.api.*;
import cn.iocoder.yudao.module.cloudmold.quality.api.*;
import cn.iocoder.yudao.module.cloudmold.quality.dal.dataobject.ProcurementReceiptInspectionRecords.*;
import cn.iocoder.yudao.module.cloudmold.quality.dal.dataobject.QualityRecords.StandardVersion;
import cn.iocoder.yudao.module.cloudmold.quality.dal.mysql.ProcurementReceiptInspectionMapper;
import cn.iocoder.yudao.module.cloudmold.quality.service.actor.QualityActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.*;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcurementReceiptInspectionServiceTest {
    private static final String RECEIPT = "40000000-0000-0000-0000-000000000001";
    private static final String PO = "50000000-0000-0000-0000-000000000001";
    private static final String SUPPLIER = "60000000-0000-0000-0000-000000000001";
    private static final String OWNER = "70000000-0000-0000-0000-000000000001";
    private static final String RECEIPT_LINE = "80000000-0000-0000-0000-000000000001";
    private static final String ITEM = "90000000-0000-0000-0000-000000000001";
    private static final String SCHEDULE = "a0000000-0000-0000-0000-000000000001";
    private static final String SKU = "b0000000-0000-0000-0000-000000000001";
    private static final String WAREHOUSE = "c0000000-0000-0000-0000-000000000001";
    private static final String LOCATION_1 = "d0000000-0000-0000-0000-000000000001";
    private static final String LOCATION_2 = "d0000000-0000-0000-0000-000000000002";
    private static final String LOT = "e0000000-0000-0000-0000-000000000001";
    private static final String DECISION = "f0000000-0000-0000-0000-000000000001";
    private static final String FINANCE_RECEIPT_EVIDENCE = "11000000-0000-0000-0000-000000000001";
    private static final String FINANCE_QUALITY_EVIDENCE = "12000000-0000-0000-0000-000000000001";
    private static final String POLICY_HASH = "d".repeat(64);
    private final ProcurementReceiptInspectionMapper mapper = mock(ProcurementReceiptInspectionMapper.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final QualityActorPrincipalPort actorPort = mock(QualityActorPrincipalPort.class);
    private final InventoryProcurementReceiptApi inventoryApi = mock(InventoryProcurementReceiptApi.class);
    private final WarehouseProcurementQualityDecisionApi warehouseApi =
            mock(WarehouseProcurementQualityDecisionApi.class);
    private final P2pEvidenceIngestionApi financeApi = mock(P2pEvidenceIngestionApi.class);
    private final ProcurementReceiptInspectionService service =
            new ProcurementReceiptInspectionService(mapper, outbox, actorPort, inventoryApi, warehouseApi,
                    financeApi);
    private final AtomicReference<String> requestHash = new AtomicReference<>();
    private final AtomicReference<String> attemptToken = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(23L);
        when(mapper.insertOrResolveOperation(eq(23L), anyString(), anyString(), anyString(),
                anyString(), any())).thenAnswer(invocation -> {
                    requestHash.set(invocation.getArgument(3));
                    attemptToken.set(invocation.getArgument(4));
                    return 1;
                });
        when(mapper.selectLastInsertId()).thenReturn(701L);
        when(mapper.selectOperationForUpdate(23L, 701L)).thenAnswer(invocation -> new Operation()
                .setOperationId(701L).setTenantId(23L).setRequestHash(requestHash.get())
                .setAttemptToken(attemptToken.get()).setStatus(0));
        when(mapper.markOperationSucceeded(eq(23L), eq(701L), anyString(), anyLong(), anyString(),
                nullable(String.class), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(mapper.insertInspection(any())).thenReturn(1);
        when(mapper.insertLine(any())).thenReturn(1);
        when(mapper.insertSplit(any())).thenReturn(1);
        when(mapper.insertInspectionHistory(any())).thenReturn(1);
        when(mapper.insertLineHistory(any())).thenReturn(1);
        when(mapper.insertResultBatch(any())).thenReturn(1);
        when(mapper.insertResultSplit(any())).thenReturn(1);
        when(mapper.insertDefect(any())).thenReturn(1);
        when(inventoryApi.execute(any())).thenAnswer(invocation -> {
            InventoryProcurementReceiptCommand command = invocation.getArgument(0);
            long offset = switch (command.getDisposition()) {
                case ACCEPTED -> 1L;
                case REJECTED -> 2L;
                case QUARANTINED -> 3L;
                case PENDING -> throw new AssertionError("Quality must not create pending receipt effects");
            };
            return InventoryProcurementReceiptResult.builder()
                    .operationId(800L + offset).ledgerTransactionId(900L + offset)
                    .receiptId(command.getReceiptId()).receiptLineId(command.getReceiptLineId())
                    .targetAggregateVersion(40L + offset)
                    .unitCostAmountMinor(command.getUnitCostAmountMinor())
                    .movementCostAmountMinor(command.getMovementCostAmountMinor())
                    .currencyCode(command.getCurrencyCode()).valuationPolicyId(command.getValuationPolicy())
                    .valuationPolicyVersion(command.getValuationPolicyVersion())
                    .valuationPolicyHash(command.getValuationPolicyHash()).build();
        });
        when(warehouseApi.execute(any())).thenAnswer(invocation -> {
            WarehouseProcurementQualityDecisionCommand command = invocation.getArgument(0);
            long offset = switch (command.getDisposition()) {
                case ACCEPTED -> 1L;
                case REJECTED -> 2L;
                case QUARANTINED -> 3L;
            };
            return WarehouseProcurementQualityDecisionResult.builder()
                    .operationId(1000L + offset).receiptId(command.getReceiptId())
                    .receiptLineId(command.getReceiptLineId()).receiptVersion(10L + offset)
                    .receiptLineVersion(20L + offset).scheduleFulfillmentVersion(30L + offset)
                    .financeReceiptEvidenceOperationId(1050L)
                    .financeReceiptEvidenceId(FINANCE_RECEIPT_EVIDENCE)
                    .financeReceiptEvidenceVersion(1L)
                    .receiptStatus("PARTIALLY_QUALITY_DECIDED").receiptLineStatus("PARTIALLY_QUALITY_DECIDED")
                    .pendingQualityQuantity(BigDecimal.ZERO).acceptedQuantity(BigDecimal.ZERO)
                    .rejectedQuantity(BigDecimal.ZERO).quarantinedQuantity(BigDecimal.ZERO).build();
        });
        when(financeApi.ingestQualityDisposition(any(), anyString())).thenAnswer(invocation -> {
            P2pEvidenceCommands.QualityDisposition command = invocation.getArgument(0);
            return ProcureToPayResult.builder().operationId(1101L).duplicate(false)
                    .aggregateType("finance_quality_disposition_evidence")
                    .aggregateId(FINANCE_QUALITY_EVIDENCE).aggregateVersion(command.getSourceVersion())
                    .status("RECORDED").build();
        });
        when(financeApi.ingestInventoryMovement(any(), anyString())).thenAnswer(invocation -> {
            P2pEvidenceCommands.InventoryMovement command = invocation.getArgument(0);
            long offset = switch (command.getDisposition()) {
                case "ACCEPTED" -> 1L;
                case "REJECTED" -> 2L;
                case "QUARANTINED" -> 3L;
                default -> throw new AssertionError("unexpected Finance disposition");
            };
            return ProcureToPayResult.builder().operationId(1200L + offset).duplicate(false)
                    .aggregateType("finance_inventory_movement_evidence")
                    .aggregateId("13000000-0000-0000-0000-00000000000" + offset)
                    .aggregateVersion(command.getSourceVersion()).status("RECORDED").build();
        });
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createsStronglyTypedReceiptLinesAndPhysicalSplitsFromPublishedStandardSnapshot() {
        when(mapper.selectStandardVersion(23L, "standard-1", 3L, "standard-version-3"))
                .thenReturn(new StandardVersion().setStandardId("standard-1").setStandardVersion(3L)
                        .setStandardVersionId("standard-version-3").setContentSha256("a".repeat(64))
                        .setEffectiveAt(java.time.LocalDateTime.of(2026, 8, 1, 0, 0)));

        ProcurementReceiptInspectionResult result = execute(createCommand());

        assertThat(result.getStatus()).isEqualTo("OPEN");
        assertThat(result.getReceivedQuantity()).isEqualByComparingTo("12");
        verify(mapper).insertInspection(argThat(value -> value.getTenantId().equals(23L)
                && value.getReceiptId().equals(RECEIPT)
                && value.getPurchaseOrderId().equals(PO)
                && value.getSupplierId().equals(SUPPLIER) && value.getOwnerId().equals(OWNER)
                && value.getStandardContentSha256().equals("a".repeat(64))
                && value.getReceivedQuantity().compareTo(new BigDecimal("12")) == 0));
        verify(mapper).insertLine(argThat(value -> value.getReceiptLineId().equals(RECEIPT_LINE)
                && value.getItemId().equals(ITEM) && value.getScheduleId().equals(SCHEDULE)
                && value.getCanonicalSkuId().equals(SKU) && value.getUomCode().equals("EA")
                && value.getValuationPolicy().equals("MOVING_AVERAGE")
                && value.getUnitCostAmountMinor().equals(100L)));
        verify(mapper, times(2)).insertSplit(any());
        verify(outbox).append(argThat(event ->
                event.getEventType().equals("quality.procurement_receipt_inspection.created")
                        && event.getPayload().get("receipt_id").equals(RECEIPT)
                        && event.getPayload().get("purchase_order_id").equals(PO)));
        verify(actorPort).requireActive("principal-quality-1");
    }

    @Test
    void rejectsCreateWhenSplitQuantitiesDoNotConserveReceiptLineQuantity() {
        when(mapper.selectStandardVersion(anyLong(), anyString(), anyLong(), anyString()))
                .thenReturn(new StandardVersion().setContentSha256("a".repeat(64))
                        .setEffectiveAt(java.time.LocalDateTime.of(2026, 8, 1, 0, 0)));
        ProcurementReceiptInspectionCommand command = createCommand();
        command.getCreate().getLines().get(0).getSplits().get(1).setReceivedQuantity(new BigDecimal("4"));

        assertThatThrownBy(() -> execute(command))
                .hasMessage("split received quantity must equal line received quantity");
        verify(mapper, never()).insertInspection(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void recordsPartialResultWithDefectEvidenceAndKeepsLineOpen() {
        stubOpenAggregate();
        when(mapper.selectLineForUpdate(23L, "inspection-1", "inspection-line-1"))
                .thenReturn(openLine());
        when(mapper.selectSplitForUpdate(23L, "inspection-1", "inspection-line-1", "split-1"))
                .thenReturn(openSplit());
        when(mapper.applySplitResult(anyLong(), anyString(), anyLong(), any(), any(), any(), any(),
                anyString(), nullable(java.time.LocalDateTime.class), any())).thenReturn(1);
        when(mapper.selectLineTotals(23L, "inspection-line-1"))
                .thenReturn(totals("12", "2", "1", "1", "0", null, null));
        when(mapper.updateLineTotals(anyLong(), anyString(), anyLong(), any(), anyString(),
                nullable(java.time.LocalDateTime.class), any())).thenReturn(1);
        when(mapper.selectInspectionTotals(23L, "inspection-1"))
                .thenReturn(totals("12", "2", "1", "1", "0", 1L, 0L));
        when(mapper.updateInspectionTotals(anyLong(), anyString(), anyLong(), any(), anyString(),
                anyString(), any()))
                .thenReturn(1);

        ProcurementReceiptInspectionResult result = execute(resultCommand(
                new BigDecimal("2"), new BigDecimal("1"), new BigDecimal("1"), BigDecimal.ZERO));

        assertThat(result.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(result.getAggregateVersion()).isEqualTo(2L);
        verify(mapper).insertDefect(argThat(value -> value.getDefectCode().equals("SEAM_OPEN")
                && value.getSeverity().equals("MAJOR")
                && value.getAffectedQuantity().compareTo(BigDecimal.ONE) == 0));
        verify(mapper).updateLineTotals(eq(23L), eq("inspection-line-1"), eq(1L), any(),
                eq("PARTIALLY_INSPECTED"), isNull(), any());
        verify(outbox).append(argThat(event ->
                event.getEventType().equals("quality.procurement_receipt_inspection.results_recorded")));
        verify(inventoryApi).execute(argThat(command -> command.getOperation()
                == InventoryProcurementReceiptOperation.ACCEPT_QUALITY
                && command.getDisposition() == InventoryProcurementReceiptDisposition.ACCEPTED
                && command.getQuantity().compareTo(BigDecimal.ONE) == 0
                && command.getQualityDecisionId().equals(DECISION)
                && command.getDecisionVersion().equals(2L)
                && command.getReceiptId().equals(RECEIPT) && command.getReceiptLineId().equals(RECEIPT_LINE)
                && command.getPurchaseOrderId().equals(PO) && command.getPurchaseOrderItemId().equals(ITEM)
                && command.getPurchaseOrderScheduleId().equals(SCHEDULE)
                && command.getSupplierId().equals(SUPPLIER) && command.getOwnerId().equals(OWNER)
                && command.getCanonicalSkuId().equals(SKU) && command.getWarehouseId().equals(WAREHOUSE)
                && command.getLocationId().equals(LOCATION_1) && command.getLotId().equals(LOT)
                && command.getUnitCostAmountMinor().equals(100L)
                && command.getMovementCostAmountMinor().equals(100L)
                && command.getValuationPolicyHash().equals(POLICY_HASH)));
        verify(inventoryApi).execute(argThat(command -> command.getOperation()
                == InventoryProcurementReceiptOperation.REJECT_QUALITY
                && command.getDisposition() == InventoryProcurementReceiptDisposition.REJECTED));
        verify(warehouseApi).execute(argThat(command -> command.getDisposition()
                == WarehouseProcurementQualityDisposition.ACCEPTED
                && command.getQuantity().compareTo(BigDecimal.ONE) == 0
                && command.getQualityDecisionId().equals(DECISION)
                && command.getDecisionVersion().equals(2L)
                && command.getInspectionSplitId().equals("split-1")
                && command.getReceiptId().equals(RECEIPT) && command.getReceiptLineId().equals(RECEIPT_LINE)
                && command.getProcurementOrderId().equals(PO)
                && command.getProcurementOrderItemId().equals(ITEM)
                && command.getDeliveryScheduleId().equals(SCHEDULE)
                && command.getWarehouseId().equals(WAREHOUSE)
                && command.getLocationId().equals(LOCATION_1) && command.getLotId().equals(LOT)
                && command.getEvidenceRef().equals("sha256:" + "b".repeat(64))));
        verify(warehouseApi).execute(argThat(command -> command.getDisposition()
                == WarehouseProcurementQualityDisposition.REJECTED));
        verify(financeApi).ingestQualityDisposition(argThat(command ->
                        command.getQualityDispositionId().equals(DECISION)
                                && command.getSourceVersion().equals(2L)
                                && command.getEvidenceSha256().equals("b".repeat(64))
                                && command.getReceiptLineId().equals(RECEIPT_LINE)
                                && command.getReceiptLineVersion().equals(1L)
                                && command.getPurchaseOrderItemId().equals(ITEM)
                                && command.getInspectedQuantity().compareTo(new BigDecimal("2")) == 0
                                && command.getAcceptedQuantity().compareTo(BigDecimal.ONE) == 0
                                && command.getRejectedQuantity().compareTo(BigDecimal.ONE) == 0
                                && command.getHeldQuantity().compareTo(BigDecimal.ZERO) == 0),
                eq("principal-quality-1"));
        verify(financeApi).ingestInventoryMovement(argThat(command ->
                        command.getInventoryMovementId().equals("901")
                                && command.getSourceVersion().equals(41L)
                                && command.getQualityDispositionId().equals(DECISION)
                                && command.getQualityDispositionVersion().equals(2L)
                                && command.getDisposition().equals("ACCEPTED")
                                && command.getMovementQuantity().compareTo(BigDecimal.ONE) == 0
                                && command.getUnitCostAmountMinor().equals(100L)
                                && command.getMovementCostAmountMinor().equals(100L)
                                && command.getCurrencyCode().equals("CNY")
                                && command.getValuationPolicyId().equals("MOVING_AVERAGE")
                                && command.getValuationPolicyVersion().equals("V1")),
                eq("principal-quality-1"));
        verify(financeApi).ingestInventoryMovement(argThat(command ->
                command.getInventoryMovementId().equals("902")
                        && command.getDisposition().equals("REJECTED")), eq("principal-quality-1"));
        verify(financeApi, never()).postQualifiedReceipt(any(), anyString());
        verify(mapper).insertResultSplit(argThat(value -> value.getAcceptedLedgerTransactionId().equals(901L)
                && value.getRejectedLedgerTransactionId().equals(902L)
                && value.getQuarantinedLedgerTransactionId() == null
                && value.getFinanceReceiptEvidenceId().equals(FINANCE_RECEIPT_EVIDENCE)
                && value.getFinanceReceiptEvidenceVersion().equals(1L)
                && value.getFinanceQualityOperationId().equals(1101L)
                && value.getFinanceQualityEvidenceId().equals(FINANCE_QUALITY_EVIDENCE)
                && value.getAcceptedInventoryAggregateVersion().equals(41L)
                && value.getAcceptedFinanceInventoryOperationId().equals(1201L)
                && value.getAcceptedFinanceInventoryEvidenceVersion().equals(41L)
                && value.getRejectedFinanceInventoryOperationId().equals(1202L)
                && value.getAcceptedWarehouseOperationId().equals(1001L)
                && value.getAcceptedWarehouseReceiptVersion().equals(11L)
                && value.getAcceptedWarehouseReceiptLineVersion().equals(21L)
                && value.getAcceptedWarehouseScheduleFulfillmentVersion().equals(31L)
                && value.getRejectedWarehouseOperationId().equals(1002L)));
    }

    @Test
    void marksHeaderReadyWhenEveryLineDispositionConservesReceivedQuantity() {
        stubOpenAggregate();
        when(mapper.selectLineForUpdate(23L, "inspection-1", "inspection-line-1"))
                .thenReturn(openLine());
        when(mapper.selectSplitForUpdate(23L, "inspection-1", "inspection-line-1", "split-1"))
                .thenReturn(openSplit());
        when(mapper.applySplitResult(anyLong(), anyString(), anyLong(), any(), any(), any(), any(),
                anyString(), nullable(java.time.LocalDateTime.class), any())).thenReturn(1);
        when(mapper.selectLineTotals(23L, "inspection-line-1"))
                .thenReturn(totals("12", "3", "10", "1", "1", null, null));
        when(mapper.updateLineTotals(anyLong(), anyString(), anyLong(), any(), anyString(), any(), any()))
                .thenReturn(1);
        when(mapper.selectInspectionTotals(23L, "inspection-1"))
                .thenReturn(totals("12", "3", "10", "1", "1", 1L, 1L));
        when(mapper.updateInspectionTotals(anyLong(), anyString(), anyLong(), any(), anyString(),
                anyString(), any()))
                .thenReturn(1);

        ProcurementReceiptInspectionResult result = execute(resultCommand(
                new BigDecimal("3"), new BigDecimal("10"), new BigDecimal("1"), BigDecimal.ONE));

        assertThat(result.getStatus()).isEqualTo("READY_TO_COMPLETE");
        verify(mapper).updateLineTotals(eq(23L), eq("inspection-line-1"), eq(1L), any(),
                eq("COMPLETED"), any(), any());
        verify(mapper).updateInspectionTotals(eq(23L), eq("inspection-1"), eq(1L), any(),
                eq("READY_TO_COMPLETE"), eq("principal-quality-1"), any());
        verify(inventoryApi, times(3)).execute(any());
        verify(warehouseApi, times(3)).execute(any());
        verify(financeApi).ingestQualityDisposition(any(), eq("principal-quality-1"));
        verify(financeApi, times(3)).ingestInventoryMovement(any(), eq("principal-quality-1"));
        verify(financeApi, never()).postQualifiedReceipt(any(), anyString());
    }

    @Test
    void completesOnlyFullyConservedInspectionAndDerivesMixedDecision() {
        when(mapper.selectInspectionForUpdate(23L, "inspection-1"))
                .thenReturn(openInspection().setStatus("READY_TO_COMPLETE").setVersion(2L)
                        .setLastDecisionActorPrincipalId("principal-quality-maker")
                        .setAcceptedQuantity(new BigDecimal("10")).setRejectedQuantity(BigDecimal.ONE)
                        .setQuarantinedQuantity(BigDecimal.ONE));
        when(mapper.selectInspectionTotals(23L, "inspection-1"))
                .thenReturn(totals("12", "3", "10", "1", "1", 1L, 1L));
        when(mapper.completeInspection(23L, "inspection-1", 2L, "MIXED",
                "principal-quality-1", fixedTime()))
                .thenReturn(1);

        ProcurementReceiptInspectionResult result = execute(base(
                ProcurementReceiptInspectionOperation.COMPLETE_INSPECTION)
                .inspectionId("inspection-1").expectedVersion(2L).build());

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getFinalDecision()).isEqualTo("MIXED");
        verify(mapper).insertInspectionHistory(argThat(value -> value.getCurrentStatus().equals("COMPLETED")
                && value.getFinalDecision().equals("MIXED") && value.getInspectionVersion().equals(3L)));
        verify(outbox).append(argThat(event ->
                event.getEventType().equals("quality.procurement_receipt_inspection.completed")));
    }

    @Test
    void immutableReplayReturnsStoredTypedOutcomeWithoutNewFacts() {
        when(mapper.selectOperationForUpdate(23L, 701L)).thenAnswer(invocation -> new Operation()
                .setOperationId(701L).setTenantId(23L).setRequestHash(requestHash.get())
                .setAttemptToken("different-attempt").setStatus(10).setInspectionId("inspection-1")
                .setAggregateVersion(4L).setResultStatus("COMPLETED").setFinalDecision("ACCEPTED")
                .setReceivedQuantity(new BigDecimal("12")).setSampledQuantity(new BigDecimal("3"))
                .setAcceptedQuantity(new BigDecimal("12")).setRejectedQuantity(BigDecimal.ZERO)
                .setQuarantinedQuantity(BigDecimal.ZERO));

        ProcurementReceiptInspectionResult result = execute(base(
                ProcurementReceiptInspectionOperation.COMPLETE_INSPECTION)
                .inspectionId("inspection-1").expectedVersion(3L).build());

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.getFinalDecision()).isEqualTo("ACCEPTED");
        verify(mapper, never()).selectInspectionForUpdate(anyLong(), anyString());
        verifyNoInteractions(outbox);
        verifyNoInteractions(inventoryApi);
        verifyNoInteractions(warehouseApi);
        verifyNoInteractions(financeApi);
    }

    @Test
    void rejectsIdempotencyKeyReuseWithDifferentPayload() {
        when(mapper.selectOperationForUpdate(23L, 701L)).thenAnswer(invocation -> new Operation()
                .setOperationId(701L).setTenantId(23L).setRequestHash("f".repeat(64))
                .setAttemptToken("different-attempt").setStatus(10).setInspectionId("inspection-1"));

        assertThatThrownBy(() -> execute(base(ProcurementReceiptInspectionOperation.COMPLETE_INSPECTION)
                .inspectionId("inspection-1").expectedVersion(3L).build()))
                .hasMessage("idempotency key conflicts with different payload");
        verify(mapper, never()).selectInspectionForUpdate(anyLong(), anyString());
        verifyNoInteractions(outbox);
        verifyNoInteractions(inventoryApi);
        verifyNoInteractions(warehouseApi);
        verifyNoInteractions(financeApi);
    }

    @Test
    void warehouseFailureRollsBackInventoryAndAllQualityDecisionFacts() {
        stubOpenAggregate();
        when(mapper.selectLineForUpdate(23L, "inspection-1", "inspection-line-1"))
                .thenReturn(openLine());
        when(mapper.selectSplitForUpdate(23L, "inspection-1", "inspection-line-1", "split-1"))
                .thenReturn(openSplit());
        when(mapper.applySplitResult(anyLong(), anyString(), anyLong(), any(), any(), any(), any(),
                anyString(), nullable(java.time.LocalDateTime.class), any())).thenReturn(1);
        doThrow(new IllegalStateException("warehouse decision unavailable"))
                .when(warehouseApi).execute(any());

        assertThatThrownBy(() -> execute(resultCommand(
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO)))
                .hasMessage("warehouse decision unavailable");

        verify(inventoryApi).execute(any());
        verify(warehouseApi).execute(any());
        verify(mapper, never()).insertResultSplit(any());
        verify(mapper, never()).insertLineHistory(any());
        verify(mapper, never()).insertInspectionHistory(any());
        verify(mapper, never()).markOperationSucceeded(anyLong(), anyLong(), anyString(), anyLong(),
                anyString(), any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(outbox);
        verifyNoInteractions(financeApi);
    }

    @Test
    void refusesCompletionWhenCheckerParticipatedInAnyDecisionBatch() {
        when(mapper.selectInspectionForUpdate(23L, "inspection-1"))
                .thenReturn(openInspection().setStatus("READY_TO_COMPLETE").setVersion(2L)
                        .setLastDecisionActorPrincipalId("principal-quality-maker"));
        when(mapper.countResultBatchesByActor(23L, "inspection-1", "principal-quality-1"))
                .thenReturn(1L);

        assertThatThrownBy(() -> execute(base(ProcurementReceiptInspectionOperation.COMPLETE_INSPECTION)
                .inspectionId("inspection-1").expectedVersion(2L).build()))
                .hasMessage("inspection checker must be independent from every decision maker");
        verify(mapper, never()).completeInspection(anyLong(), anyString(), anyLong(), anyString(),
                anyString(), any());
        verifyNoInteractions(outbox);
    }

    @Test
    void outboxFailurePreventsOperationCommitInsideTransactionalBoundary() throws Exception {
        when(mapper.selectStandardVersion(23L, "standard-1", 3L, "standard-version-3"))
                .thenReturn(new StandardVersion().setContentSha256("a".repeat(64))
                        .setEffectiveAt(java.time.LocalDateTime.of(2026, 8, 1, 0, 0)));
        when(outbox.append(any())).thenThrow(new IllegalStateException("outbox unavailable"));

        assertThatThrownBy(() -> execute(createCommand())).hasMessage("outbox unavailable");
        verify(mapper, never()).markOperationSucceeded(anyLong(), anyLong(), anyString(), anyLong(),
                anyString(), any(), any(), any(), any(), any(), any(), any());
        assertThat(ProcurementReceiptInspectionService.class
                .getMethod("execute", ProcurementReceiptInspectionCommand.class, String.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class))
                .isNotNull();
    }

    @Test
    void inventoryFailureAbortsDecisionBeforeImmutableResultHistoryOutboxAndOperationCompletion() {
        stubOpenAggregate();
        when(mapper.selectLineForUpdate(23L, "inspection-1", "inspection-line-1"))
                .thenReturn(openLine());
        when(mapper.selectSplitForUpdate(23L, "inspection-1", "inspection-line-1", "split-1"))
                .thenReturn(openSplit());
        when(mapper.applySplitResult(anyLong(), anyString(), anyLong(), any(), any(), any(), any(),
                anyString(), nullable(java.time.LocalDateTime.class), any())).thenReturn(1);
        doThrow(new IllegalStateException("inventory ledger unavailable"))
                .when(inventoryApi).execute(any());

        assertThatThrownBy(() -> execute(resultCommand(
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO)))
                .hasMessage("inventory ledger unavailable");

        verify(mapper, never()).insertResultSplit(any());
        verify(mapper, never()).insertDefect(any());
        verify(mapper, never()).insertLineHistory(any());
        verify(mapper, never()).insertInspectionHistory(any());
        verify(mapper, never()).markOperationSucceeded(anyLong(), anyLong(), anyString(), anyLong(),
                anyString(), any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(outbox);
        verifyNoInteractions(warehouseApi);
        verifyNoInteractions(financeApi);
    }

    @Test
    void financeQualityEvidenceFailureRollsBackInventoryWarehouseAndEveryQualityFact() {
        stubOpenAggregate();
        when(mapper.selectLineForUpdate(23L, "inspection-1", "inspection-line-1"))
                .thenReturn(openLine());
        when(mapper.selectSplitForUpdate(23L, "inspection-1", "inspection-line-1", "split-1"))
                .thenReturn(openSplit());
        when(mapper.applySplitResult(anyLong(), anyString(), anyLong(), any(), any(), any(), any(),
                anyString(), nullable(java.time.LocalDateTime.class), any())).thenReturn(1);
        doThrow(new IllegalStateException("finance quality inbox unavailable"))
                .when(financeApi).ingestQualityDisposition(any(), anyString());

        assertThatThrownBy(() -> execute(resultCommand(
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO)))
                .hasMessage("finance quality inbox unavailable");

        verify(inventoryApi).execute(any());
        verify(warehouseApi).execute(any());
        verify(financeApi).ingestQualityDisposition(any(), eq("principal-quality-1"));
        verify(financeApi, never()).ingestInventoryMovement(any(), anyString());
        verify(financeApi, never()).postQualifiedReceipt(any(), anyString());
        verify(mapper, never()).insertResultSplit(any());
        verify(mapper, never()).insertDefect(any());
        verify(mapper, never()).insertLineHistory(any());
        verify(mapper, never()).insertInspectionHistory(any());
        verify(mapper, never()).markOperationSucceeded(anyLong(), anyLong(), anyString(), anyLong(),
                anyString(), any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(outbox);
    }

    @Test
    void financeInventoryEvidenceFailureRollsBackPriorFinanceQualityInventoryWarehouseAndQualityFacts() {
        stubOpenAggregate();
        when(mapper.selectLineForUpdate(23L, "inspection-1", "inspection-line-1"))
                .thenReturn(openLine());
        when(mapper.selectSplitForUpdate(23L, "inspection-1", "inspection-line-1", "split-1"))
                .thenReturn(openSplit());
        when(mapper.applySplitResult(anyLong(), anyString(), anyLong(), any(), any(), any(), any(),
                anyString(), nullable(java.time.LocalDateTime.class), any())).thenReturn(1);
        doThrow(new IllegalStateException("finance inventory inbox unavailable"))
                .when(financeApi).ingestInventoryMovement(any(), anyString());

        assertThatThrownBy(() -> execute(resultCommand(
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO)))
                .hasMessage("finance inventory inbox unavailable");

        verify(inventoryApi).execute(any());
        verify(warehouseApi).execute(any());
        verify(financeApi).ingestQualityDisposition(any(), eq("principal-quality-1"));
        verify(financeApi).ingestInventoryMovement(any(), eq("principal-quality-1"));
        verify(financeApi, never()).postQualifiedReceipt(any(), anyString());
        verify(mapper, never()).insertResultSplit(any());
        verify(mapper, never()).insertDefect(any());
        verify(mapper, never()).insertLineHistory(any());
        verify(mapper, never()).insertInspectionHistory(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void rejectsInventoryResultWhenAuthoritativeFrozenValuationSnapshotDiffers() {
        stubOpenAggregate();
        when(mapper.selectLineForUpdate(23L, "inspection-1", "inspection-line-1"))
                .thenReturn(openLine());
        when(mapper.selectSplitForUpdate(23L, "inspection-1", "inspection-line-1", "split-1"))
                .thenReturn(openSplit());
        when(mapper.applySplitResult(anyLong(), anyString(), anyLong(), any(), any(), any(), any(),
                anyString(), nullable(java.time.LocalDateTime.class), any())).thenReturn(1);
        doReturn(InventoryProcurementReceiptResult.builder()
                .operationId(801L).ledgerTransactionId(901L).targetAggregateVersion(41L)
                .receiptId(RECEIPT).receiptLineId(RECEIPT_LINE).unitCostAmountMinor(101L)
                .movementCostAmountMinor(101L).currencyCode("CNY").valuationPolicyId("MOVING_AVERAGE")
                .valuationPolicyVersion("V1").valuationPolicyHash(POLICY_HASH).build())
                .when(inventoryApi).execute(any());

        assertThatThrownBy(() -> execute(resultCommand(
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO)))
                .hasMessage("inventory quality effect returned mismatched frozen valuation snapshot");

        verifyNoInteractions(warehouseApi, financeApi, outbox);
        verify(mapper, never()).insertResultSplit(any());
    }

    @Test
    void propagatesCanonicalZeroCostInventorySnapshotIntoFinanceEvidence() {
        stubOpenAggregate();
        when(mapper.selectLineForUpdate(23L, "inspection-1", "inspection-line-1"))
                .thenReturn(openLine().setUnitCostAmountMinor(0L));
        when(mapper.selectSplitForUpdate(23L, "inspection-1", "inspection-line-1", "split-1"))
                .thenReturn(openSplit());
        when(mapper.applySplitResult(anyLong(), anyString(), anyLong(), any(), any(), any(), any(),
                anyString(), nullable(java.time.LocalDateTime.class), any())).thenReturn(1);
        when(mapper.selectLineTotals(23L, "inspection-line-1"))
                .thenReturn(totals("12", "1", "1", "0", "0", null, null));
        when(mapper.updateLineTotals(anyLong(), anyString(), anyLong(), any(), anyString(),
                nullable(java.time.LocalDateTime.class), any())).thenReturn(1);
        when(mapper.selectInspectionTotals(23L, "inspection-1"))
                .thenReturn(totals("12", "1", "1", "0", "0", 1L, 0L));
        when(mapper.updateInspectionTotals(anyLong(), anyString(), anyLong(), any(), anyString(),
                anyString(), any())).thenReturn(1);

        execute(resultCommand(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO));

        verify(financeApi).ingestInventoryMovement(argThat(command ->
                        command.getUnitCostAmountMinor().equals(0L)
                                && command.getMovementCostAmountMinor().equals(0L)
                                && command.getDisposition().equals("ACCEPTED")),
                eq("principal-quality-1"));
    }

    @Test
    void createFailsClosedWhenFrozenValuationFactIsMissing() {
        when(mapper.selectStandardVersion(anyLong(), anyString(), anyLong(), anyString()))
                .thenReturn(new StandardVersion().setContentSha256("a".repeat(64))
                        .setEffectiveAt(java.time.LocalDateTime.of(2026, 8, 1, 0, 0)));
        ProcurementReceiptInspectionCommand command = createCommand();
        command.getCreate().getLines().get(0).setValuationPolicyHash(null);

        assertThatThrownBy(() -> execute(command))
                .hasMessage("valuationPolicyHash must be lowercase SHA-256");
        verify(mapper, never()).insertInspection(any());
        verifyNoInteractions(inventoryApi, warehouseApi, financeApi, outbox);
    }

    private void stubOpenAggregate() {
        when(mapper.selectInspectionForUpdate(23L, "inspection-1")).thenReturn(openInspection());
    }

    private static ProcurementReceiptInspectionCommand createCommand() {
        return base(ProcurementReceiptInspectionOperation.CREATE_INSPECTION)
                .create(ProcurementReceiptInspectionCommand.CreateDefinition.builder()
                        .inspectionId("inspection-1").inspectionCode("PRI-20260802-001")
                        .receiptId(RECEIPT).purchaseOrderId(PO).supplierId(SUPPLIER)
                        .ownerType("MERCHANT").ownerId(OWNER).businessNo("GRN-20260802-001")
                        .standardId("standard-1").standardVersion(3L)
                        .standardVersionId("standard-version-3").standardContentSha256("a".repeat(64))
                        .lines(List.of(ProcurementReceiptInspectionCommand.LineDefinition.builder()
                                .inspectionLineId("inspection-line-1").lineNumber(1)
                                .receiptLineId(RECEIPT_LINE).itemId(ITEM).scheduleId(SCHEDULE)
                                .canonicalSkuId(SKU).uomCode("EA").valuationPolicy("MOVING_AVERAGE")
                                .valuationPolicyVersion("V1").valuationPolicyHash(POLICY_HASH)
                                .unitCostAmountMinor(100L).currencyCode("CNY")
                                .receivedQuantity(new BigDecimal("12"))
                                .splits(List.of(
                                        ProcurementReceiptInspectionCommand.SplitDefinition.builder()
                                                .inspectionSplitId("split-1").splitNumber(1)
                                                .warehouseId(WAREHOUSE).locationId(LOCATION_1)
                                                .lotId(LOT).receivedQuantity(new BigDecimal("7")).build(),
                                        ProcurementReceiptInspectionCommand.SplitDefinition.builder()
                                                .inspectionSplitId("split-2").splitNumber(2)
                                                .warehouseId(WAREHOUSE).locationId(LOCATION_2)
                                                .receivedQuantity(new BigDecimal("5")).build()))
                                .build()))
                        .build())
                .build();
    }

    private static ProcurementReceiptInspectionCommand resultCommand(
            BigDecimal sampled, BigDecimal accepted, BigDecimal rejected, BigDecimal quarantined) {
        ProcurementReceiptInspectionCommand.SplitResultDefinition.SplitResultDefinitionBuilder split =
                ProcurementReceiptInspectionCommand.SplitResultDefinition.builder()
                        .resultSplitId("result-split-1").inspectionSplitId("split-1").expectedVersion(1L)
                        .qualityDecisionId(DECISION)
                        .sampledQuantity(sampled).acceptedQuantity(accepted).rejectedQuantity(rejected)
                        .quarantinedQuantity(quarantined).decisionEvidenceSha256("b".repeat(64))
                        .evidenceRef("evidence/result-1")
                        .acceptedDispositionCode(accepted.signum() > 0 ? "ACCEPT" : null)
                        .rejectedDispositionCode(rejected.signum() > 0 ? "RETURN_TO_SUPPLIER" : null)
                        .quarantineDispositionCode(quarantined.signum() > 0 ? "HOLD" : null);
        if (rejected.add(quarantined).signum() > 0) {
            split.defects(List.of(ProcurementReceiptInspectionCommand.DefectDefinition.builder()
                    .defectId("defect-1").defectCode("SEAM_OPEN").defectCategory("WORKMANSHIP")
                    .severity("MAJOR").affectedQuantity(BigDecimal.ONE)
                    .evidenceSha256("c".repeat(64)).evidenceRef("evidence/defect-1").build()));
        }
        return base(ProcurementReceiptInspectionOperation.RECORD_LINE_RESULTS)
                .inspectionId("inspection-1").expectedVersion(1L)
                .results(ProcurementReceiptInspectionCommand.ResultsDefinition.builder()
                        .resultBatchId("result-batch-1")
                        .lines(List.of(ProcurementReceiptInspectionCommand.LineResultDefinition.builder()
                                .inspectionLineId("inspection-line-1").expectedVersion(1L)
                                .splits(List.of(split.build())).build()))
                        .build())
                .build();
    }

    private static ProcurementReceiptInspectionCommand.ProcurementReceiptInspectionCommandBuilder base(
            ProcurementReceiptInspectionOperation operation) {
        return ProcurementReceiptInspectionCommand.builder().operation(operation)
                .idempotencyKey("quality-procurement-receipt-1")
                .sourceEventId("10000000-0000-0000-0000-000000000001")
                .correlationId("20000000-0000-0000-0000-000000000001")
                .causationId("30000000-0000-0000-0000-000000000001")
                .occurredAt(Instant.parse("2026-08-02T00:00:00Z"));
    }

    private static Inspection openInspection() {
        return new Inspection().setInspectionId("inspection-1").setTenantId(23L)
                .setInspectionCode("PRI-20260802-001").setReceiptId(RECEIPT)
                .setPurchaseOrderId(PO).setSupplierId(SUPPLIER).setOwnerType("MERCHANT")
                .setOwnerId(OWNER).setBusinessNo("GRN-20260802-001").setStatus("OPEN").setVersion(1L)
                .setReceivedQuantity(new BigDecimal("12")).setSampledQuantity(BigDecimal.ZERO)
                .setAcceptedQuantity(BigDecimal.ZERO).setRejectedQuantity(BigDecimal.ZERO)
                .setQuarantinedQuantity(BigDecimal.ZERO);
    }

    private static InspectionLine openLine() {
        return new InspectionLine().setInspectionLineId("inspection-line-1").setInspectionId("inspection-1")
                .setReceiptLineId(RECEIPT_LINE).setPurchaseOrderId(PO).setItemId(ITEM).setScheduleId(SCHEDULE)
                .setSupplierId(SUPPLIER).setOwnerType("MERCHANT").setOwnerId(OWNER).setCanonicalSkuId(SKU)
                .setUomCode("EA").setValuationPolicy("MOVING_AVERAGE").setValuationPolicyVersion("V1")
                .setValuationPolicyHash(POLICY_HASH).setUnitCostAmountMinor(100L).setCurrencyCode("CNY")
                .setStatus("OPEN").setVersion(1L).setReceivedQuantity(new BigDecimal("12"));
    }

    private static InspectionSplit openSplit() {
        return new InspectionSplit().setInspectionSplitId("split-1").setInspectionLineId("inspection-line-1")
                .setWarehouseId(WAREHOUSE).setLocationId(LOCATION_1).setLotId(LOT)
                .setStatus("OPEN").setVersion(1L).setReceivedQuantity(new BigDecimal("12"))
                .setSampledQuantity(BigDecimal.ZERO).setAcceptedQuantity(BigDecimal.ZERO)
                .setRejectedQuantity(BigDecimal.ZERO).setQuarantinedQuantity(BigDecimal.ZERO);
    }

    private static QuantityTotals totals(String received, String sampled, String accepted,
                                         String rejected, String quarantined,
                                         Long totalLines, Long completedLines) {
        return new QuantityTotals().setReceivedQuantity(new BigDecimal(received))
                .setSampledQuantity(new BigDecimal(sampled)).setAcceptedQuantity(new BigDecimal(accepted))
                .setRejectedQuantity(new BigDecimal(rejected))
                .setQuarantinedQuantity(new BigDecimal(quarantined))
                .setTotalLines(totalLines).setCompletedLines(completedLines);
    }

    private ProcurementReceiptInspectionResult execute(ProcurementReceiptInspectionCommand command) {
        return service.execute(command, "principal-quality-1");
    }

    private static java.time.LocalDateTime fixedTime() {
        return java.time.LocalDateTime.of(2026, 8, 2, 0, 0);
    }
}
