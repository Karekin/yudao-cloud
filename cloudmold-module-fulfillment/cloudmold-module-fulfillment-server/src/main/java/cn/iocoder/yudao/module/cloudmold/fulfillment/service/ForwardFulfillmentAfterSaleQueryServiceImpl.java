package cn.iocoder.yudao.module.cloudmold.fulfillment.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.ForwardFulfillmentAfterSaleQueryApi;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.ForwardFulfillmentAfterSaleView;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.FulfillmentItemDO;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.FulfillmentOrderDO;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.ShipmentDO;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.FulfillmentItemMapper;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.FulfillmentOrderMapper;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.ShipmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ForwardFulfillmentAfterSaleQueryServiceImpl implements ForwardFulfillmentAfterSaleQueryApi {
    private final FulfillmentOrderMapper fulfillmentMapper;
    private final FulfillmentItemMapper itemMapper;
    private final ShipmentMapper shipmentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ForwardFulfillmentAfterSaleView requireDelivered(String orderId, String fulfillmentId,
                                                            String shipmentId, String orderItemId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        FulfillmentOrderDO fulfillment = fulfillmentMapper.selectForUpdate(tenantId, fulfillmentId);
        require(fulfillment != null && Objects.equals(orderId, fulfillment.getOrderId()),
                "forward Fulfillment does not belong to order");
        require("DELIVERED".equals(fulfillment.getStatus()), "after sale requires DELIVERED Fulfillment");
        List<FulfillmentItemDO> items = itemMapper.selectByFulfillment(tenantId, fulfillmentId);
        require(items.size() == 1 && Objects.equals(orderItemId, items.get(0).getOrderItemId()),
                "after-sale first slice requires exact sole Fulfillment item");
        ShipmentDO shipment = shipmentMapper.selectByFulfillment(tenantId, fulfillmentId);
        require(shipment != null && Objects.equals(shipmentId, shipment.getShipmentId())
                        && "DELIVERED".equals(shipment.getStatus()),
                "forward shipment is not the delivered order shipment");
        FulfillmentItemDO item = items.get(0);
        return ForwardFulfillmentAfterSaleView.builder().fulfillmentId(fulfillmentId)
                .shipmentId(shipmentId).orderId(orderId).orderItemId(orderItemId)
                .canonicalSkuId(item.getCanonicalSkuId()).quantity(item.getQuantity())
                .reservationId(item.getReservationId()).ownerId(fulfillment.getSellerId())
                .warehouseId(fulfillment.getWarehouseId()).uomCode("PCS")
                .status(fulfillment.getStatus()).aggregateVersion(fulfillment.getVersion()).build();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
