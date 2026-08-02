package cn.iocoder.yudao.module.cloudmold.inventory.api;

/**
 * Canonical stock-transfer stages. A transfer line keeps one movement group across all partial dispatches and receipts.
 */
public enum InventoryStockTransferOperation {
    DISPATCH,
    RECEIVE
}
