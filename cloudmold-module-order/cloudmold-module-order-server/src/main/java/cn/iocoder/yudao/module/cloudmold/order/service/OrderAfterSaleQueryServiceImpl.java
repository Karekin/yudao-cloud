package cn.iocoder.yudao.module.cloudmold.order.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderAfterSaleQueryApi;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderAfterSaleView;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderBenefitAllocationView;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderBenefitApplicationView;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderBenefitFundingView;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.OrderHeaderDO;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.OrderItemDO;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.OrderBenefitAllocationDO;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.OrderBenefitApplicationDO;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.OrderBenefitFundingDO;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.OrderItemReturnSettlementDO;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderAfterSaleQueryServiceImpl implements OrderAfterSaleQueryApi {
    private final OrderHeaderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final OrderBenefitApplicationMapper benefitApplicationMapper;
    private final OrderBenefitAllocationMapper benefitAllocationMapper;
    private final OrderBenefitFundingMapper benefitFundingMapper;
    private final OrderItemReturnSettlementMapper itemReturnSettlementMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderAfterSaleView requireEligible(String orderId, String orderItemId) {
        return requireEligible(orderId, orderItemId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderAfterSaleView requireEligible(String orderId, String orderItemId, BigDecimal requestedQuantity) {
        requireText(orderId, "orderId");
        requireText(orderItemId, "orderItemId");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        OrderHeaderDO order = orderMapper.selectForUpdate(tenantId, orderId);
        require(order != null, "canonical order does not exist");
        require("COMPLETED".equals(order.getStatus()), "after sale requires COMPLETED order");
        requireText(order.getPaymentId(), "paymentId");
        requireText(order.getFulfillmentId(), "fulfillmentId");
        requireText(order.getShipmentId(), "shipmentId");
        require("CNY".equals(order.getCurrencyCode()), "after-sale return settlement supports CNY only");
        require(Objects.equals(order.getShippingAmountMinor(), 0L),
                "partial return settlement requires an explicit zero-freight policy");
        List<OrderItemDO> items = itemMapper.selectByOrder(tenantId, orderId);
        require(!items.isEmpty(), "canonical order has no items");
        OrderItemDO item = items.stream().filter(value -> Objects.equals(value.getOrderItemId(), orderItemId))
                .findFirst().orElseThrow(() -> new IllegalStateException("order item does not belong to order"));
        require(item.getQuantity().signum() > 0 && item.getQuantity().stripTrailingZeros().scale() <= 0,
                "after-sale return settlement requires whole-piece quantity");
        require(item.getUnitPriceMinor() != null && item.getUnitPriceMinor() >= 0
                        && Objects.equals(item.getLineAmountMinor(), Math.multiplyExact(
                        item.getUnitPriceMinor(), item.getQuantity().longValueExact())),
                "after-sale order item gross money does not reconcile with unit price and quantity");
        require(item.getLineAmountMinor() >= 0 && item.getDiscountAmountMinor() >= 0
                        && Objects.equals(item.getNetAmountMinor(),
                        Math.subtractExact(item.getLineAmountMinor(), item.getDiscountAmountMinor())),
                "after-sale order item money does not reconcile");
        OrderItemReturnSettlementDO settlement = itemReturnSettlementMapper.selectTenant(tenantId, orderItemId);
        BigDecimal previouslyReturned = settlement == null ? BigDecimal.ZERO : settlement.getReturnedQuantity();
        BigDecimal remaining = item.getQuantity().subtract(previouslyReturned);
        require(remaining.signum() > 0, "order item is already fully returned");
        BigDecimal quantity = requestedQuantity == null ? remaining : requestedQuantity;
        require(quantity.signum() > 0 && quantity.stripTrailingZeros().scale() <= 0,
                "requestedQuantity must be positive whole pieces");
        require(quantity.compareTo(remaining) <= 0, "requestedQuantity exceeds remaining returnable quantity");

        List<OrderBenefitApplicationView> benefits = benefitViews(tenantId, orderId, item.getOrderItemId(),
                previouslyReturned, quantity, item.getQuantity(), item.getDiscountAmountMinor());
        long benefitAmount = benefits.stream().mapToLong(value -> value.getReturnAmountMinor() == null
                ? 0L : value.getReturnAmountMinor()).sum();
        long grossAmount = Math.multiplyExact(item.getUnitPriceMinor(), quantity.longValueExact());
        long netAmount = Math.subtractExact(grossAmount, benefitAmount);
        require(netAmount > 0, "current partial-return slice requires a positive cash refund");
        requireText(item.getReservationId(), "reservationId");
        requireText(item.getListingId(), "listingId");
        requireText(item.getListingOfferId(), "listingOfferId");
        return OrderAfterSaleView.builder().orderId(order.getOrderId()).orderNo(order.getOrderNo())
                .buyerId(order.getBuyerId()).status(order.getStatus()).aggregateVersion(order.getVersion())
                .payableAmountMinor(order.getPayableAmountMinor()).currencyCode(order.getCurrencyCode())
                .paymentId(order.getPaymentId()).fulfillmentId(order.getFulfillmentId())
                .shipmentId(order.getShipmentId()).orderItemId(item.getOrderItemId())
                .canonicalSkuId(item.getCanonicalSkuId()).orderedQuantity(item.getQuantity())
                .previouslyReturnedQuantity(previouslyReturned).remainingReturnableQuantity(remaining)
                .quantity(quantity).orderedLineAmountMinor(item.getLineAmountMinor())
                .orderedDiscountAmountMinor(item.getDiscountAmountMinor())
                .orderedNetAmountMinor(item.getNetAmountMinor())
                .lineAmountMinor(grossAmount).discountAmountMinor(benefitAmount).netAmountMinor(netAmount)
                .benefitApplications(benefits)
                .reservationId(item.getReservationId())
                .listingId(item.getListingId()).listingOfferId(item.getListingOfferId()).build();
    }

    private List<OrderBenefitApplicationView> benefitViews(Long tenantId, String orderId, String orderItemId,
                                                            BigDecimal previouslyReturned,
                                                            BigDecimal requestedQuantity,
                                                            BigDecimal orderedQuantity,
                                                            long expectedBenefitAmount) {
        List<OrderBenefitApplicationDO> applications = benefitApplicationMapper.selectByOrder(tenantId, orderId);
        List<OrderBenefitAllocationDO> allocations = benefitAllocationMapper.selectByOrder(tenantId, orderId);
        List<OrderBenefitAllocationDO> itemAllocations = allocations.stream()
                .filter(value -> Objects.equals(value.getOrderItemId(), orderItemId)).toList();
        require(itemAllocations.stream().mapToLong(OrderBenefitAllocationDO::getAmountMinor).sum()
                        == expectedBenefitAmount,
                "immutable benefit allocations do not reconcile with order item discount");
        if (expectedBenefitAmount == 0) return List.of();
        require(!applications.isEmpty() && !itemAllocations.isEmpty(),
                "discounted order item has no immutable benefit allocation");
        Set<String> applicationIds = applications.stream().map(OrderBenefitApplicationDO::getBenefitApplicationId)
                .collect(java.util.stream.Collectors.toSet());
        require(itemAllocations.stream().allMatch(value -> applicationIds.contains(value.getBenefitApplicationId())),
                "benefit allocation references an unknown application");
        Map<String, List<OrderBenefitFundingDO>> fundingByAllocation = benefitFundingMapper
                .selectByOrder(tenantId, orderId).stream().collect(java.util.stream.Collectors.groupingBy(
                        OrderBenefitFundingDO::getBenefitAllocationId, LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        for (OrderBenefitAllocationDO allocation : itemAllocations) {
            require(fundingByAllocation.getOrDefault(allocation.getBenefitAllocationId(), List.of()).stream()
                            .mapToLong(OrderBenefitFundingDO::getAmountMinor).sum() == allocation.getAmountMinor(),
                    "immutable benefit funding does not reconcile with allocation");
        }
        Map<String, List<OrderBenefitAllocationDO>> allocationsByApplication = itemAllocations.stream()
                .collect(java.util.stream.Collectors.groupingBy(OrderBenefitAllocationDO::getBenefitApplicationId,
                        LinkedHashMap::new, java.util.stream.Collectors.toList()));
        return applications.stream()
                .filter(value -> allocationsByApplication.containsKey(value.getBenefitApplicationId()))
                .map(application -> partialBenefitView(application,
                        allocationsByApplication.get(application.getBenefitApplicationId()), fundingByAllocation,
                        previouslyReturned, requestedQuantity, orderedQuantity))
                .filter(Objects::nonNull)
                .toList();
    }

    private static OrderBenefitApplicationView partialBenefitView(OrderBenefitApplicationDO application,
                                                                   List<OrderBenefitAllocationDO> allocations,
                                                                   Map<String, List<OrderBenefitFundingDO>> funding,
                                                                   BigDecimal previousQuantity,
                                                                   BigDecimal requestedQuantity,
                                                                   BigDecimal orderedQuantity) {
        List<OrderBenefitAllocationView> allocationViews = new ArrayList<>();
        long applicationReturnAmount = 0;
        for (OrderBenefitAllocationDO allocation : allocations) {
            long returnAmount = proportionalDelta(allocation.getAmountMinor(), previousQuantity,
                    requestedQuantity, orderedQuantity);
            if (returnAmount == 0) continue;
            List<OrderBenefitFundingDO> sourceFunding = new ArrayList<>(funding.getOrDefault(
                    allocation.getBenefitAllocationId(), List.of()));
            sourceFunding.sort(Comparator.comparing(OrderBenefitFundingDO::getFundingKey)
                    .thenComparing(OrderBenefitFundingDO::getBenefitFundingId));
            require(!sourceFunding.isEmpty(), "benefit allocation has no funding shares");
            List<OrderBenefitFundingView> fundingViews = new ArrayList<>();
            long assigned = 0;
            for (int index = 0; index < sourceFunding.size(); index++) {
                OrderBenefitFundingDO share = sourceFunding.get(index);
                long shareReturn = index == sourceFunding.size() - 1 ? returnAmount - assigned
                        : proportionalDelta(share.getAmountMinor(), previousQuantity,
                        requestedQuantity, orderedQuantity);
                if (shareReturn > 0) {
                    fundingViews.add(OrderBenefitFundingView.builder()
                            .benefitFundingId(share.getBenefitFundingId()).fundingKey(share.getFundingKey())
                            .funderType(share.getFunderType()).funderId(share.getFunderId())
                            .amountMinor(share.getAmountMinor()).returnAmountMinor(shareReturn)
                            .currencyCode(share.getCurrencyCode()).build());
                }
                assigned = Math.addExact(assigned, shareReturn);
            }
            require(assigned == returnAmount, "partial benefit funding does not conserve allocation amount");
            allocationViews.add(OrderBenefitAllocationView.builder()
                    .benefitAllocationId(allocation.getBenefitAllocationId())
                    .allocationKey(allocation.getAllocationKey()).orderItemId(allocation.getOrderItemId())
                    .lineKey(allocation.getLineKey()).amountMinor(allocation.getAmountMinor())
                    .returnAmountMinor(returnAmount).currencyCode(allocation.getCurrencyCode())
                    .funding(fundingViews).build());
            applicationReturnAmount = Math.addExact(applicationReturnAmount, returnAmount);
        }
        if (applicationReturnAmount == 0) return null;
        return OrderBenefitApplicationView.builder()
                .benefitApplicationId(application.getBenefitApplicationId())
                .applicationKey(application.getApplicationKey()).benefitType(application.getBenefitType())
                .benefitSourceType(application.getBenefitSourceType())
                .benefitSourceId(application.getBenefitSourceId())
                .benefitSourceVersion(application.getBenefitSourceVersion())
                .entitlementId(application.getEntitlementId()).amountMinor(application.getAmountMinor())
                .returnAmountMinor(applicationReturnAmount).currencyCode(application.getCurrencyCode())
                .calculationDigest(application.getCalculationDigest()).version(application.getVersion())
                .allocations(allocationViews).build();
    }

    static long proportionalDelta(long amountMinor, BigDecimal previousQuantity,
                                  BigDecimal requestedQuantity, BigDecimal orderedQuantity) {
        long previous = previousQuantity.longValueExact();
        long requested = requestedQuantity.longValueExact();
        long ordered = orderedQuantity.longValueExact();
        require(amountMinor >= 0 && previous >= 0 && requested > 0 && ordered > 0
                        && previous + requested <= ordered,
                "invalid proportional return inputs");
        BigInteger amount = BigInteger.valueOf(amountMinor);
        long before = amount.multiply(BigInteger.valueOf(previous)).divide(BigInteger.valueOf(ordered)).longValueExact();
        long after = amount.multiply(BigInteger.valueOf(previous + requested))
                .divide(BigInteger.valueOf(ordered)).longValueExact();
        return Math.subtractExact(after, before);
    }

    private static void requireText(String value, String field) {
        require(value != null && !value.isBlank(), field + " is required");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
