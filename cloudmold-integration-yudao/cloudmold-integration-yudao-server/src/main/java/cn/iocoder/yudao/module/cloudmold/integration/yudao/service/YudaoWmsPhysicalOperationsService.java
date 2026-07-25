package cn.iocoder.yudao.module.cloudmold.integration.yudao.service;

import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoWmsCommandApi;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.wms.LegacyWmsMasterDataPort;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.wms.LegacyWmsPhysicalOperationsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class YudaoWmsPhysicalOperationsService implements YudaoWmsCommandApi {

    private static final int FINISHED_STATUS = 4;
    private static final String FAILURE_UNSUPPORTED_CAPABILITY = "UNSUPPORTED_CAPABILITY";
    private static final String FAILURE_UNSUPPORTED_VERSION = "UNSUPPORTED_VERSION_CHECK";
    private static final String FAILURE_STATUS_MISMATCH = "UNEXPECTED_CURRENT_STATUS";
    private static final String FAILURE_INVALID_ARGUMENT = "INVALID_ARGUMENT";
    private static final String FAILURE_STATE_CONFLICT = "STATE_CONFLICT";
    private static final String FAILURE_UPSTREAM_FAILURE = "UPSTREAM_FAILURE";

    private final LegacyWmsMasterDataPort masterDataPort;
    private final LegacyWmsPhysicalOperationsPort physicalOperationsPort;
    private final YudaoWmsReceiptInventoryBridgeService receiptInventoryBridgeService;
    private final YudaoCommandOperationService operationService;

    @Override
    public Long createMerchant(MerchantCommand command) {
        return masterDataPort.createMerchant(command);
    }

    @Override
    public Long createWarehouse(WarehouseCommand command) {
        return masterDataPort.createWarehouse(command);
    }

    @Override
    public Long createItemCategory(ItemCategoryCommand command) {
        return masterDataPort.createItemCategory(command);
    }

    @Override
    public Long createItem(ItemCommand command) {
        return masterDataPort.createItem(command);
    }

    @Override
    public Long createReceiptOrder(ReceiptOrderCommand command) {
        return operationService.executeLong("CREATE_WMS_RECEIPT_ORDER", command.idempotencyKey(), command,
                () -> physicalOperationsPort.createReceiptOrder(command));
    }

    @Override
    public YudaoWmsCommandApi.PhysicalOperationResult completeReceiptOrder(DocumentActionCommand command) {
        try {
            return operationService.executeTyped("COMPLETE_WMS_RECEIPT_ORDER", command.idempotencyKey(), command,
                    YudaoWmsCommandApi.PhysicalOperationResult.class,
                    () -> requireCommittable(evaluateReceiptCompletion(command)));
        } catch (PhysicalOperationNotCommitted failure) {
            return failure.result();
        }
    }

    @Override
    public Long createShipmentOrder(ShipmentOrderCommand command) {
        return operationService.executeLong("CREATE_WMS_SHIPMENT_ORDER", command.idempotencyKey(), command,
                () -> physicalOperationsPort.createShipmentOrder(command));
    }

    @Override
    public YudaoWmsCommandApi.PhysicalOperationResult completeShipmentOrder(DocumentActionCommand command) {
        return executePhysicalAction("COMPLETE_WMS_SHIPMENT_ORDER", command,
                () -> physicalOperationsPort.getShipmentOrder(command.documentId()),
                () -> physicalOperationsPort.completeShipmentOrder(command));
    }

    @Override
    public Long createMovementOrder(MovementOrderCommand command) {
        return operationService.executeLong("CREATE_WMS_MOVEMENT_ORDER", command.idempotencyKey(), command,
                () -> physicalOperationsPort.createMovementOrder(command));
    }

    @Override
    public YudaoWmsCommandApi.PhysicalOperationResult completeMovementOrder(DocumentActionCommand command) {
        return executePhysicalAction("COMPLETE_WMS_MOVEMENT_ORDER", command,
                () -> physicalOperationsPort.getMovementOrder(command.documentId()),
                () -> physicalOperationsPort.completeMovementOrder(command));
    }

    @Override
    public Long createCheckOrder(CheckOrderCommand command) {
        return operationService.executeLong("CREATE_WMS_CHECK_ORDER", command.idempotencyKey(), command,
                () -> physicalOperationsPort.createCheckOrder(command));
    }

    @Override
    public YudaoWmsCommandApi.PhysicalOperationResult completeCheckOrder(DocumentActionCommand command) {
        return executePhysicalAction("COMPLETE_WMS_CHECK_ORDER", command,
                () -> physicalOperationsPort.getCheckOrder(command.documentId()),
                () -> physicalOperationsPort.completeCheckOrder(command));
    }

    @Override
    public YudaoWmsCommandApi.PhysicalOperationResult completePutaway(DocumentActionCommand command) {
        return executeUnsupportedAction("COMPLETE_WMS_PUTAWAY", command,
                "upstream yudao WMS has no dedicated putaway capability");
    }

    @Override
    public YudaoWmsCommandApi.PhysicalOperationResult completePicking(DocumentActionCommand command) {
        return executeUnsupportedAction("COMPLETE_WMS_PICKING", command,
                "upstream yudao WMS has no dedicated picking capability");
    }

    private YudaoWmsCommandApi.PhysicalOperationResult executeUnsupportedAction(String operationType,
                                                                                DocumentActionCommand command,
                                                                                String message) {
        return operationService.executeTyped(operationType, command.idempotencyKey(), command,
                YudaoWmsCommandApi.PhysicalOperationResult.class,
                () -> unsupported(operationType, message));
    }

    private YudaoWmsCommandApi.PhysicalOperationResult executePhysicalAction(
            String operationType,
            DocumentActionCommand command,
            java.util.function.Supplier<LegacyWmsPhysicalOperationsPort.PhysicalOrderSnapshot> snapshotSupplier,
            java.util.function.Supplier<LegacyWmsPhysicalOperationsPort.PhysicalOrderSnapshot> action) {
        try {
            return operationService.executeTyped(operationType, command.idempotencyKey(), command,
                    YudaoWmsCommandApi.PhysicalOperationResult.class,
                    () -> requireCommittable(evaluatePhysicalAction(
                            operationType, command, snapshotSupplier, action)));
        } catch (PhysicalOperationNotCommitted failure) {
            return failure.result();
        }
    }

    private YudaoWmsCommandApi.PhysicalOperationResult evaluatePhysicalAction(
            String operationType,
            DocumentActionCommand command,
            java.util.function.Supplier<LegacyWmsPhysicalOperationsPort.PhysicalOrderSnapshot> snapshotSupplier,
            java.util.function.Supplier<LegacyWmsPhysicalOperationsPort.PhysicalOrderSnapshot> action) {
        if (command.expectedDocumentVersion() != null) {
            return failed(operationType, FAILURE_UNSUPPORTED_VERSION,
                    "upstream yudao WMS does not expose a document version for this capability", null, false);
        }
        try {
            LegacyWmsPhysicalOperationsPort.PhysicalOrderSnapshot current = snapshotSupplier.get();
            if (command.expectedCurrentStatus() != null && current != null
                    && !command.expectedCurrentStatus().equals(current.status())) {
                return failed(operationType, FAILURE_STATUS_MISMATCH,
                        "current status does not match the expected status", current, true);
            }
            LegacyWmsPhysicalOperationsPort.PhysicalOrderSnapshot completed = action.get();
            if (completed == null) {
                return failed(operationType, FAILURE_INVALID_ARGUMENT,
                        "completion did not return a document snapshot", null, true);
            }
            if (completed.status() == null || completed.status() != FINISHED_STATUS) {
                return failed(operationType, FAILURE_STATE_CONFLICT,
                        "physical document is not finished after completion", completed, true);
            }
            return new YudaoWmsCommandApi.PhysicalOperationResult(true, true, operationType, null, null, toView(completed));
        } catch (IllegalArgumentException ex) {
            return failed(operationType, FAILURE_INVALID_ARGUMENT, ex.getMessage(), null, true);
        } catch (IllegalStateException ex) {
            return failed(operationType, FAILURE_STATE_CONFLICT, ex.getMessage(), null, true);
        } catch (RuntimeException ex) {
            return failed(operationType, FAILURE_UPSTREAM_FAILURE, ex.getMessage(), null, true);
        }
    }

    private YudaoWmsCommandApi.PhysicalOperationResult evaluateReceiptCompletion(DocumentActionCommand command) {
        if (command.expectedDocumentVersion() != null) {
            return failed("COMPLETE_WMS_RECEIPT_ORDER", FAILURE_UNSUPPORTED_VERSION,
                    "upstream yudao WMS does not expose a document version for this capability", null, false);
        }
        try {
            LegacyWmsPhysicalOperationsPort.ReceiptOrderContext context =
                    physicalOperationsPort.getReceiptOrderContext(command.documentId());
            if (context == null || context.order() == null) {
                return failed("COMPLETE_WMS_RECEIPT_ORDER", FAILURE_INVALID_ARGUMENT,
                        "receipt order does not exist", null, true);
            }
            if (context.order().status() != null && context.order().status() == FINISHED_STATUS) {
                YudaoWmsReceiptInventoryBridgeService.ReceiptBridgeReplay replay =
                        receiptInventoryBridgeService.resolveReplay(command.documentId(), context);
                if (replay.exists()) {
                    return new YudaoWmsCommandApi.PhysicalOperationResult(
                            true, true, "COMPLETE_WMS_RECEIPT_ORDER", null, null, toView(context.order()));
                }
                return failed("COMPLETE_WMS_RECEIPT_ORDER", FAILURE_STATE_CONFLICT,
                        "receipt order is already finished without canonical inventory evidence",
                        context.order(), true);
            }
            if (command.expectedCurrentStatus() != null
                    && !command.expectedCurrentStatus().equals(context.order().status())) {
                return failed("COMPLETE_WMS_RECEIPT_ORDER", FAILURE_STATUS_MISMATCH,
                        "current status does not match the expected status", context.order(), true);
            }
            YudaoWmsReceiptInventoryBridgeService.PreparedReceipt prepared =
                    receiptInventoryBridgeService.prepare(context);
            LegacyWmsPhysicalOperationsPort.PhysicalOrderSnapshot completed =
                    physicalOperationsPort.completeReceiptOrder(command);
            if (completed == null) {
                return failed("COMPLETE_WMS_RECEIPT_ORDER", FAILURE_INVALID_ARGUMENT,
                        "completion did not return a document snapshot", null, true);
            }
            if (completed.status() == null || completed.status() != FINISHED_STATUS) {
                return failed("COMPLETE_WMS_RECEIPT_ORDER", FAILURE_STATE_CONFLICT,
                        "physical document is not finished after completion", completed, true);
            }
            receiptInventoryBridgeService.apply(prepared);
            return new YudaoWmsCommandApi.PhysicalOperationResult(
                    true, true, "COMPLETE_WMS_RECEIPT_ORDER", null, null, toView(completed));
        } catch (IllegalArgumentException ex) {
            return failed("COMPLETE_WMS_RECEIPT_ORDER", FAILURE_INVALID_ARGUMENT, ex.getMessage(), null, true);
        } catch (IllegalStateException ex) {
            return failed("COMPLETE_WMS_RECEIPT_ORDER", FAILURE_STATE_CONFLICT, ex.getMessage(), null, true);
        } catch (RuntimeException ex) {
            return failed("COMPLETE_WMS_RECEIPT_ORDER", FAILURE_UPSTREAM_FAILURE, ex.getMessage(), null, true);
        }
    }

    private static YudaoWmsCommandApi.PhysicalOperationResult unsupported(String operationType, String message) {
        return new YudaoWmsCommandApi.PhysicalOperationResult(false, false, operationType,
                FAILURE_UNSUPPORTED_CAPABILITY, message, null);
    }

    /**
     * A supported physical action that did not succeed must cross the
     * transactional operation boundary as an exception. The caller converts it
     * back to the typed result only after the ledger/upstream transaction has
     * rolled back, so the same idempotency key remains retryable.
     */
    private static YudaoWmsCommandApi.PhysicalOperationResult requireCommittable(
            YudaoWmsCommandApi.PhysicalOperationResult result) {
        if (result.supported() && !result.success()) {
            throw new PhysicalOperationNotCommitted(result);
        }
        return result;
    }

    private static YudaoWmsCommandApi.PhysicalOperationResult failed(
            String operationType, String failureCode,
            String message,
            LegacyWmsPhysicalOperationsPort.PhysicalOrderSnapshot snapshot,
            boolean supported) {
        return new YudaoWmsCommandApi.PhysicalOperationResult(false, supported, operationType, failureCode,
                message, toView(snapshot));
    }

    private static YudaoWmsCommandApi.PhysicalOrderView toView(
            LegacyWmsPhysicalOperationsPort.PhysicalOrderSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new YudaoWmsCommandApi.PhysicalOrderView(snapshot.sourceSystem(), snapshot.documentType(), snapshot.documentId(),
                snapshot.documentNo(), snapshot.status(), snapshot.businessTime(), snapshot.warehouseId(),
                snapshot.quantity(), snapshot.amount(), snapshot.remark());
    }

    private static final class PhysicalOperationNotCommitted extends RuntimeException {
        private final YudaoWmsCommandApi.PhysicalOperationResult result;

        private PhysicalOperationNotCommitted(YudaoWmsCommandApi.PhysicalOperationResult result) {
            super(result.failureCode());
            this.result = result;
        }

        private YudaoWmsCommandApi.PhysicalOperationResult result() {
            return result;
        }
    }
}
