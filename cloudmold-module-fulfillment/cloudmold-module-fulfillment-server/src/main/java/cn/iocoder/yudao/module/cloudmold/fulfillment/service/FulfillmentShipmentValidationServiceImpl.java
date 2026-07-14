package cn.iocoder.yudao.module.cloudmold.fulfillment.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.*;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FulfillmentShipmentValidationServiceImpl implements FulfillmentShipmentValidationApi {

    private final FulfillmentOrderMapper fulfillmentMapper;
    private final FulfillmentItemMapper itemMapper;
    private final ShipmentMapper shipmentMapper;

    @Override
    public FulfillmentCommandResult requireShipped(String orderId, String fulfillmentId, String shipmentId) {
        return requireState(orderId, fulfillmentId, shipmentId, List.of("SHIPPED", "IN_TRANSIT", "DELIVERED"));
    }

    @Override
    public FulfillmentCommandResult requireDelivered(String orderId, String fulfillmentId) {
        return requireState(orderId, fulfillmentId, null, List.of("DELIVERED"));
    }

    private FulfillmentCommandResult requireState(String orderId, String fulfillmentId, String shipmentId,
                                                  List<String> allowedStatuses) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireText(orderId, "orderId");
        requireText(fulfillmentId, "fulfillmentId");
        FulfillmentOrderDO fulfillment = fulfillmentMapper.selectByIdForValidation(tenantId, fulfillmentId);
        require(fulfillment != null && orderId.equals(fulfillment.getOrderId()),
                "fulfillment does not belong to canonical order");
        require(allowedStatuses.contains(fulfillment.getStatus()), "fulfillment is not in required delivery state");
        ShipmentDO shipment = shipmentMapper.selectByFulfillment(tenantId, fulfillmentId);
        require(shipment != null, "fulfillment shipment is missing");
        if (shipmentId != null) require(shipmentId.equals(shipment.getShipmentId()),
                "shipment does not belong to fulfillment");
        List<FulfillmentItemDO> items = itemMapper.selectByFulfillment(tenantId, fulfillmentId);
        return FulfillmentCommandResult.builder().fulfillmentId(fulfillment.getFulfillmentId())
                .fulfillmentNo(fulfillment.getFulfillmentNo()).shipmentId(shipment.getShipmentId())
                .orderId(fulfillment.getOrderId()).currentStatus(fulfillment.getStatus())
                .aggregateVersion(fulfillment.getVersion()).sellerId(fulfillment.getSellerId())
                .warehouseId(fulfillment.getWarehouseId()).carrierCode(shipment.getCarrierCode())
                .waybillNo(shipment.getWaybillNo()).items(items.stream().map(item -> FulfillmentLineView.builder()
                        .fulfillmentItemId(item.getFulfillmentItemId()).orderItemId(item.getOrderItemId())
                        .canonicalSkuId(item.getCanonicalSkuId()).quantity(item.getQuantity())
                        .reservationId(item.getReservationId()).build()).toList()).duplicate(false).build();
    }

    private static void requireText(String value, String field) {
        require(value != null && !value.isBlank(), field + " is required");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
