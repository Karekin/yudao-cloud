package cn.iocoder.yudao.module.cloudmold.warehouse.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.P2pEvidenceCommands;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.P2pEvidenceIngestionApi;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.ProcureToPayResult;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryProcurementReceiptApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryProcurementReceiptCommand;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryProcurementReceiptDisposition;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryProcurementReceiptOperation;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryProcurementReceiptResult;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3Command;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3CommandApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3CommandResult;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3Operation;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementOrderView;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementQueryApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.AsnDefinition;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.AsnLineDefinition;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.InboundCommand;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.InboundCommandResult;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.InboundOperation;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.PutawayDefinition;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.PutawayLineDefinition;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.ReceiptDefinition;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.ReceiptLineDefinition;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseReferenceValidationApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.AsnDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.AsnLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.InboundOperationDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.InboundStatusHistoryDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.PutawayDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.ReceiptDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.ReceiptLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.ScheduleFulfillmentDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.AsnLineMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.AsnMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.InboundOperationMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.InboundStatusHistoryMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.PutawayMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.PutawayLineMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.ReceiptLineMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.ReceiptMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.ScheduleFulfillmentMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.actor.WarehouseActorPrincipalPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InboundCommandServiceImplTest {

    private static final String ORDER_ID = "po-01";
    private static final String ITEM_ID = "po-item-01";
    private static final String SCHEDULE_ID = "schedule-01";
    private static final String SUPPLIER_ID = "supplier-01";
    private static final String OWNER = "merchant-01";
    private static final String SKU = "sku-01";
    private static final String WAREHOUSE = "warehouse-01";
    private static final String LOCATION = "receipt-loc-01";
    private static final String TARGET_LOCATION = "putaway-loc-01";
    private static final String ASN_ID = "asn-01";
    private static final String ASN_LINE_ID = "asn-line-01";
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-02T10:00:00Z");
    private static final String CORRELATION_ID = "11111111-1111-4111-8111-111111111111";
    private static final String ACTOR_PRINCIPAL_ID = "principal-admin-01";
    private static final BigDecimal ZERO = new BigDecimal("0.000000");

    private final InboundOperationMapper operationMapper = mock(InboundOperationMapper.class);
    private final AsnMapper asnMapper = mock(AsnMapper.class);
    private final AsnLineMapper asnLineMapper = mock(AsnLineMapper.class);
    private final ReceiptMapper receiptMapper = mock(ReceiptMapper.class);
    private final ReceiptLineMapper receiptLineMapper = mock(ReceiptLineMapper.class);
    private final PutawayMapper putawayMapper = mock(PutawayMapper.class);
    private final PutawayLineMapper putawayLineMapper = mock(PutawayLineMapper.class);
    private final InboundStatusHistoryMapper historyMapper = mock(InboundStatusHistoryMapper.class);
    private final ScheduleFulfillmentMapper scheduleFulfillmentMapper = mock(ScheduleFulfillmentMapper.class);
    private final ProcurementQueryApi procurementQueryApi = mock(ProcurementQueryApi.class);
    private final InventoryProcurementReceiptApi inventoryReceiptApi = mock(InventoryProcurementReceiptApi.class);
    private final InventoryV3CommandApi inventoryV3CommandApi = mock(InventoryV3CommandApi.class);
    private final WarehouseReferenceValidationApi warehouseValidationApi = mock(WarehouseReferenceValidationApi.class);
    private final WarehouseActorPrincipalPort actorPrincipalPort = mock(WarehouseActorPrincipalPort.class);
    private final P2pEvidenceIngestionApi p2pEvidenceIngestionApi = mock(P2pEvidenceIngestionApi.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);

    private final InboundCommandServiceImpl service = new InboundCommandServiceImpl(
            operationMapper, asnMapper, asnLineMapper, receiptMapper, receiptLineMapper, putawayMapper, putawayLineMapper,
            historyMapper, scheduleFulfillmentMapper, procurementQueryApi, inventoryReceiptApi, inventoryV3CommandApi,
            warehouseValidationApi, actorPrincipalPort, p2pEvidenceIngestionApi, outboxAppender);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(historyMapper.insert(any(InboundStatusHistoryDO.class))).thenReturn(1);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event", "a".repeat(64), false));
        when(p2pEvidenceIngestionApi.ingestReceiptLine(any(), eq(ACTOR_PRINCIPAL_ID)))
                .thenReturn(ProcureToPayResult.builder().operationId(901L)
                        .aggregateType("finance_receipt_line_evidence").aggregateId("finance-receipt-evidence-01")
                        .aggregateVersion(1L).status("RECORDED").duplicate(false).build());
        when(receiptLineMapper.bindFinanceEvidence(eq(1L), anyString(), eq(901L),
                eq("finance-receipt-evidence-01"), eq(1L), any())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createsTypedProcurementAsn() {
        prepareNewOperation("CREATE_ASN");
        when(asnMapper.selectByProcurementOrderId(1L, ORDER_ID)).thenReturn(null);
        when(asnMapper.selectByAsnNo(1L, "ASN-20260802-001")).thenReturn(null);
        when(procurementQueryApi.requireCurrent(ORDER_ID)).thenReturn(procurementOrderView());
        when(asnMapper.insert(any(AsnDO.class))).thenReturn(1);
        when(asnLineMapper.insert(any(AsnLineDO.class))).thenReturn(1);
        when(scheduleFulfillmentMapper.insertIgnore(any())).thenReturn(1);
        when(scheduleFulfillmentMapper.selectForUpdate(1L, ITEM_ID, SCHEDULE_ID))
                .thenReturn(scheduleFulfillment("0.000000", "0.000000", 1L, "1.000000"));

        InboundCommandResult result = execute(InboundCommand.builder()
                .operation(InboundOperation.CREATE_ASN)
                .idempotencyKey("asn-create-key")
                .correlationId(CORRELATION_ID)
                .occurredAt(OCCURRED_AT)
                .asn(AsnDefinition.builder()
                        .asnId(ASN_ID).asnNo("ASN-20260802-001")
                        .procurementOrderId(ORDER_ID).supplierId(SUPPLIER_ID).warehouseId(WAREHOUSE)
                        .lines(List.of(AsnLineDefinition.builder()
                                .asnLineId(ASN_LINE_ID).lineNo(10)
                                .procurementOrderId(ORDER_ID).procurementOrderItemId(ITEM_ID)
                                .deliveryScheduleId(SCHEDULE_ID).poReleaseVersion(4L)
                                .supplierId(SUPPLIER_ID).warehouseId(WAREHOUSE).receiptLocationId(LOCATION)
                                .canonicalSkuId(SKU).ownerType("MERCHANT").ownerId(OWNER)
                                .baseUomCode("PIECE").scheduledQuantity(new BigDecimal("10.000000"))
                                .allowedOverReceiptQuantity(new BigDecimal("1.000000"))
                                .tolerancePolicyVersion("tol-v1").tolerancePolicyHash("a".repeat(64))
                                .build()))
                        .build())
                .build());

        assertThat(result.getStatus()).isEqualTo("DRAFT");
        assertThat(result.getProcurementOrderId()).isEqualTo(ORDER_ID);
        verify(warehouseValidationApi).requireActiveLocation(WAREHOUSE, LOCATION);
        ArgumentCaptor<AsnLineDO> frozenLine = ArgumentCaptor.forClass(AsnLineDO.class);
        verify(asnLineMapper).insert(frozenLine.capture());
        assertThat(frozenLine.getValue().getValuationPolicyId()).isEqualTo("STANDARD_V1");
        assertThat(frozenLine.getValue().getValuationPolicyVersion()).isEqualTo("valuation-v1");
        assertThat(frozenLine.getValue().getValuationPolicyHash()).isEqualTo("b".repeat(64));
        assertThat(frozenLine.getValue().getUnitCostAmountMinor()).isEqualTo(1200L);
        assertThat(frozenLine.getValue().getCurrencyCode()).isEqualTo("CNY");
        assertThat(frozenLine.getValue().getRoundingPolicyCode()).isEqualTo("HALF_UP");
        verify(outboxAppender).append(argThat(event ->
                "inbound.procurement_asn.created".equals(event.getEventType())
                        && ORDER_ID.equals(event.getPayload().get("procurement_order_id"))
                        && SUPPLIER_ID.equals(event.getPayload().get("supplier_id"))
                        && !event.getPayload().containsKey("supplier_ref")
                        && !event.getPayload().containsKey("source_business_ref")));
    }

    @Test
    void recordsPartialReceiptIntoPendingQualityInventory() {
        prepareNewOperation("COMPLETE_RECEIPT");
        when(asnMapper.selectForUpdate(1L, ASN_ID)).thenReturn(asn("IN_TRANSIT", 1L));
        when(asnLineMapper.selectForUpdate(1L, ASN_LINE_ID)).thenReturn(asnLine("0.000000", "0.000000", 1L, "OPEN"));
        when(scheduleFulfillmentMapper.selectForUpdate(1L, ITEM_ID, SCHEDULE_ID)).thenReturn(scheduleFulfillment("0.000000", "0.000000", 1L, "0.000000"));
        when(receiptMapper.insert(any(ReceiptDO.class))).thenReturn(1);
        when(scheduleFulfillmentMapper.updateReceiptCas(eq(1L), eq("sched-fulfillment-01"), eq(1L),
                eq(new BigDecimal("3.000000")), eq(new BigDecimal("3.000000")), any())).thenReturn(1);
        when(asnLineMapper.updateFulfillmentCas(eq(1L), eq(ASN_LINE_ID), eq(1L),
                eq(new BigDecimal("3.000000")), eq(new BigDecimal("3.000000")),
                eq("PARTIAL_RECEIVED_PENDING_QUALITY"), any())).thenReturn(1);
        when(receiptLineMapper.insert(any(ReceiptLineDO.class))).thenReturn(1);
        when(asnLineMapper.selectByAsn(1L, ASN_ID)).thenReturn(List.of(
                asnLine("3.000000", "3.000000", 2L, "PARTIAL_RECEIVED_PENDING_QUALITY")));
        when(asnMapper.updateStatusCas(eq(1L), eq(ASN_ID), eq(1L), eq("PARTIAL_RECEIVED"), any())).thenReturn(1);
        when(inventoryReceiptApi.execute(any())).thenReturn(InventoryProcurementReceiptResult.builder()
                .operationId(201L).ledgerTransactionId(301L).targetBalanceId("bal-01")
                .pendingQuantity(new BigDecimal("3.000000")).build());

        InboundCommandResult result = execute(partialReceiptCommand("3.000000", 1L, "PENDING_QUALITY"));

        assertThat(result.getReceiptStatus()).isEqualTo("PENDING_QUALITY");
        assertThat(result.getAsnStatus()).isEqualTo("PARTIAL_RECEIVED");
        ArgumentCaptor<InventoryProcurementReceiptCommand> inventory =
                ArgumentCaptor.forClass(InventoryProcurementReceiptCommand.class);
        verify(inventoryReceiptApi).execute(inventory.capture());
        assertThat(inventory.getValue().getOperation()).isEqualTo(InventoryProcurementReceiptOperation.RECEIVE_PENDING_QUALITY);
        assertThat(inventory.getValue().getDisposition()).isEqualTo(InventoryProcurementReceiptDisposition.PENDING);
        assertThat(inventory.getValue().getValuationPolicy()).isEqualTo("STANDARD_V1");
        assertThat(inventory.getValue().getValuationPolicyVersion()).isEqualTo("valuation-v1");
        assertThat(inventory.getValue().getValuationPolicyHash()).isEqualTo("b".repeat(64));
        assertThat(inventory.getValue().getUnitCostAmountMinor()).isEqualTo(1200L);
        assertThat(inventory.getValue().getMovementCostAmountMinor()).isEqualTo(3600L);
        ArgumentCaptor<ReceiptLineDO> receiptLine = ArgumentCaptor.forClass(ReceiptLineDO.class);
        verify(receiptLineMapper).insert(receiptLine.capture());
        assertThat(receiptLine.getValue().getQualityStatus()).isEqualTo("PENDING_QUALITY");
        assertThat(receiptLine.getValue().getPendingQualityQuantity()).isEqualByComparingTo("3.000000");
        assertThat(receiptLine.getValue().getInventoryLedgerTxId()).isEqualTo(301L);
        ArgumentCaptor<P2pEvidenceCommands.ReceiptLine> financeEvidence =
                ArgumentCaptor.forClass(P2pEvidenceCommands.ReceiptLine.class);
        verify(p2pEvidenceIngestionApi).ingestReceiptLine(financeEvidence.capture(), eq(ACTOR_PRINCIPAL_ID));
        assertThat(financeEvidence.getValue().getReceiptId()).isEqualTo("receipt-3.000000");
        assertThat(financeEvidence.getValue().getReceiptLineId()).isEqualTo("receipt-line-3.000000");
        assertThat(financeEvidence.getValue().getPurchaseOrderId()).isEqualTo(ORDER_ID);
        assertThat(financeEvidence.getValue().getPurchaseOrderItemId()).isEqualTo(ITEM_ID);
        assertThat(financeEvidence.getValue().getDeliveryScheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(financeEvidence.getValue().getReceivedQuantity()).isEqualByComparingTo("3.000000");
        assertThat(financeEvidence.getValue().getUnitOfMeasure()).isEqualTo("PIECE");
        assertThat(financeEvidence.getValue().getSourceVersion()).isEqualTo(1L);
        assertThat(financeEvidence.getValue().getEvidenceSha256()).matches("[0-9a-f]{64}");
        assertThat(financeEvidence.getValue().getSourceOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(financeEvidence.getValue().getEnvelope().getIdempotencyKey())
                .isEqualTo("warehouse-receipt-evidence:receipt-line-3.000000");
        verify(receiptLineMapper).bindFinanceEvidence(eq(1L), eq("receipt-line-3.000000"), eq(901L),
                eq("finance-receipt-evidence-01"), eq(1L), any());
    }

    @Test
    void allowsSecondReceiptOnSameAsn() {
        prepareNewOperation("COMPLETE_RECEIPT");
        when(asnMapper.selectForUpdate(1L, ASN_ID)).thenReturn(asn("PARTIAL_RECEIVED", 2L));
        when(asnLineMapper.selectForUpdate(1L, ASN_LINE_ID)).thenReturn(asnLine("3.000000", "3.000000", 2L,
                "PARTIAL_RECEIVED_PENDING_QUALITY"));
        when(scheduleFulfillmentMapper.selectForUpdate(1L, ITEM_ID, SCHEDULE_ID)).thenReturn(scheduleFulfillment("3.000000", "3.000000", 2L, "0.000000"));
        when(receiptMapper.insert(any(ReceiptDO.class))).thenReturn(1);
        when(scheduleFulfillmentMapper.updateReceiptCas(eq(1L), eq("sched-fulfillment-01"), eq(2L),
                eq(new BigDecimal("5.000000")), eq(new BigDecimal("5.000000")), any())).thenReturn(1);
        when(asnLineMapper.updateFulfillmentCas(eq(1L), eq(ASN_LINE_ID), eq(2L),
                eq(new BigDecimal("5.000000")), eq(new BigDecimal("5.000000")),
                eq("PARTIAL_RECEIVED_PENDING_QUALITY"), any())).thenReturn(1);
        when(receiptLineMapper.insert(any(ReceiptLineDO.class))).thenReturn(1);
        when(asnLineMapper.selectByAsn(1L, ASN_ID)).thenReturn(List.of(
                asnLine("5.000000", "5.000000", 3L, "PARTIAL_RECEIVED_PENDING_QUALITY")));
        when(asnMapper.updateStatusCas(eq(1L), eq(ASN_ID), eq(2L), eq("PARTIAL_RECEIVED"), any())).thenReturn(1);
        when(inventoryReceiptApi.execute(any())).thenReturn(InventoryProcurementReceiptResult.builder()
                .operationId(202L).ledgerTransactionId(302L).targetBalanceId("bal-02")
                .pendingQuantity(new BigDecimal("2.000000")).build());

        InboundCommandResult result = execute(partialReceiptCommand("2.000000", 2L, "PENDING_QUALITY"));

        assertThat(result.getReceiptStatus()).isEqualTo("PENDING_QUALITY");
        assertThat(result.getAsnStatus()).isEqualTo("PARTIAL_RECEIVED");
        verify(receiptMapper).insert(any(ReceiptDO.class));
    }

    @Test
    void rejectsOverReceiptBeforeInventoryCall() {
        prepareNewOperation("COMPLETE_RECEIPT");
        when(asnMapper.selectForUpdate(1L, ASN_ID)).thenReturn(asn("IN_TRANSIT", 1L));
        when(asnLineMapper.selectForUpdate(1L, ASN_LINE_ID)).thenReturn(asnLine("9.000000", "9.000000", 2L,
                "PARTIAL_RECEIVED_PENDING_QUALITY"));
        when(scheduleFulfillmentMapper.selectForUpdate(1L, ITEM_ID, SCHEDULE_ID)).thenReturn(scheduleFulfillment("9.000000", "9.000000", 2L, "0.000000"));
        when(receiptMapper.insert(any(ReceiptDO.class))).thenReturn(1);

        assertThatThrownBy(() -> execute(partialReceiptCommand("2.000000", 2L, "PENDING_QUALITY")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("receipt exceeds scheduled quantity tolerance");
        verifyNoInteractions(inventoryReceiptApi);
    }

    @Test
    void failsClosedWhenScheduleFulfillmentCasConflicts() {
        prepareNewOperation("COMPLETE_RECEIPT");
        when(asnMapper.selectForUpdate(1L, ASN_ID)).thenReturn(asn("IN_TRANSIT", 1L));
        when(asnLineMapper.selectForUpdate(1L, ASN_LINE_ID)).thenReturn(asnLine("0.000000", "0.000000", 1L, "OPEN"));
        when(scheduleFulfillmentMapper.selectForUpdate(1L, ITEM_ID, SCHEDULE_ID)).thenReturn(scheduleFulfillment("0.000000", "0.000000", 1L, "0.000000"));
        when(receiptMapper.insert(any(ReceiptDO.class))).thenReturn(1);
        when(inventoryReceiptApi.execute(any())).thenReturn(InventoryProcurementReceiptResult.builder()
                .operationId(203L).ledgerTransactionId(303L).targetBalanceId("bal-03")
                .pendingQuantity(new BigDecimal("3.000000")).build());
        when(scheduleFulfillmentMapper.updateReceiptCas(eq(1L), eq("sched-fulfillment-01"), eq(1L),
                any(), any(), any())).thenReturn(0);
        when(asnLineMapper.updateFulfillmentCas(eq(1L), eq(ASN_LINE_ID), eq(1L),
                any(), any(), anyString(), any())).thenReturn(0);

        assertThatThrownBy(() -> execute(partialReceiptCommand("3.000000", 1L, "PENDING_QUALITY")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("global schedule fulfillment version conflict");
        verify(receiptLineMapper, never()).insert(any(ReceiptLineDO.class));
        verify(outboxAppender, never()).append(any());
        verify(operationMapper, never()).markSucceeded(anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void rejectsMissingProcurementScheduleOnAsnCreation() {
        prepareNewOperation("CREATE_ASN");
        when(asnMapper.selectByAsnNo(1L, "ASN-20260802-002")).thenReturn(null);
        when(procurementQueryApi.requireCurrent(ORDER_ID)).thenReturn(ProcurementOrderView.builder()
                .orderId(ORDER_ID).supplierId(SUPPLIER_ID).currencyCode("CNY")
                .roundingPolicyCode("HALF_UP").status("RELEASED").version(4L)
                .items(List.of(ProcurementOrderView.PurchaseOrderItemView.builder()
                        .itemId(ITEM_ID).canonicalSkuId(SKU).orderedQuantity(new BigDecimal("10.000000"))
                        .uomCode("PIECE").unitNetPriceMinor(new BigDecimal("1200.000000"))
                        .valuationPolicyId("STANDARD_V1").valuationPolicyVersion("valuation-v1")
                        .valuationPolicyHash("b".repeat(64)).schedules(List.of()).build()))
                .build());

        InboundCommand command = InboundCommand.builder()
                .operation(InboundOperation.CREATE_ASN)
                .idempotencyKey("asn-missing-schedule")
                .correlationId(CORRELATION_ID)
                .occurredAt(OCCURRED_AT)
                .asn(AsnDefinition.builder()
                        .asnNo("ASN-20260802-002").procurementOrderId(ORDER_ID)
                        .supplierId(SUPPLIER_ID).warehouseId(WAREHOUSE)
                        .lines(List.of(asnLineDefinition()))
                        .build())
                .build();

        assertThatThrownBy(() -> execute(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("procurement delivery schedule not found");
        verify(asnMapper, never()).insert(any(AsnDO.class));
        verifyNoInteractions(inventoryReceiptApi);
    }

    @Test
    void replaysDuplicateBeforeTouchingProcurementOrInventory() {
        InboundCommand command = partialReceiptCommand("1.000000", 1L, "PENDING_QUALITY");
        InboundCommandResult stored = InboundCommandResult.builder()
                .asnId(ASN_ID).receiptId("receipt-dup").receiptStatus("PENDING_QUALITY")
                .aggregateVersion(2L).status("PENDING_QUALITY").duplicate(false).build();
        when(operationMapper.selectLastInsertId()).thenReturn(500L);
        when(operationMapper.selectForUpdate(500L, 1L)).thenReturn(new InboundOperationDO()
                .setOperationId(500L).setTenantId(1L).setAttemptToken("first-attempt")
                .setRequestHash(InboundCommandServiceImpl.fingerprint(1L, command))
                .setStatus(10).setResultJson(JsonUtils.toJsonString(stored)));

        InboundCommandResult replay = execute(command);

        assertThat(replay.isDuplicate()).isTrue();
        assertThat(replay.getReceiptId()).isEqualTo("receipt-dup");
        verifyNoInteractions(procurementQueryApi, inventoryReceiptApi, inventoryV3CommandApi,
                p2pEvidenceIngestionApi, outboxAppender, historyMapper);
    }

    @Test
    void rejectsPutawayWhenReceiptLineHasNoAcceptedQuantity() {
        prepareNewOperation("COMPLETE_PUTAWAY");
        when(receiptMapper.selectForUpdate(1L, "receipt-01")).thenReturn(new ReceiptDO()
                .setReceiptId("receipt-01").setProcurementOrderId(ORDER_ID).setReceiptNo("RCV-01")
                .setWarehouseId(WAREHOUSE).setStatus("PENDING_QUALITY").setVersion(1L));
        when(receiptLineMapper.selectForUpdate(1L, "receipt-line-01")).thenReturn(receiptLine(
                "receipt-line-01", "3.000000", "3.000000", "0.000000", "0.000000", 1L));

        assertThatThrownBy(() -> execute(InboundCommand.builder()
                .operation(InboundOperation.COMPLETE_PUTAWAY)
                .idempotencyKey("putaway-key").correlationId(CORRELATION_ID).occurredAt(OCCURRED_AT)
                .putaway(PutawayDefinition.builder().putawayId("putaway-01").receiptId("receipt-01")
                        .warehouseId(WAREHOUSE).expectedVersion(1L)
                        .lines(List.of(PutawayLineDefinition.builder().receiptLineId("receipt-line-01")
                                .sourceLocationId(LOCATION).targetLocationId(TARGET_LOCATION)
                                .lotId("lot-01").quantity(new BigDecimal("1.000000"))
                                .expectedReceiptLineVersion(1L).build()))
                        .build())
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("putaway quantity exceeds accepted not-yet-putaway quantity");
        verifyNoInteractions(inventoryV3CommandApi);
    }

    @Test
    void putsAwayAcceptedQuantityInMultipleLocationsWithoutWaitingForWholeReceipt() {
        prepareNewOperation("COMPLETE_PUTAWAY");
        ReceiptDO receipt = new ReceiptDO().setReceiptId("receipt-01").setProcurementOrderId(ORDER_ID)
                .setReceiptNo("RCV-01").setWarehouseId(WAREHOUSE)
                .setStatus("PARTIAL_QUALITY_DECIDED").setVersion(2L);
        ReceiptLineDO line = receiptLine("receipt-line-01", "10.000000", "4.000000", "6.000000",
                "2.000000", 3L);
        when(receiptMapper.selectForUpdate(1L, "receipt-01")).thenReturn(receipt);
        when(receiptLineMapper.selectForUpdate(1L, "receipt-line-01")).thenReturn(line);
        when(receiptLineMapper.updatePutawayCas(eq(1L), eq("receipt-line-01"), eq(3L),
                eq(new BigDecimal("2.000000")), eq(new BigDecimal("4.000000")),
                eq("PARTIALLY_PUTAWAY"), any())).thenReturn(1);
        when(putawayLineMapper.insert(any(cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.PutawayLineDO.class)))
                .thenReturn(1);
        when(putawayMapper.insert(any(PutawayDO.class))).thenReturn(1);
        when(receiptLineMapper.selectByReceipt(1L, "receipt-01")).thenReturn(List.of(line));
        when(receiptMapper.updateStatusCas(eq(1L), eq("receipt-01"), eq(2L),
                eq("PARTIALLY_PUTAWAY"), any())).thenReturn(1);
        when(inventoryV3CommandApi.execute(any())).thenReturn(InventoryV3CommandResult.builder()
                .operationId(701L).ledgerTransactionId(702L).movementGroupId("move-group-01")
                .balanceId("target-balance-01").build());

        InboundCommandResult result = execute(InboundCommand.builder()
                .operation(InboundOperation.COMPLETE_PUTAWAY).idempotencyKey("putaway-partial-01")
                .sourceEventId("11111111-1111-1111-1111-111111111111")
                .correlationId("22222222-2222-2222-2222-222222222222").occurredAt(OCCURRED_AT)
                .putaway(PutawayDefinition.builder().putawayId("putaway-01").receiptId("receipt-01")
                        .warehouseId(WAREHOUSE).expectedVersion(2L)
                        .lines(List.of(PutawayLineDefinition.builder().putawayLineId("putaway-line-01")
                                .receiptLineId("receipt-line-01").sourceLocationId(LOCATION)
                                .targetLocationId(TARGET_LOCATION).lotId("lot-01")
                                .quantity(new BigDecimal("2.000000")).expectedReceiptLineVersion(3L).build()))
                        .build()).build());

        assertThat(result.getReceiptStatus()).isEqualTo("PARTIALLY_PUTAWAY");
        ArgumentCaptor<InventoryV3Command> inventory = ArgumentCaptor.forClass(InventoryV3Command.class);
        verify(inventoryV3CommandApi).execute(inventory.capture());
        assertThat(inventory.getValue().getOperation()).isEqualTo(InventoryV3Operation.RELOCATE);
        assertThat(inventory.getValue().getStockStatus()).isEqualTo("SELLABLE");
        assertThat(inventory.getValue().getQualityStatus()).isEqualTo("QUALIFIED");
        assertThat(inventory.getValue().getQuantity()).isEqualByComparingTo("2.000000");
        assertThat(inventory.getValue().getTargetLocationId()).isEqualTo(TARGET_LOCATION);
        ArgumentCaptor<cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.PutawayLineDO> persisted =
                ArgumentCaptor.forClass(cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.PutawayLineDO.class);
        verify(putawayLineMapper).insert(persisted.capture());
        assertThat(persisted.getValue().getInventoryLedgerTxId()).isEqualTo(702L);
        assertThat(persisted.getValue().getInventoryMovementGroupId()).isEqualTo("move-group-01");
        assertThat(persisted.getValue().getCumulativePutawayQuantity()).isEqualByComparingTo("4.000000");
    }

    @Test
    void blocksSecondAsnOnSameScheduleWhenGlobalFulfillmentWouldOverReceive() {
        prepareNewOperation("COMPLETE_RECEIPT");
        when(asnMapper.selectForUpdate(1L, ASN_ID)).thenReturn(asn("IN_TRANSIT", 1L));
        when(asnLineMapper.selectForUpdate(1L, ASN_LINE_ID)).thenReturn(asnLine("0.000000", "0.000000", 1L, "OPEN"));
        when(scheduleFulfillmentMapper.selectForUpdate(1L, ITEM_ID, SCHEDULE_ID)).thenReturn(scheduleFulfillment("9.500000", "9.500000", 5L, "0.000000"));
        when(receiptMapper.insert(any(ReceiptDO.class))).thenReturn(1);

        assertThatThrownBy(() -> execute(partialReceiptCommand("1.000000", 1L, "PENDING_QUALITY")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("receipt exceeds scheduled quantity tolerance");
        verifyNoInteractions(inventoryReceiptApi);
    }

    @Test
    void rejectsUppercasePolicyHashesBeforeAnyAuthoritativeWrite() {
        prepareNewOperation("CREATE_ASN");
        when(asnMapper.selectByAsnNo(1L, "ASN-20260802-003")).thenReturn(null);
        ProcurementOrderView order = procurementOrderView();
        order.getItems().get(0).setValuationPolicyHash("B".repeat(64));
        when(procurementQueryApi.requireCurrent(ORDER_ID)).thenReturn(order);
        InboundCommand command = InboundCommand.builder().operation(InboundOperation.CREATE_ASN)
                .idempotencyKey("asn-invalid-valuation").correlationId(CORRELATION_ID).occurredAt(OCCURRED_AT)
                .asn(AsnDefinition.builder().asnNo("ASN-20260802-003").procurementOrderId(ORDER_ID)
                        .supplierId(SUPPLIER_ID).warehouseId(WAREHOUSE).lines(List.of(asnLineDefinition())).build())
                .build();

        assertThatThrownBy(() -> execute(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("procurement valuationPolicyHash must be a lowercase SHA-256");
        verify(asnMapper, never()).insert(any(AsnDO.class));
        verifyNoInteractions(receiptMapper, inventoryReceiptApi, p2pEvidenceIngestionApi, outboxAppender);
    }

    @Test
    void financeEvidenceFailureAbortsReceiptBeforeHistoryOutboxAndOperationCompletion() {
        prepareNewOperation("COMPLETE_RECEIPT");
        when(asnMapper.selectForUpdate(1L, ASN_ID)).thenReturn(asn("IN_TRANSIT", 1L));
        when(asnLineMapper.selectForUpdate(1L, ASN_LINE_ID)).thenReturn(asnLine("0.000000", "0.000000", 1L, "OPEN"));
        when(scheduleFulfillmentMapper.selectForUpdate(1L, ITEM_ID, SCHEDULE_ID))
                .thenReturn(scheduleFulfillment("0.000000", "0.000000", 1L, "0.000000"));
        when(receiptMapper.insert(any(ReceiptDO.class))).thenReturn(1);
        when(scheduleFulfillmentMapper.updateReceiptCas(eq(1L), eq("sched-fulfillment-01"), eq(1L),
                eq(new BigDecimal("3.000000")), eq(new BigDecimal("3.000000")), any())).thenReturn(1);
        when(asnLineMapper.updateFulfillmentCas(eq(1L), eq(ASN_LINE_ID), eq(1L),
                eq(new BigDecimal("3.000000")), eq(new BigDecimal("3.000000")),
                eq("PARTIAL_RECEIVED_PENDING_QUALITY"), any())).thenReturn(1);
        when(receiptLineMapper.insert(any(ReceiptLineDO.class))).thenReturn(1);
        when(inventoryReceiptApi.execute(any())).thenReturn(InventoryProcurementReceiptResult.builder()
                .operationId(204L).ledgerTransactionId(304L).targetBalanceId("bal-04")
                .pendingQuantity(new BigDecimal("3.000000")).build());
        when(p2pEvidenceIngestionApi.ingestReceiptLine(any(), eq(ACTOR_PRINCIPAL_ID)))
                .thenThrow(new IllegalStateException("finance evidence store unavailable"));

        assertThatThrownBy(() -> execute(partialReceiptCommand("3.000000", 1L, "PENDING_QUALITY")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("finance evidence store unavailable");

        verify(receiptLineMapper).insert(any(ReceiptLineDO.class));
        verifyNoInteractions(outboxAppender);
        verify(historyMapper, never()).insert(any(InboundStatusHistoryDO.class));
        verify(operationMapper, never()).markSucceeded(anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void financeEvidenceBindingConflictAbortsWholeReceiptTransaction() {
        prepareNewOperation("COMPLETE_RECEIPT");
        when(asnMapper.selectForUpdate(1L, ASN_ID)).thenReturn(asn("IN_TRANSIT", 1L));
        when(asnLineMapper.selectForUpdate(1L, ASN_LINE_ID)).thenReturn(asnLine("0.000000", "0.000000", 1L, "OPEN"));
        when(scheduleFulfillmentMapper.selectForUpdate(1L, ITEM_ID, SCHEDULE_ID))
                .thenReturn(scheduleFulfillment("0.000000", "0.000000", 1L, "0.000000"));
        when(receiptMapper.insert(any(ReceiptDO.class))).thenReturn(1);
        when(scheduleFulfillmentMapper.updateReceiptCas(eq(1L), eq("sched-fulfillment-01"), eq(1L),
                eq(new BigDecimal("3.000000")), eq(new BigDecimal("3.000000")), any())).thenReturn(1);
        when(asnLineMapper.updateFulfillmentCas(eq(1L), eq(ASN_LINE_ID), eq(1L),
                eq(new BigDecimal("3.000000")), eq(new BigDecimal("3.000000")),
                eq("PARTIAL_RECEIVED_PENDING_QUALITY"), any())).thenReturn(1);
        when(receiptLineMapper.insert(any(ReceiptLineDO.class))).thenReturn(1);
        when(inventoryReceiptApi.execute(any())).thenReturn(InventoryProcurementReceiptResult.builder()
                .operationId(205L).ledgerTransactionId(305L).targetBalanceId("bal-05")
                .pendingQuantity(new BigDecimal("3.000000")).build());
        when(receiptLineMapper.bindFinanceEvidence(eq(1L), anyString(), eq(901L),
                eq("finance-receipt-evidence-01"), eq(1L), any())).thenReturn(0);

        assertThatThrownBy(() -> execute(partialReceiptCommand("3.000000", 1L, "PENDING_QUALITY")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failed to bind finance receipt-line evidence");

        verify(outboxAppender, never()).append(any());
        verify(historyMapper, never()).insert(any(InboundStatusHistoryDO.class));
        verify(operationMapper, never()).markSucceeded(anyLong(), anyLong(), anyString(), anyString(), any());
    }

    private InboundCommandResult execute(InboundCommand command) {
        return service.execute(command, ACTOR_PRINCIPAL_ID);
    }

    private void prepareNewOperation(String type) {
        AtomicReference<String> attemptToken = new AtomicReference<>();
        when(operationMapper.insertOrResolve(eq(1L), anyString(), nullable(String.class), eq(type),
                anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    attemptToken.set(invocation.getArgument(5));
                    return 1;
                });
        when(operationMapper.selectLastInsertId()).thenReturn(101L);
        when(operationMapper.selectForUpdate(101L, 1L)).thenAnswer(ignored -> new InboundOperationDO()
                .setOperationId(101L).setTenantId(1L).setAttemptToken(attemptToken.get()).setStatus(0));
        when(operationMapper.markSucceeded(eq(101L), eq(1L), anyString(), anyString(), any())).thenReturn(1);
    }

    private ProcurementOrderView procurementOrderView() {
        return ProcurementOrderView.builder()
                .orderId(ORDER_ID).orderCode("PO-01").supplierId(SUPPLIER_ID)
                .currencyCode("CNY").roundingPolicyCode("HALF_UP").status("RELEASED").version(4L)
                .items(List.of(ProcurementOrderView.PurchaseOrderItemView.builder()
                        .itemId(ITEM_ID).lineNumber(10).canonicalSkuId(SKU)
                        .orderedQuantity(new BigDecimal("10.000000")).uomCode("PIECE")
                        .unitNetPriceMinor(new BigDecimal("1200.000000"))
                        .valuationPolicyId("STANDARD_V1").valuationPolicyVersion("valuation-v1")
                        .valuationPolicyHash("b".repeat(64))
                        .schedules(List.of(ProcurementOrderView.PurchaseOrderDeliveryScheduleView.builder()
                                .scheduleId(SCHEDULE_ID).scheduleNumber(1)
                                .canonicalWarehouseId(WAREHOUSE)
                                .scheduledQuantity(new BigDecimal("10.000000")).build()))
                        .build()))
                .build();
    }

    private AsnDO asn(String status, Long version) {
        return new AsnDO().setAsnId(ASN_ID).setAsnNo("ASN-01").setProcurementOrderId(ORDER_ID)
                .setSupplierId(SUPPLIER_ID).setWarehouseId(WAREHOUSE).setStatus(status).setVersion(version);
    }

    private AsnLineDO asnLine(String received, String pending, Long fulfillmentVersion, String status) {
        return new AsnLineDO().setAsnLineId(ASN_LINE_ID).setAsnId(ASN_ID).setLineNo(10)
                .setProcurementOrderId(ORDER_ID).setProcurementOrderItemId(ITEM_ID)
                .setDeliveryScheduleId(SCHEDULE_ID).setPoReleaseVersion(4L).setSupplierId(SUPPLIER_ID)
                .setWarehouseId(WAREHOUSE).setReceiptLocationId(LOCATION).setCanonicalSkuId(SKU)
                .setOwnerType("MERCHANT").setOwnerId(OWNER).setBaseUomCode("PIECE")
                .setScheduledQuantity(new BigDecimal("10.000000"))
                .setAllowedOverReceiptQuantity(new BigDecimal("0.000000"))
                .setReceivedQuantity(new BigDecimal(received)).setPendingQualityQuantity(new BigDecimal(pending))
                .setFulfillmentVersion(fulfillmentVersion).setTolerancePolicyVersion("tol-v1")
                .setValuationPolicyId("STANDARD_V1").setValuationPolicyVersion("valuation-v1")
                .setValuationPolicyHash("b".repeat(64)).setUnitCostAmountMinor(1200L)
                .setCurrencyCode("CNY").setRoundingPolicyCode("HALF_UP")
                .setTolerancePolicyHash("a".repeat(64)).setStatus(status);
    }

    private ScheduleFulfillmentDO scheduleFulfillment(String received, String pending, Long version, String allowed) {
        return new ScheduleFulfillmentDO().setScheduleFulfillmentId("sched-fulfillment-01")
                .setTenantId(1L).setProcurementOrderId(ORDER_ID).setProcurementOrderItemId(ITEM_ID)
                .setDeliveryScheduleId(SCHEDULE_ID).setOrderedQuantity(new BigDecimal("10.000000"))
                .setCancelledQuantity(ZERO).setAllowedOverReceiptQuantity(new BigDecimal(allowed))
                .setReceivedQuantity(new BigDecimal(received)).setPendingQualityQuantity(new BigDecimal(pending))
                .setAcceptedQuantity(ZERO).setRejectedQuantity(ZERO).setReturnedQuantity(ZERO)
                .setQuarantinedQuantity(ZERO)
                .setTolerancePolicyVersion("tol-v1").setTolerancePolicyHash("a".repeat(64)).setVersion(version);
    }

    private AsnLineDefinition asnLineDefinition() {
        return AsnLineDefinition.builder().asnLineId(ASN_LINE_ID).lineNo(10)
                .procurementOrderId(ORDER_ID).procurementOrderItemId(ITEM_ID)
                .deliveryScheduleId(SCHEDULE_ID).poReleaseVersion(4L)
                .supplierId(SUPPLIER_ID).warehouseId(WAREHOUSE).receiptLocationId(LOCATION)
                .canonicalSkuId(SKU).ownerType("MERCHANT").ownerId(OWNER).baseUomCode("PIECE")
                .scheduledQuantity(new BigDecimal("10.000000")).allowedOverReceiptQuantity(new BigDecimal("0.000000"))
                .tolerancePolicyVersion("tol-v1").tolerancePolicyHash("a".repeat(64)).build();
    }

    private ReceiptLineDO receiptLine(String receiptLineId, String received, String pending,
                                      String accepted, String cumulativePutaway, Long version) {
        return new ReceiptLineDO().setReceiptLineId(receiptLineId).setReceiptId("receipt-01")
                .setProcurementOrderId(ORDER_ID).setProcurementOrderItemId(ITEM_ID)
                .setDeliveryScheduleId(SCHEDULE_ID).setWarehouseId(WAREHOUSE).setReceiptLocationId(LOCATION)
                .setCanonicalSkuId(SKU).setOwnerType("MERCHANT").setOwnerId(OWNER).setBaseUomCode("PIECE")
                .setLotId("lot-01").setReceivedQuantity(new BigDecimal(received))
                .setPendingQualityQuantity(new BigDecimal(pending)).setAcceptedQuantity(new BigDecimal(accepted))
                .setRejectedQuantity(ZERO).setQuarantinedQuantity(ZERO)
                .setCumulativePutawayQuantity(new BigDecimal(cumulativePutaway)).setVersion(version);
    }

    private InboundCommand partialReceiptCommand(String quantity, long expectedFulfillmentVersion, String qualityStatus) {
        return InboundCommand.builder().operation(InboundOperation.COMPLETE_RECEIPT)
                .idempotencyKey("receipt-key-" + quantity).correlationId(CORRELATION_ID).occurredAt(OCCURRED_AT)
                .receipt(ReceiptDefinition.builder()
                        .receiptId("receipt-" + quantity).receiptNo("RCV-" + quantity.replace(".", ""))
                        .asnId(ASN_ID).procurementOrderId(ORDER_ID).supplierId(SUPPLIER_ID).warehouseId(WAREHOUSE)
                        .lines(List.of(ReceiptLineDefinition.builder()
                                .receiptLineId("receipt-line-" + quantity).lineNo(10).asnLineId(ASN_LINE_ID)
                                .procurementOrderId(ORDER_ID).procurementOrderItemId(ITEM_ID)
                                .deliveryScheduleId(SCHEDULE_ID).poReleaseVersion(4L)
                                .expectedFulfillmentVersion(expectedFulfillmentVersion)
                                .supplierId(SUPPLIER_ID).warehouseId(WAREHOUSE).receiptLocationId(LOCATION)
                                .canonicalSkuId(SKU).ownerType("MERCHANT").ownerId(OWNER).baseUomCode("PIECE")
                                .receivedQuantity(new BigDecimal(quantity)).lotId("lot-01").qualityStatus(qualityStatus)
                                .qualityInspectionId("inspection-pending-01")
                                .build()))
                        .build())
                .build();
    }
}
