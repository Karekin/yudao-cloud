package cn.iocoder.yudao.module.cloudmold.integration.yudao.api;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/** Typed commands for physical WMS work, isolated from upstream controller types. */
public interface YudaoWmsCommandApi {

    Long createMerchant(MerchantCommand command);

    Long createWarehouse(WarehouseCommand command);

    Long createItemCategory(ItemCategoryCommand command);

    Long createItem(ItemCommand command);

    Long createReceiptOrder(ReceiptOrderCommand command);

    PhysicalOperationResult completeReceiptOrder(DocumentActionCommand command);

    Long createShipmentOrder(ShipmentOrderCommand command);

    PhysicalOperationResult completeShipmentOrder(DocumentActionCommand command);

    Long createMovementOrder(MovementOrderCommand command);

    PhysicalOperationResult completeMovementOrder(DocumentActionCommand command);

    Long createCheckOrder(CheckOrderCommand command);

    PhysicalOperationResult completeCheckOrder(DocumentActionCommand command);

    PhysicalOperationResult completePutaway(DocumentActionCommand command);

    PhysicalOperationResult completePicking(DocumentActionCommand command);

    record DocumentActionCommand(String idempotencyKey,
                                 Long documentId,
                                 Integer expectedCurrentStatus,
                                 Long expectedDocumentVersion) implements Serializable {
    }

    record PhysicalOrderView(String sourceSystem,
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

    record PhysicalOperationResult(boolean success,
                                   boolean supported,
                                   String operationType,
                                   String failureCode,
                                   String failureMessage,
                                   PhysicalOrderView snapshot) implements Serializable {
    }

    record MerchantCommand(String idempotencyKey, String code, String name, Integer type,
                           String level, String address, String mobile, String contact,
                           String email, String remark) implements Serializable {
    }

    record WarehouseCommand(String idempotencyKey, String code, String name,
                            Integer sort, String remark) implements Serializable {
    }

    record ItemCategoryCommand(String idempotencyKey, Long parentId, String code, String name,
                               Integer sort, Integer status) implements Serializable {
    }

    record ItemSkuLine(String name, String barCode, String code,
                       BigDecimal length, BigDecimal width, BigDecimal height,
                       BigDecimal grossWeight, BigDecimal netWeight,
                       BigDecimal costPrice, BigDecimal sellingPrice) implements Serializable {
    }

    record ItemCommand(String idempotencyKey, String code, String name, Long categoryId,
                       String unit, String remark, List<ItemSkuLine> skus) implements Serializable {
    }

    record QuantityLine(Long skuId, BigDecimal quantity, BigDecimal price,
                        BigDecimal totalPrice) implements Serializable {
    }

    record ReceiptOrderCommand(String idempotencyKey, String no, Integer type, String orderTime, String bizOrderNo,
                               Long merchantId, String remark, Long warehouseId,
                               List<QuantityLine> details) implements Serializable {
    }

    record ShipmentOrderCommand(String idempotencyKey, String no, Integer type, String orderTime, String bizOrderNo,
                                Long merchantId, String remark, Long warehouseId,
                                List<QuantityLine> details) implements Serializable {
    }

    record MovementOrderCommand(String idempotencyKey, String no, String orderTime, String remark,
                                Long sourceWarehouseId, Long targetWarehouseId,
                                List<QuantityLine> details) implements Serializable {
    }

    record CheckLine(Long skuId, Long inventoryId, String receiptTime,
                     BigDecimal quantity, BigDecimal checkQuantity,
                     BigDecimal price) implements Serializable {
    }

    record CheckOrderCommand(String idempotencyKey, String no, String orderTime, String remark,
                             Long warehouseId, List<CheckLine> details) implements Serializable {
    }
}
