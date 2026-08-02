package cn.iocoder.yudao.module.cloudmold.warehouse.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockTransferApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockTransferCommand;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockTransferOperation;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockTransferResult;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockTransferCommand;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockTransferOperation;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockTransferResult;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseReferenceValidationApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferExecutionLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferOperationDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferOrderDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferOrderLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferRequestDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.StockTransferStoreMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StockTransferCommandServiceImplTest {

    private static final BigDecimal ZERO = new BigDecimal("0.000000");

    private final StockTransferStoreMapper mapper = mock(StockTransferStoreMapper.class);
    private final WarehouseReferenceValidationApi warehouseApi = mock(WarehouseReferenceValidationApi.class);
    private final CatalogSkuValidationApi skuApi = mock(CatalogSkuValidationApi.class);
    private final InventoryStockTransferApi inventoryApi = mock(InventoryStockTransferApi.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final StockTransferCommandServiceImpl service = new StockTransferCommandServiceImpl(
            mapper, warehouseApi, skuApi, inventoryApi, outboxAppender);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event-1", "a".repeat(64), false));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createsApprovedTransferRequestAndPrepareOrder() {
        StockTransferCommand command = createCommand();
        prepareNewOperation(command, 11L);
        when(mapper.selectRequestBySourceBusiness(1L, "REPLENISHMENT", "recommendation-01")).thenReturn(null);
        when(mapper.insertRequest(any())).thenReturn(1);
        when(mapper.insertRequestLine(any())).thenReturn(1);
        when(mapper.insertOrder(any())).thenReturn(1);
        when(mapper.insertOrderLine(any())).thenReturn(1);
        when(mapper.insertStatusHistory(any())).thenReturn(1);
        when(mapper.markOperationSucceeded(eq(11L), eq(1L), anyString(), anyString(), any())).thenReturn(1);

        StockTransferResult result = service.execute(command);

        assertThat(result.getRequestStatus()).isEqualTo("APPROVED");
        assertThat(result.getOrderStatus()).isEqualTo("PREPARE");
        verify(warehouseApi).requireActiveWarehouse("warehouse-source");
        verify(warehouseApi).requireActiveWarehouse("warehouse-target");
        verify(skuApi).requireActiveSku("sku-01");
        verify(skuApi).requireActiveSku("sku-02");
        verifyNoInteractions(inventoryApi);
        verify(outboxAppender, times(2)).append(any(AppendDomainEventCommand.class));
    }

    @Test
    void completesOutboundBatchUsingInventoryDispatchAndWritesLedgerReferences() {
        StockTransferCommand command = outboundCommand();
        prepareNewOperation(command, 21L);
        when(mapper.selectOrderForUpdate(1L, "order-01")).thenReturn(order("PREPARE", 1L));
        when(mapper.selectRequestForUpdate(1L, "request-01")).thenReturn(request("APPROVED", 1L));
        when(mapper.selectOrderLineForUpdate(1L, "order-01", 10)).thenReturn(orderLine(
                "order-01:line:10", 10, "sku-01", "PIECE",
                new BigDecimal("8.000000"), ZERO, ZERO, "PREPARE", 1L, null));
        when(mapper.selectOrderLineForUpdate(1L, "order-01", 20)).thenReturn(orderLine(
                "order-01:line:20", 20, "sku-02", "PIECE",
                new BigDecimal("4.250000"), ZERO, ZERO, "PREPARE", 1L, null));
        when(mapper.insertExecutionBatch(any())).thenReturn(1);
        when(mapper.insertExecutionLine(any())).thenReturn(1);
        when(mapper.updateOrderLineExecutionCas(eq(1L), eq("order-01:line:10"), eq(1L), eq(2L), anyString(),
                eq(new BigDecimal("3.000000")), eq(ZERO), eq("PARTIAL_OUTBOUND"), any())).thenReturn(1);
        when(mapper.updateOrderLineExecutionCas(eq(1L), eq("order-01:line:20"), eq(1L), eq(2L), anyString(),
                eq(new BigDecimal("4.250000")), eq(ZERO), eq("FULL_OUTBOUND"), any())).thenReturn(1);
        when(mapper.selectOrderLines(1L, "order-01")).thenReturn(List.of(
                orderLine("order-01:line:10", 10, "sku-01", "PIECE",
                        new BigDecimal("8.000000"), new BigDecimal("3.000000"), ZERO,
                        "PARTIAL_OUTBOUND", 2L, "mg-10"),
                orderLine("order-01:line:20", 20, "sku-02", "PIECE",
                        new BigDecimal("4.250000"), new BigDecimal("4.250000"), ZERO,
                        "FULL_OUTBOUND", 2L, "mg-20")));
        when(mapper.updateOrderStatusCas(eq(1L), eq("order-01"), eq(1L), eq(2L), eq("RELEASED"), any()))
                .thenReturn(1);
        when(mapper.insertStatusHistory(any())).thenReturn(1);
        when(mapper.markOperationSucceeded(eq(21L), eq(1L), anyString(), anyString(), any())).thenReturn(1);
        AtomicReference<Long> dispatchLedger = new AtomicReference<>(601L);
        when(inventoryApi.execute(any(InventoryStockTransferCommand.class))).thenAnswer(invocation -> {
            InventoryStockTransferCommand dispatched = invocation.getArgument(0);
            long ledger = dispatchLedger.getAndSet(dispatchLedger.get() + 1);
            return dispatchResult(dispatched.getMovementGroupId(),
                    dispatched.getQuantity().toPlainString(), "0.000000",
                    dispatched.getQuantity().toPlainString(), ledger);
        });

        StockTransferResult result = service.execute(command);

        assertThat(result.getBatchType()).isEqualTo("OUTBOUND");
        assertThat(result.getOrderStatus()).isEqualTo("RELEASED");
        ArgumentCaptor<InventoryStockTransferCommand> inventoryCaptor =
                ArgumentCaptor.forClass(InventoryStockTransferCommand.class);
        verify(inventoryApi, times(2)).execute(inventoryCaptor.capture());
        assertThat(inventoryCaptor.getAllValues()).extracting(InventoryStockTransferCommand::getOperation)
                .containsExactly(InventoryStockTransferOperation.DISPATCH, InventoryStockTransferOperation.DISPATCH);
        ArgumentCaptor<StockTransferExecutionLineDO> executionCaptor =
                ArgumentCaptor.forClass(StockTransferExecutionLineDO.class);
        verify(mapper, times(2)).insertExecutionLine(executionCaptor.capture());
        assertThat(executionCaptor.getAllValues()).extracting(StockTransferExecutionLineDO::getDispatchLedgerTransactionId)
                .containsExactly(601L, 602L);
        assertThat(executionCaptor.getAllValues()).extracting(StockTransferExecutionLineDO::getMovementGroupId)
                .hasSize(2).allSatisfy(value -> assertThat(value).isNotBlank()).doesNotHaveDuplicates();
    }

    @Test
    void completesReceiptBatchUsingInventoryReceiveAndClosesRequest() {
        StockTransferCommand command = receiptCommand();
        prepareNewOperation(command, 31L);
        when(mapper.selectOrderForUpdate(1L, "order-01")).thenReturn(order("IN_TRANSIT", 2L));
        when(mapper.selectRequestForUpdate(1L, "request-01")).thenReturn(request("APPROVED", 1L));
        when(mapper.selectExecutionLineForUpdate(1L, "exec-out-10")).thenReturn(outboundExecution(
                "exec-out-10", "order-01:line:10", 10, "sku-01",
                "mg-10", new BigDecimal("8.000000"), new BigDecimal("3.000000"),
                "PARTIAL_RECEIVED", "source-loc-01", "target-loc-01"));
        when(mapper.selectExecutionLineForUpdate(1L, "exec-out-20")).thenReturn(outboundExecution(
                "exec-out-20", "order-01:line:20", 20, "sku-02",
                "mg-20", new BigDecimal("4.250000"), ZERO,
                "IN_TRANSIT", "source-loc-02", "target-loc-02"));
        when(mapper.selectOrderLineForUpdate(1L, "order-01", 10)).thenReturn(orderLine(
                "order-01:line:10", 10, "sku-01", "PIECE",
                new BigDecimal("8.000000"), new BigDecimal("8.000000"), new BigDecimal("3.000000"),
                "PARTIAL_RECEIVED", 3L, "mg-10"));
        when(mapper.selectOrderLineForUpdate(1L, "order-01", 20)).thenReturn(orderLine(
                "order-01:line:20", 20, "sku-02", "PIECE",
                new BigDecimal("4.250000"), new BigDecimal("4.250000"), ZERO,
                "FULL_OUTBOUND", 2L, "mg-20"));
        when(mapper.insertExecutionBatch(any())).thenReturn(1);
        when(mapper.insertExecutionLine(any())).thenReturn(1);
        when(mapper.updateExecutionLineReceiptCas(eq(1L), eq("exec-out-10"), eq(1L), eq(2L),
                eq(new BigDecimal("8.000000")), eq(new BigDecimal("8.000000")), eq(ZERO), eq(701L),
                eq("FULL_RECEIVED"), any())).thenReturn(1);
        when(mapper.updateExecutionLineReceiptCas(eq(1L), eq("exec-out-20"), eq(1L), eq(2L),
                eq(new BigDecimal("4.250000")), eq(new BigDecimal("4.250000")), eq(ZERO), eq(702L),
                eq("FULL_RECEIVED"), any())).thenReturn(1);
        when(mapper.updateOrderLineExecutionCas(eq(1L), eq("order-01:line:10"), eq(3L), eq(4L), eq("mg-10"),
                eq(new BigDecimal("8.000000")), eq(new BigDecimal("8.000000")), eq("FULL_RECEIVED"), any())).thenReturn(1);
        when(mapper.updateOrderLineExecutionCas(eq(1L), eq("order-01:line:20"), eq(2L), eq(3L), eq("mg-20"),
                eq(new BigDecimal("4.250000")), eq(new BigDecimal("4.250000")), eq("FULL_RECEIVED"), any())).thenReturn(1);
        when(mapper.selectOrderLines(1L, "order-01")).thenReturn(List.of(
                orderLine("order-01:line:10", 10, "sku-01", "PIECE",
                        new BigDecimal("8.000000"), new BigDecimal("8.000000"), new BigDecimal("8.000000"),
                        "FULL_RECEIVED", 4L, "mg-10"),
                orderLine("order-01:line:20", 20, "sku-02", "PIECE",
                        new BigDecimal("4.250000"), new BigDecimal("4.250000"), new BigDecimal("4.250000"),
                        "FULL_RECEIVED", 3L, "mg-20")));
        when(mapper.updateOrderStatusCas(eq(1L), eq("order-01"), eq(2L), eq(3L), eq("COMPLETED"), any()))
                .thenReturn(1);
        when(mapper.updateRequestStatusCas(eq(1L), eq("request-01"), eq(1L), eq(2L), eq("COMPLETED"), any()))
                .thenReturn(1);
        when(mapper.insertStatusHistory(any())).thenReturn(1);
        when(mapper.markOperationSucceeded(eq(31L), eq(1L), anyString(), anyString(), any())).thenReturn(1);
        when(inventoryApi.execute(any(InventoryStockTransferCommand.class)))
                .thenReturn(receiveResult("mg-10", "8.000000", "8.000000", "0.000000", 701L))
                .thenReturn(receiveResult("mg-20", "4.250000", "4.250000", "0.000000", 702L));

        StockTransferResult result = service.execute(command);

        assertThat(result.getBatchType()).isEqualTo("RECEIPT");
        assertThat(result.getOrderStatus()).isEqualTo("COMPLETED");
        assertThat(result.getRequestStatus()).isEqualTo("COMPLETED");
        ArgumentCaptor<InventoryStockTransferCommand> inventoryCaptor =
                ArgumentCaptor.forClass(InventoryStockTransferCommand.class);
        verify(inventoryApi, times(2)).execute(inventoryCaptor.capture());
        assertThat(inventoryCaptor.getAllValues()).extracting(InventoryStockTransferCommand::getOperation)
                .containsExactly(InventoryStockTransferOperation.RECEIVE, InventoryStockTransferOperation.RECEIVE);
        ArgumentCaptor<StockTransferExecutionLineDO> executionCaptor =
                ArgumentCaptor.forClass(StockTransferExecutionLineDO.class);
        verify(mapper, times(2)).insertExecutionLine(executionCaptor.capture());
        assertThat(executionCaptor.getAllValues()).extracting(StockTransferExecutionLineDO::getReceiveLedgerTransactionId)
                .containsExactly(701L, 702L);
    }

    @Test
    void replaysStoredOutboundResultBeforeInventoryCall() {
        StockTransferCommand command = outboundCommand();
        StockTransferResult stored = StockTransferResult.builder()
                .requestId("request-01").requestCode("STRRQ-0001").requestStatus("APPROVED")
                .orderId("order-01").orderCode("STRORD-0001").orderStatus("RELEASED")
                .batchId("batch-out-01").batchNo("STROUT-0001").batchType("OUTBOUND")
                .currentStageCode("TRANSFER_OUTBOUND").currentStageLabel("等待调拨出库")
                .aggregateVersion(2L).processedLineCount(2).duplicate(false).build();
        when(mapper.selectLastInsertId()).thenReturn(41L);
        when(mapper.selectOperationForUpdate(41L, 1L)).thenReturn(new StockTransferOperationDO()
                .setOperationId(41L).setTenantId(1L).setAttemptToken("first-attempt")
                .setRequestHash(StockTransferCommandServiceImpl.fingerprint(1L, command))
                .setStatus(StockTransferCommandServiceImpl.OPERATION_SUCCEEDED)
                .setResultJson(JsonUtils.toJsonString(stored)));

        StockTransferResult replay = service.execute(command);

        assertThat(replay.isDuplicate()).isTrue();
        assertThat(replay.getBatchId()).isEqualTo("batch-out-01");
        verifyNoInteractions(inventoryApi, warehouseApi, skuApi, outboxAppender);
    }

    @Test
    void inventoryFailureAbortsOutboundBeforeWarehouseExecutionFactsCommit() {
        StockTransferCommand command = outboundCommand();
        prepareNewOperation(command, 51L);
        when(mapper.selectOrderForUpdate(1L, "order-01")).thenReturn(order("PREPARE", 1L));
        when(mapper.selectRequestForUpdate(1L, "request-01")).thenReturn(request("APPROVED", 1L));
        when(mapper.selectOrderLineForUpdate(1L, "order-01", 10)).thenReturn(orderLine(
                "order-01:line:10", 10, "sku-01", "PIECE",
                new BigDecimal("8.000000"), ZERO, ZERO, "PREPARE", 1L, null));
        when(mapper.insertExecutionBatch(any())).thenReturn(1);
        when(inventoryApi.execute(any())).thenThrow(new IllegalArgumentException("inventory dispatch failed"));

        assertThatThrownBy(() -> service.execute(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("inventory dispatch failed");

        verify(mapper, never()).insertExecutionLine(any());
        verify(mapper, never()).updateOrderLineExecutionCas(anyLong(), anyString(), anyLong(), anyLong(), anyString(),
                any(), any(), anyString(), any());
        verify(mapper, never()).markOperationSucceeded(anyLong(), anyLong(), anyString(), anyString(), any());
        verifyNoInteractions(outboxAppender);
    }

    private void prepareNewOperation(StockTransferCommand command, long operationId) {
        AtomicReference<String> attemptToken = new AtomicReference<>();
        AtomicReference<String> requestHash = new AtomicReference<>();
        doAnswer(invocation -> {
            requestHash.set(invocation.getArgument(4));
            attemptToken.set(invocation.getArgument(5));
            return 1;
        }).when(mapper).insertOrResolveOperation(eq(1L), eq(command.getIdempotencyKey()),
                eq(command.getSourceEventId()), eq(command.getOperation().name()), anyString(), anyString(), any());
        when(mapper.selectLastInsertId()).thenReturn(operationId);
        doAnswer(invocation -> new StockTransferOperationDO()
                .setOperationId(operationId).setTenantId(1L).setAttemptToken(attemptToken.get())
                .setRequestHash(requestHash.get()).setStatus(0))
                .when(mapper).selectOperationForUpdate(operationId, 1L);
    }

    private static StockTransferCommand createCommand() {
        return StockTransferCommand.builder()
                .operation(StockTransferOperation.CREATE_APPROVED_REQUEST)
                .idempotencyKey("transfer-request-key-01")
                .correlationId("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .occurredAt(Instant.parse("2026-08-02T02:00:00Z"))
                .requestCode("STRRQ-0001")
                .orderCode("STRORD-0001")
                .sourceBusinessType("REPLENISHMENT")
                .sourceBusinessRef("recommendation-01")
                .ownerType("MERCHANT")
                .ownerId("merchant-01")
                .sourceWarehouseId("warehouse-source")
                .targetWarehouseId("warehouse-target")
                .reasonCode("REPLENISHMENT_APPROVED")
                .remark("replenishment transfer")
                .lines(List.of(
                        StockTransferCommand.LineDefinition.builder()
                                .lineNumber(10).canonicalSkuId("sku-01")
                                .requestedQuantity(new BigDecimal("8.000000")).uomCode("PIECE")
                                .remark("line-01").build(),
                        StockTransferCommand.LineDefinition.builder()
                                .lineNumber(20).canonicalSkuId("sku-02")
                                .requestedQuantity(new BigDecimal("4.250000")).uomCode("PIECE")
                                .remark("line-02").build()))
                .build();
    }

    private static StockTransferCommand outboundCommand() {
        return StockTransferCommand.builder()
                .operation(StockTransferOperation.COMPLETE_OUTBOUND_BATCH)
                .idempotencyKey("transfer-outbound-key-01")
                .correlationId("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
                .occurredAt(Instant.parse("2026-08-02T03:00:00Z"))
                .orderId("order-01")
                .outboundBatch(StockTransferCommand.OutboundBatchDefinition.builder()
                        .batchId("batch-out-01").batchNo("STROUT-0001").remark("outbound batch")
                        .lines(List.of(
                                StockTransferCommand.OutboundLineDefinition.builder()
                                        .executionLineId("exec-out-10").lineNumber(10)
                                        .quantity(new BigDecimal("3.000000")).lotId("lot-01")
                                        .sourceLocationId("source-loc-01").sourceStockStatus("SELLABLE")
                                        .sourceQualityStatus("QUALIFIED").targetLocationId("target-loc-01")
                                        .targetStockStatus("SELLABLE").targetQualityStatus("QUALIFIED")
                                        .remark("line-10").build(),
                                StockTransferCommand.OutboundLineDefinition.builder()
                                        .executionLineId("exec-out-20").lineNumber(20)
                                        .quantity(new BigDecimal("4.250000")).lotId("lot-02")
                                        .sourceLocationId("source-loc-02").sourceStockStatus("SELLABLE")
                                        .sourceQualityStatus("QUALIFIED").targetLocationId("target-loc-02")
                                        .targetStockStatus("SELLABLE").targetQualityStatus("QUALIFIED")
                                        .remark("line-20").build()))
                        .build())
                .build();
    }

    private static StockTransferCommand receiptCommand() {
        return StockTransferCommand.builder()
                .operation(StockTransferOperation.COMPLETE_RECEIPT_BATCH)
                .idempotencyKey("transfer-receipt-key-01")
                .correlationId("cccccccc-cccc-4ccc-8ccc-cccccccccccc")
                .occurredAt(Instant.parse("2026-08-02T04:00:00Z"))
                .orderId("order-01")
                .receiptBatch(StockTransferCommand.ReceiptBatchDefinition.builder()
                        .batchId("batch-rcv-01").batchNo("STRRCV-0001").remark("receipt batch")
                        .lines(List.of(
                                StockTransferCommand.ReceiptLineDefinition.builder()
                                        .executionLineId("exec-rcv-10").outboundExecutionLineId("exec-out-10")
                                        .quantity(new BigDecimal("5.000000")).remark("receive-10").build(),
                                StockTransferCommand.ReceiptLineDefinition.builder()
                                        .executionLineId("exec-rcv-20").outboundExecutionLineId("exec-out-20")
                                        .quantity(new BigDecimal("4.250000")).remark("receive-20").build()))
                        .build())
                .build();
    }

    private static StockTransferRequestDO request(String status, long version) {
        return new StockTransferRequestDO()
                .setRequestId("request-01").setRequestCode("STRRQ-0001")
                .setSourceBusinessType("REPLENISHMENT").setSourceBusinessRef("recommendation-01")
                .setOwnerType("MERCHANT").setOwnerId("merchant-01")
                .setSourceWarehouseId("warehouse-source").setTargetWarehouseId("warehouse-target")
                .setReasonCode("REPLENISHMENT_APPROVED").setRemark("replenishment transfer")
                .setStatus(status).setVersion(version);
    }

    private static StockTransferOrderDO order(String status, long version) {
        return new StockTransferOrderDO()
                .setOrderId("order-01").setOrderCode("STRORD-0001").setRequestId("request-01")
                .setOwnerType("MERCHANT").setOwnerId("merchant-01")
                .setSourceWarehouseId("warehouse-source").setTargetWarehouseId("warehouse-target")
                .setStatus(status).setVersion(version);
    }

    private static StockTransferOrderLineDO orderLine(String lineId, int lineNumber, String sku, String uom,
                                                      BigDecimal requested, BigDecimal outbound, BigDecimal received,
                                                      String status, long version, String movementGroupId) {
        return new StockTransferOrderLineDO()
                .setLineId(lineId).setOrderId("order-01").setLineNumber(lineNumber).setCanonicalSkuId(sku)
                .setMovementGroupId(movementGroupId).setRequestedQuantity(requested)
                .setOutboundQuantity(outbound).setReceivedQuantity(received)
                .setUomCode(uom).setStatus(status).setVersion(version);
    }

    private static StockTransferExecutionLineDO outboundExecution(String executionLineId, String orderLineId,
                                                                  int lineNumber, String sku, String movementGroupId,
                                                                  BigDecimal executed, BigDecimal received,
                                                                  String status, String sourceLocationId,
                                                                  String targetLocationId) {
        return new StockTransferExecutionLineDO()
                .setExecutionLineId(executionLineId).setOrderId("order-01").setOrderLineId(orderLineId)
                .setLineNumber(lineNumber).setCanonicalSkuId(sku).setMovementGroupId(movementGroupId)
                .setExecutedQuantity(executed).setReceivedQuantity(received)
                .setStatus(status).setVersion(1L).setLotId("lot-" + lineNumber)
                .setSourceLocationId(sourceLocationId).setSourceStockStatus("SELLABLE")
                .setSourceQualityStatus("QUALIFIED")
                .setTargetLocationId(targetLocationId).setTargetStockStatus("SELLABLE")
                .setTargetQualityStatus("QUALIFIED");
    }

    private static InventoryStockTransferResult dispatchResult(String movementGroupId, String dispatched,
                                                               String received, String outstanding,
                                                               long ledgerTransactionId) {
        return InventoryStockTransferResult.builder()
                .movementGroupId(movementGroupId)
                .ledgerTransactionId(ledgerTransactionId)
                .cumulativeDispatchedQuantity(new BigDecimal(dispatched))
                .cumulativeReceivedQuantity(new BigDecimal(received))
                .outstandingQuantity(new BigDecimal(outstanding))
                .duplicate(false)
                .build();
    }

    private static InventoryStockTransferResult receiveResult(String movementGroupId, String dispatched,
                                                              String received, String outstanding,
                                                              long ledgerTransactionId) {
        return InventoryStockTransferResult.builder()
                .movementGroupId(movementGroupId)
                .ledgerTransactionId(ledgerTransactionId)
                .cumulativeDispatchedQuantity(new BigDecimal(dispatched))
                .cumulativeReceivedQuantity(new BigDecimal(received))
                .outstandingQuantity(new BigDecimal(outstanding))
                .duplicate(false)
                .build();
    }
}
