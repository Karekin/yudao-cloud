package cn.iocoder.yudao.module.cloudmold.order.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderAfterSaleQueryApi;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderAfterSaleView;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.OrderHeaderDO;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.OrderItemDO;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.OrderHeaderMapper;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.OrderItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OrderAfterSaleQueryServiceImpl implements OrderAfterSaleQueryApi {
    private final OrderHeaderMapper orderMapper;
    private final OrderItemMapper itemMapper;

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
        require(Objects.equals(item.getLineAmountMinor(), order.getPayableAmountMinor()),
                "after-sale first slice requires full order amount on one line");
        requireText(item.getReservationId(), "reservationId");
        requireText(item.getListingId(), "listingId");
        requireText(item.getListingOfferId(), "listingOfferId");
        return OrderAfterSaleView.builder().orderId(order.getOrderId()).orderNo(order.getOrderNo())
                .buyerId(order.getBuyerId()).status(order.getStatus()).aggregateVersion(order.getVersion())
                .payableAmountMinor(order.getPayableAmountMinor()).currencyCode(order.getCurrencyCode())
                .paymentId(order.getPaymentId()).fulfillmentId(order.getFulfillmentId())
                .shipmentId(order.getShipmentId()).orderItemId(item.getOrderItemId())
                .canonicalSkuId(item.getCanonicalSkuId()).quantity(item.getQuantity())
                .lineAmountMinor(item.getLineAmountMinor()).reservationId(item.getReservationId())
                .listingId(item.getListingId()).listingOfferId(item.getListingOfferId()).build();
    }

    private static void requireText(String value, String field) {
        require(value != null && !value.isBlank(), field + " is required");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
