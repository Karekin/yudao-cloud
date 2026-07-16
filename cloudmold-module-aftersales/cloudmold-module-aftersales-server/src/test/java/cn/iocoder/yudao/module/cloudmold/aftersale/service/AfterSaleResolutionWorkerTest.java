package cn.iocoder.yudao.module.cloudmold.aftersale.service;

import cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject.AfterSaleResolutionSagaDO;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.mysql.AfterSaleResolutionSagaMapper;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.ReturnFulfillmentQueryApi;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.ReturnFulfillmentView;
import cn.iocoder.yudao.module.cloudmold.inventory.api.*;
import cn.iocoder.yudao.module.cloudmold.order.api.*;
import cn.iocoder.yudao.module.cloudmold.payment.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AfterSaleResolutionWorkerTest {
    private final AfterSaleResolutionSagaMapper sagaMapper = mock(AfterSaleResolutionSagaMapper.class);
    private final ReturnFulfillmentQueryApi returnQueryApi = mock(ReturnFulfillmentQueryApi.class);
    private final InventoryCommandApi inventoryApi = mock(InventoryCommandApi.class);
    private final PaymentCommandApi paymentApi = mock(PaymentCommandApi.class);
    private final OrderCommandApi orderApi = mock(OrderCommandApi.class);
    private final OrderAfterSaleSettlementApi orderSettlementApi = mock(OrderAfterSaleSettlementApi.class);
    private final AfterSaleBenefitReversalService benefitReversalService = mock(AfterSaleBenefitReversalService.class);
    private final AfterSaleResolutionCheckpointService checkpoint = mock(AfterSaleResolutionCheckpointService.class);
    private final AfterSaleResolutionWorker worker = new AfterSaleResolutionWorker(sagaMapper, returnQueryApi, inventoryApi,
            paymentApi, orderApi, orderSettlementApi, benefitReversalService, checkpoint);
    private AfterSaleResolutionSagaDO saga;
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 15, 1, 0);

    @BeforeEach
    void setUp() {
        saga = saga();
        when(sagaMapper.selectTenant(1L, "saga-1")).thenAnswer(ignored -> saga);
        when(returnQueryApi.requireInspectionAccepted("after-sale-1", "return-1"))
                .thenReturn(ReturnFulfillmentView.builder().currentStatus("INSPECTION_ACCEPTED")
                        .qualityStatus("QUALIFIED").build());
        when(inventoryApi.execute(any())).thenReturn(InventoryCommandResult.builder()
                .operationId(101L).ledgerTransactionId(102L).duplicate(false).build());
        when(paymentApi.execute(any())).thenReturn(PaymentCommandResult.builder()
                .operationId(201L).transactionId(202L).currentStatus("REFUNDED")
                .capturedAmountMinor(39800L).refundedAmountMinor(39800L)
                .transactionAmountMinor(39800L).remainingRefundableAmountMinor(0L)
                .currencyCode("CNY").build());
        when(orderSettlementApi.record(any())).thenReturn(OrderAfterSaleSettlementResult.builder()
                .settlementEffectId("settlement-effect-1").orderId("order-1")
                .orderItemId("order-item-1").orderSettlementVersion(1L)
                .itemSettlementVersion(1L).orderVersion(5L).fullReturn(true).duplicate(false).build());
        when(orderApi.execute(any())).thenAnswer(invocation -> {
            OrderCommand command = invocation.getArgument(0);
            return OrderCommandResult.builder().operationId(command.getOperation() == OrderOperation.CONFIRM_REFUND
                            ? 301L : 401L).currentStatus(command.getOperation() == OrderOperation.CONFIRM_REFUND
                            ? "REFUNDED" : "RETURNED").aggregateVersion(command.getOperation()
                            == OrderOperation.CONFIRM_REFUND ? 6L : 7L).build();
        });
        doAnswer(invocation -> {
            InventoryCommandResult result = invocation.getArgument(3);
            saga.setInventoryOperationId(result.getOperationId())
                    .setInventoryLedgerTransactionId(result.getLedgerTransactionId());
            return null;
        }).when(checkpoint).markInventoryReturned(anyLong(), anyString(), anyString(), any(), any());
        doAnswer(invocation -> {
            AfterSaleBenefitReversalResult result = invocation.getArgument(3);
            saga.setBenefitReversalStatus("RECORDED").setBenefitReversalBatchId(result.batchId())
                    .setBenefitReversalAmountMinor(result.amountMinor());
            return null;
        }).when(checkpoint).markBenefitsReversed(anyLong(), anyString(), anyString(), any(), any());
        doAnswer(invocation -> {
            PaymentCommandResult result = invocation.getArgument(3);
            saga.setPaymentRefundTransactionId(result.getTransactionId());
            return null;
        }).when(checkpoint).markPaymentRefunded(anyLong(), anyString(), anyString(), any(), any());
        doAnswer(invocation -> {
            OrderAfterSaleSettlementResult result = invocation.getArgument(3);
            saga.setOrderSettlementEffectId(result.getSettlementEffectId())
                    .setOrderSettlementVersion(result.getOrderSettlementVersion())
                    .setOrderReturnFull(result.getFullReturn());
            return null;
        }).when(checkpoint).markOrderSettled(anyLong(), anyString(), anyString(), any(), any());
        doAnswer(invocation -> {
            OrderCommandResult result = invocation.getArgument(3);
            saga.setOrderRefundOperationId(result.getOperationId()).setOrderVersion(result.getAggregateVersion());
            return null;
        }).when(checkpoint).markOrderRefunded(anyLong(), anyString(), anyString(), any(), any());
        doAnswer(invocation -> {
            OrderCommandResult result = invocation.getArgument(3);
            saga.setOrderReturnOperationId(result.getOperationId()).setOrderVersion(result.getAggregateVersion());
            return null;
        }).when(checkpoint).markOrderReturned(anyLong(), anyString(), anyString(), any(), any());
    }

    @Test
    void shouldRunParticipantsInStrictOrderWithExactBusinessIdentity() {
        worker.process(1L, "saga-1", "worker-1", now);

        verify(inventoryApi).execute(argThat(command -> command.getOperation() == InventoryOperation.RETURN
                && command.getBusinessType().equals("AFTER_SALE_RETURN")
                && command.getBusinessId().equals("after-sale-1")
                && command.getBusinessItemId().equals("after-sale-item-1")
                && command.getIdempotencyKey().equals("after-sale-saga:saga-1:inventory-return")));
        verify(paymentApi).execute(argThat(command -> command.getOperation() == PaymentOperation.REFUND
                && command.getAmountMinor() == 39800L
                && command.getExpectedVersion() == null
                && command.getIdempotencyKey().equals("after-sale-saga:saga-1:payment-refund")));
        verify(orderSettlementApi).record(argThat(command -> command.getAfterSaleId().equals("after-sale-1")
                && command.getQuantity().compareTo(new BigDecimal("2")) == 0
                && command.getInventoryLedgerTransactionId() == 102L
                && command.getPaymentRefundTransactionId() == 202L));
        verify(orderApi).execute(argThat(command -> command.getOperation() == OrderOperation.CONFIRM_REFUND
                && command.getExpectedVersion() == 5L
                && command.getIdempotencyKey().equals("after-sale-saga:saga-1:order-confirm-refund")));
        verify(orderApi).execute(argThat(command -> command.getOperation() == OrderOperation.RETURN
                && command.getExpectedVersion() == 6L
                && command.getIdempotencyKey().equals("after-sale-saga:saga-1:order-return")));
        verify(checkpoint).markCompleted(eq(1L), eq("saga-1"), eq("worker-1"), any());
    }

    @Test
    void shouldReverseBenefitsBeforeRefundingOnlyTheNetCashAmount() {
        saga.setApprovedAmountMinor(36000L).setGrossAmountMinor(39800L).setBenefitAmountMinor(3800L)
                .setNetAmountMinor(36000L).setBenefitReversalStatus("PENDING")
                .setBenefitReversalOccurredAt(LocalDateTime.of(2026, 7, 15, 1, 0, 2));
        when(benefitReversalService.record(same(saga), any())).thenReturn(
                new AfterSaleBenefitReversalResult("batch-1", 1, 2, 3800L));
        when(paymentApi.execute(any())).thenReturn(PaymentCommandResult.builder()
                .operationId(201L).transactionId(202L).currentStatus("REFUNDED")
                .capturedAmountMinor(36000L).refundedAmountMinor(36000L)
                .transactionAmountMinor(36000L).remainingRefundableAmountMinor(0L)
                .currencyCode("CNY").build());

        worker.process(1L, "saga-1", "worker-1", now);

        verify(benefitReversalService).record(same(saga), any());
        verify(checkpoint).markBenefitsReversed(eq(1L), eq("saga-1"), eq("worker-1"),
                argThat(result -> result.amountMinor() == 3800L), any());
        verify(paymentApi).execute(argThat(command -> command.getAmountMinor() == 36000L));
        var ordered = inOrder(benefitReversalService, paymentApi);
        ordered.verify(benefitReversalService).record(same(saga), any());
        ordered.verify(paymentApi).execute(any());
    }

    @Test
    void shouldNotCallParticipantsBeforeQualifiedInspection() {
        when(returnQueryApi.requireInspectionAccepted("after-sale-1", "return-1"))
                .thenThrow(new IllegalStateException("return Fulfillment inspection has not been accepted"));

        assertThatThrownBy(() -> worker.process(1L, "saga-1", "worker-1", now))
                .hasMessage("return Fulfillment inspection has not been accepted");

        verifyNoInteractions(inventoryApi, paymentApi, orderApi);
        verify(checkpoint).markFailure(eq(1L), eq("saga-1"), eq("worker-1"), any(), any());
    }

    @Test
    void shouldReplayInventoryAfterEffectCommittedBeforeCheckpoint() {
        doThrow(new IllegalStateException("checkpoint unavailable"))
                .doAnswer(invocation -> {
                    InventoryCommandResult result = invocation.getArgument(3);
                    saga.setInventoryOperationId(result.getOperationId())
                            .setInventoryLedgerTransactionId(result.getLedgerTransactionId());
                    return null;
                }).when(checkpoint).markInventoryReturned(anyLong(), anyString(), anyString(), any(), any());
        replayTwiceAndAssertStable(() -> inventoryApi.execute(any()), InventoryCommand.class,
                "after-sale-saga:saga-1:inventory-return");
    }

    @Test
    void shouldReplayPaymentAfterEffectCommittedBeforeCheckpoint() {
        saga.setInventoryOperationId(101L).setInventoryLedgerTransactionId(102L);
        doThrow(new IllegalStateException("checkpoint unavailable"))
                .doAnswer(invocation -> {
                    PaymentCommandResult result = invocation.getArgument(3);
                    saga.setPaymentRefundTransactionId(result.getTransactionId());
                    return null;
                }).when(checkpoint).markPaymentRefunded(anyLong(), anyString(), anyString(), any(), any());
        assertThatThrownBy(() -> worker.process(1L, "saga-1", "worker-1", now))
                .hasMessage("checkpoint unavailable");
        worker.process(1L, "saga-1", "worker-1", now.plusSeconds(1));
        ArgumentCaptor<PaymentCommand> captor = ArgumentCaptor.forClass(PaymentCommand.class);
        verify(paymentApi, times(2)).execute(captor.capture());
        assertThat(captor.getAllValues()).extracting(PaymentCommand::getIdempotencyKey)
                .containsOnly("after-sale-saga:saga-1:payment-refund");
    }

    @Test
    void shouldReplayOrderRefundAfterEffectCommittedBeforeCheckpoint() {
        saga.setInventoryOperationId(101L).setInventoryLedgerTransactionId(102L)
                .setPaymentRefundTransactionId(202L);
        doThrow(new IllegalStateException("checkpoint unavailable"))
                .doAnswer(invocation -> {
                    OrderCommandResult result = invocation.getArgument(3);
                    saga.setOrderRefundOperationId(result.getOperationId()).setOrderVersion(result.getAggregateVersion());
                    return null;
                }).when(checkpoint).markOrderRefunded(anyLong(), anyString(), anyString(), any(), any());
        replayOrderOperation(OrderOperation.CONFIRM_REFUND,
                "after-sale-saga:saga-1:order-confirm-refund");
    }

    @Test
    void shouldReplayOrderSettlementAfterEffectCommittedBeforeCheckpoint() {
        saga.setInventoryOperationId(101L).setInventoryLedgerTransactionId(102L)
                .setPaymentRefundTransactionId(202L);
        doThrow(new IllegalStateException("checkpoint unavailable"))
                .doAnswer(invocation -> {
                    OrderAfterSaleSettlementResult result = invocation.getArgument(3);
                    saga.setOrderSettlementEffectId(result.getSettlementEffectId())
                            .setOrderSettlementVersion(result.getOrderSettlementVersion())
                            .setOrderReturnFull(result.getFullReturn());
                    return null;
                }).when(checkpoint).markOrderSettled(anyLong(), anyString(), anyString(), any(), any());

        assertThatThrownBy(() -> worker.process(1L, "saga-1", "worker-1", now))
                .hasMessage("checkpoint unavailable");
        worker.process(1L, "saga-1", "worker-1", now.plusSeconds(1));

        ArgumentCaptor<OrderAfterSaleSettlementCommand> captor =
                ArgumentCaptor.forClass(OrderAfterSaleSettlementCommand.class);
        verify(orderSettlementApi, times(2)).record(captor.capture());
        assertThat(captor.getAllValues()).extracting(OrderAfterSaleSettlementCommand::getAfterSaleId)
                .containsOnly("after-sale-1");
        assertThat(captor.getAllValues()).extracting(OrderAfterSaleSettlementCommand::getPaymentRefundTransactionId)
                .containsOnly(202L);
    }

    @Test
    void shouldCompletePartialReturnWithoutChangingCommercialOrderStatus() {
        when(orderSettlementApi.record(any())).thenReturn(OrderAfterSaleSettlementResult.builder()
                .settlementEffectId("settlement-effect-partial").orderId("order-1")
                .orderItemId("order-item-1").orderSettlementVersion(1L)
                .itemSettlementVersion(1L).orderVersion(5L).fullReturn(false).duplicate(false).build());

        worker.process(1L, "saga-1", "worker-1", now);

        assertThat(saga.getOrderReturnFull()).isFalse();
        verifyNoInteractions(orderApi);
        verify(checkpoint).markCompleted(eq(1L), eq("saga-1"), eq("worker-1"), any());
    }

    @Test
    void shouldReplayOrderReturnAfterEffectCommittedBeforeCheckpoint() {
        saga.setInventoryOperationId(101L).setInventoryLedgerTransactionId(102L)
                .setPaymentRefundTransactionId(202L).setOrderRefundOperationId(301L).setOrderVersion(6L);
        doThrow(new IllegalStateException("checkpoint unavailable"))
                .doAnswer(invocation -> {
                    OrderCommandResult result = invocation.getArgument(3);
                    saga.setOrderReturnOperationId(result.getOperationId()).setOrderVersion(result.getAggregateVersion());
                    return null;
                }).when(checkpoint).markOrderReturned(anyLong(), anyString(), anyString(), any(), any());
        replayOrderOperation(OrderOperation.RETURN, "after-sale-saga:saga-1:order-return");
    }

    @Test
    void shouldCapBackoff() {
        assertThat(AfterSaleResolutionCheckpointService.backoff(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(AfterSaleResolutionCheckpointService.backoff(20)).isEqualTo(Duration.ofMinutes(30));
    }

    private void replayOrderOperation(OrderOperation operation, String idempotencyKey) {
        assertThatThrownBy(() -> worker.process(1L, "saga-1", "worker-1", now))
                .hasMessage("checkpoint unavailable");
        worker.process(1L, "saga-1", "worker-1", now.plusSeconds(1));
        ArgumentCaptor<OrderCommand> captor = ArgumentCaptor.forClass(OrderCommand.class);
        verify(orderApi, times(operation == OrderOperation.CONFIRM_REFUND ? 3 : 2)).execute(captor.capture());
        assertThat(captor.getAllValues().stream().filter(c -> c.getOperation() == operation).toList())
                .extracting(OrderCommand::getIdempotencyKey).containsOnly(idempotencyKey);
    }

    private void replayTwiceAndAssertStable(Runnable ignored, Class<InventoryCommand> type, String idempotencyKey) {
        assertThatThrownBy(() -> worker.process(1L, "saga-1", "worker-1", now))
                .hasMessage("checkpoint unavailable");
        worker.process(1L, "saga-1", "worker-1", now.plusSeconds(1));
        ArgumentCaptor<InventoryCommand> captor = ArgumentCaptor.forClass(type);
        verify(inventoryApi, times(2)).execute(captor.capture());
        assertThat(captor.getAllValues()).extracting(InventoryCommand::getIdempotencyKey)
                .containsOnly(idempotencyKey);
    }

    private static AfterSaleResolutionSagaDO saga() {
        return new AfterSaleResolutionSagaDO().setSagaId("saga-1").setTenantId(1L)
                .setAfterSaleId("after-sale-1").setAfterSaleItemId("after-sale-item-1").setRunId("run-1")
                .setOrderId("order-1").setOrderNo("CMO1").setOrderItemId("order-item-1")
                .setOrderVersionAtRequest(5L).setOrderVersion(5L).setPaymentId("payment-1")
                .setPaymentVersionAtRequest(1L).setReturnFulfillmentId("return-1")
                .setReturnShipmentId("return-shipment-1").setInspectionId("inspection-1")
                .setCanonicalSkuId("sku-1").setQuantity(new BigDecimal("2.000000"))
                .setOwnerId("internal-company").setWarehouseId("warehouse-1").setUomCode("PCS")
                .setApprovedAmountMinor(39800L).setGrossAmountMinor(39800L).setBenefitAmountMinor(0L)
                .setNetAmountMinor(39800L).setBenefitReversalStatus("NOT_REQUIRED")
                .setBenefitReversalAmountMinor(0L).setCurrencyCode("CNY").setReason("size not fit")
                .setStatus("REQUESTED").setActiveStep("RETURN_INVENTORY").setAttemptCount(1).setMaxAttempts(8)
                .setVersion(1L).setLeaseOwner("worker-1")
                .setCorrelationId("70000000-0000-4000-8000-000000000001")
                .setInventoryOccurredAt(LocalDateTime.of(2026, 7, 15, 1, 0, 1))
                .setPaymentOccurredAt(LocalDateTime.of(2026, 7, 15, 1, 0, 2))
                .setOrderRefundOccurredAt(LocalDateTime.of(2026, 7, 15, 1, 0, 3))
                .setOrderReturnOccurredAt(LocalDateTime.of(2026, 7, 15, 1, 0, 4));
    }
}
