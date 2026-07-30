package cn.iocoder.yudao.module.cloudmold.integration.yudao.api;

import java.io.Serializable;
import java.math.BigDecimal;

/** Typed anti-corruption commands for the upstream yudao MES implementation. */
public interface YudaoMesCommandApi {

    Long createUnitMeasure(UnitMeasureCommand command);

    Long createItemType(ItemTypeCommand command);

    Long createItem(ItemCommand command);

    Long createWorkshop(WorkshopCommand command);

    Long createProcess(ProcessCommand command);

    Long createWorkstation(WorkstationCommand command);

    Long createRoute(RouteCommand command);

    Long createRouteProcess(RouteProcessCommand command);

    Boolean setRouteStatus(RouteStatusCommand command);

    Long createWorkOrder(WorkOrderCommand command);

    Boolean confirmWorkOrder(DocumentActionCommand command);

    Long createTask(TaskCommand command);

    Long createFeedback(FeedbackCommand command);

    Boolean submitFeedback(DocumentActionCommand command);

    Boolean approveFeedback(DocumentActionCommand command);

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

    record WorkshopCommand(String idempotencyKey, String code, String name, BigDecimal area,
                           Long chargeUserId, Integer status, String remark) implements Serializable {
    }

    record ProcessCommand(String idempotencyKey, String code, String name, String attention,
                          Integer status, String remark) implements Serializable {
    }

    record WorkstationCommand(String idempotencyKey, String code, String name, String address,
                              Long workshopId, Long processId, Long warehouseId, Long locationId,
                              Long areaId, Integer status, String remark) implements Serializable {
    }

    record RouteCommand(String idempotencyKey, String code, String name,
                        String description, String remark) implements Serializable {
    }

    record RouteProcessCommand(String idempotencyKey, Long routeId, Long processId,
                               Integer sort, Integer linkType, Integer prepareTime, Integer waitTime,
                               String colorCode, Boolean keyFlag, Boolean checkFlag,
                               String remark) implements Serializable {
    }

    record RouteStatusCommand(String idempotencyKey, Long routeId, Integer status) implements Serializable {
    }

    record WorkOrderCommand(String idempotencyKey, String code, String name, Integer type, Integer orderSourceType,
                            String orderSourceCode, Long productId, BigDecimal quantity,
                            Long clientId, Long vendorId, String batchCode,
                            String requestDate, Long parentId,
                            String remark) implements Serializable {
    }

    record TaskCommand(String idempotencyKey, Long workOrderId, Long workstationId,
                       Long routeId, Long processId, Long itemId, BigDecimal quantity,
                       String startTime, Integer duration, String endTime,
                       String colorCode, String remark) implements Serializable {
    }

    record FeedbackCommand(String idempotencyKey, String code, Integer type,
                           Long workstationId, Long routeId, Long processId,
                           Long workOrderId, Long taskId, Long itemId, String expireDate,
                           String lotNumber, BigDecimal scheduledQuantity, BigDecimal feedbackQuantity,
                           BigDecimal qualifiedQuantity, BigDecimal unqualifiedQuantity,
                           BigDecimal uncheckQuantity, BigDecimal laborScrapQuantity,
                           BigDecimal materialScrapQuantity, BigDecimal otherScrapQuantity,
                           Long feedbackUserId, String feedbackTime, Long approveUserId,
                           String remark) implements Serializable {
    }
}
