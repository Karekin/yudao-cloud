package cn.iocoder.yudao.module.cloudmold.integration.yudao.wms;

import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoWmsCommandApi;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * CloudMold-owned physical-operations boundary over upstream WMS internals.
 * WMS remains the executor of physical work, not the inventory system of record.
 */
public interface LegacyWmsPhysicalOperationsPort {

    Long createReceiptOrder(YudaoWmsCommandApi.ReceiptOrderCommand command);

    PhysicalOrderSnapshot completeReceiptOrder(YudaoWmsCommandApi.DocumentActionCommand command);

    Long createShipmentOrder(YudaoWmsCommandApi.ShipmentOrderCommand command);

    PhysicalOrderSnapshot completeShipmentOrder(YudaoWmsCommandApi.DocumentActionCommand command);

    Long createMovementOrder(YudaoWmsCommandApi.MovementOrderCommand command);

    PhysicalOrderSnapshot completeMovementOrder(YudaoWmsCommandApi.DocumentActionCommand command);

    Long createCheckOrder(YudaoWmsCommandApi.CheckOrderCommand command);

    PhysicalOrderSnapshot completeCheckOrder(YudaoWmsCommandApi.DocumentActionCommand command);

    PhysicalOrderSnapshot getReceiptOrder(Long documentId);

    ReceiptOrderContext getReceiptOrderContext(Long documentId);

    PhysicalOrderSnapshot getShipmentOrder(Long documentId);

    PhysicalOrderSnapshot getMovementOrder(Long documentId);

    PhysicalOrderSnapshot getCheckOrder(Long documentId);

    InventorySnapshot getWmsInventory(Long warehouseId, Long skuId);

    record PhysicalOrderSnapshot(String sourceSystem,
                                 String documentType,
                                 Long documentId,
                                 String documentNo,
                                 Integer status,
                                 String businessTime,
                                 Long warehouseId,
                                 BigDecimal quantity,
                                 BigDecimal amount,
                                 String remark) implements Serializable {
    }

    record InventorySnapshot(Long inventoryId,
                             Long warehouseId,
                             Long skuId,
                             BigDecimal quantity) implements Serializable {
    }

    record ReceiptOrderContext(PhysicalOrderSnapshot order,
                               Long merchantId,
                               java.util.List<ReceiptLineSnapshot> lines) implements Serializable {
    }

    record ReceiptLineSnapshot(Long receiptLineId,
                               Long skuId,
                               Long warehouseId,
                               BigDecimal quantity,
                               BigDecimal price,
                               BigDecimal totalPrice) implements Serializable {
    }
}
