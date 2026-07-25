package cn.iocoder.yudao.module.cloudmold.fulfillment.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.fulfillment.controller.admin.vo.FulfillmentPageReqVO;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.FulfillmentQueryMapper;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.TrackingEventMapper;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class FulfillmentQueryService implements AppFulfillmentQueryApi {

    private final FulfillmentQueryMapper queryMapper;
    private final TrackingEventMapper trackingEventMapper;

    public PageResult<FulfillmentPageItem> getPage(FulfillmentPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String fulfillmentId = normalize(request.getFulfillmentId());
        String fulfillmentNo = normalize(request.getFulfillmentNo());
        String orderId = normalize(request.getOrderId());
        String orderNo = normalize(request.getOrderNo());
        String sellerId = normalize(request.getSellerId());
        String warehouseId = normalize(request.getWarehouseId());
        String status = normalizeUpper(request.getStatus());
        String shipmentId = normalize(request.getShipmentId());
        String shipmentStatus = normalizeUpper(request.getShipmentStatus());
        String carrierCode = normalizeUpper(request.getCarrierCode());
        String waybillNo = normalize(request.getWaybillNo());
        long total = queryMapper.countPage(tenantId, fulfillmentId, fulfillmentNo, orderId, orderNo, sellerId,
                warehouseId, status, shipmentId, shipmentStatus, carrierCode, waybillNo);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(queryMapper.selectPage(tenantId, fulfillmentId, fulfillmentNo, orderId, orderNo,
                sellerId, warehouseId, status, shipmentId, shipmentStatus, carrierCode, waybillNo, offset,
                request.getPageSize()), total);
    }

    public FulfillmentDetailVO getFulfillmentDetail(String fulfillmentId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String normalizedFulfillmentId = normalize(fulfillmentId);
        FulfillmentDetailVO detail = queryMapper.selectFulfillmentDetail(tenantId, normalizedFulfillmentId);
        if (detail == null) {
            return null;
        }
        detail.setItems(queryMapper.selectFulfillmentItems(tenantId, normalizedFulfillmentId));
        return detail;
    }

    @Override
    public AppFulfillmentView getByOrder(String orderId) {
        if (!StringUtils.hasText(orderId)) throw new IllegalArgumentException("orderId is required");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        FulfillmentDetailVO detail = queryMapper.selectByOrder(tenantId, orderId.trim());
        if (detail == null) return null;
        var items = queryMapper.selectFulfillmentItems(tenantId, detail.getFulfillmentId()).stream()
                .map(item -> AppFulfillmentView.Item.builder().orderItemId(item.getOrderItemId())
                        .canonicalSkuId(item.getCanonicalSkuId()).quantity(item.getQuantity()).build()).toList();
        var events = detail.getFirstSliceShipmentId() == null ? java.util.List.<AppFulfillmentView.TrackingEvent>of()
                : trackingEventMapper.selectByShipment(tenantId, detail.getFirstSliceShipmentId()).stream()
                .map(event -> AppFulfillmentView.TrackingEvent.builder().status(event.getTrackingStatus())
                        .occurredAt(event.getOccurredAt()).description(event.getContent()).build()).toList();
        return AppFulfillmentView.builder().fulfillmentId(detail.getFulfillmentId())
                .fulfillmentNo(detail.getFulfillmentNo()).orderId(detail.getOrderId()).status(detail.getStatus())
                .aggregateVersion(detail.getAggregateVersion()).warehouseId(detail.getWarehouseId())
                .carrierCode(detail.getCarrierCode()).waybillNo(detail.getWaybillNo())
                .shipmentId(detail.getFirstSliceShipmentId()).items(items).trackingEvents(events).build();
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
