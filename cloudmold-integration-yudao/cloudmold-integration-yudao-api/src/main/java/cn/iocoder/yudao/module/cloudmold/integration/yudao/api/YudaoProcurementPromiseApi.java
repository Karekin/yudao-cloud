package cn.iocoder.yudao.module.cloudmold.integration.yudao.api;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Governed procurement-promise adapter over upstream ERP purchase orders.
 * The promise is explicit, versioned, and separate from mutable ERP remarks.
 */
public interface YudaoProcurementPromiseApi {

    PurchasePromiseView savePurchasePromise(PurchasePromiseCommand command);

    PurchasePromiseView getPurchasePromise(Long purchaseOrderLineId);

    List<PurchaseOrderLineView> listPurchaseOrderLines(Long purchaseOrderId);

    record PurchasePromiseCommand(String idempotencyKey, String runId, Long purchaseOrderId,
                                  Long purchaseOrderLineId, String promisedReceiptAt,
                                  String promiseTimezone, Integer graceMinutes, Integer pauseMinutes,
                                  String status, String reason) implements Serializable {
    }

    record PurchasePromiseView(String promiseId, String promiseKey, Long purchaseOrderId,
                               String purchaseOrderNo, Long purchaseOrderLineId, Long supplierId,
                               Long productId, Long productUnitId, BigDecimal orderedQuantity,
                               String promisedReceiptAt, String promiseTimezone, Integer graceMinutes,
                               Integer pauseMinutes, String promiseFrozenAt, String status,
                               Long version, String runId, String reason) implements Serializable {
    }

    record PurchaseOrderLineView(Long purchaseOrderLineId, Long purchaseOrderId,
                                 Long productId, Long productUnitId, BigDecimal orderedQuantity,
                                 BigDecimal receivedQuantity, BigDecimal returnedQuantity)
            implements Serializable {
    }
}
