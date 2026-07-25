package cn.iocoder.yudao.module.cloudmold.integration.yudao.bridge.wms;

import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoWmsCommandApi;
import cn.iocoder.yudao.module.wms.dal.dataobject.order.receipt.WmsReceiptOrderDO;
import cn.iocoder.yudao.module.wms.enums.order.WmsOrderStatusEnum;
import cn.iocoder.yudao.module.wms.service.inventory.WmsInventoryService;
import cn.iocoder.yudao.module.wms.service.order.check.WmsCheckOrderService;
import cn.iocoder.yudao.module.wms.service.order.movement.WmsMovementOrderService;
import cn.iocoder.yudao.module.wms.service.order.receipt.WmsReceiptOrderDetailService;
import cn.iocoder.yudao.module.wms.service.order.receipt.WmsReceiptOrderService;
import cn.iocoder.yudao.module.wms.service.order.shipment.WmsShipmentOrderService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class YudaoWmsPhysicalOperationsBridgeTest {

    private final WmsInventoryService inventoryService = mock(WmsInventoryService.class);
    private final WmsReceiptOrderService receiptOrderService = mock(WmsReceiptOrderService.class);
    private final WmsReceiptOrderDetailService receiptOrderDetailService = mock(WmsReceiptOrderDetailService.class);
    private final WmsShipmentOrderService shipmentOrderService = mock(WmsShipmentOrderService.class);
    private final WmsMovementOrderService movementOrderService = mock(WmsMovementOrderService.class);
    private final WmsCheckOrderService checkOrderService = mock(WmsCheckOrderService.class);

    private final YudaoWmsPhysicalOperationsBridge bridge = new YudaoWmsPhysicalOperationsBridge(
            inventoryService, receiptOrderService, receiptOrderDetailService,
            shipmentOrderService, movementOrderService, checkOrderService);

    @Test
    void shouldReturnATypedFinishedSnapshotAfterReceiptCompletion() {
        WmsReceiptOrderDO finished = new WmsReceiptOrderDO()
                .setId(41L)
                .setNo("RK-41")
                .setStatus(WmsOrderStatusEnum.FINISHED.getStatus())
                .setOrderTime(LocalDateTime.of(2026, 7, 25, 11, 0))
                .setWarehouseId(100L)
                .setTotalQuantity(new BigDecimal("2"))
                .setTotalPrice(new BigDecimal("40"))
                .setRemark("done");
        when(receiptOrderService.getReceiptOrder(41L)).thenReturn(finished);

        var result = bridge.completeReceiptOrder(new YudaoWmsCommandApi.DocumentActionCommand(
                "receipt-complete-001", 41L, 0, null));

        verify(receiptOrderService).completeReceiptOrder(41L);
        assertThat(result.documentType()).isEqualTo("RECEIPT_ORDER");
        assertThat(result.status()).isEqualTo(WmsOrderStatusEnum.FINISHED.getStatus());
        assertThat(result.warehouseId()).isEqualTo(100L);
    }

    @Test
    void shouldFailClosedWhenReceiptCompletionCannotReadBackTheDocument() {
        when(receiptOrderService.getReceiptOrder(41L)).thenReturn(null);

        assertThatThrownBy(() -> bridge.completeReceiptOrder(
                new YudaoWmsCommandApi.DocumentActionCommand("receipt-complete-002", 41L, 0, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("receipt order does not exist");
    }
}
