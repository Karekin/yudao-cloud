package cn.iocoder.yudao.module.cloudmold.order.service.cancellation;

import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryCommandResult;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.FulfillmentCommandResult;
import cn.iocoder.yudao.module.cloudmold.payment.api.PaymentCommandResult;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.cancellation.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.cancellation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderCancellationSagaCheckpointService {

    private static final Duration MAX_BACKOFF = Duration.ofMinutes(30);

    private final OrderCancellationSagaMapper sagaMapper;
    private final OrderCancellationSagaItemMapper itemMapper;
    private final OrderCancellationSagaHistoryMapper historyMapper;
    private final OrderCancellationSagaFulfillmentMapper fulfillmentMapper;
    private final OutboxAppender outboxAppender;

    public void appendInitial(OrderCancellationSagaDO saga, LocalDateTime now) {
        appendHistoryAndEvent(saga, null, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markProgress(Long tenantId, String sagaId, String leaseOwner,
                             String nextStatus, String activeStep, LocalDateTime now) {
        OrderCancellationSagaDO saga = requireLeased(tenantId, sagaId, leaseOwner);
        if (nextStatus.equals(saga.getStatus()) && activeStep.equals(saga.getActiveStep())) {
            return;
        }
        String previous = saga.getStatus();
        saga.setStatus(nextStatus).setActiveStep(activeStep).setVersion(saga.getVersion() + 1)
                .setNextRetryAt(null).setLastErrorCode(null).setLastErrorMessage(null).setUpdatedAt(now);
        require(sagaMapper.updateById(saga) == 1, "cancellation Saga progress checkpoint conflict");
        appendHistoryAndEvent(saga, previous, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markItemRunning(Long tenantId, String sagaId, String sagaItemId,
                                String leaseOwner, LocalDateTime now) {
        requireLeased(tenantId, sagaId, leaseOwner);
        OrderCancellationSagaItemDO item = itemMapper.selectForUpdate(tenantId, sagaItemId);
        require(item != null && Objects.equals(item.getSagaId(), sagaId), "cancellation Saga item disappeared");
        if ("RELEASED".equals(item.getStatus())) return;
        item.setStatus("RUNNING").setAttemptCount(item.getAttemptCount() + 1)
                .setLastErrorCode(null).setLastErrorMessage(null).setUpdatedAt(now);
        require(itemMapper.updateById(item) == 1, "cancellation Saga item running checkpoint conflict");
    }

    @Transactional(rollbackFor = Exception.class)
    public void markItemReleased(Long tenantId, String sagaId, String sagaItemId, String leaseOwner,
                                 InventoryCommandResult result, LocalDateTime now) {
        OrderCancellationSagaDO saga = requireLeased(tenantId, sagaId, leaseOwner);
        OrderCancellationSagaItemDO item = itemMapper.selectForUpdate(tenantId, sagaItemId);
        require(item != null && Objects.equals(item.getSagaId(), sagaId), "cancellation Saga item disappeared");
        if ("RELEASED".equals(item.getStatus())) return;
        item.setStatus("RELEASED").setInventoryOperationId(result.getOperationId())
                .setLastErrorCode(null).setLastErrorMessage(null).setReleasedAt(now).setUpdatedAt(now);
        require(itemMapper.updateById(item) == 1, "cancellation Saga item release checkpoint conflict");
        String previous = saga.getStatus();
        int released = saga.getReleasedReservationCount() + 1;
        boolean allReleased = released == saga.getExpectedReservationCount();
        saga.setReleasedReservationCount(released)
                .setStatus(allReleased ? "RESERVATIONS_RELEASED" : "RELEASING_RESERVATIONS")
                .setActiveStep(allReleased ? "CANCEL_ORDER" : "RELEASE_RESERVATIONS")
                .setVersion(saga.getVersion() + 1).setUpdatedAt(now);
        require(sagaMapper.updateById(saga) == 1, "cancellation Saga release count checkpoint conflict");
        appendHistoryAndEvent(saga, previous, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markFulfillmentCancelled(Long tenantId, String sagaId, String leaseOwner,
                                         FulfillmentCommandResult result, LocalDateTime now) {
        OrderCancellationSagaDO saga = requireLeased(tenantId, sagaId, leaseOwner);
        OrderCancellationSagaFulfillmentDO fulfillment = fulfillmentMapper.selectBySaga(tenantId, sagaId);
        require(fulfillment != null && Objects.equals(fulfillment.getFulfillmentId(), result.getFulfillmentId()),
                "cancellation Saga Fulfillment snapshot disappeared");
        if ("CANCELLED".equals(fulfillment.getStatus())) return;
        fulfillment.setStatus("CANCELLED").setAttemptCount(fulfillment.getAttemptCount() + 1)
                .setFulfillmentOperationId(result.getOperationId()).setCancelledAt(now).setUpdatedAt(now);
        require(fulfillmentMapper.updateById(fulfillment) == 1, "Fulfillment cancellation checkpoint conflict");
        String previous = saga.getStatus();
        saga.setCancelledFulfillmentCount(1).setStatus("FULFILLMENT_CANCELLED")
                .setActiveStep("REFUND_PAYMENT").setVersion(saga.getVersion() + 1).setUpdatedAt(now);
        require(sagaMapper.updateById(saga) == 1, "cancellation Saga Fulfillment checkpoint conflict");
        appendHistoryAndEvent(saga, previous, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markPaymentRefunded(Long tenantId, String sagaId, String leaseOwner,
                                    PaymentCommandResult result, LocalDateTime now) {
        OrderCancellationSagaDO saga = requireLeased(tenantId, sagaId, leaseOwner);
        require(Objects.equals(saga.getPaymentId(), result.getPaymentId()),
                "cancellation Saga Payment snapshot changed");
        if ("REFUNDED".equals(saga.getPaymentStatus())) return;
        String previous = saga.getStatus();
        saga.setPaymentStatus("REFUNDED").setPaymentRefundTransactionId(result.getTransactionId())
                .setStatus("PAYMENT_REFUNDED").setActiveStep("RELEASE_RESERVATIONS")
                .setVersion(saga.getVersion() + 1).setUpdatedAt(now);
        require(sagaMapper.updateById(saga) == 1, "cancellation Saga Payment checkpoint conflict");
        appendHistoryAndEvent(saga, previous, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markFailure(Long tenantId, String sagaId, String sagaItemId, String leaseOwner,
                            Throwable failure, LocalDateTime now) {
        OrderCancellationSagaDO saga = requireLeased(tenantId, sagaId, leaseOwner);
        String code = failure.getClass().getSimpleName();
        String message = summarize(failure);
        boolean exhausted = saga.getAttemptCount() >= saga.getMaxAttempts();
        String previous = saga.getStatus();
        LocalDateTime nextRetry = exhausted ? null : now.plus(backoff(saga.getAttemptCount()));
        saga.setStatus(exhausted ? "MANUAL_REVIEW" : "RETRY_SCHEDULED")
                .setNextRetryAt(nextRetry).setLeaseOwner(null).setLeaseUntil(null)
                .setLastErrorCode(code).setLastErrorMessage(message)
                .setVersion(saga.getVersion() + 1).setUpdatedAt(now);
        require(sagaMapper.updateById(saga) == 1, "cancellation Saga failure checkpoint conflict");
        if (sagaItemId != null) {
            OrderCancellationSagaItemDO item = itemMapper.selectForUpdate(tenantId, sagaItemId);
            if (item != null && !"RELEASED".equals(item.getStatus())) {
                item.setStatus(exhausted ? "MANUAL_REVIEW" : "RETRY_SCHEDULED")
                        .setLastErrorCode(code).setLastErrorMessage(message).setUpdatedAt(now);
                require(itemMapper.updateById(item) == 1, "cancellation Saga item failure checkpoint conflict");
            }
        }
        appendHistoryAndEvent(saga, previous, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markCompleted(Long tenantId, String sagaId, String leaseOwner, LocalDateTime now) {
        OrderCancellationSagaDO saga = requireLeased(tenantId, sagaId, leaseOwner);
        require(saga.getReleasedReservationCount().equals(saga.getExpectedReservationCount()),
                "cancellation Saga cannot complete before every reservation release");
        if ("PAID_UNSHIPPED".equals(saga.getCancellationMode())) {
            require(saga.getCancelledFulfillmentCount().equals(saga.getExpectedFulfillmentCount()),
                    "paid cancellation cannot complete before Fulfillment cancellation");
            require("REFUNDED".equals(saga.getPaymentStatus()) && saga.getPaymentRefundTransactionId() != null,
                    "paid cancellation cannot complete before Payment refund");
        }
        String previous = saga.getStatus();
        saga.setStatus("COMPLETED").setActiveStep("NONE").setNextRetryAt(null)
                .setLeaseOwner(null).setLeaseUntil(null).setLastErrorCode(null).setLastErrorMessage(null)
                .setCompletedAt(now).setVersion(saga.getVersion() + 1).setUpdatedAt(now);
        require(sagaMapper.updateById(saga) == 1, "cancellation Saga terminal checkpoint conflict");
        appendHistoryAndEvent(saga, previous, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderCancellationSagaDO retryManually(Long tenantId, String sagaId, Long expectedVersion,
                                                   LocalDateTime now) {
        OrderCancellationSagaDO saga = sagaMapper.selectForUpdate(tenantId, sagaId);
        require(saga != null, "cancellation Saga does not exist");
        require("MANUAL_REVIEW".equals(saga.getStatus()), "RETRY requires MANUAL_REVIEW");
        require(Objects.equals(saga.getVersion(), expectedVersion), "cancellation Saga version conflict");
        String previous = saga.getStatus();
        saga.setStatus("RETRY_SCHEDULED").setAttemptCount(0).setNextRetryAt(now)
                .setLeaseOwner(null).setLeaseUntil(null).setLastErrorCode(null).setLastErrorMessage(null)
                .setVersion(saga.getVersion() + 1).setUpdatedAt(now);
        require(sagaMapper.updateById(saga) == 1, "cancellation Saga manual retry checkpoint conflict");
        for (OrderCancellationSagaItemDO item : itemMapper.selectBySaga(tenantId, sagaId)) {
            if ("MANUAL_REVIEW".equals(item.getStatus())) {
                item.setStatus("RETRY_SCHEDULED").setLastErrorCode(null).setLastErrorMessage(null)
                        .setUpdatedAt(now);
                require(itemMapper.updateById(item) == 1, "cancellation Saga item manual retry conflict");
            }
        }
        appendHistoryAndEvent(saga, previous, now);
        return saga;
    }

    private OrderCancellationSagaDO requireLeased(Long tenantId, String sagaId, String leaseOwner) {
        OrderCancellationSagaDO saga = sagaMapper.selectForUpdate(tenantId, sagaId);
        require(saga != null, "cancellation Saga does not exist");
        require(Objects.equals(saga.getLeaseOwner(), leaseOwner), "cancellation Saga lease was lost");
        return saga;
    }

    private void appendHistoryAndEvent(OrderCancellationSagaDO saga, String previous, LocalDateTime now) {
        historyMapper.insert(new OrderCancellationSagaHistoryDO().setTenantId(saga.getTenantId())
                .setSagaId(saga.getSagaId()).setAggregateVersion(saga.getVersion())
                .setPreviousStatus(previous).setCurrentStatus(saga.getStatus()).setActiveStep(saga.getActiveStep())
                .setAttemptCount(saga.getAttemptCount())
                .setExpectedReservationCount(saga.getExpectedReservationCount())
                .setReleasedReservationCount(saga.getReleasedReservationCount())
                .setResponsibilityParty(saga.getResponsibilityParty())
                .setResponsibilityCode(saga.getResponsibilityCode())
                .setErrorCode(saga.getLastErrorCode()).setErrorMessage(saga.getLastErrorMessage())
                .setNextRetryAt(saga.getNextRetryAt()).setOccurredAt(now).setCreatedAt(now));

        List<Map<String, Object>> reservations = itemMapper.selectBySaga(saga.getTenantId(), saga.getSagaId())
                .stream().filter(item -> item.getReservationId() != null).map(item -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("order_item_id", item.getOrderItemId());
                    value.put("reservation_id", item.getReservationId());
                    value.put("quantity", item.getQuantity().toPlainString());
                    return value;
                }).toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", saga.getRunId());
        payload.put("saga_id", saga.getSagaId());
        payload.put("order_id", saga.getOrderId());
        payload.put("order_no", saga.getOrderNo());
        payload.put("previous_status", previous);
        payload.put("current_status", saga.getStatus());
        payload.put("active_step", saga.getActiveStep());
        payload.put("attempt", saga.getAttemptCount());
        payload.put("reason", saga.getReason());
        payload.put("responsibility_party", saga.getResponsibilityParty());
        payload.put("responsibility_code", saga.getResponsibilityCode());
        payload.put("counts_toward_paid_cancellation_rate",
                "MERCHANT".equals(saga.getResponsibilityParty())
                        && "PAID_UNSHIPPED".equals(saga.getCancellationMode()));
        payload.put("expected_reservation_count", saga.getExpectedReservationCount());
        payload.put("released_reservation_count", saga.getReleasedReservationCount());
        payload.put("reservations", reservations);
        OrderCancellationSagaFulfillmentDO fulfillment = fulfillmentMapper.selectBySaga(
                saga.getTenantId(), saga.getSagaId());
        payload.put("cancellation_mode", saga.getCancellationMode());
        payload.put("order_status_at_request", saga.getOrderStatusAtRequest());
        payload.put("step_ordinal", stepOrdinal(saga));
        payload.put("payment_id", saga.getPaymentId());
        payload.put("payment_refund_transaction_id", saga.getPaymentRefundTransactionId());
        payload.put("payment_status", saga.getPaymentStatus());
        payload.put("expected_fulfillment_count", saga.getExpectedFulfillmentCount());
        payload.put("cancelled_fulfillment_count", saga.getCancelledFulfillmentCount());
        payload.put("fulfillments", fulfillment == null ? List.of() : List.of(Map.of(
                "fulfillment_id", fulfillment.getFulfillmentId(), "status", fulfillment.getStatus())));
        payload.put("error_code", truncate(saga.getLastErrorCode(), 64));
        payload.put("error_message", truncate(saga.getLastErrorMessage(), 256));
        payload.put("next_retry_at", instant(saga.getNextRetryAt()));
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType("order.cancellation_saga.status_changed")
                .schemaVersion("PAID_UNSHIPPED".equals(saga.getCancellationMode()) ? 2 : 1)
                .sourceSystem("cloudmold-order").tenantId(saga.getTenantId())
                .aggregateType("order_cancellation_saga").aggregateId(saga.getSagaId())
                .aggregateVersion(saga.getVersion()).eventSequence((short) 1)
                .occurredAt(now.toInstant(ZoneOffset.UTC)).correlationId(saga.getCorrelationId())
                .causationId(saga.getCausationId())
                .idempotencyKey("cancel-saga:" + saga.getSagaId() + ":event:" + saga.getVersion())
                .payload(payload).headers(Map.of("active_step", saga.getActiveStep()))
                .destination("lakehouse").build());
    }

    static Duration backoff(int attempt) {
        long seconds = Math.min(MAX_BACKOFF.toSeconds(), 1L << Math.min(Math.max(attempt - 1, 0), 30));
        return Duration.ofSeconds(seconds);
    }

    private static int stepOrdinal(OrderCancellationSagaDO saga) {
        if ("COMPLETED".equals(saga.getStatus())) return 5;
        return switch (saga.getActiveStep()) {
            case "CANCEL_FULFILLMENT" -> 1;
            case "REFUND_PAYMENT" -> 2;
            case "RELEASE_RESERVATIONS" -> 3;
            case "CANCEL_ORDER" -> 4;
            default -> 0;
        };
    }

    private static String instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC).toString();
    }

    private static String summarize(Throwable failure) {
        String value = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        value = value.replaceAll("(?i)(password|token|secret|authorization)\\s*[=:]\\s*[^,;\\s]+", "$1=[REDACTED]");
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
