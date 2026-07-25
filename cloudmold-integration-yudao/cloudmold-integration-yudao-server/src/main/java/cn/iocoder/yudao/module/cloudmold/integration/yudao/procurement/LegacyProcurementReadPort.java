package cn.iocoder.yudao.module.cloudmold.integration.yudao.procurement;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * CloudMold-owned read boundary over upstream procurement internals.
 */
public interface LegacyProcurementReadPort {

    PurchaseOrderSnapshot getPurchaseOrder(Long purchaseOrderId);

    record PurchaseOrderSnapshot(Long purchaseOrderId,
                                 String purchaseOrderNo,
                                 Long supplierId,
                                 List<PurchaseOrderLineSnapshot> lines) implements Serializable {
    }

    record PurchaseOrderLineSnapshot(Long purchaseOrderLineId,
                                     Long purchaseOrderId,
                                     Long productId,
                                     Long productUnitId,
                                     BigDecimal orderedQuantity,
                                     BigDecimal receivedQuantity,
                                     BigDecimal returnedQuantity) implements Serializable {
    }
}
