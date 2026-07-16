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
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderAfterSaleQueryServiceImpl implements OrderAfterSaleQueryApi {
    private final OrderHeaderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final OrderBenefitApplicationMapper benefitApplicationMapper;
    private final OrderBenefitAllocationMapper benefitAllocationMapper;
    private final OrderBenefitFundingMapper benefitFundingMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderAfterSaleView requireEligible(String orderId, String orderItemId) {
        requireText(orderId, "orderId");
        requireText(orderItemId, "orderItemId");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        OrderHeaderDO order = orderMapper.selectForUpdate(tenantId, orderId);
        require(order != null, "canonical order does not exist");
        require("COMPLETED".equals(order.getStatus()), "after sale requires COMPLETED order");
        requireText(order.getPaymentId(), "paymentId");
        requireText(order.getFulfillmentId(), "fulfillmentId");
        requireText(order.getShipmentId(), "shipmentId");
        require("CNY".equals(order.getCurrencyCode()), "after-sale first slice supports CNY only");
        List<OrderItemDO> items = itemMapper.selectByOrder(tenantId, orderId);
        require(items.size() == 1, "after-sale first slice requires exactly one order item");
        OrderItemDO item = items.get(0);
        require(Objects.equals(item.getOrderItemId(), orderItemId), "order item does not belong to order");
        require(item.getQuantity().signum() > 0 && item.getQuantity().stripTrailingZeros().scale() <= 0,
                "after-sale first slice requires whole-piece quantity");
        require(Objects.equals(item.getNetAmountMinor(), order.getPayableAmountMinor()),
                "after-sale first slice requires full order net amount on one line");
        require(item.getLineAmountMinor() >= 0 && item.getDiscountAmountMinor() >= 0
                        && Objects.equals(item.getNetAmountMinor(),
                        Math.subtractExact(item.getLineAmountMinor(), item.getDiscountAmountMinor())),
                "after-sale order item money does not reconcile");
        requireText(item.getReservationId(), "reservationId");
        requireText(item.getListingId(), "listingId");
        requireText(item.getListingOfferId(), "listingOfferId");
        return OrderAfterSaleView.builder().orderId(order.getOrderId()).orderNo(order.getOrderNo())
                .buyerId(order.getBuyerId()).status(order.getStatus()).aggregateVersion(order.getVersion())
                .payableAmountMinor(order.getPayableAmountMinor()).currencyCode(order.getCurrencyCode())
                .paymentId(order.getPaymentId()).fulfillmentId(order.getFulfillmentId())
                .shipmentId(order.getShipmentId()).orderItemId(item.getOrderItemId())
                .canonicalSkuId(item.getCanonicalSkuId()).quantity(item.getQuantity())
                .lineAmountMinor(item.getLineAmountMinor()).discountAmountMinor(item.getDiscountAmountMinor())
                .netAmountMinor(item.getNetAmountMinor())
                .benefitApplications(benefitViews(tenantId, orderId, item.getOrderItemId()))
                .reservationId(item.getReservationId())
                .listingId(item.getListingId()).listingOfferId(item.getListingOfferId()).build();
    }

    private List<OrderBenefitApplicationView> benefitViews(Long tenantId, String orderId, String orderItemId) {
        List<OrderBenefitApplicationDO> applications = benefitApplicationMapper.selectByOrder(tenantId, orderId);
        if (applications.isEmpty()) return List.of();
        List<OrderBenefitAllocationDO> allocations = benefitAllocationMapper.selectByOrder(tenantId, orderId);
        Map<String, List<OrderBenefitFundingDO>> fundingByAllocation = benefitFundingMapper
                .selectByOrder(tenantId, orderId).stream().collect(java.util.stream.Collectors.groupingBy(
                        OrderBenefitFundingDO::getBenefitAllocationId, LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        Map<String, List<OrderBenefitAllocationDO>> allocationsByApplication = allocations.stream()
                .filter(value -> Objects.equals(value.getOrderItemId(), orderItemId))
                .collect(java.util.stream.Collectors.groupingBy(OrderBenefitAllocationDO::getBenefitApplicationId,
                        LinkedHashMap::new, java.util.stream.Collectors.toList()));
        return applications.stream()
                .filter(value -> allocationsByApplication.containsKey(value.getBenefitApplicationId()))
                .map(application -> OrderBenefitApplicationView.builder()
                        .benefitApplicationId(application.getBenefitApplicationId())
                        .applicationKey(application.getApplicationKey()).benefitType(application.getBenefitType())
                        .benefitSourceType(application.getBenefitSourceType())
                        .benefitSourceId(application.getBenefitSourceId())
                        .benefitSourceVersion(application.getBenefitSourceVersion())
                        .entitlementId(application.getEntitlementId()).amountMinor(application.getAmountMinor())
                        .currencyCode(application.getCurrencyCode())
                        .calculationDigest(application.getCalculationDigest()).version(application.getVersion())
                        .allocations(allocationsByApplication.get(application.getBenefitApplicationId()).stream()
                                .map(allocation -> OrderBenefitAllocationView.builder()
                                        .benefitAllocationId(allocation.getBenefitAllocationId())
                                        .allocationKey(allocation.getAllocationKey())
                                        .orderItemId(allocation.getOrderItemId()).lineKey(allocation.getLineKey())
                                        .amountMinor(allocation.getAmountMinor())
                                        .currencyCode(allocation.getCurrencyCode())
                                        .funding(fundingByAllocation.getOrDefault(allocation.getBenefitAllocationId(),
                                                        List.of()).stream()
                                                .map(funding -> OrderBenefitFundingView.builder()
                                                        .benefitFundingId(funding.getBenefitFundingId())
                                                        .fundingKey(funding.getFundingKey())
                                                        .funderType(funding.getFunderType())
                                                        .funderId(funding.getFunderId())
                                                        .amountMinor(funding.getAmountMinor())
                                                        .currencyCode(funding.getCurrencyCode()).build())
                                                .toList()).build())
                                .toList()).build())
                .toList();
    }

    private static void requireText(String value, String field) {
        require(value != null && !value.isBlank(), field + " is required");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
