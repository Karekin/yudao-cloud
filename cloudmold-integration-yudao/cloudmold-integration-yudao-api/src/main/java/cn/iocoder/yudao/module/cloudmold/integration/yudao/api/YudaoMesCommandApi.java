package cn.iocoder.yudao.module.cloudmold.integration.yudao.api;

import java.io.Serializable;
import java.math.BigDecimal;

/** Typed anti-corruption commands for the upstream yudao MES implementation. */
public interface YudaoMesCommandApi {

    Long createUnitMeasure(UnitMeasureCommand command);

    Long createItemType(ItemTypeCommand command);

    Long createItem(ItemCommand command);

    Long createWorkOrder(WorkOrderCommand command);

    Boolean confirmWorkOrder(DocumentActionCommand command);

    Boolean finishWorkOrder(DocumentActionCommand command);

    record DocumentActionCommand(String idempotencyKey, Long documentId) implements Serializable {
    }

    record UnitMeasureCommand(String idempotencyKey, String code, String name,
                              Boolean primaryFlag, Long primaryId, BigDecimal changeRate,
                              Integer status, String remark) implements Serializable {
    }

    record ItemTypeCommand(String idempotencyKey, Long parentId, String code, String name,
                           String itemOrProduct, Integer sort, Integer status,
                           String remark) implements Serializable {
    }

    record ItemCommand(String idempotencyKey, String code, String name, String specification,
                       Long unitMeasureId, Long itemTypeId, Boolean safeStockFlag,
                       BigDecimal minStock, BigDecimal maxStock, Boolean highValue,
                       Boolean batchFlag, String remark) implements Serializable {
    }

    record WorkOrderCommand(String idempotencyKey, String code, String name, Integer type, Integer orderSourceType,
                            String orderSourceCode, Long productId, BigDecimal quantity,
                            Long clientId, Long vendorId, String batchCode,
                            String requestDate, Long parentId,
                            String remark) implements Serializable {
    }
}
