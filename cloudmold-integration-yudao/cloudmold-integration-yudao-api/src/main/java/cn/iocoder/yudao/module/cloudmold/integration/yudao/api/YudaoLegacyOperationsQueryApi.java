package cn.iocoder.yudao.module.cloudmold.integration.yudao.api;

import java.io.Serializable;
import java.math.BigDecimal;

/** Read model shared by ERP, WMS and MES Skill assertions. */
public interface YudaoLegacyOperationsQueryApi {

    LegacyDocumentView getPurchaseOrder(Long documentId);

    LegacyDocumentView getPurchaseIn(Long documentId);

    LegacyDocumentView getSaleOrder(Long documentId);

    LegacyDocumentView getSaleOut(Long documentId);

    LegacyDocumentView getStockMove(Long documentId);

    LegacyDocumentView getStockCheck(Long documentId);

    LegacyDocumentView getFinancePayment(Long documentId);

    LegacyDocumentView getFinanceReceipt(Long documentId);

    LegacyDocumentView getReceiptOrder(Long documentId);

    LegacyDocumentView getShipmentOrder(Long documentId);

    LegacyDocumentView getMovementOrder(Long documentId);

    LegacyDocumentView getCheckOrder(Long documentId);

    LegacyDocumentView getWorkOrder(Long documentId);

    WmsSkuView getWmsItemSku(Long itemId);

    WmsInventoryView getWmsInventory(Long warehouseId, Long skuId);

    record LegacyDocumentView(String sourceSystem, String documentType, Long documentId,
                              String documentNo, Integer status, String businessTime,
                              BigDecimal quantity, BigDecimal amount,
                              String remark) implements Serializable {
    }

    record WmsSkuView(Long itemId, Long skuId, String itemCode, String skuCode,
                      String skuName) implements Serializable {
    }

    record WmsInventoryView(Long inventoryId, Long warehouseId, Long skuId,
                            BigDecimal quantity) implements Serializable {
    }
}
