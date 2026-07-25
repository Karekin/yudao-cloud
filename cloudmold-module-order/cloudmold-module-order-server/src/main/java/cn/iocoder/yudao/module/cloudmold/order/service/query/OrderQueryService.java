package cn.iocoder.yudao.module.cloudmold.order.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.order.controller.admin.vo.OrderPageReqVO;
import cn.iocoder.yudao.module.cloudmold.order.api.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.OrderQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service("cloudMoldOrderQueryService")
@RequiredArgsConstructor
public class OrderQueryService implements AppOrderQueryApi {

    private final OrderQueryMapper orderQueryMapper;

    public PageResult<OrderPageItem> getOrderPage(OrderPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String orderId = normalize(request.getOrderId());
        String orderNo = normalize(request.getOrderNo());
        String buyerId = normalize(request.getBuyerId());
        String status = normalize(request.getStatus());
        long total = orderQueryMapper.countOrderPage(tenantId, orderId, orderNo, buyerId, status);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(orderQueryMapper.selectOrderPage(tenantId, orderId, orderNo, buyerId, status,
                offset, request.getPageSize()), total);
    }

    /**
     * 查询单个规范订单详情（含订单行）。跨租户或不存在时返回 null，
     * 由前端处理为"未找到"，不抛 404 以避免泄露资源存在性。
     */
    public OrderDetailVO getOrderDetail(String orderId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        OrderDetailVO detail = orderQueryMapper.selectOrderDetail(tenantId, orderId);
        if (detail == null) {
            return null;
        }
        detail.setItems(orderQueryMapper.selectOrderItems(tenantId, orderId));
        return detail;
    }

    @Override
    public AppOrderView requireOwned(String buyerPrincipalId, String orderId) {
        if (!StringUtils.hasText(buyerPrincipalId) || !StringUtils.hasText(orderId)) {
            throw new IllegalArgumentException("buyerPrincipalId and orderId are required");
        }
        OrderDetailVO detail = getOrderDetail(orderId.trim());
        if (detail == null || !buyerPrincipalId.trim().equals(detail.getBuyerId())) {
            throw new IllegalArgumentException("canonical order does not exist");
        }
        return toAppView(detail);
    }

    @Override
    public AppOrderPageView listOwned(String buyerPrincipalId, String status, int pageNo, int pageSize) {
        if (!StringUtils.hasText(buyerPrincipalId)) {
            throw new IllegalArgumentException("buyerPrincipalId is required");
        }
        if (pageNo <= 0 || pageSize <= 0 || pageSize > 50) {
            throw new IllegalArgumentException("invalid page");
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String normalizedStatus = normalize(status);
        long total = orderQueryMapper.countOrderPage(tenantId, null, null, buyerPrincipalId.trim(), normalizedStatus);
        if (total == 0) {
            return AppOrderPageView.builder().list(java.util.List.of()).total(0L)
                    .pageNo(pageNo).pageSize(pageSize).build();
        }
        long offset = (long) (pageNo - 1) * pageSize;
        java.util.List<AppOrderView> list = orderQueryMapper.selectOrderPage(
                        tenantId, null, null, buyerPrincipalId.trim(), normalizedStatus, offset, pageSize)
                .stream().map(row -> {
                    OrderDetailVO detail = getOrderDetail(row.getOrderId());
                    return toAppView(detail);
                }).toList();
        return AppOrderPageView.builder().list(list).total(total).pageNo(pageNo).pageSize(pageSize).build();
    }

    private static AppOrderView toAppView(OrderDetailVO detail) {
        java.util.List<OrderLineView> items = detail.getItems() == null ? java.util.List.of()
                : detail.getItems().stream().map(item -> OrderLineView.builder()
                .orderItemId(item.getOrderItemId()).lineKey(item.getLineKey())
                .canonicalSkuId(item.getCanonicalSkuId()).quantity(item.getQuantity())
                .unitPriceMinor(item.getUnitPriceMinor()).lineAmountMinor(item.getLineAmountMinor())
                .discountAmountMinor(item.getDiscountAmountMinor()).netAmountMinor(item.getNetAmountMinor())
                .reservationId(item.getReservationId()).listingId(item.getListingId())
                .listingOfferId(item.getListingOfferId()).listingRevision(item.getListingRevision())
                .listingVersion(item.getListingVersion()).channelCode(item.getChannelCode())
                .shopId(item.getShopId()).build()).toList();
        return AppOrderView.builder().orderId(detail.getOrderId()).orderNo(detail.getOrderNo())
                .runId(detail.getRunId()).buyerPrincipalId(detail.getBuyerId()).status(detail.getStatus())
                .addressRef(detail.getAddressRef())
                .addressSnapshotVersion(detail.getAddressSnapshotVersion())
                .destinationRegionCode(detail.getDestinationRegionCode())
                .aggregateVersion(detail.getAggregateVersion()).totalQuantity(detail.getTotalQuantity())
                .productAmountMinor(detail.getProductAmountMinor()).shippingAmountMinor(detail.getShippingAmountMinor())
                .discountAmountMinor(detail.getDiscountAmountMinor()).payableAmountMinor(detail.getPayableAmountMinor())
                .currencyCode(detail.getCurrencyCode()).paymentId(detail.getPaymentId())
                .paymentStatus(detail.getPaymentStatus()).fulfillmentId(detail.getFulfillmentId())
                .fulfillmentStatus(detail.getFulfillmentStatus()).shipmentId(detail.getShipmentId())
                .items(items).build();
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
