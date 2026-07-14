package cn.iocoder.yudao.module.cloudmold.order.service.cancellation;

import cn.iocoder.yudao.module.cloudmold.inventory.api.*;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.*;
import cn.iocoder.yudao.module.cloudmold.order.api.*;
import cn.iocoder.yudao.module.cloudmold.payment.api.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.cancellation.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.cancellation.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderCancellationSagaWorkerTest {

    private final OrderCancellationSagaMapper sagaMapper = mock(OrderCancellationSagaMapper.class);
    private final OrderCancellationSagaItemMapper itemMapper = mock(OrderCancellationSagaItemMapper.class);
    private final InventoryCommandApi inventoryApi = mock(InventoryCommandApi.class);
    private final FulfillmentCommandApi fulfillmentApi = mock(FulfillmentCommandApi.class);
    private final PaymentCommandApi paymentApi = mock(PaymentCommandApi.class);
    private final PaymentCancellationQueryApi paymentQueryApi = mock(PaymentCancellationQueryApi.class);
    private final OrderCancellationSagaFulfillmentMapper fulfillmentMapper =
            mock(OrderCancellationSagaFulfillmentMapper.class);
    private final OrderCommandApi orderApi = mock(OrderCommandApi.class);
    private final OrderCancellationSagaCheckpointService checkpoint =
            mock(OrderCancellationSagaCheckpointService.class);
    private final OrderCancellationSagaWorker worker = new OrderCancellationSagaWorker(sagaMapper, itemMapper,
            inventoryApi, fulfillmentApi, paymentApi, paymentQueryApi, fulfillmentMapper, orderApi, checkpoint);

    @Test
    void shouldReplayStableReleaseAndFinalizeAfterTimeout() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 12, 16, 0);
        OrderCancellationSagaDO initial = saga(0);
        OrderCancellationSagaDO released = saga(1).setStatus("RESERVATIONS_RELEASED")
                .setActiveStep("CANCEL_ORDER");
        OrderCancellationSagaItemDO item = item();
        when(sagaMapper.selectTenantSaga(1L, "saga-1"))
                .thenReturn(initial, initial, released, released);
        when(itemMapper.selectNextRelease(1L, "saga-1")).thenReturn(item, item, null);
        when(inventoryApi.execute(any())).thenThrow(new IllegalStateException("timeout after commit"))
                .thenReturn(InventoryCommandResult.builder().operationId(91L).duplicate(true).build());
        when(orderApi.execute(any())).thenReturn(OrderCommandResult.builder()
                .currentStatus("CANCELLED").duplicate(false).build());

        assertThatThrownBy(() -> worker.process(1L, "saga-1", "worker-1", now))
                .isInstanceOf(IllegalStateException.class).hasMessage("timeout after commit");
        assertThatCode(() -> worker.process(1L, "saga-1", "worker-1", now.plusSeconds(1)))
                .doesNotThrowAnyException();

        ArgumentCaptor<InventoryCommand> inventoryCommands = ArgumentCaptor.forClass(InventoryCommand.class);
        verify(inventoryApi, times(2)).execute(inventoryCommands.capture());
        assertThat(inventoryCommands.getAllValues()).extracting(InventoryCommand::getIdempotencyKey)
                .containsOnly("cancel-saga:saga-1:release:reservation-1");
        assertThat(inventoryCommands.getAllValues()).extracting(InventoryCommand::getOccurredAt)
                .containsOnly(item.getOccurredAt().toInstant(ZoneOffset.UTC));
        verify(orderApi).execute(argThat(command -> command.getOperation() == OrderOperation.FINALIZE_CANCELLATION
                && command.getExpectedVersion() == 3L
                && command.getIdempotencyKey().equals("cancel-saga:saga-1:order-finalize")));
        ArgumentCaptor<LocalDateTime> completedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(checkpoint).markCompleted(eq(1L), eq("saga-1"), eq("worker-1"), completedAt.capture());
        assertThat(completedAt.getValue()).isAfter(now.plusSeconds(1));
    }

    @Test
    void shouldCapExponentialBackoff() {
        assertThat(OrderCancellationSagaCheckpointService.backoff(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(OrderCancellationSagaCheckpointService.backoff(20)).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void shouldRunPaidUnshippedParticipantsInStrictOrder() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 13, 1, 0);
        OrderCancellationSagaDO initial = paidSaga(0, "CAPTURED", 0, "REQUESTED");
        OrderCancellationSagaDO fulfillmentCancelled = paidSaga(1, "CAPTURED", 0,
                "FULFILLMENT_CANCELLED");
        OrderCancellationSagaDO paymentRefunded = paidSaga(1, "REFUNDED", 0, "PAYMENT_REFUNDED")
                .setPaymentRefundTransactionId(301L);
        OrderCancellationSagaDO reservationsReleased = paidSaga(1, "REFUNDED", 1,
                "RESERVATIONS_RELEASED").setPaymentRefundTransactionId(301L);
        when(sagaMapper.selectTenantSaga(1L, "saga-1")).thenReturn(initial, fulfillmentCancelled,
                paymentRefunded, reservationsReleased, reservationsReleased);
        when(fulfillmentMapper.selectBySaga(1L, "saga-1")).thenReturn(new OrderCancellationSagaFulfillmentDO()
                .setFulfillmentId("fulfillment-1").setFulfillmentVersionAtRequest(1L)
                .setFinalizeIdempotencyKey("cancel-saga:saga-1:fulfillment:fulfillment-1:finalize")
                .setOccurredAt(now.plusSeconds(1)));
        when(fulfillmentApi.execute(any())).thenReturn(FulfillmentCommandResult.builder()
                .operationId(201L).fulfillmentId("fulfillment-1").currentStatus("CANCELLED").build());
        when(paymentQueryApi.requireCaptured("order-1", "payment-1")).thenReturn(PaymentCancellationView.builder()
                .paymentId("payment-1").orderId("order-1").status("CAPTURED").aggregateVersion(1L)
                .capturedAmountMinor(1000L).currencyCode("CNY").providerCode("INTERNAL_TEST").testMode(true).build());
        when(paymentApi.execute(any())).thenReturn(PaymentCommandResult.builder()
                .transactionId(301L).paymentId("payment-1").currentStatus("REFUNDED").build());
        when(itemMapper.selectNextRelease(1L, "saga-1")).thenReturn(item(), null);
        when(inventoryApi.execute(any())).thenReturn(InventoryCommandResult.builder().operationId(401L).build());
        when(orderApi.execute(any())).thenReturn(OrderCommandResult.builder().currentStatus("CANCELLED").build());

        worker.process(1L, "saga-1", "worker-1", now);

        verify(fulfillmentApi).execute(argThat(command ->
                command.getOperation() == FulfillmentOperation.FINALIZE_CANCELLATION
                        && command.getExpectedVersion() == 2L
                        && command.getCancellationStepOrdinal() == 1));
        verify(paymentApi).execute(argThat(command -> command.getOperation() == PaymentOperation.REFUND
                && command.getIdempotencyKey().equals("cancel-saga:saga-1:payment:payment-1:refund")
                && command.getCancellationStepOrdinal() == 2));
        verify(inventoryApi).execute(argThat(command -> command.getOperation() == InventoryOperation.RELEASE
                && command.getCancellationSagaId().equals("saga-1")
                && command.getCancellationStepOrdinal() == 3));
        verify(orderApi).execute(argThat(command -> command.getOperation() == OrderOperation.FINALIZE_CANCELLATION
                && command.getCancellationMode().equals("PAID_UNSHIPPED")
                && command.getCancellationStepOrdinal() == 4
                && command.getRefundId().equals("301")));
        ArgumentCaptor<LocalDateTime> completedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(checkpoint).markCompleted(eq(1L), eq("saga-1"), eq("worker-1"), completedAt.capture());
        assertThat(completedAt.getValue()).isAfter(now);
    }

    private static OrderCancellationSagaDO saga(int released) {
        return new OrderCancellationSagaDO().setSagaId("saga-1").setTenantId(1L).setRunId("run-1")
                .setOrderId("order-1").setOrderNo("CMO1").setOrderStatusAtRequest("INVENTORY_RESERVED")
                .setOrderVersionAtRequest(2L).setStatus("RELEASING_RESERVATIONS")
                .setActiveStep("RELEASE_RESERVATIONS").setExpectedReservationCount(1)
                .setReleasedReservationCount(released).setAttemptCount(1).setMaxAttempts(8).setVersion(2L)
                .setReason("buyer cancel").setCorrelationId("70000000-0000-4000-8000-000000000001")
                .setOccurredAt(LocalDateTime.of(2026, 7, 12, 16, 0))
                .setFinalizeOccurredAt(LocalDateTime.of(2026, 7, 12, 16, 0, 3));
    }

    private static OrderCancellationSagaDO paidSaga(int cancelledFulfillments, String paymentStatus,
                                                      int released, String status) {
        return saga(released).setCancellationMode("PAID_UNSHIPPED").setOrderStatusAtRequest("PAYMENT_CONFIRMED")
                .setOrderVersionAtRequest(4L).setStatus(status).setExpectedFulfillmentCount(1)
                .setCancelledFulfillmentCount(cancelledFulfillments).setPaymentId("payment-1")
                .setPaymentVersionAtRequest(1L).setPaymentStatus(paymentStatus)
                .setPaymentRefundOccurredAt(LocalDateTime.of(2026, 7, 13, 1, 0, 2))
                .setFinalizeOccurredAt(LocalDateTime.of(2026, 7, 13, 1, 0, 6));
    }

    private static OrderCancellationSagaItemDO item() {
        return new OrderCancellationSagaItemDO().setSagaItemId("item-1").setTenantId(1L).setSagaId("saga-1")
                .setOrderItemId("order-item-1").setReservationId("reservation-1").setOwnerId("owner-1")
                .setCanonicalSkuId("sku-1").setWarehouseId("warehouse-1").setStockStatus("SELLABLE")
                .setQualityStatus("QUALIFIED").setUomCode("PIECE").setQuantity(new BigDecimal("2.000000"))
                .setReleaseIdempotencyKey("cancel-saga:saga-1:release:reservation-1")
                .setStatus("PENDING").setAttemptCount(0)
                .setOccurredAt(LocalDateTime.of(2026, 7, 12, 16, 0, 1));
    }
}
