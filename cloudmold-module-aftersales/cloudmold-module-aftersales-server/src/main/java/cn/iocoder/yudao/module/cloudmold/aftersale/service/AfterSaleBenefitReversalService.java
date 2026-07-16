package cn.iocoder.yudao.module.cloudmold.aftersale.service;

import cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.mysql.*;
import cn.iocoder.yudao.module.cloudmold.order.api.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AfterSaleBenefitReversalService {
    private final AfterSaleBenefitReversalMapper reversalMapper;
    private final AfterSaleBenefitFundingReversalMapper fundingReversalMapper;
    private final OrderAfterSaleQueryApi orderQueryApi;
    private final AfterSaleEventService eventService;

    @Transactional(rollbackFor = Exception.class)
    public AfterSaleBenefitReversalResult record(AfterSaleResolutionSagaDO saga, LocalDateTime now) {
        require(saga != null && saga.getBenefitAmountMinor() != null && saga.getBenefitAmountMinor() > 0,
                "positive benefit amount is required for reversal");
        List<AfterSaleBenefitReversalDO> existing = reversalMapper.selectByAfterSale(
                saga.getTenantId(), saga.getAfterSaleId());
        if (!existing.isEmpty()) return resolveExisting(saga, existing);

        OrderAfterSaleView order = orderQueryApi.requireEligible(saga.getOrderId(), saga.getOrderItemId());
        require(Objects.equals(order.getLineAmountMinor(), saga.getGrossAmountMinor())
                        && Objects.equals(order.getDiscountAmountMinor(), saga.getBenefitAmountMinor())
                        && Objects.equals(order.getNetAmountMinor(), saga.getNetAmountMinor()),
                "order benefit money changed after after-sale admission");
        List<OrderBenefitApplicationView> applications = order.getBenefitApplications() == null
                ? List.of() : order.getBenefitApplications();
        require(!applications.isEmpty(), "discounted after-sale item has no immutable benefit applications");

        String batchId = UUID.randomUUID().toString();
        LocalDateTime occurredAt = Objects.requireNonNull(saga.getBenefitReversalOccurredAt(),
                "benefitReversalOccurredAt is required");
        long total = 0;
        int reversalCount = 0;
        int fundingCount = 0;
        for (OrderBenefitApplicationView application : applications) {
            require(application.getEntitlementId() == null,
                    "coupon entitlement reversal requires the Promotion return adapter");
            for (OrderBenefitAllocationView allocation : application.getAllocations()) {
                require(Objects.equals(allocation.getOrderItemId(), saga.getOrderItemId()),
                        "benefit allocation does not belong to after-sale order item");
                long allocationAmount = requiredPositive(allocation.getAmountMinor(),
                        "benefit allocation amount must be positive");
                long fundingTotal = 0;
                List<OrderBenefitFundingView> fundingViews = allocation.getFunding() == null
                        ? List.of() : allocation.getFunding();
                require(!fundingViews.isEmpty(), "benefit allocation has no funding shares");
                AfterSaleBenefitReversalDO reversal = new AfterSaleBenefitReversalDO()
                        .setBenefitReversalId(UUID.randomUUID().toString()).setTenantId(saga.getTenantId())
                        .setReversalBatchId(batchId).setAfterSaleId(saga.getAfterSaleId())
                        .setAfterSaleItemId(saga.getAfterSaleItemId()).setOrderId(saga.getOrderId())
                        .setOrderItemId(saga.getOrderItemId())
                        .setBenefitApplicationId(application.getBenefitApplicationId())
                        .setBenefitAllocationId(allocation.getBenefitAllocationId())
                        .setBenefitType(application.getBenefitType())
                        .setBenefitSourceType(application.getBenefitSourceType())
                        .setBenefitSourceId(application.getBenefitSourceId())
                        .setBenefitSourceVersion(application.getBenefitSourceVersion())
                        .setEntitlementId(null).setAmountMinor(allocationAmount).setCurrencyCode("CNY")
                        .setEntitlementEffectStatus("NOT_REQUIRED").setOccurredAt(occurredAt).setCreatedAt(now);
                require(reversalMapper.insert(reversal) == 1, "failed to record benefit reversal");
                List<AfterSaleBenefitFundingReversalDO> fundingRows = new ArrayList<>();
                for (OrderBenefitFundingView funding : fundingViews) {
                    long amount = requiredPositive(funding.getAmountMinor(),
                            "benefit funding reversal amount must be positive");
                    fundingTotal = Math.addExact(fundingTotal, amount);
                    AfterSaleBenefitFundingReversalDO row = new AfterSaleBenefitFundingReversalDO()
                            .setFundingReversalId(UUID.randomUUID().toString()).setTenantId(saga.getTenantId())
                            .setBenefitReversalId(reversal.getBenefitReversalId()).setReversalBatchId(batchId)
                            .setAfterSaleId(saga.getAfterSaleId())
                            .setBenefitFundingId(funding.getBenefitFundingId())
                            .setFunderType(funding.getFunderType()).setFunderId(funding.getFunderId())
                            .setAmountMinor(amount).setCurrencyCode("CNY")
                            .setOccurredAt(occurredAt).setCreatedAt(now);
                    require(fundingReversalMapper.insert(row) == 1,
                            "failed to record benefit funding reversal");
                    fundingRows.add(row);
                    fundingCount++;
                }
                require(fundingTotal == allocationAmount,
                        "benefit funding reversals do not conserve allocation amount");
                eventService.appendBenefitReversal(saga, reversal, fundingRows, now);
                total = Math.addExact(total, allocationAmount);
                reversalCount++;
            }
        }
        require(total == saga.getBenefitAmountMinor(),
                "benefit reversals do not conserve after-sale benefit amount");
        return new AfterSaleBenefitReversalResult(batchId, reversalCount, fundingCount, total);
    }

    private AfterSaleBenefitReversalResult resolveExisting(AfterSaleResolutionSagaDO saga,
                                                            List<AfterSaleBenefitReversalDO> reversals) {
        Set<String> batches = new HashSet<>();
        long total = 0;
        for (AfterSaleBenefitReversalDO reversal : reversals) {
            batches.add(reversal.getReversalBatchId());
            total = Math.addExact(total, reversal.getAmountMinor());
        }
        require(batches.size() == 1 && total == saga.getBenefitAmountMinor(),
                "existing benefit reversals conflict with Saga snapshot");
        List<AfterSaleBenefitFundingReversalDO> funding = fundingReversalMapper.selectByAfterSale(
                saga.getTenantId(), saga.getAfterSaleId());
        long fundingTotal = funding.stream().mapToLong(AfterSaleBenefitFundingReversalDO::getAmountMinor).sum();
        require(fundingTotal == total, "existing funding reversals do not conserve benefit amount");
        return new AfterSaleBenefitReversalResult(batches.iterator().next(), reversals.size(), funding.size(), total);
    }

    private static long requiredPositive(Long value, String message) {
        require(value != null && value > 0, message);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
