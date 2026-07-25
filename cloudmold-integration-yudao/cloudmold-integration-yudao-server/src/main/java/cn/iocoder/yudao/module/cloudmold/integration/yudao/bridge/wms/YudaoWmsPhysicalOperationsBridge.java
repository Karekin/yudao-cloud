package cn.iocoder.yudao.module.cloudmold.integration.yudao.bridge.wms;

import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoWmsCommandApi;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.wms.LegacyWmsPhysicalOperationsPort;
import cn.iocoder.yudao.module.wms.controller.admin.inventory.vo.WmsInventoryListReqVO;
import cn.iocoder.yudao.module.wms.controller.admin.order.check.vo.detail.WmsCheckOrderDetailSaveReqVO;
import cn.iocoder.yudao.module.wms.controller.admin.order.check.vo.order.WmsCheckOrderSaveReqVO;
import cn.iocoder.yudao.module.wms.controller.admin.order.movement.vo.detail.WmsMovementOrderDetailSaveReqVO;
import cn.iocoder.yudao.module.wms.controller.admin.order.movement.vo.order.WmsMovementOrderSaveReqVO;
import cn.iocoder.yudao.module.wms.controller.admin.order.receipt.vo.detail.WmsReceiptOrderDetailSaveReqVO;
import cn.iocoder.yudao.module.wms.controller.admin.order.receipt.vo.order.WmsReceiptOrderSaveReqVO;
import cn.iocoder.yudao.module.wms.controller.admin.order.shipment.vo.detail.WmsShipmentOrderDetailSaveReqVO;
import cn.iocoder.yudao.module.wms.controller.admin.order.shipment.vo.order.WmsShipmentOrderSaveReqVO;
import cn.iocoder.yudao.module.wms.dal.dataobject.inventory.WmsInventoryDO;
import cn.iocoder.yudao.module.wms.dal.dataobject.order.check.WmsCheckOrderDO;
import cn.iocoder.yudao.module.wms.dal.dataobject.order.movement.WmsMovementOrderDO;
import cn.iocoder.yudao.module.wms.dal.dataobject.order.receipt.WmsReceiptOrderDO;
import cn.iocoder.yudao.module.wms.dal.dataobject.order.shipment.WmsShipmentOrderDO;
import cn.iocoder.yudao.module.wms.service.inventory.WmsInventoryService;
import cn.iocoder.yudao.module.wms.service.order.check.WmsCheckOrderService;
import cn.iocoder.yudao.module.wms.service.order.movement.WmsMovementOrderService;
import cn.iocoder.yudao.module.wms.service.order.receipt.WmsReceiptOrderDetailService;
import cn.iocoder.yudao.module.wms.service.order.receipt.WmsReceiptOrderService;
import cn.iocoder.yudao.module.wms.service.order.shipment.WmsShipmentOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class YudaoWmsPhysicalOperationsBridge implements LegacyWmsPhysicalOperationsPort {

    private final WmsInventoryService wmsInventoryService;
    private final WmsReceiptOrderService receiptOrderService;
    private final WmsReceiptOrderDetailService receiptOrderDetailService;
    private final WmsShipmentOrderService shipmentOrderService;
    private final WmsMovementOrderService movementOrderService;
    private final WmsCheckOrderService checkOrderService;

    @Override
    public Long createReceiptOrder(YudaoWmsCommandApi.ReceiptOrderCommand command) {
        WmsReceiptOrderSaveReqVO request = new WmsReceiptOrderSaveReqVO();
        request.setNo(command.no());
        request.setType(command.type());
        request.setOrderTime(parseBusinessTime(command.orderTime(), "orderTime"));
        request.setBizOrderNo(command.bizOrderNo());
        request.setMerchantId(command.merchantId());
        request.setRemark(command.remark());
        request.setWarehouseId(command.warehouseId());
        request.setDetails(command.details().stream().map(line -> {
            WmsReceiptOrderDetailSaveReqVO item = new WmsReceiptOrderDetailSaveReqVO();
            copyQuantityLine(line, item);
            return item;
        }).toList());
        return receiptOrderService.createReceiptOrder(request);
    }

    @Override
    public PhysicalOrderSnapshot completeReceiptOrder(YudaoWmsCommandApi.DocumentActionCommand command) {
        receiptOrderService.completeReceiptOrder(command.documentId());
        return requireDocument(getReceiptOrder(command.documentId()), "receipt order");
    }

    @Override
    public Long createShipmentOrder(YudaoWmsCommandApi.ShipmentOrderCommand command) {
        WmsShipmentOrderSaveReqVO request = new WmsShipmentOrderSaveReqVO();
        request.setNo(command.no());
        request.setType(command.type());
        request.setOrderTime(parseBusinessTime(command.orderTime(), "orderTime"));
        request.setBizOrderNo(command.bizOrderNo());
        request.setMerchantId(command.merchantId());
        request.setRemark(command.remark());
        request.setWarehouseId(command.warehouseId());
        request.setDetails(command.details().stream().map(line -> {
            WmsShipmentOrderDetailSaveReqVO item = new WmsShipmentOrderDetailSaveReqVO();
            copyQuantityLine(line, item);
            return item;
        }).toList());
        return shipmentOrderService.createShipmentOrder(request);
    }

    @Override
    public PhysicalOrderSnapshot completeShipmentOrder(YudaoWmsCommandApi.DocumentActionCommand command) {
        shipmentOrderService.completeShipmentOrder(command.documentId());
        return requireDocument(getShipmentOrder(command.documentId()), "shipment order");
    }

    @Override
    public Long createMovementOrder(YudaoWmsCommandApi.MovementOrderCommand command) {
        WmsMovementOrderSaveReqVO request = new WmsMovementOrderSaveReqVO();
        request.setNo(command.no());
        request.setOrderTime(parseBusinessTime(command.orderTime(), "orderTime"));
        request.setRemark(command.remark());
        request.setSourceWarehouseId(command.sourceWarehouseId());
        request.setTargetWarehouseId(command.targetWarehouseId());
        request.setDetails(command.details().stream().map(line -> {
            WmsMovementOrderDetailSaveReqVO item = new WmsMovementOrderDetailSaveReqVO();
            copyQuantityLine(line, item);
            return item;
        }).toList());
        return movementOrderService.createMovementOrder(request);
    }

    @Override
    public PhysicalOrderSnapshot completeMovementOrder(YudaoWmsCommandApi.DocumentActionCommand command) {
        movementOrderService.completeMovementOrder(command.documentId());
        return requireDocument(getMovementOrder(command.documentId()), "movement order");
    }

    @Override
    public Long createCheckOrder(YudaoWmsCommandApi.CheckOrderCommand command) {
        WmsCheckOrderSaveReqVO request = new WmsCheckOrderSaveReqVO();
        request.setNo(command.no());
        request.setOrderTime(parseBusinessTime(command.orderTime(), "orderTime"));
        request.setRemark(command.remark());
        request.setWarehouseId(command.warehouseId());
        request.setDetails(command.details().stream().map(line -> {
            WmsCheckOrderDetailSaveReqVO item = new WmsCheckOrderDetailSaveReqVO();
            item.setSkuId(line.skuId());
            item.setInventoryId(line.inventoryId());
            item.setReceiptTime(parseBusinessTime(line.receiptTime(), "receiptTime"));
            item.setQuantity(line.quantity());
            item.setCheckQuantity(line.checkQuantity());
            item.setPrice(line.price());
            return item;
        }).toList());
        return checkOrderService.createCheckOrder(request);
    }

    @Override
    public PhysicalOrderSnapshot completeCheckOrder(YudaoWmsCommandApi.DocumentActionCommand command) {
        checkOrderService.completeCheckOrder(command.documentId());
        return requireDocument(getCheckOrder(command.documentId()), "check order");
    }

    @Override
    public PhysicalOrderSnapshot getReceiptOrder(Long documentId) {
        return toSnapshot("RECEIPT_ORDER", receiptOrderService.getReceiptOrder(documentId));
    }

    @Override
    public ReceiptOrderContext getReceiptOrderContext(Long documentId) {
        WmsReceiptOrderDO order = receiptOrderService.getReceiptOrder(documentId);
        if (order == null) {
            return null;
        }
        return new ReceiptOrderContext(
                toSnapshot("RECEIPT_ORDER", order),
                order.getMerchantId(),
                receiptOrderDetailService.getReceiptOrderDetailList(documentId).stream()
                        .map(line -> new ReceiptLineSnapshot(
                                line.getId(), line.getSkuId(), line.getWarehouseId(),
                                line.getQuantity(), line.getPrice(), line.getTotalPrice()))
                        .toList());
    }

    @Override
    public PhysicalOrderSnapshot getShipmentOrder(Long documentId) {
        return toSnapshot("SHIPMENT_ORDER", shipmentOrderService.getShipmentOrder(documentId));
    }

    @Override
    public PhysicalOrderSnapshot getMovementOrder(Long documentId) {
        return toSnapshot("MOVEMENT_ORDER", movementOrderService.getMovementOrder(documentId));
    }

    @Override
    public PhysicalOrderSnapshot getCheckOrder(Long documentId) {
        return toSnapshot("CHECK_ORDER", checkOrderService.getCheckOrder(documentId));
    }

    @Override
    public InventorySnapshot getWmsInventory(Long warehouseId, Long skuId) {
        WmsInventoryListReqVO request = new WmsInventoryListReqVO();
        request.setWarehouseId(warehouseId);
        WmsInventoryDO inventory = wmsInventoryService.getInventoryList(request).stream()
                .filter(value -> value.getSkuId().equals(skuId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "WMS inventory does not exist for warehouse=" + warehouseId + ", sku=" + skuId));
        return new InventorySnapshot(inventory.getId(), inventory.getWarehouseId(), inventory.getSkuId(),
                inventory.getQuantity());
    }

    private static PhysicalOrderSnapshot toSnapshot(String documentType, WmsReceiptOrderDO value) {
        return value == null ? null : new PhysicalOrderSnapshot("WMS", documentType, value.getId(), value.getNo(),
                value.getStatus(), stringify(value.getOrderTime()), value.getWarehouseId(), value.getTotalQuantity(),
                value.getTotalPrice(), value.getRemark());
    }

    private static PhysicalOrderSnapshot toSnapshot(String documentType, WmsShipmentOrderDO value) {
        return value == null ? null : new PhysicalOrderSnapshot("WMS", documentType, value.getId(), value.getNo(),
                value.getStatus(), stringify(value.getOrderTime()), value.getWarehouseId(), value.getTotalQuantity(),
                value.getTotalPrice(), value.getRemark());
    }

    private static PhysicalOrderSnapshot toSnapshot(String documentType, WmsMovementOrderDO value) {
        return value == null ? null : new PhysicalOrderSnapshot("WMS", documentType, value.getId(), value.getNo(),
                value.getStatus(), stringify(value.getOrderTime()), value.getTargetWarehouseId(), value.getTotalQuantity(),
                value.getTotalPrice(), value.getRemark());
    }

    private static PhysicalOrderSnapshot toSnapshot(String documentType, WmsCheckOrderDO value) {
        return value == null ? null : new PhysicalOrderSnapshot("WMS", documentType, value.getId(), value.getNo(),
                value.getStatus(), stringify(value.getOrderTime()), value.getWarehouseId(), value.getTotalQuantity(),
                value.getTotalPrice(), value.getRemark());
    }

    private static PhysicalOrderSnapshot requireDocument(PhysicalOrderSnapshot snapshot, String documentType) {
        if (snapshot == null) {
            throw new IllegalArgumentException(documentType + " does not exist");
        }
        return snapshot;
    }

    private static String stringify(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private static LocalDateTime parseBusinessTime(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required as ISO-8601 local date-time");
        }
        try {
            return LocalDateTime.parse(value);
        } catch (java.time.format.DateTimeParseException ex) {
            throw new IllegalArgumentException(field + " must be an ISO-8601 local date-time", ex);
        }
    }

    private static void copyQuantityLine(YudaoWmsCommandApi.QuantityLine line, WmsReceiptOrderDetailSaveReqVO item) {
        item.setSkuId(line.skuId());
        item.setQuantity(line.quantity());
        item.setPrice(line.price());
        item.setTotalPrice(line.totalPrice());
    }

    private static void copyQuantityLine(YudaoWmsCommandApi.QuantityLine line, WmsShipmentOrderDetailSaveReqVO item) {
        item.setSkuId(line.skuId());
        item.setQuantity(line.quantity());
        item.setPrice(line.price());
        item.setTotalPrice(line.totalPrice());
    }

    private static void copyQuantityLine(YudaoWmsCommandApi.QuantityLine line, WmsMovementOrderDetailSaveReqVO item) {
        item.setSkuId(line.skuId());
        item.setQuantity(line.quantity());
        item.setPrice(line.price());
        item.setTotalPrice(line.totalPrice());
    }
}
