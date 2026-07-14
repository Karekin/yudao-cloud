package cn.iocoder.yudao.module.cloudmold.aftersale.service;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject.AfterSaleResolutionSagaDO;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.mysql.AfterSaleResolutionSagaMapper;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.ReturnFulfillmentQueryApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.*;
import cn.iocoder.yudao.module.cloudmold.order.api.*;
import cn.iocoder.yudao.module.cloudmold.payment.api.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AfterSaleResolutionWorker {
    static final Duration LEASE_DURATION = Duration.ofSeconds(30);

    private final AfterSaleResolutionSagaMapper sagaMapper;
    private final ReturnFulfillmentQueryApi returnFulfillmentQueryApi;
    private final InventoryCommandApi inventoryCommandApi;
    private final PaymentCommandApi paymentCommandApi;
    private final OrderCommandApi orderCommandApi;
    private final AfterSaleResolutionCheckpointService checkpointService;

    public AfterSaleResolutionRunResult runBatch(String leaseOwner, int batchSize, LocalDateTime now) {
        require(leaseOwner != null && !leaseOwner.isBlank(), "leaseOwner is required");
        require(batchSize > 0 && batchSize <= 1000, "batchSize must be between 1 and 1000");
        List<AfterSaleResolutionSagaDO> candidates = sagaMapper.selectDue(now, batchSize);
        int claimed = 0, completed = 0, retry = 0, manual = 0;
        for (AfterSaleResolutionSagaDO candidate : candidates) {
            if (sagaMapper.claim(candidate.getTenantId(), candidate.getSagaId(), leaseOwner,
                    now.plus(LEASE_DURATION), now) != 1) continue;
            claimed++;
            try {
                TenantUtils.execute(candidate.getTenantId(), () -> process(candidate.getTenantId(),
                        candidate.getSagaId(), leaseOwner, now));
                completed++;
            } catch (Exception ignored) {
                AfterSaleResolutionSagaDO current = sagaMapper.selectTenant(candidate.getTenantId(),
                        candidate.getSagaId());
                if (current != null && "MANUAL_REVIEW".equals(current.getStatus())) manual++;
                else retry++;
            }
        }
        return new AfterSaleResolutionRunResult(candidates.size(), claimed, completed, retry, manual);
    }

    void process(Long tenantId, String sagaId, String leaseOwner, LocalDateTime floor) {
        try {
            AfterSaleResolutionSagaDO saga = requireSaga(tenantId, sagaId);
            returnFulfillmentQueryApi.requireInspectionAccepted(saga.getAfterSaleId(),
                    saga.getReturnFulfillmentId());
            if (saga.getInventoryLedgerTransactionId() == null) {
                checkpointService.markProgress(tenantId, sagaId, leaseOwner,
                        "RETURNING_INVENTORY", "RETURN_INVENTORY", checkpointNow(floor));
                saga = requireSaga(tenantId, sagaId);
                InventoryCommandResult returned = inventoryCommandApi.execute(InventoryCommand.builder()
                        .operation(InventoryOperation.RETURN)
                        .idempotencyKey("after-sale-saga:" + sagaId + ":inventory-return")
                        .ownerId(saga.getOwnerId()).canonicalSkuId(saga.getCanonicalSkuId())
                        .warehouseId(saga.getWarehouseId()).stockStatus("SELLABLE").qualityStatus("QUALIFIED")
                        .uomCode(saga.getUomCode()).quantity(saga.getQuantity())
                        .businessType("AFTER_SALE_RETURN").businessId(saga.getAfterSaleId())
                        .businessItemId(saga.getAfterSaleItemId()).businessNo(saga.getOrderNo())
                        .correlationId(saga.getCorrelationId()).causationId(saga.getCausationId())
                        .occurredAt(saga.getInventoryOccurredAt().toInstant(ZoneOffset.UTC)).build());
                checkpointService.markInventoryReturned(tenantId, sagaId, leaseOwner, returned,
                        checkpointNow(floor));
            }
            saga = requireSaga(tenantId, sagaId);
            if (saga.getPaymentRefundTransactionId() == null) {
                checkpointService.markProgress(tenantId, sagaId, leaseOwner,
                        "REFUNDING_PAYMENT", "REFUND_PAYMENT", checkpointNow(floor));
                saga = requireSaga(tenantId, sagaId);
                PaymentCommandResult refunded = paymentCommandApi.execute(PaymentCommand.builder()
                        .operation(PaymentOperation.REFUND)
                        .idempotencyKey("after-sale-saga:" + sagaId + ":payment-refund")
                        .runId(saga.getRunId()).paymentId(saga.getPaymentId())
                        .expectedVersion(saga.getPaymentVersionAtRequest()).orderId(saga.getOrderId())
                        .amountMinor(saga.getApprovedAmountMinor()).currencyCode(saga.getCurrencyCode())
                        .providerCode("INTERNAL_TEST")
                        .providerTransactionId("after-sale-saga:" + sagaId + ":refund:" + saga.getPaymentId())
                        .reason(saga.getReason()).correlationId(saga.getCorrelationId())
                        .causationId(saga.getCausationId())
                        .occurredAt(saga.getPaymentOccurredAt().toInstant(ZoneOffset.UTC)).build());
                checkpointService.markPaymentRefunded(tenantId, sagaId, leaseOwner, refunded,
                        checkpointNow(floor));
            }
            saga = requireSaga(tenantId, sagaId);
            if (saga.getOrderRefundOperationId() == null) {
                checkpointService.markProgress(tenantId, sagaId, leaseOwner,
                        "CONFIRMING_ORDER_REFUND", "CONFIRM_ORDER_REFUND", checkpointNow(floor));
                saga = requireSaga(tenantId, sagaId);
                OrderCommandResult refunded = orderCommandApi.execute(OrderCommand.builder()
                        .operation(OrderOperation.CONFIRM_REFUND)
                        .idempotencyKey("after-sale-saga:" + sagaId + ":order-confirm-refund")
                        .runId(saga.getRunId()).orderId(saga.getOrderId())
                        .expectedVersion(saga.getOrderVersionAtRequest())
                        .refundId(String.valueOf(saga.getPaymentRefundTransactionId())).reason(saga.getReason())
                        .correlationId(saga.getCorrelationId()).causationId(saga.getCausationId())
                        .occurredAt(saga.getOrderRefundOccurredAt().toInstant(ZoneOffset.UTC)).build());
                checkpointService.markOrderRefunded(tenantId, sagaId, leaseOwner, refunded,
                        checkpointNow(floor));
            }
            saga = requireSaga(tenantId, sagaId);
            if (saga.getOrderReturnOperationId() == null) {
                checkpointService.markProgress(tenantId, sagaId, leaseOwner,
                        "RETURNING_ORDER", "RETURN_ORDER", checkpointNow(floor));
                saga = requireSaga(tenantId, sagaId);
                OrderCommandResult returned = orderCommandApi.execute(OrderCommand.builder()
                        .operation(OrderOperation.RETURN)
                        .idempotencyKey("after-sale-saga:" + sagaId + ":order-return")
                        .runId(saga.getRunId()).orderId(saga.getOrderId()).expectedVersion(saga.getOrderVersion())
                        .reason(saga.getReason()).correlationId(saga.getCorrelationId())
                        .causationId(saga.getCausationId())
                        .occurredAt(saga.getOrderReturnOccurredAt().toInstant(ZoneOffset.UTC)).build());
                checkpointService.markOrderReturned(tenantId, sagaId, leaseOwner, returned,
                        checkpointNow(floor));
            }
            checkpointService.markCompleted(tenantId, sagaId, leaseOwner, checkpointNow(floor));
        } catch (Throwable failure) {
            try {
                checkpointService.markFailure(tenantId, sagaId, leaseOwner, failure, checkpointNow(floor));
            } catch (Throwable checkpointFailure) {
                failure.addSuppressed(checkpointFailure);
            }
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("after-sale resolution Saga failed", failure);
        }
    }

    private AfterSaleResolutionSagaDO requireSaga(Long tenantId, String sagaId) {
        AfterSaleResolutionSagaDO saga = sagaMapper.selectTenant(tenantId, sagaId);
        require(saga != null, "resolution Saga does not exist");
        return saga;
    }

    private static LocalDateTime checkpointNow(LocalDateTime floor) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return now.isAfter(floor) ? now : floor.plusNanos(1);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
