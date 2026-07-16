package cn.iocoder.yudao.module.cloudmold.payment.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderPaymentView;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderQueryApi;
import cn.iocoder.yudao.module.cloudmold.payment.api.*;
import cn.iocoder.yudao.module.cloudmold.payment.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.payment.dal.mysql.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PaymentCommandServiceImpl implements PaymentCommandApi {

    static final int OPERATION_SUCCEEDED = 10;
    private static final String CURRENCY_CNY = "CNY";
    private static final String TEST_PROVIDER = "INTERNAL_TEST";

    private final PaymentOperationMapper operationMapper;
    private final PaymentMapper paymentMapper;
    private final PaymentTransactionMapper transactionMapper;
    private final OrderQueryApi orderQueryApi;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentCommandResult execute(PaymentCommand command) {
        validate(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (command.getOperation() == PaymentOperation.CAPTURE) {
            require(command.getPaymentId() == null, "CAPTURE does not accept paymentId");
            require(TEST_PROVIDER.equals(command.getProviderCode()),
                    "first slice accepts INTERNAL_TEST provider only");
        } else {
            requireText(command.getPaymentId(), "paymentId", 36);
            require(command.getExpectedVersion() == null || command.getExpectedVersion() > 0,
                    "expectedVersion must be positive when supplied");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = fingerprint(tenantId, command);
        String attemptToken = UUID.randomUUID().toString();
        operationMapper.insertOrResolve(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                requestHash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve payment operation");
        PaymentOperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "payment operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(operation.getRequestHash(), requestHash),
                    "idempotency key conflicts with different payment payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing payment operation is not complete");
            PaymentCommandResult replay = JsonUtils.parseObject(operation.getResultJson(), PaymentCommandResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        PaymentCommandResult result = command.getOperation() == PaymentOperation.CAPTURE
                ? capture(tenantId, operationId, command, now)
                : refund(tenantId, operationId, command, now);
        require(operationMapper.markSucceeded(operationId, tenantId, result.getPaymentId(),
                JsonUtils.toJsonString(result), now) == 1, "payment operation completion conflict");
        return result;
    }

    private PaymentCommandResult capture(Long tenantId, Long operationId, PaymentCommand command,
                                         LocalDateTime now) {
        OrderPaymentView order = orderQueryApi.requirePayableOrder(command.getOrderId(),
                command.getAmountMinor(), command.getCurrencyCode());
        String paymentId = UUID.randomUUID().toString();
        String paymentNo = "CMP" + paymentId.replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
        PaymentDO payment = new PaymentDO().setPaymentId(paymentId).setTenantId(tenantId).setPaymentNo(paymentNo)
                .setRunId(command.getRunId()).setOrderId(order.getOrderId()).setStatus("CAPTURED")
                .setPayableAmountMinor(command.getAmountMinor()).setCapturedAmountMinor(command.getAmountMinor())
                .setRefundedAmountMinor(0L).setCurrencyCode(command.getCurrencyCode())
                .setProviderCode(command.getProviderCode()).setProviderTransactionId(command.getProviderTransactionId())
                .setTestMode(true).setVersion(1L).setCapturedAt(now).setCreatedAt(now).setUpdatedAt(now);
        paymentMapper.insert(payment);
        PaymentTransactionDO transaction = transaction(tenantId, payment, operationId, command, 1L, now);
        transactionMapper.insert(transaction);
        appendEvent(tenantId, payment, null, "CAPTURED", transaction.getTransactionId(), command, 1L);
        return result(operationId, transaction.getTransactionId(), payment, null,
                command.getAmountMinor(), false);
    }

    private PaymentCommandResult refund(Long tenantId, Long operationId, PaymentCommand command,
                                        LocalDateTime now) {
        PaymentDO payment = paymentMapper.selectForUpdate(tenantId, command.getPaymentId());
        require(payment != null, "canonical payment does not exist");
        require(Objects.equals(payment.getRunId(), command.getRunId()), "payment runId does not match");
        require(Objects.equals(payment.getOrderId(), command.getOrderId()), "payment orderId does not match");
        require("CAPTURED".equals(payment.getStatus()) || "PARTIALLY_REFUNDED".equals(payment.getStatus()),
                "payment is not refundable");
        if (command.getExpectedVersion() != null) {
            require(Objects.equals(payment.getVersion(), command.getExpectedVersion()), "payment version conflict");
        }
        long remaining = Math.subtractExact(payment.getCapturedAmountMinor(), payment.getRefundedAmountMinor());
        require(command.getAmountMinor() > 0 && command.getAmountMinor() <= remaining,
                "refund amount exceeds remaining captured money");
        require(Objects.equals(payment.getCurrencyCode(), command.getCurrencyCode()), "refund currency mismatch");
        require(Objects.equals(payment.getProviderCode(), command.getProviderCode()), "refund provider mismatch");
        long refundedTotal = Math.addExact(payment.getRefundedAmountMinor(), command.getAmountMinor());
        String nextStatus = refundedTotal == payment.getCapturedAmountMinor() ? "REFUNDED" : "PARTIALLY_REFUNDED";
        require(paymentMapper.refund(tenantId, payment.getPaymentId(), payment.getVersion(),
                command.getAmountMinor(), nextStatus, now) == 1, "payment refund transition conflict");
        String previous = payment.getStatus();
        payment.setStatus(nextStatus).setRefundedAmountMinor(refundedTotal)
                .setVersion(payment.getVersion() + 1).setRefundedAt(now).setUpdatedAt(now);
        PaymentTransactionDO transaction = transaction(tenantId, payment, operationId, command,
                payment.getVersion(), now);
        transactionMapper.insert(transaction);
        appendEvent(tenantId, payment, previous, nextStatus, transaction.getTransactionId(), command,
                payment.getVersion());
        return result(operationId, transaction.getTransactionId(), payment, previous,
                command.getAmountMinor(), false);
    }

    private static PaymentTransactionDO transaction(Long tenantId, PaymentDO payment, Long operationId,
                                                    PaymentCommand command, Long version, LocalDateTime now) {
        return new PaymentTransactionDO().setTenantId(tenantId).setPaymentId(payment.getPaymentId())
                .setOperationId(operationId).setTransactionType(command.getOperation().name())
                .setAmountMinor(command.getAmountMinor()).setCurrencyCode(command.getCurrencyCode())
                .setProviderCode(command.getProviderCode()).setProviderTransactionId(command.getProviderTransactionId())
                .setAggregateVersion(version)
                .setOccurredAt(LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC)).setCreatedAt(now);
    }

    private void appendEvent(Long tenantId, PaymentDO payment, String previous, String current,
                             Long transactionId, PaymentCommand command, Long version) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", command.getRunId());
        payload.put("payment_id", payment.getPaymentId());
        payload.put("payment_no", payment.getPaymentNo());
        payload.put("order_id", payment.getOrderId());
        payload.put("transaction_id", transactionId);
        payload.put("transaction_type", command.getOperation().name());
        payload.put("previous_status", previous);
        payload.put("current_status", current);
        payload.put("payable_amount_minor", payment.getPayableAmountMinor());
        payload.put("captured_amount_minor", payment.getCapturedAmountMinor());
        payload.put("refunded_amount_minor", payment.getRefundedAmountMinor());
        payload.put("currency_code", payment.getCurrencyCode());
        payload.put("provider_code", payment.getProviderCode());
        payload.put("provider_transaction_id", command.getProviderTransactionId());
        payload.put("test_mode", payment.getTestMode());
        payload.put("reason", command.getReason());
        int schemaVersion;
        if (command.getCancellationSagaId() != null) {
            schemaVersion = 2;
            payload.put("cancellation_saga_id", command.getCancellationSagaId());
            payload.put("step_ordinal", command.getCancellationStepOrdinal());
        } else if (command.getOperation() == PaymentOperation.REFUND) {
            schemaVersion = 3;
            payload.put("refund_amount_minor", command.getAmountMinor());
            payload.put("remaining_refundable_amount_minor",
                    Math.subtractExact(payment.getCapturedAmountMinor(), payment.getRefundedAmountMinor()));
        } else {
            schemaVersion = 1;
        }
        outboxAppender.append(AppendDomainEventCommand.builder().eventType("payment.status.changed")
                .schemaVersion(schemaVersion)
                .sourceSystem("cloudmold-payment").tenantId(tenantId)
                .aggregateType("payment").aggregateId(payment.getPaymentId()).aggregateVersion(version)
                .eventSequence((short) 1).occurredAt(command.getOccurredAt()).correlationId(command.getCorrelationId())
                .causationId(command.getCausationId()).idempotencyKey(command.getIdempotencyKey())
                .payload(payload).headers(Map.of("operation", command.getOperation().name()))
                .destination("lakehouse").build());
    }

    private static PaymentCommandResult result(Long operationId, Long transactionId, PaymentDO payment,
                                               String previous, Long transactionAmountMinor,
                                               boolean duplicate) {
        return PaymentCommandResult.builder().operationId(operationId).transactionId(transactionId)
                .paymentId(payment.getPaymentId()).paymentNo(payment.getPaymentNo()).orderId(payment.getOrderId())
                .previousStatus(previous).currentStatus(payment.getStatus()).aggregateVersion(payment.getVersion())
                .capturedAmountMinor(payment.getCapturedAmountMinor()).refundedAmountMinor(payment.getRefundedAmountMinor())
                .transactionAmountMinor(transactionAmountMinor)
                .remainingRefundableAmountMinor(Math.subtractExact(payment.getCapturedAmountMinor(),
                        payment.getRefundedAmountMinor()))
                .currencyCode(payment.getCurrencyCode()).testMode(payment.getTestMode()).duplicate(duplicate).build();
    }

    private static void validate(PaymentCommand command) {
        require(command != null && command.getOperation() != null, "payment operation is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        require(command.getIdempotencyKey().length() >= 8, "idempotencyKey is too short");
        requireText(command.getRunId(), "runId", 64);
        requireText(command.getOrderId(), "orderId", 36);
        require(command.getAmountMinor() != null && command.getAmountMinor() >= 0,
                "amountMinor must be nonnegative");
        require(CURRENCY_CNY.equals(command.getCurrencyCode()), "first slice supports CNY only");
        requireText(command.getProviderCode(), "providerCode", 32);
        requireText(command.getProviderTransactionId(), "providerTransactionId", 128);
        require(command.getOccurredAt() != null, "occurredAt is required");
        requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) requireUuid(command.getCausationId(), "causationId");
    }

    private static String fingerprint(Long tenantId, PaymentCommand command) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("tenant_id", tenantId); value.put("operation", command.getOperation());
        value.put("run_id", command.getRunId()); value.put("payment_id", command.getPaymentId());
        value.put("expected_version", command.getExpectedVersion()); value.put("order_id", command.getOrderId());
        value.put("amount_minor", command.getAmountMinor()); value.put("currency", command.getCurrencyCode());
        value.put("provider", command.getProviderCode()); value.put("provider_tx", command.getProviderTransactionId());
        value.put("reason", command.getReason()); value.put("occurred_at", command.getOccurredAt());
        value.put("cancellation_saga_id", command.getCancellationSagaId());
        value.put("cancellation_step_ordinal", command.getCancellationStepOrdinal());
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(value));
    }

    private static void requireUuid(String value, String field) {
        requireText(value, field, 36);
        try { UUID.fromString(value); } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(field + " must be a UUID", error);
        }
    }

    private static void requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank() && value.length() <= maxLength, field + " is required");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
