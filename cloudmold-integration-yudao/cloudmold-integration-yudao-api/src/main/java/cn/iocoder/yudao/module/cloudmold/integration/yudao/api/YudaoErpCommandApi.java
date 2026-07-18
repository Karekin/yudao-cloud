package cn.iocoder.yudao.module.cloudmold.integration.yudao.api;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Typed anti-corruption commands for the upstream yudao ERP implementation.
 * CloudMold Skills depend on this contract and never on controller VOs.
 */
public interface YudaoErpCommandApi {

    Long createCustomer(CustomerCommand command);

    Long createWarehouse(WarehouseCommand command);

    Long createPurchaseOrder(PurchaseOrderCommand command);

    Boolean setPurchaseOrderStatus(DocumentStatusCommand command);

    Long createPurchaseInFromOrder(PurchaseInFromOrderCommand command);

    Boolean setPurchaseInStatus(DocumentStatusCommand command);

    Long createSaleOrder(SaleOrderCommand command);

    Boolean setSaleOrderStatus(DocumentStatusCommand command);

    Long createSaleOutFromOrder(SaleOutFromOrderCommand command);

    Boolean setSaleOutStatus(DocumentStatusCommand command);

    Long createStockMove(StockMoveCommand command);

    Boolean setStockMoveStatus(DocumentStatusCommand command);

    Long createStockCheck(StockCheckCommand command);

    Boolean setStockCheckStatus(DocumentStatusCommand command);

    Long createFinancePayment(FinancePaymentCommand command);

    Boolean setFinancePaymentStatus(DocumentStatusCommand command);

    Long createFinanceReceipt(FinanceReceiptCommand command);

    Boolean setFinanceReceiptStatus(DocumentStatusCommand command);

    record DocumentStatusCommand(String idempotencyKey, Long documentId, Integer targetStatus) implements Serializable {
    }

    record CustomerCommand(String idempotencyKey, String name, String contact, String mobile,
                           String remark, Integer status, Integer sort) implements Serializable {
    }

    record WarehouseCommand(String idempotencyKey, String name, String address, Long sort,
                            String remark, String principal, BigDecimal warehousePrice,
                            BigDecimal truckagePrice, Integer status) implements Serializable {
    }

    record PurchaseOrderCommand(String idempotencyKey, Long supplierId, Long accountId, String orderTime,
                                BigDecimal discountPercent, BigDecimal depositPrice, String remark,
                                List<PurchaseOrderLine> items) implements Serializable {
    }

    record PurchaseOrderLine(Long productId, Long productUnitId, BigDecimal productPrice,
                             BigDecimal count, BigDecimal taxPercent, String remark) implements Serializable {
    }

    record PurchaseInFromOrderCommand(String idempotencyKey, Long orderId, Long warehouseId,
                                      Long accountId, String inTime, String remark) implements Serializable {
    }

    record SaleOrderCommand(String idempotencyKey, Long customerId, Long saleUserId, Long accountId, String orderTime,
                            BigDecimal discountPercent, BigDecimal depositPrice, String remark,
                            List<SaleOrderLine> items) implements Serializable {
    }

    record SaleOrderLine(Long productId, Long productUnitId, BigDecimal productPrice,
                         BigDecimal count, BigDecimal taxPercent, String remark) implements Serializable {
    }

    record SaleOutFromOrderCommand(String idempotencyKey, Long orderId, Long warehouseId,
                                   Long accountId, Long saleUserId, String outTime,
                                   String remark) implements Serializable {
    }

    record StockMoveCommand(String idempotencyKey, Long customerId, String moveTime, String remark,
                            List<StockMoveLine> items) implements Serializable {
    }

    record StockMoveLine(Long fromWarehouseId, Long toWarehouseId, Long productId,
                         BigDecimal productPrice, BigDecimal count, String remark) implements Serializable {
    }

    record StockCheckCommand(String idempotencyKey, String checkTime, String remark,
                             List<StockCheckLine> items) implements Serializable {
    }

    record StockCheckLine(Long warehouseId, Long productId, BigDecimal productPrice,
                          BigDecimal stockCount, BigDecimal actualCount, BigDecimal count,
                          String remark) implements Serializable {
    }

    record FinancePaymentCommand(String idempotencyKey, String paymentTime, Long financeUserId, Long supplierId,
                                 Long accountId, BigDecimal discountPrice, String remark,
                                 List<FinancePaymentLine> items) implements Serializable {
    }

    record FinancePaymentLine(Integer bizType, Long bizId, BigDecimal paidPrice,
                              BigDecimal paymentPrice, String remark) implements Serializable {
    }

    record FinanceReceiptCommand(String idempotencyKey, String receiptTime, Long financeUserId, Long customerId,
                                 Long accountId, BigDecimal discountPrice, String remark,
                                 List<FinanceReceiptLine> items) implements Serializable {
    }

    record FinanceReceiptLine(Integer bizType, Long bizId, BigDecimal receiptedPrice,
                              BigDecimal receiptPrice, String remark) implements Serializable {
    }
}
