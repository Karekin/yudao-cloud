package cn.iocoder.yudao.module.cloudmold.aftersale.service;

import cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.mysql.*;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryCommandResult;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderCommandResult;
import cn.iocoder.yudao.module.cloudmold.payment.api.PaymentCommandResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AfterSaleResolutionCheckpointService {
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(30);

    private final AfterSaleResolutionSagaMapper sagaMapper;
    private final AfterSaleCaseMapper caseMapper;
    private final AfterSaleItemMapper itemMapper;
    private final AfterSaleEventService eventService;

    @Transactional(rollbackFor = Exception.class)
    public void markProgress(Long tenantId, String sagaId, String leaseOwner, String status,
                             String activeStep, LocalDateTime now) {
        AfterSaleResolutionSagaDO saga = requireLeased(tenantId, sagaId, leaseOwner);
        if (status.equals(saga.getStatus()) && activeStep.equals(saga.getActiveStep())) return;
        String previous = saga.getStatus();
        saga.setStatus(status).setActiveStep(activeStep).setVersion(saga.getVersion() + 1)
                .setNextRetryAt(null).setLastErrorCode(null).setLastErrorMessage(null).setUpdatedAt(now);
        require(sagaMapper.updateById(saga) == 1, "resolution Saga progress conflict");
        eventService.appendSaga(saga, previous, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markInventoryReturned(Long tenantId, String sagaId, String leaseOwner,
                                      InventoryCommandResult result, LocalDateTime now) {
        AfterSaleResolutionSagaDO saga = requireLeased(tenantId, sagaId, leaseOwner);
        String previous = saga.getStatus();
        saga.setInventoryOperationId(result.getOperationId())
                .setInventoryLedgerTransactionId(result.getLedgerTransactionId())
                .setStatus("INVENTORY_RETURNED").setActiveStep(
                        "PENDING".equals(saga.getBenefitReversalStatus()) ? "REVERSE_BENEFITS" : "REFUND_PAYMENT")
                .setVersion(saga.getVersion() + 1).setUpdatedAt(now);
        require(sagaMapper.updateById(saga) == 1, "inventory return checkpoint conflict");
        eventService.appendSaga(saga, previous, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markBenefitsReversed(Long tenantId, String sagaId, String leaseOwner,
                                     AfterSaleBenefitReversalResult result, LocalDateTime now) {
        AfterSaleResolutionSagaDO saga = requireLeased(tenantId, sagaId, leaseOwner);
        require("PENDING".equals(saga.getBenefitReversalStatus())
                        && result.reversalCount() > 0 && result.fundingReversalCount() > 0
                        && Objects.equals(result.amountMinor(), saga.getBenefitAmountMinor()),
                "benefit reversal does not reconcile with after-sale snapshot");
        String previous = saga.getStatus();
        saga.setBenefitReversalStatus("RECORDED").setBenefitReversalBatchId(result.batchId())
                .setBenefitReversalAmountMinor(result.amountMinor())
                .setStatus("BENEFITS_REVERSED").setActiveStep("REFUND_PAYMENT")
                .setVersion(saga.getVersion() + 1).setUpdatedAt(now);
        require(sagaMapper.updateById(saga) == 1, "benefit reversal checkpoint conflict");
        eventService.appendSaga(saga, previous, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markPaymentRefunded(Long tenantId, String sagaId, String leaseOwner,
                                    PaymentCommandResult result, LocalDateTime now) {
        AfterSaleResolutionSagaDO saga = requireLeased(tenantId, sagaId, leaseOwner);
        require(Objects.equals(result.getCapturedAmountMinor(), saga.getApprovedAmountMinor())
                        && Objects.equals(result.getRefundedAmountMinor(), saga.getApprovedAmountMinor())
                        && Objects.equals(result.getCurrencyCode(), saga.getCurrencyCode()),
                "payment refund does not reconcile with after-sale entitlement");
        AfterSaleCaseDO sale = caseMapper.selectForUpdate(tenantId, saga.getAfterSaleId());
        require(sale != null && "REQUESTED".equals(sale.getRefundStatus()),
                "after-sale refund entitlement is not pending");
        AfterSaleItemDO item = requireSingleItem(tenantId, sale.getAfterSaleId());
        require(caseMapper.markRefundSucceeded(tenantId, sale.getAfterSaleId(), now) == 1,
                "after-sale refund status conflict");
        sale.setRefundStatus("SUCCEEDED").setUpdatedAt(now);
        eventService.appendRefund(sale, item, "REQUESTED", "SUCCEEDED", 2L,
                result.getTransactionId(), saga.getPaymentOccurredAt().toInstant(ZoneOffset.UTC), now);
        String previous = saga.getStatus();
        saga.setPaymentRefundTransactionId(result.getTransactionId()).setStatus("PAYMENT_REFUNDED")
                .setActiveStep("CONFIRM_ORDER_REFUND").setVersion(saga.getVersion() + 1).setUpdatedAt(now);
        require(sagaMapper.updateById(saga) == 1, "payment refund checkpoint conflict");
        eventService.appendSaga(saga, previous, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markOrderRefunded(Long tenantId, String sagaId, String leaseOwner,
                                  OrderCommandResult result, LocalDateTime now) {
        AfterSaleResolutionSagaDO saga = requireLeased(tenantId, sagaId, leaseOwner);
        require("REFUNDED".equals(result.getCurrentStatus()), "canonical order is not REFUNDED");
        String previous = saga.getStatus();
        saga.setOrderRefundOperationId(result.getOperationId()).setOrderVersion(result.getAggregateVersion())
                .setStatus("ORDER_REFUNDED").setActiveStep("RETURN_ORDER")
                .setVersion(saga.getVersion() + 1).setUpdatedAt(now);
        require(sagaMapper.updateById(saga) == 1, "order refund checkpoint conflict");
        eventService.appendSaga(saga, previous, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markOrderReturned(Long tenantId, String sagaId, String leaseOwner,
                                  OrderCommandResult result, LocalDateTime now) {
        AfterSaleResolutionSagaDO saga = requireLeased(tenantId, sagaId, leaseOwner);
        require("RETURNED".equals(result.getCurrentStatus()), "canonical order is not RETURNED");
        String previous = saga.getStatus();
        saga.setOrderReturnOperationId(result.getOperationId()).setOrderVersion(result.getAggregateVersion())
                .setStatus("ORDER_RETURNED").setActiveStep("COMPLETE")
                .setVersion(saga.getVersion() + 1).setUpdatedAt(now);
        require(sagaMapper.updateById(saga) == 1, "order return checkpoint conflict");
        eventService.appendSaga(saga, previous, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markCompleted(Long tenantId, String sagaId, String leaseOwner, LocalDateTime now) {
        AfterSaleResolutionSagaDO saga = requireLeased(tenantId, sagaId, leaseOwner);
        require(saga.getInventoryLedgerTransactionId() != null && saga.getPaymentRefundTransactionId() != null
                        && saga.getOrderRefundOperationId() != null && saga.getOrderReturnOperationId() != null,
                "resolution Saga participant checkpoints are incomplete");
        require("NOT_REQUIRED".equals(saga.getBenefitReversalStatus())
                        || ("RECORDED".equals(saga.getBenefitReversalStatus())
                        && saga.getBenefitReversalBatchId() != null
                        && Objects.equals(saga.getBenefitReversalAmountMinor(), saga.getBenefitAmountMinor())),
                "resolution Saga benefit reversal checkpoint is incomplete");
        AfterSaleCaseDO sale = caseMapper.selectForUpdate(tenantId, saga.getAfterSaleId());
        require(sale != null && Objects.equals(sale.getResolutionSagaId(), sagaId),
                "resolution Saga does not own after-sale case");
        require(caseMapper.complete(tenantId, sale.getAfterSaleId(), sale.getVersion(), now) == 1,
                "after-sale completion conflict");
        require(itemMapper.releaseActiveGuard(tenantId, sale.getAfterSaleId()) == 1,
                "after-sale active item guard release conflict");
        String casePrevious = sale.getStatus();
        sale.setStatus("COMPLETED").setVersion(sale.getVersion() + 1).setCompletedAt(now).setUpdatedAt(now);
        eventService.appendCase(null, sale, requireSingleItem(tenantId, sale.getAfterSaleId()), casePrevious,
                now.toInstant(ZoneOffset.UTC), now);
        String previous = saga.getStatus();
        saga.setStatus("COMPLETED").setActiveStep("NONE").setLeaseOwner(null).setLeaseUntil(null)
                .setNextRetryAt(null).setLastErrorCode(null).setLastErrorMessage(null).setCompletedAt(now)
                .setVersion(saga.getVersion() + 1).setUpdatedAt(now);
        require(sagaMapper.updateById(saga) == 1, "resolution Saga completion conflict");
        eventService.appendSaga(saga, previous, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markFailure(Long tenantId, String sagaId, String leaseOwner, Throwable failure,
                            LocalDateTime now) {
        AfterSaleResolutionSagaDO saga = requireLeased(tenantId, sagaId, leaseOwner);
        String previous = saga.getStatus();
        boolean exhausted = saga.getAttemptCount() >= saga.getMaxAttempts();
        saga.setStatus(exhausted ? "MANUAL_REVIEW" : "RETRY_SCHEDULED")
                .setNextRetryAt(exhausted ? null : now.plus(backoff(saga.getAttemptCount())))
                .setLastErrorCode(failure.getClass().getSimpleName())
                .setLastErrorMessage(summarize(failure)).setLeaseOwner(null).setLeaseUntil(null)
                .setVersion(saga.getVersion() + 1).setUpdatedAt(now);
        require(sagaMapper.updateById(saga) == 1, "resolution Saga failure checkpoint conflict");
        eventService.appendSaga(saga, previous, now);
    }

    @Transactional(rollbackFor = Exception.class)
    public AfterSaleResolutionSagaDO retryManually(Long tenantId, String sagaId, Long expectedVersion,
                                                    LocalDateTime now) {
        AfterSaleResolutionSagaDO saga = sagaMapper.selectForUpdate(tenantId, sagaId);
        require(saga != null && "MANUAL_REVIEW".equals(saga.getStatus()),
                "manual retry requires MANUAL_REVIEW");
        require(Objects.equals(expectedVersion, saga.getVersion()), "resolution Saga version conflict");
        String previous = saga.getStatus();
        saga.setStatus("RETRY_SCHEDULED").setAttemptCount(0).setNextRetryAt(now)
                .setLeaseOwner(null).setLeaseUntil(null).setLastErrorCode(null).setLastErrorMessage(null)
                .setVersion(saga.getVersion() + 1).setUpdatedAt(now);
        require(sagaMapper.updateById(saga) == 1, "resolution Saga manual retry conflict");
        eventService.appendSaga(saga, previous, now);
        return saga;
    }

    private AfterSaleResolutionSagaDO requireLeased(Long tenantId, String sagaId, String leaseOwner) {
        AfterSaleResolutionSagaDO saga = sagaMapper.selectForUpdate(tenantId, sagaId);
        require(saga != null && Objects.equals(leaseOwner, saga.getLeaseOwner()), "resolution Saga lease was lost");
        return saga;
    }

    private AfterSaleItemDO requireSingleItem(Long tenantId, String afterSaleId) {
        List<AfterSaleItemDO> items = itemMapper.selectByAfterSale(tenantId, afterSaleId);
        require(items.size() == 1, "first slice requires exactly one after-sale item");
        return items.get(0);
    }

    static Duration backoff(int attempt) {
        return Duration.ofSeconds(Math.min(MAX_BACKOFF.toSeconds(),
                1L << Math.min(Math.max(attempt - 1, 0), 30)));
    }

    private static String summarize(Throwable failure) {
        String value = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        value = value.replaceAll("(?i)(password|token|secret|authorization)\\s*[=:]\\s*[^,;\\s]+",
                "$1=[REDACTED]");
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
