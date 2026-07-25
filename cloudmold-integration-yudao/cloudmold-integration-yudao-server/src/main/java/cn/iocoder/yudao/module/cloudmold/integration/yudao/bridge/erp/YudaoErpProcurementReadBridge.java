package cn.iocoder.yudao.module.cloudmold.integration.yudao.bridge.erp;

import cn.iocoder.yudao.module.cloudmold.integration.yudao.procurement.LegacyProcurementReadPort;
import cn.iocoder.yudao.module.erp.dal.dataobject.purchase.ErpPurchaseOrderDO;
import cn.iocoder.yudao.module.erp.service.purchase.ErpPurchaseOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class YudaoErpProcurementReadBridge implements LegacyProcurementReadPort {

    private final ErpPurchaseOrderService purchaseOrderService;

    @Override
    public PurchaseOrderSnapshot getPurchaseOrder(Long purchaseOrderId) {
        ErpPurchaseOrderDO order = purchaseOrderService.validatePurchaseOrder(purchaseOrderId);
        return new PurchaseOrderSnapshot(
                order.getId(),
                order.getNo(),
                order.getSupplierId(),
                purchaseOrderService.getPurchaseOrderItemListByOrderId(order.getId()).stream()
                        .map(line -> new PurchaseOrderLineSnapshot(
                                line.getId(),
                                line.getOrderId(),
                                line.getProductId(),
                                line.getProductUnitId(),
                                line.getCount(),
                                defaultZero(line.getInCount()),
                                defaultZero(line.getReturnCount())))
                        .toList());
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
