package cn.iocoder.yudao.module.cloudmold.fulfillment.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.ForwardFulfillmentAfterSaleView;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.FulfillmentItemDO;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.FulfillmentOrderDO;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.ShipmentDO;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.FulfillmentItemMapper;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.FulfillmentOrderMapper;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.ShipmentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ForwardFulfillmentAfterSaleQueryServiceImplTest {
    private final FulfillmentOrderMapper fulfillmentMapper = mock(FulfillmentOrderMapper.class);
    private final FulfillmentItemMapper itemMapper = mock(FulfillmentItemMapper.class);
    private final ShipmentMapper shipmentMapper = mock(ShipmentMapper.class);
    private final ForwardFulfillmentAfterSaleQueryServiceImpl service =
            new ForwardFulfillmentAfterSaleQueryServiceImpl(
                    fulfillmentMapper, itemMapper, shipmentMapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(fulfillmentMapper.selectForUpdate(1L, "fulfillment-1"))
                .thenReturn(new FulfillmentOrderDO().setFulfillmentId("fulfillment-1")
                        .setOrderId("order-1").setSellerId("seller-1")
                        .setWarehouseId("warehouse-1").setStatus("DELIVERED").setVersion(4L));
        when(shipmentMapper.selectByFulfillment(1L, "fulfillment-1"))
                .thenReturn(new ShipmentDO().setShipmentId("shipment-1")
                        .setFulfillmentId("fulfillment-1").setStatus("DELIVERED"));
        when(itemMapper.selectByFulfillment(1L, "fulfillment-1")).thenReturn(List.of(
                item("fulfillment-item-1", "order-item-1", "sku-1"),
                item("fulfillment-item-2", "order-item-2", "sku-2")));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldSelectTheExactItemFromAMultiLineDeliveredFulfillment() {
        ForwardFulfillmentAfterSaleView result = service.requireDelivered(
                "order-1", "fulfillment-1", "shipment-1", "order-item-2");

        assertThat(result.getOrderItemId()).isEqualTo("order-item-2");
        assertThat(result.getCanonicalSkuId()).isEqualTo("sku-2");
        assertThat(result.getReservationId()).isEqualTo("reservation-order-item-2");
        assertThat(result.getQuantity()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void shouldRejectWhenTheRequestedOrderItemIsNotInTheFulfillment() {
        assertThatThrownBy(() -> service.requireDelivered(
                "order-1", "fulfillment-1", "shipment-1", "order-item-missing"))
                .hasMessage("after sale requires exactly one matching Fulfillment item");
    }

    private static FulfillmentItemDO item(String fulfillmentItemId, String orderItemId, String skuId) {
        return new FulfillmentItemDO().setFulfillmentItemId(fulfillmentItemId)
                .setFulfillmentId("fulfillment-1").setOrderItemId(orderItemId)
                .setCanonicalSkuId(skuId).setQuantity(BigDecimal.ONE)
                .setReservationId("reservation-" + orderItemId);
    }
}
