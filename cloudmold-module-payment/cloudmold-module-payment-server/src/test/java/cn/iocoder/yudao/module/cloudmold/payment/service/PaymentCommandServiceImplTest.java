package cn.iocoder.yudao.module.cloudmold.payment.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.order.api.*;
import cn.iocoder.yudao.module.cloudmold.payment.api.*;
import cn.iocoder.yudao.module.cloudmold.payment.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.payment.dal.mysql.*;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentCommandServiceImplTest {

    private final PaymentOperationMapper operationMapper = mock(PaymentOperationMapper.class);
    private final PaymentMapper paymentMapper = mock(PaymentMapper.class);
    private final PaymentTransactionMapper transactionMapper = mock(PaymentTransactionMapper.class);
    private final OrderQueryApi orderQueryApi = mock(OrderQueryApi.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final PaymentCommandServiceImpl service = new PaymentCommandServiceImpl(operationMapper, paymentMapper,
            transactionMapper, orderQueryApi, outboxAppender);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(operationMapper.selectLastInsertId()).thenReturn(21L);
        when(operationMapper.markSucceeded(anyLong(), anyLong(), anyString(), anyString(), any())).thenReturn(1);
        when(paymentMapper.insert(any(PaymentDO.class))).thenReturn(1);
        when(transactionMapper.insert(any(PaymentTransactionDO.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, PaymentTransactionDO.class).setTransactionId(31L); return 1;
        });
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event-1", "a".repeat(64), false));
        when(orderQueryApi.requirePayableOrder("order-1", 530L, "CNY")).thenReturn(
                OrderPaymentView.builder().orderId("order-1").orderNo("CMO1").buyerId("buyer-1")
                        .status("INVENTORY_RESERVED").payableAmountMinor(530L).currencyCode("CNY")
                        .aggregateVersion(2L).build());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldCaptureExactOrderAmountInTestMode() {
        claimNewOperation();

        PaymentCommandResult result = service.execute(captureCommand());

        assertThat(result.getCurrentStatus()).isEqualTo("CAPTURED");
        assertThat(result.getCapturedAmountMinor()).isEqualTo(530L);
        assertThat(result.getRefundedAmountMinor()).isZero();
        assertThat(result.getTestMode()).isTrue();
        verify(orderQueryApi).requirePayableOrder("order-1", 530L, "CNY");
        verify(outboxAppender).append(argThat(event -> event.getEventType().equals("payment.status.changed")
                && event.getAggregateVersion() == 1L && event.getPayload().get("test_mode").equals(true)));
    }

    @Test
    void shouldFullRefundCapturedPayment() {
        claimNewOperation();
        PaymentDO payment = payment("CAPTURED", 1L);
        when(paymentMapper.selectForUpdate(1L, "payment-1")).thenReturn(payment);
        when(paymentMapper.refund(eq(1L), eq("payment-1"), eq(1L), eq(530L), any())).thenReturn(1);

        PaymentCommandResult result = service.execute(refundCommand());

        assertThat(result.getCurrentStatus()).isEqualTo("REFUNDED");
        assertThat(result.getAggregateVersion()).isEqualTo(2L);
        assertThat(result.getRefundedAmountMinor()).isEqualTo(530L);
    }

    @Test
    void shouldRejectPartialRefund() {
        claimNewOperation();
        when(paymentMapper.selectForUpdate(1L, "payment-1")).thenReturn(payment("CAPTURED", 1L));
        PaymentCommand command = refundCommand();
        command.setAmountMinor(100L);

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("first slice supports full refund only");
    }

    @Test
    void shouldRejectRefundThroughDifferentProvider() {
        claimNewOperation();
        when(paymentMapper.selectForUpdate(1L, "payment-1")).thenReturn(payment("CAPTURED", 1L));
        PaymentCommand command = refundCommand();
        command.setProviderCode("OTHER_PROVIDER");

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("refund provider mismatch");
        verify(paymentMapper, never()).refund(anyLong(), anyString(), anyLong(), anyLong(), any());
    }

    @Test
    void shouldReturnImmutableFirstResultOnReplay() {
        AtomicReference<String> hash = new AtomicReference<>();
        when(operationMapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> { hash.set(invocation.getArgument(3)); return 0; });
        PaymentCommandResult first = PaymentCommandResult.builder().operationId(21L).transactionId(31L)
                .paymentId("payment-1").orderId("order-1").currentStatus("CAPTURED")
                .aggregateVersion(1L).capturedAmountMinor(530L).refundedAmountMinor(0L).duplicate(false).build();
        when(operationMapper.selectForUpdate(21L, 1L)).thenAnswer(ignored -> new PaymentOperationDO()
                .setOperationId(21L).setAttemptToken("existing").setRequestHash(hash.get()).setStatus(10)
                .setResultJson(JsonUtils.toJsonString(first)));

        PaymentCommandResult replay = service.execute(captureCommand());

        assertThat(replay.getDuplicate()).isTrue();
        verify(paymentMapper, never()).insert(any(PaymentDO.class));
        verify(orderQueryApi, never()).requirePayableOrder(anyString(), anyLong(), anyString());
    }

    private void claimNewOperation() {
        AtomicReference<String> attempt = new AtomicReference<>();
        when(operationMapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> { attempt.set(invocation.getArgument(4)); return 1; });
        when(operationMapper.selectForUpdate(21L, 1L)).thenAnswer(ignored -> new PaymentOperationDO()
                .setOperationId(21L).setAttemptToken(attempt.get()).setStatus(0));
    }

    private static PaymentCommand captureCommand() {
        return PaymentCommand.builder().operation(PaymentOperation.CAPTURE).idempotencyKey("pay-run-1-capture")
                .runId("pay-run-1").orderId("order-1").amountMinor(530L).currencyCode("CNY")
                .providerCode("INTERNAL_TEST").providerTransactionId("provider-capture-1")
                .correlationId("6f9619ff-8b86-d011-b42d-00cf4fc964ff")
                .occurredAt(Instant.parse("2026-07-12T00:00:02Z")).build();
    }

    private static PaymentCommand refundCommand() {
        return PaymentCommand.builder().operation(PaymentOperation.REFUND).idempotencyKey("pay-run-1-refund")
                .runId("pay-run-1").paymentId("payment-1").expectedVersion(1L).orderId("order-1")
                .amountMinor(530L).currencyCode("CNY").providerCode("INTERNAL_TEST")
                .providerTransactionId("provider-refund-1").reason("FULL_RETURN")
                .correlationId("6f9619ff-8b86-d011-b42d-00cf4fc964ff")
                .occurredAt(Instant.parse("2026-07-12T00:00:03Z")).build();
    }

    private static PaymentDO payment(String status, long version) {
        return new PaymentDO().setPaymentId("payment-1").setTenantId(1L).setPaymentNo("CMP1")
                .setRunId("pay-run-1").setOrderId("order-1").setStatus(status).setPayableAmountMinor(530L)
                .setCapturedAmountMinor(530L).setRefundedAmountMinor(0L).setCurrencyCode("CNY")
                .setProviderCode("INTERNAL_TEST").setProviderTransactionId("provider-capture-1")
                .setTestMode(true).setVersion(version);
    }
}
