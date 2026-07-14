package cn.iocoder.yudao.module.cloudmold.order.service.cancellation;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.cloudmold.inventory.api.*;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.*;
import cn.iocoder.yudao.module.cloudmold.order.api.*;
import cn.iocoder.yudao.module.cloudmold.payment.api.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.cancellation.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.cancellation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderCancellationSagaWorker {

    static final Duration LEASE_DURATION = Duration.ofSeconds(30);

    private final OrderCancellationSagaMapper sagaMapper;
    private final OrderCancellationSagaItemMapper itemMapper;
    private final InventoryCommandApi inventoryCommandApi;
    private final FulfillmentCommandApi fulfillmentCommandApi;
    private final PaymentCommandApi paymentCommandApi;
    private final PaymentCancellationQueryApi paymentQueryApi;
    private final OrderCancellationSagaFulfillmentMapper fulfillmentMapper;
    private final OrderCommandApi orderCommandApi;
    private final OrderCancellationSagaCheckpointService checkpointService;

    public OrderCancellationSagaRunResult runBatch(String leaseOwner, int batchSize, LocalDateTime now) {
        require(leaseOwner != null && !leaseOwner.isBlank(), "leaseOwner is required");
        require(batchSize > 0 && batchSize <= 1000, "batchSize must be between 1 and 1000");
        require(now != null, "now is required");
        List<OrderCancellationSagaDO> candidates = sagaMapper.selectDue(now, batchSize);
        int claimed = 0;
        int completed = 0;
        int retry = 0;
        int manual = 0;
        for (OrderCancellationSagaDO candidate : candidates) {
            if (sagaMapper.claim(candidate.getTenantId(), candidate.getSagaId(), leaseOwner,
                    now.plus(LEASE_DURATION), now) != 1) continue;
            claimed++;
            try {
                TenantUtils.execute(candidate.getTenantId(), () -> process(candidate.getTenantId(),
                        candidate.getSagaId(), leaseOwner, now));
                completed++;
            } catch (Exception ignored) {
                OrderCancellationSagaDO current = sagaMapper.selectTenantSaga(candidate.getTenantId(),
                        candidate.getSagaId());
                if (current != null && "MANUAL_REVIEW".equals(current.getStatus())) manual++;
                else retry++;
            }
        }
        return new OrderCancellationSagaRunResult(candidates.size(), claimed, completed, retry, manual);
    }

    void process(Long tenantId, String sagaId, String leaseOwner, LocalDateTime now) {
        String activeItemId = null;
        try {
            OrderCancellationSagaDO saga = requireSaga(tenantId, sagaId);
            if ("PAID_UNSHIPPED".equals(saga.getCancellationMode())) {
                if (saga.getCancelledFulfillmentCount() < saga.getExpectedFulfillmentCount()) {
                    checkpointService.markProgress(tenantId, sagaId, leaseOwner,
                            "CANCELLING_FULFILLMENT", "CANCEL_FULFILLMENT", checkpointNow(now));
                    OrderCancellationSagaFulfillmentDO fulfillment = fulfillmentMapper.selectBySaga(tenantId, sagaId);
                    require(fulfillment != null, "paid cancellation Fulfillment snapshot is missing");
                    FulfillmentCommandResult cancelled = fulfillmentCommandApi.execute(FulfillmentCommand.builder()
                            .operation(FulfillmentOperation.FINALIZE_CANCELLATION)
                            .idempotencyKey(fulfillment.getFinalizeIdempotencyKey()).runId(saga.getRunId())
                            .fulfillmentId(fulfillment.getFulfillmentId())
                            .expectedVersion(fulfillment.getFulfillmentVersionAtRequest() + 1)
                            .orderId(saga.getOrderId()).reason(saga.getReason()).cancellationSagaId(sagaId)
                            .cancellationStepOrdinal(1).correlationId(saga.getCorrelationId())
                            .causationId(saga.getCausationId())
                            .occurredAt(fulfillment.getOccurredAt().toInstant(ZoneOffset.UTC)).build());
                    checkpointService.markFulfillmentCancelled(tenantId, sagaId, leaseOwner, cancelled,
                            checkpointNow(now));
                }
                saga = requireSaga(tenantId, sagaId);
                if (!"REFUNDED".equals(saga.getPaymentStatus())) {
                    checkpointService.markProgress(tenantId, sagaId, leaseOwner,
                            "REFUNDING_PAYMENT", "REFUND_PAYMENT", checkpointNow(now));
                    PaymentCancellationView payment = paymentQueryApi.requireCaptured(saga.getOrderId(),
                            saga.getPaymentId());
                    PaymentCommandResult refunded = paymentCommandApi.execute(PaymentCommand.builder()
                            .operation(PaymentOperation.REFUND)
                            .idempotencyKey("cancel-saga:" + sagaId + ":payment:" + saga.getPaymentId() + ":refund")
                            .runId(saga.getRunId()).paymentId(saga.getPaymentId())
                            .expectedVersion(saga.getPaymentVersionAtRequest()).orderId(saga.getOrderId())
                            .amountMinor(payment.getCapturedAmountMinor()).currencyCode(payment.getCurrencyCode())
                            .providerCode(payment.getProviderCode())
                            .providerTransactionId("cancel-saga:" + sagaId + ":refund:" + saga.getPaymentId())
                            .reason(saga.getReason()).cancellationSagaId(sagaId).cancellationStepOrdinal(2)
                            .correlationId(saga.getCorrelationId()).causationId(saga.getCausationId())
                            .occurredAt(saga.getPaymentRefundOccurredAt().toInstant(ZoneOffset.UTC)).build());
                    checkpointService.markPaymentRefunded(tenantId, sagaId, leaseOwner, refunded,
                            checkpointNow(now));
                }
            }
            // Paid flow has participant checkpoints before reservation release and must
            // reload their committed state. Keep the unpaid v1 path on its original
            // single snapshot so its retry behavior and mapper interaction stay stable.
            if ("PAID_UNSHIPPED".equals(saga.getCancellationMode())) {
                saga = requireSaga(tenantId, sagaId);
            }
            if (saga.getReleasedReservationCount() < saga.getExpectedReservationCount()) {
                checkpointService.markProgress(tenantId, sagaId, leaseOwner,
                        "RELEASING_RESERVATIONS", "RELEASE_RESERVATIONS", checkpointNow(now));
                while (true) {
                    OrderCancellationSagaItemDO item = itemMapper.selectNextRelease(tenantId, sagaId);
                    if (item == null) break;
                    activeItemId = item.getSagaItemId();
                    checkpointService.markItemRunning(tenantId, sagaId, activeItemId, leaseOwner,
                            checkpointNow(now));
                    InventoryCommandResult result = inventoryCommandApi.execute(InventoryCommand.builder()
                            .operation(InventoryOperation.RELEASE)
                            .idempotencyKey(item.getReleaseIdempotencyKey())
                            .ownerId(item.getOwnerId()).canonicalSkuId(item.getCanonicalSkuId())
                            .warehouseId(item.getWarehouseId()).stockStatus(item.getStockStatus())
                            .qualityStatus(item.getQualityStatus()).uomCode(item.getUomCode())
                            .quantity(item.getQuantity()).reservationId(item.getReservationId())
                            .businessType("TRADE_ORDER").businessId(saga.getOrderId())
                            .businessItemId(item.getOrderItemId()).businessNo(saga.getOrderNo())
                            .correlationId(saga.getCorrelationId()).causationId(saga.getCausationId())
                            .cancellationSagaId("PAID_UNSHIPPED".equals(saga.getCancellationMode()) ? sagaId : null)
                            .cancellationStepOrdinal("PAID_UNSHIPPED".equals(saga.getCancellationMode()) ? 3 : null)
                            .occurredAt(item.getOccurredAt().toInstant(ZoneOffset.UTC)).build());
                    checkpointService.markItemReleased(tenantId, sagaId, activeItemId, leaseOwner, result,
                            checkpointNow(now));
                    activeItemId = null;
                }
                saga = requireSaga(tenantId, sagaId);
                require(saga.getReleasedReservationCount().equals(saga.getExpectedReservationCount()),
                        "cancellation Saga release set is incomplete");
            } else if (!"RESERVATIONS_RELEASED".equals(saga.getStatus())
                    && !"CANCELLING_ORDER".equals(saga.getStatus())) {
                checkpointService.markProgress(tenantId, sagaId, leaseOwner,
                        "RESERVATIONS_RELEASED", "CANCEL_ORDER", checkpointNow(now));
            }

            checkpointService.markProgress(tenantId, sagaId, leaseOwner,
                    "CANCELLING_ORDER", "CANCEL_ORDER", checkpointNow(now));
            saga = requireSaga(tenantId, sagaId);
            OrderCommandResult cancelled = orderCommandApi.execute(OrderCommand.builder()
                    .operation(OrderOperation.FINALIZE_CANCELLATION)
                    .idempotencyKey("cancel-saga:" + sagaId + ":order-finalize")
                    .runId(saga.getRunId()).orderId(saga.getOrderId())
                    .expectedVersion(saga.getOrderVersionAtRequest() + 1)
                    .cancellationSagaId(sagaId).reason(saga.getReason())
                    .cancellationMode(saga.getCancellationMode())
                    .cancellationStepOrdinal("PAID_UNSHIPPED".equals(saga.getCancellationMode()) ? 4 : null)
                    .paymentId(saga.getPaymentId())
                    .fulfillmentId("PAID_UNSHIPPED".equals(saga.getCancellationMode())
                            ? fulfillmentMapper.selectBySaga(tenantId, sagaId).getFulfillmentId() : null)
                    .refundId(saga.getPaymentRefundTransactionId() == null ? null
                            : saga.getPaymentRefundTransactionId().toString())
                    .correlationId(saga.getCorrelationId()).causationId(saga.getCausationId())
                    .occurredAt(saga.getFinalizeOccurredAt().toInstant(ZoneOffset.UTC)).build());
            require("CANCELLED".equals(cancelled.getCurrentStatus()),
                    "canonical order cancellation was not finalized");
            checkpointService.markCompleted(tenantId, sagaId, leaseOwner, checkpointNow(now));
        } catch (Throwable failure) {
            try {
                checkpointService.markFailure(tenantId, sagaId, activeItemId, leaseOwner, failure,
                        checkpointNow(now));
            } catch (Throwable checkpointFailure) {
                failure.addSuppressed(checkpointFailure);
            }
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("cancellation Saga execution failed", failure);
        }
    }

    private OrderCancellationSagaDO requireSaga(Long tenantId, String sagaId) {
        OrderCancellationSagaDO saga = sagaMapper.selectTenantSaga(tenantId, sagaId);
        require(saga != null, "cancellation Saga does not exist");
        return saga;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static LocalDateTime checkpointNow(LocalDateTime floor) {
        LocalDateTime wallClock = LocalDateTime.now(ZoneOffset.UTC);
        return wallClock.isAfter(floor) ? wallClock : floor.plusNanos(1);
    }
}
