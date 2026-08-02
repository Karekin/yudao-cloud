package cn.iocoder.yudao.module.cloudmold.warehouse.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockTransferApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockTransferCommand;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockTransferOperation;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockTransferResult;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockTransferCommand;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockTransferCommandApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockTransferOperation;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockTransferResult;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseReferenceValidationApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferExecutionBatchDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferExecutionLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferOperationDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferOrderDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferOrderLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferRequestDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferRequestLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferStatusHistoryDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.StockTransferStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class StockTransferCommandServiceImpl implements StockTransferCommandApi {

    static final int OPERATION_SUCCEEDED = 10;
    static final String REQUEST_APPROVED_EVENT = "stock_transfer.request.approved";
    static final String ORDER_PREPARED_EVENT = "stock_transfer.order.prepared";
    static final String OUTBOUND_BATCH_EVENT = "stock_transfer.outbound.batch_completed";
    static final String RECEIPT_BATCH_EVENT = "stock_transfer.receipt.batch_completed";
    static final String REQUEST_AGGREGATE_TYPE = "stock_transfer_request";
    static final String ORDER_AGGREGATE_TYPE = "stock_transfer_order";
    static final String BATCH_AGGREGATE_TYPE = "stock_transfer_execution_batch";
    static final String REQUEST_STAGE_CODE = "REQUEST_APPROVED";
    static final String REQUEST_STAGE_LABEL = "调拨请求已批准";
    static final String OUTBOUND_STAGE_CODE = "TRANSFER_OUTBOUND";
    static final String OUTBOUND_STAGE_LABEL = "等待调拨出库";
    static final String INBOUND_STAGE_CODE = "TRANSFER_INBOUND";
    static final String INBOUND_STAGE_LABEL = "等待调拨入库";
    static final String COMPLETE_STAGE_CODE = "NONE";
    static final String COMPLETE_STAGE_LABEL = "调拨已完成";
    static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    static final Pattern SAFE_CODE = Pattern.compile("[A-Z][A-Z0-9_-]{0,63}");
    private static final BigDecimal ZERO = new BigDecimal("0.000000");

    private final StockTransferStoreMapper mapper;
    private final WarehouseReferenceValidationApi warehouseValidationApi;
    private final CatalogSkuValidationApi catalogSkuValidationApi;
    private final InventoryStockTransferApi inventoryStockTransferApi;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockTransferResult execute(StockTransferCommand command) {
        validate(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC);
        String requestHash = fingerprint(tenantId, command);
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), command.getSourceEventId(),
                command.getOperation().name(), requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve stock transfer operation");
        StockTransferOperationDO operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "stock transfer operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with a different stock transfer payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing stock transfer operation is incomplete");
            StockTransferResult replay = JsonUtils.parseObject(operation.getResultJson(), StockTransferResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        StockTransferResult result = switch (command.getOperation()) {
            case CREATE_APPROVED_REQUEST -> createApprovedRequest(tenantId, operationId, command, now);
            case COMPLETE_OUTBOUND_BATCH -> completeOutboundBatch(tenantId, operationId, command, now);
            case COMPLETE_RECEIPT_BATCH -> completeReceiptBatch(tenantId, operationId, command, now);
        };
        result.setOperationId(operationId);
        String aggregateId = result.getBatchId() != null ? result.getBatchId() : result.getRequestId();
        require(mapper.markOperationSucceeded(operationId, tenantId, aggregateId,
                        JsonUtils.toJsonString(result), now) == 1,
                "stock transfer operation completion conflict");
        return result;
    }

    private StockTransferResult createApprovedRequest(Long tenantId, Long operationId,
                                                      StockTransferCommand command, LocalDateTime now) {
        warehouseValidationApi.requireActiveWarehouse(command.getSourceWarehouseId());
        warehouseValidationApi.requireActiveWarehouse(command.getTargetWarehouseId());
        require(mapper.selectRequestBySourceBusiness(tenantId, command.getSourceBusinessType(),
                command.getSourceBusinessRef()) == null,
                "stock transfer request already exists for this source business");

        String requestId = valueOrUuid(command.getRequestId());
        String requestCode = valueOrCode(command.getRequestCode(), "STRRQ");
        String orderId = valueOrUuid(command.getOrderId());
        String orderCode = valueOrCode(command.getOrderCode(), "STRORD");

        StockTransferRequestDO request = new StockTransferRequestDO()
                .setRequestId(requestId).setTenantId(tenantId).setRequestCode(requestCode)
                .setSourceBusinessType(command.getSourceBusinessType())
                .setSourceBusinessRef(command.getSourceBusinessRef())
                .setOwnerType(command.getOwnerType()).setOwnerId(command.getOwnerId())
                .setSourceWarehouseId(command.getSourceWarehouseId())
                .setTargetWarehouseId(command.getTargetWarehouseId())
                .setReasonCode(command.getReasonCode()).setRemark(command.getRemark())
                .setStatus("APPROVED").setVersion(1L).setApprovedAt(now).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertRequest(request) == 1, "failed to persist stock transfer request");

        StockTransferOrderDO order = new StockTransferOrderDO()
                .setOrderId(orderId).setTenantId(tenantId).setRequestId(requestId).setOrderCode(orderCode)
                .setOwnerType(command.getOwnerType()).setOwnerId(command.getOwnerId())
                .setSourceWarehouseId(command.getSourceWarehouseId())
                .setTargetWarehouseId(command.getTargetWarehouseId())
                .setStatus("PREPARE").setVersion(1L).setPreparedAt(now).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertOrder(order) == 1, "failed to persist stock transfer order");

        int generatedLineNumber = 10;
        List<Map<String, Object>> linePayloads = new ArrayList<>();
        for (StockTransferCommand.LineDefinition inputLine : command.getLines()) {
            catalogSkuValidationApi.requireActiveSku(inputLine.getCanonicalSkuId());
            Integer lineNumber = inputLine.getLineNumber() == null ? generatedLineNumber : inputLine.getLineNumber();
            BigDecimal requestedQuantity = normalizeQuantity(inputLine.getRequestedQuantity());
            String requestLineId = inputLine.getLineId() == null || inputLine.getLineId().isBlank()
                    ? requestId + ":line:" + lineNumber : inputLine.getLineId();
            String orderLineId = orderId + ":line:" + lineNumber;
            require(mapper.insertRequestLine(new StockTransferRequestLineDO()
                    .setLineId(requestLineId).setTenantId(tenantId).setRequestId(requestId)
                    .setLineNumber(lineNumber).setCanonicalSkuId(inputLine.getCanonicalSkuId())
                    .setRequestedQuantity(requestedQuantity).setUomCode(inputLine.getUomCode())
                    .setRemark(inputLine.getRemark()).setCreatedAt(now).setUpdatedAt(now)) == 1,
                    "failed to persist stock transfer request line");
            require(mapper.insertOrderLine(new StockTransferOrderLineDO()
                    .setLineId(orderLineId).setTenantId(tenantId).setOrderId(orderId)
                    .setLineNumber(lineNumber).setCanonicalSkuId(inputLine.getCanonicalSkuId())
                    .setMovementGroupId(null).setRequestedQuantity(requestedQuantity)
                    .setOutboundQuantity(ZERO).setReceivedQuantity(ZERO).setUomCode(inputLine.getUomCode())
                    .setStatus("PREPARE").setVersion(1L).setRemark(inputLine.getRemark())
                    .setCreatedAt(now).setUpdatedAt(now)) == 1,
                    "failed to persist stock transfer order line");
            linePayloads.add(Map.of(
                    "line_number", lineNumber,
                    "canonical_sku_id", inputLine.getCanonicalSkuId(),
                    "requested_quantity", requestedQuantity,
                    "uom_code", inputLine.getUomCode()));
            generatedLineNumber += 10;
        }

        insertHistory(tenantId, operationId, "REQUEST", requestId, "APPROVED", 1L,
                REQUEST_STAGE_CODE, REQUEST_STAGE_LABEL, request.getRemark(), now);
        insertHistory(tenantId, operationId, "ORDER", orderId, "PREPARE", 1L,
                OUTBOUND_STAGE_CODE, OUTBOUND_STAGE_LABEL, request.getRemark(), now);

        appendEvent(tenantId, command, REQUEST_APPROVED_EVENT, REQUEST_AGGREGATE_TYPE, requestId, 1L,
                command.getIdempotencyKey() + ":1", requestPayload(request, linePayloads),
                Map.of("status", request.getStatus()));
        Map<String, Object> orderPayload = requestPayload(request, linePayloads);
        orderPayload.put("order_id", orderId);
        orderPayload.put("order_code", orderCode);
        orderPayload.put("current_status", order.getStatus());
        orderPayload.put("current_stage_code", OUTBOUND_STAGE_CODE);
        orderPayload.put("current_stage_label", OUTBOUND_STAGE_LABEL);
        appendEvent(tenantId, command, ORDER_PREPARED_EVENT, ORDER_AGGREGATE_TYPE, orderId, 1L,
                command.getIdempotencyKey() + ":2", orderPayload,
                Map.of("status", order.getStatus(), "stage_code", OUTBOUND_STAGE_CODE));
        return StockTransferResult.builder()
                .requestId(requestId).requestCode(requestCode).requestStatus(request.getStatus())
                .orderId(orderId).orderCode(orderCode).orderStatus(order.getStatus())
                .currentStageCode(OUTBOUND_STAGE_CODE).currentStageLabel(OUTBOUND_STAGE_LABEL)
                .aggregateVersion(order.getVersion()).processedLineCount(command.getLines().size())
                .duplicate(false).build();
    }

    private StockTransferResult completeOutboundBatch(Long tenantId, Long operationId,
                                                      StockTransferCommand command, LocalDateTime now) {
        String orderId = requireRef(command.getOrderId(), "orderId", 128);
        StockTransferCommand.OutboundBatchDefinition batch = requireNonNull(command.getOutboundBatch(),
                "outboundBatch is required");
        StockTransferOrderDO order = requireNonNull(mapper.selectOrderForUpdate(tenantId, orderId),
                "stock transfer order not found");
        StockTransferRequestDO request = requireNonNull(mapper.selectRequestForUpdate(tenantId, order.getRequestId()),
                "stock transfer request not found");
        require(!"COMPLETED".equals(order.getStatus()) && !"CANCELED".equals(order.getStatus()),
                "stock transfer order is closed");

        String batchId = valueOrUuid(batch.getBatchId());
        String batchNo = valueOrCode(batch.getBatchNo(), "STROUT");
        require(mapper.insertExecutionBatch(new StockTransferExecutionBatchDO()
                        .setBatchId(batchId).setTenantId(tenantId).setOrderId(orderId).setBatchNo(batchNo)
                        .setBatchType("OUTBOUND").setStatus("COMPLETED").setVersion(1L).setRemark(batch.getRemark())
                        .setOccurredAt(now).setCreatedAt(now).setUpdatedAt(now)) == 1,
                "failed to persist stock transfer outbound batch");

        List<Map<String, Object>> linePayloads = new ArrayList<>();
        Set<Integer> lineNumbers = new LinkedHashSet<>();
        for (StockTransferCommand.OutboundLineDefinition inputLine : batch.getLines()) {
            require(lineNumbers.add(requirePositive(inputLine.getLineNumber(), "lineNumber")),
                    "duplicate outbound batch lineNumber");
            StockTransferOrderLineDO orderLine = requireNonNull(
                    mapper.selectOrderLineForUpdate(tenantId, orderId, inputLine.getLineNumber()),
                    "stock transfer order line not found");
            require(!"FULL_RECEIVED".equals(orderLine.getStatus()), "stock transfer order line already received");
            warehouseValidationApi.requireActiveLocation(order.getSourceWarehouseId(), inputLine.getSourceLocationId());
            warehouseValidationApi.requireActiveLocation(order.getTargetWarehouseId(), inputLine.getTargetLocationId());
            require(inputLine.getSourceStockStatus().equals(inputLine.getTargetStockStatus()),
                    "sourceStockStatus and targetStockStatus must match inventory transfer contract");
            require(inputLine.getSourceQualityStatus().equals(inputLine.getTargetQualityStatus()),
                    "sourceQualityStatus and targetQualityStatus must match inventory transfer contract");
            String movementGroupId = orderLine.getMovementGroupId() == null || orderLine.getMovementGroupId().isBlank()
                    ? UUID.randomUUID().toString() : orderLine.getMovementGroupId();
            String executionLineId = valueOrUuid(inputLine.getExecutionLineId());
            InventoryStockTransferResult transfer = inventoryStockTransferApi.execute(InventoryStockTransferCommand.builder()
                    .operation(InventoryStockTransferOperation.DISPATCH)
                    .idempotencyKey(command.getIdempotencyKey() + ":dispatch:" + executionLineId)
                    .sourceEventId(eventId(command.getSourceEventId(), "dispatch", executionLineId))
                    .movementGroupId(movementGroupId)
                    .ownerType(order.getOwnerType()).ownerId(order.getOwnerId())
                    .canonicalSkuId(orderLine.getCanonicalSkuId())
                    .sourceWarehouseId(order.getSourceWarehouseId())
                    .sourceLocationId(inputLine.getSourceLocationId())
                    .targetWarehouseId(order.getTargetWarehouseId())
                    .targetLocationId(inputLine.getTargetLocationId())
                    .lotId(inputLine.getLotId())
                    .stockStatus(inputLine.getSourceStockStatus())
                    .qualityStatus(inputLine.getSourceQualityStatus())
                    .baseUomCode(orderLine.getUomCode())
                    .quantity(normalizeQuantity(inputLine.getQuantity()))
                    .businessType("WAREHOUSE_TRANSFER")
                    .businessId(order.getOrderId())
                    .businessItemId(orderLine.getLineId())
                    .businessNo(order.getOrderCode())
                    .correlationId(command.getCorrelationId())
                    .causationId(command.getCausationId())
                    .occurredAt(command.getOccurredAt())
                    .build());
            require(transfer.getLedgerTransactionId() != null,
                    "inventory stock transfer dispatch did not return ledgerTransactionId");
            require(movementGroupId.equals(transfer.getMovementGroupId()),
                    "inventory stock transfer dispatch returned mismatched movementGroupId");
            require(transfer.getCumulativeDispatchedQuantity().compareTo(orderLine.getRequestedQuantity()) <= 0,
                    "inventory dispatch exceeds requestedQuantity");

            require(mapper.insertExecutionLine(new StockTransferExecutionLineDO()
                            .setExecutionLineId(executionLineId).setTenantId(tenantId)
                            .setBatchId(batchId).setOrderId(orderId).setOrderLineId(orderLine.getLineId())
                            .setLineNumber(orderLine.getLineNumber())
                            .setOutboundExecutionLineId(null)
                            .setCanonicalSkuId(orderLine.getCanonicalSkuId())
                            .setMovementGroupId(movementGroupId)
                            .setExecutedQuantity(normalizeQuantity(inputLine.getQuantity()))
                            .setReceivedQuantity(ZERO)
                            .setCumulativeDispatchedQuantity(transfer.getCumulativeDispatchedQuantity())
                            .setCumulativeReceivedQuantity(transfer.getCumulativeReceivedQuantity())
                            .setOutstandingQuantity(transfer.getOutstandingQuantity())
                            .setStatus("IN_TRANSIT").setVersion(1L)
                            .setLotId(inputLine.getLotId())
                            .setSourceLocationId(inputLine.getSourceLocationId())
                            .setSourceStockStatus(inputLine.getSourceStockStatus())
                            .setSourceQualityStatus(inputLine.getSourceQualityStatus())
                            .setTargetLocationId(inputLine.getTargetLocationId())
                            .setTargetStockStatus(inputLine.getTargetStockStatus())
                            .setTargetQualityStatus(inputLine.getTargetQualityStatus())
                            .setDispatchLedgerTransactionId(transfer.getLedgerTransactionId())
                            .setReceiveLedgerTransactionId(null)
                            .setRemark(inputLine.getRemark()).setOccurredAt(now)
                            .setCreatedAt(now).setUpdatedAt(now)) == 1,
                    "failed to persist stock transfer outbound execution line");

            String lineStatus = lineStatus(transfer.getCumulativeDispatchedQuantity(),
                    transfer.getCumulativeReceivedQuantity(), orderLine.getRequestedQuantity());
            long nextVersion = orderLine.getVersion() + 1;
            require(mapper.updateOrderLineExecutionCas(tenantId, orderLine.getLineId(), orderLine.getVersion(),
                            nextVersion, movementGroupId, transfer.getCumulativeDispatchedQuantity(),
                            transfer.getCumulativeReceivedQuantity(), lineStatus, now) == 1,
                    "stock transfer order line version conflict");
            Stage lineStage = lineStage(lineStatus);
            insertHistory(tenantId, operationId, "ORDER_LINE", orderLine.getLineId(), lineStatus, nextVersion,
                    lineStage.code(), lineStage.label(), inputLine.getRemark(), now);
            linePayloads.add(Map.of(
                    "execution_line_id", executionLineId,
                    "line_number", orderLine.getLineNumber(),
                    "canonical_sku_id", orderLine.getCanonicalSkuId(),
                    "movement_group_id", movementGroupId,
                    "executed_quantity", normalizeQuantity(inputLine.getQuantity()),
                    "ledger_transaction_id", transfer.getLedgerTransactionId(),
                    "line_status", lineStatus,
                    "source_location_id", inputLine.getSourceLocationId(),
                    "target_location_id", inputLine.getTargetLocationId()));
        }

        OrderProgress progress = refreshOrderProgress(tenantId, operationId, request, order, now, batch.getRemark());
        appendEvent(tenantId, command, OUTBOUND_BATCH_EVENT, BATCH_AGGREGATE_TYPE, batchId, 1L,
                command.getIdempotencyKey() + ":outbound-batch",
                batchPayload(request, order, batchId, batchNo, "OUTBOUND", batch.getRemark(), linePayloads,
                        progress.stage()),
                Map.of("status", progress.orderStatus(), "stage_code", progress.stage().code()));
        return StockTransferResult.builder()
                .requestId(request.getRequestId()).requestCode(request.getRequestCode())
                .requestStatus(progress.requestStatus()).orderId(order.getOrderId()).orderCode(order.getOrderCode())
                .orderStatus(progress.orderStatus()).batchId(batchId).batchNo(batchNo).batchType("OUTBOUND")
                .processedLineCount(batch.getLines().size()).currentStageCode(progress.stage().code())
                .currentStageLabel(progress.stage().label()).aggregateVersion(progress.orderVersion())
                .duplicate(false).build();
    }

    private StockTransferResult completeReceiptBatch(Long tenantId, Long operationId,
                                                     StockTransferCommand command, LocalDateTime now) {
        String orderId = requireRef(command.getOrderId(), "orderId", 128);
        StockTransferCommand.ReceiptBatchDefinition batch = requireNonNull(command.getReceiptBatch(),
                "receiptBatch is required");
        StockTransferOrderDO order = requireNonNull(mapper.selectOrderForUpdate(tenantId, orderId),
                "stock transfer order not found");
        StockTransferRequestDO request = requireNonNull(mapper.selectRequestForUpdate(tenantId, order.getRequestId()),
                "stock transfer request not found");
        require(!"COMPLETED".equals(order.getStatus()) && !"CANCELED".equals(order.getStatus()),
                "stock transfer order is closed");

        String batchId = valueOrUuid(batch.getBatchId());
        String batchNo = valueOrCode(batch.getBatchNo(), "STRRCV");
        require(mapper.insertExecutionBatch(new StockTransferExecutionBatchDO()
                        .setBatchId(batchId).setTenantId(tenantId).setOrderId(orderId).setBatchNo(batchNo)
                        .setBatchType("RECEIPT").setStatus("COMPLETED").setVersion(1L).setRemark(batch.getRemark())
                        .setOccurredAt(now).setCreatedAt(now).setUpdatedAt(now)) == 1,
                "failed to persist stock transfer receipt batch");

        List<Map<String, Object>> linePayloads = new ArrayList<>();
        Set<String> outboundIds = new LinkedHashSet<>();
        for (StockTransferCommand.ReceiptLineDefinition inputLine : batch.getLines()) {
            String outboundExecutionLineId = requireRef(inputLine.getOutboundExecutionLineId(),
                    "outboundExecutionLineId", 128);
            require(outboundIds.add(outboundExecutionLineId), "duplicate outboundExecutionLineId in receipt batch");
            StockTransferExecutionLineDO outboundLine = requireNonNull(
                    mapper.selectExecutionLineForUpdate(tenantId, outboundExecutionLineId),
                    "stock transfer outbound execution line not found");
            require(orderId.equals(outboundLine.getOrderId()),
                    "stock transfer receipt line does not belong to order");
            require("IN_TRANSIT".equals(outboundLine.getStatus())
                            || "PARTIAL_RECEIVED".equals(outboundLine.getStatus()),
                    "stock transfer outbound execution line is not receivable");
            StockTransferOrderLineDO orderLine = requireNonNull(
                    mapper.selectOrderLineForUpdate(tenantId, orderId, outboundLine.getLineNumber()),
                    "stock transfer order line not found");
            String receiptExecutionLineId = valueOrUuid(inputLine.getExecutionLineId());
            InventoryStockTransferResult transfer = inventoryStockTransferApi.execute(InventoryStockTransferCommand.builder()
                    .operation(InventoryStockTransferOperation.RECEIVE)
                    .idempotencyKey(command.getIdempotencyKey() + ":receive:" + receiptExecutionLineId)
                    .sourceEventId(eventId(command.getSourceEventId(), "receive", receiptExecutionLineId))
                    .movementGroupId(outboundLine.getMovementGroupId())
                    .ownerType(order.getOwnerType()).ownerId(order.getOwnerId())
                    .canonicalSkuId(orderLine.getCanonicalSkuId())
                    .sourceWarehouseId(order.getSourceWarehouseId())
                    .sourceLocationId(outboundLine.getSourceLocationId())
                    .targetWarehouseId(order.getTargetWarehouseId())
                    .targetLocationId(outboundLine.getTargetLocationId())
                    .lotId(outboundLine.getLotId())
                    .stockStatus(outboundLine.getSourceStockStatus())
                    .qualityStatus(outboundLine.getSourceQualityStatus())
                    .baseUomCode(orderLine.getUomCode())
                    .quantity(normalizeQuantity(inputLine.getQuantity()))
                    .businessType("WAREHOUSE_TRANSFER")
                    .businessId(order.getOrderId())
                    .businessItemId(orderLine.getLineId())
                    .businessNo(order.getOrderCode())
                    .correlationId(command.getCorrelationId())
                    .causationId(command.getCausationId())
                    .occurredAt(command.getOccurredAt())
                    .build());
            require(transfer.getLedgerTransactionId() != null,
                    "inventory stock transfer receive did not return ledgerTransactionId");
            require(outboundLine.getMovementGroupId().equals(transfer.getMovementGroupId()),
                    "inventory stock transfer receive returned mismatched movementGroupId");
            require(transfer.getCumulativeReceivedQuantity().compareTo(orderLine.getRequestedQuantity()) <= 0,
                    "inventory receive exceeds requestedQuantity");

            require(mapper.insertExecutionLine(new StockTransferExecutionLineDO()
                            .setExecutionLineId(receiptExecutionLineId).setTenantId(tenantId)
                            .setBatchId(batchId).setOrderId(orderId).setOrderLineId(orderLine.getLineId())
                            .setLineNumber(orderLine.getLineNumber())
                            .setOutboundExecutionLineId(outboundExecutionLineId)
                            .setCanonicalSkuId(orderLine.getCanonicalSkuId())
                            .setMovementGroupId(outboundLine.getMovementGroupId())
                            .setExecutedQuantity(normalizeQuantity(inputLine.getQuantity()))
                            .setReceivedQuantity(normalizeQuantity(inputLine.getQuantity()))
                            .setCumulativeDispatchedQuantity(transfer.getCumulativeDispatchedQuantity())
                            .setCumulativeReceivedQuantity(transfer.getCumulativeReceivedQuantity())
                            .setOutstandingQuantity(transfer.getOutstandingQuantity())
                            .setStatus("RECEIVED").setVersion(1L)
                            .setLotId(outboundLine.getLotId())
                            .setSourceLocationId(outboundLine.getSourceLocationId())
                            .setSourceStockStatus(outboundLine.getSourceStockStatus())
                            .setSourceQualityStatus(outboundLine.getSourceQualityStatus())
                            .setTargetLocationId(outboundLine.getTargetLocationId())
                            .setTargetStockStatus(outboundLine.getTargetStockStatus())
                            .setTargetQualityStatus(outboundLine.getTargetQualityStatus())
                            .setDispatchLedgerTransactionId(null)
                            .setReceiveLedgerTransactionId(transfer.getLedgerTransactionId())
                            .setRemark(inputLine.getRemark()).setOccurredAt(now)
                            .setCreatedAt(now).setUpdatedAt(now)) == 1,
                    "failed to persist stock transfer receipt execution line");

            String outboundStatus = transfer.getOutstandingQuantity().compareTo(ZERO) == 0
                    ? "FULL_RECEIVED" : "PARTIAL_RECEIVED";
            long nextExecutionVersion = outboundLine.getVersion() + 1;
            require(mapper.updateExecutionLineReceiptCas(tenantId, outboundExecutionLineId, outboundLine.getVersion(),
                            nextExecutionVersion, transfer.getCumulativeReceivedQuantity(),
                            transfer.getCumulativeReceivedQuantity(), transfer.getOutstandingQuantity(),
                            transfer.getLedgerTransactionId(), outboundStatus, now) == 1,
                    "stock transfer outbound execution line version conflict");

            String lineStatus = lineStatus(transfer.getCumulativeDispatchedQuantity(),
                    transfer.getCumulativeReceivedQuantity(), orderLine.getRequestedQuantity());
            long nextLineVersion = orderLine.getVersion() + 1;
            require(mapper.updateOrderLineExecutionCas(tenantId, orderLine.getLineId(), orderLine.getVersion(),
                            nextLineVersion, outboundLine.getMovementGroupId(),
                            transfer.getCumulativeDispatchedQuantity(),
                            transfer.getCumulativeReceivedQuantity(), lineStatus, now) == 1,
                    "stock transfer order line version conflict");
            Stage lineStage = lineStage(lineStatus);
            insertHistory(tenantId, operationId, "ORDER_LINE", orderLine.getLineId(), lineStatus, nextLineVersion,
                    lineStage.code(), lineStage.label(), inputLine.getRemark(), now);
            linePayloads.add(Map.of(
                    "receipt_execution_line_id", receiptExecutionLineId,
                    "outbound_execution_line_id", outboundExecutionLineId,
                    "line_number", orderLine.getLineNumber(),
                    "canonical_sku_id", orderLine.getCanonicalSkuId(),
                    "movement_group_id", orderLine.getMovementGroupId(),
                    "executed_quantity", normalizeQuantity(inputLine.getQuantity()),
                    "ledger_transaction_id", transfer.getLedgerTransactionId(),
                    "line_status", lineStatus));
        }

        OrderProgress progress = refreshOrderProgress(tenantId, operationId, request, order, now, batch.getRemark());
        appendEvent(tenantId, command, RECEIPT_BATCH_EVENT, BATCH_AGGREGATE_TYPE, batchId, 1L,
                command.getIdempotencyKey() + ":receipt-batch",
                batchPayload(request, order, batchId, batchNo, "RECEIPT", batch.getRemark(), linePayloads,
                        progress.stage()),
                Map.of("status", progress.orderStatus(), "stage_code", progress.stage().code()));
        return StockTransferResult.builder()
                .requestId(request.getRequestId()).requestCode(request.getRequestCode())
                .requestStatus(progress.requestStatus()).orderId(order.getOrderId()).orderCode(order.getOrderCode())
                .orderStatus(progress.orderStatus()).batchId(batchId).batchNo(batchNo).batchType("RECEIPT")
                .processedLineCount(batch.getLines().size()).currentStageCode(progress.stage().code())
                .currentStageLabel(progress.stage().label()).aggregateVersion(progress.orderVersion())
                .duplicate(false).build();
    }

    private OrderProgress refreshOrderProgress(Long tenantId, Long operationId, StockTransferRequestDO request,
                                               StockTransferOrderDO order, LocalDateTime now, String remark) {
        List<StockTransferOrderLineDO> lines = mapper.selectOrderLines(tenantId, order.getOrderId());
        String nextOrderStatus = orderStatus(lines);
        long orderVersion = order.getVersion();
        if (!Objects.equals(order.getStatus(), nextOrderStatus)) {
            long nextVersion = order.getVersion() + 1;
            require(mapper.updateOrderStatusCas(tenantId, order.getOrderId(), order.getVersion(), nextVersion,
                            nextOrderStatus, now) == 1,
                    "stock transfer order version conflict");
            Stage stage = stage(lines, request.getStatus());
            insertHistory(tenantId, operationId, "ORDER", order.getOrderId(), nextOrderStatus, nextVersion,
                    stage.code(), stage.label(), remark, now);
            orderVersion = nextVersion;
            order.setStatus(nextOrderStatus).setVersion(nextVersion);
        }

        String requestStatus = request.getStatus();
        if ("COMPLETED".equals(nextOrderStatus) && !"COMPLETED".equals(request.getStatus())) {
            long nextRequestVersion = request.getVersion() + 1;
            require(mapper.updateRequestStatusCas(tenantId, request.getRequestId(), request.getVersion(),
                            nextRequestVersion, "COMPLETED", now) == 1,
                    "stock transfer request version conflict");
            insertHistory(tenantId, operationId, "REQUEST", request.getRequestId(), "COMPLETED",
                    nextRequestVersion, COMPLETE_STAGE_CODE, COMPLETE_STAGE_LABEL, remark, now);
            request.setStatus("COMPLETED").setVersion(nextRequestVersion);
            requestStatus = "COMPLETED";
        }
        return new OrderProgress(requestStatus, nextOrderStatus, orderVersion, stage(lines, requestStatus));
    }

    private void appendEvent(Long tenantId, StockTransferCommand command, String eventType,
                             String aggregateType, String aggregateId, Long aggregateVersion,
                             String idempotencyKey, Map<String, Object> payload, Map<String, Object> headers) {
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType).schemaVersion(1)
                .sourceSystem("cloudmold-warehouse").tenantId(tenantId)
                .aggregateType(aggregateType).aggregateId(aggregateId)
                .aggregateVersion(aggregateVersion).eventSequence((short) 1)
                .occurredAt(command.getOccurredAt()).traceId(traceId(command))
                .correlationId(command.getCorrelationId()).causationId(command.getCausationId())
                .idempotencyKey(idempotencyKey).payload(payload).headers(headers)
                .destination("lakehouse").build());
    }

    private Map<String, Object> requestPayload(StockTransferRequestDO request, List<Map<String, Object>> lines) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("request_id", request.getRequestId());
        payload.put("request_code", request.getRequestCode());
        payload.put("source_business_type", request.getSourceBusinessType());
        payload.put("source_business_ref", request.getSourceBusinessRef());
        payload.put("owner_type", request.getOwnerType());
        payload.put("owner_id", request.getOwnerId());
        payload.put("source_warehouse_id", request.getSourceWarehouseId());
        payload.put("target_warehouse_id", request.getTargetWarehouseId());
        payload.put("reason_code", request.getReasonCode());
        payload.put("remark", request.getRemark());
        payload.put("lines", lines);
        payload.put("current_status", request.getStatus());
        return payload;
    }

    private Map<String, Object> batchPayload(StockTransferRequestDO request, StockTransferOrderDO order,
                                             String batchId, String batchNo, String batchType, String remark,
                                             List<Map<String, Object>> lines, Stage stage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("request_id", request.getRequestId());
        payload.put("request_code", request.getRequestCode());
        payload.put("order_id", order.getOrderId());
        payload.put("order_code", order.getOrderCode());
        payload.put("batch_id", batchId);
        payload.put("batch_no", batchNo);
        payload.put("batch_type", batchType);
        payload.put("source_business_type", request.getSourceBusinessType());
        payload.put("source_business_ref", request.getSourceBusinessRef());
        payload.put("owner_type", request.getOwnerType());
        payload.put("owner_id", request.getOwnerId());
        payload.put("source_warehouse_id", request.getSourceWarehouseId());
        payload.put("target_warehouse_id", request.getTargetWarehouseId());
        payload.put("remark", remark);
        payload.put("current_status", order.getStatus());
        payload.put("current_stage_code", stage.code());
        payload.put("current_stage_label", stage.label());
        payload.put("lines", lines);
        return payload;
    }

    private void insertHistory(Long tenantId, Long operationId, String businessObjectType, String businessObjectId,
                               String status, Long statusVersion, String stageCode, String stageLabel,
                               String remark, LocalDateTime now) {
        require(mapper.insertStatusHistory(new StockTransferStatusHistoryDO()
                        .setHistoryId(UUID.randomUUID().toString()).setTenantId(tenantId)
                        .setOperationId(operationId).setBusinessObjectType(businessObjectType)
                        .setBusinessObjectId(businessObjectId).setStatus(status).setStatusVersion(statusVersion)
                        .setStageCode(stageCode).setStageLabel(stageLabel).setRemark(remark)
                        .setChangedAt(now).setCreatedAt(now)) == 1,
                "failed to persist stock transfer status history");
    }

    static String fingerprint(Long tenantId, StockTransferCommand command) {
        return DigestUtil.sha256Hex(tenantId + "\n" + JsonUtils.toJsonString(command));
    }

    static Stage stage(List<StockTransferOrderLineDO> lines, String requestStatus) {
        Totals totals = totals(lines);
        if ("CANCELED".equals(requestStatus)) {
            return new Stage(COMPLETE_STAGE_CODE, "调拨单已取消");
        }
        if (totals.requested().compareTo(ZERO) > 0 && totals.received().compareTo(totals.requested()) == 0) {
            return new Stage(COMPLETE_STAGE_CODE, COMPLETE_STAGE_LABEL);
        }
        if (totals.received().compareTo(ZERO) > 0 || totals.outbound().compareTo(totals.requested()) == 0) {
            return new Stage(INBOUND_STAGE_CODE, INBOUND_STAGE_LABEL);
        }
        return new Stage(OUTBOUND_STAGE_CODE, OUTBOUND_STAGE_LABEL);
    }

    static Stage lineStage(String lineStatus) {
        return switch (lineStatus) {
            case "FULL_RECEIVED" -> new Stage(COMPLETE_STAGE_CODE, "行已收齐");
            case "PARTIAL_RECEIVED", "FULL_OUTBOUND" -> new Stage(INBOUND_STAGE_CODE, INBOUND_STAGE_LABEL);
            case "PARTIAL_OUTBOUND", "PREPARE" -> new Stage(OUTBOUND_STAGE_CODE, OUTBOUND_STAGE_LABEL);
            default -> new Stage("MANUAL_RECONCILIATION", "行状态需要人工复核");
        };
    }

    static String orderStatus(List<StockTransferOrderLineDO> lines) {
        Totals totals = totals(lines);
        if (totals.requested().compareTo(ZERO) > 0 && totals.received().compareTo(totals.requested()) == 0) {
            return "COMPLETED";
        }
        if (totals.outbound().compareTo(ZERO) == 0) {
            return "PREPARE";
        }
        if (totals.outbound().compareTo(totals.requested()) < 0) {
            return "RELEASED";
        }
        return "IN_TRANSIT";
    }

    static String lineStatus(BigDecimal outboundQuantity, BigDecimal receivedQuantity, BigDecimal requestedQuantity) {
        if (receivedQuantity.compareTo(requestedQuantity) == 0) {
            return "FULL_RECEIVED";
        }
        if (receivedQuantity.compareTo(ZERO) > 0) {
            return "PARTIAL_RECEIVED";
        }
        if (outboundQuantity.compareTo(requestedQuantity) == 0) {
            return "FULL_OUTBOUND";
        }
        if (outboundQuantity.compareTo(ZERO) > 0) {
            return "PARTIAL_OUTBOUND";
        }
        return "PREPARE";
    }

    private static Totals totals(List<StockTransferOrderLineDO> lines) {
        BigDecimal requested = ZERO;
        BigDecimal outbound = ZERO;
        BigDecimal received = ZERO;
        for (StockTransferOrderLineDO line : lines) {
            requested = requested.add(scaled(line.getRequestedQuantity()));
            outbound = outbound.add(scaled(line.getOutboundQuantity()));
            received = received.add(scaled(line.getReceivedQuantity()));
        }
        return new Totals(requested, outbound, received);
    }

    private static String traceId(StockTransferCommand command) {
        return command.getCorrelationId() == null || command.getCorrelationId().isBlank()
                ? command.getIdempotencyKey() : command.getCorrelationId();
    }

    private static String valueOrUuid(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    private static String valueOrCode(String value, String prefix) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private static String eventId(String sourceEventId, String phase, String executionLineId) {
        if (sourceEventId != null && !sourceEventId.isBlank()) {
            return sourceEventId + ":" + phase + ":" + executionLineId;
        }
        return "stock-transfer:" + phase + ":" + executionLineId;
    }

    private static BigDecimal normalizeQuantity(BigDecimal value) {
        require(value != null && value.signum() > 0 && value.scale() <= 6 && value.precision() <= 24,
                "quantity must be a positive DECIMAL(24,6)");
        return value.setScale(6);
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value == null ? ZERO : value.setScale(6);
    }

    private static Integer requirePositive(Integer value, String field) {
        require(value != null && value > 0, field + " must be positive");
        return value;
    }

    private static <T> T requireNonNull(T value, String message) {
        require(value != null, message);
        return value;
    }

    private static void validate(StockTransferCommand command) {
        require(command != null, "stock transfer command is required");
        require(command.getOperation() != null, "operation is required");
        requireRef(command.getIdempotencyKey(), "idempotencyKey", 128);
        require(command.getOccurredAt() != null, "occurredAt is required");
        switch (command.getOperation()) {
            case CREATE_APPROVED_REQUEST -> validateCreate(command);
            case COMPLETE_OUTBOUND_BATCH -> validateOutboundBatch(command);
            case COMPLETE_RECEIPT_BATCH -> validateReceiptBatch(command);
        }
    }

    private static void validateCreate(StockTransferCommand command) {
        requireRef(command.getSourceBusinessType(), "sourceBusinessType", 64);
        requireRef(command.getSourceBusinessRef(), "sourceBusinessRef", 128);
        requireRef(command.getOwnerType(), "ownerType", 64);
        requireRef(command.getOwnerId(), "ownerId", 128);
        requireRef(command.getSourceWarehouseId(), "sourceWarehouseId", 128);
        requireRef(command.getTargetWarehouseId(), "targetWarehouseId", 128);
        require(!command.getSourceWarehouseId().equals(command.getTargetWarehouseId()),
                "sourceWarehouseId and targetWarehouseId must differ");
        if (command.getRequestId() != null && !command.getRequestId().isBlank()) {
            requireRef(command.getRequestId(), "requestId", 128);
        }
        if (command.getOrderId() != null && !command.getOrderId().isBlank()) {
            requireRef(command.getOrderId(), "orderId", 128);
        }
        if (command.getRequestCode() != null && !command.getRequestCode().isBlank()) {
            require(SAFE_CODE.matcher(command.getRequestCode()).matches(),
                    "requestCode must be an uppercase code");
        }
        if (command.getOrderCode() != null && !command.getOrderCode().isBlank()) {
            require(SAFE_CODE.matcher(command.getOrderCode()).matches(),
                    "orderCode must be an uppercase code");
        }
        require(command.getReasonCode() != null && SAFE_CODE.matcher(command.getReasonCode()).matches(),
                "reasonCode must be an uppercase code");
        require(command.getLines() != null && !command.getLines().isEmpty() && command.getLines().size() <= 200,
                "stock transfer request must contain between 1 and 200 lines");
        Set<String> lineIds = new LinkedHashSet<>();
        Set<Integer> lineNumbers = new LinkedHashSet<>();
        int generatedLineNumber = 10;
        for (StockTransferCommand.LineDefinition line : command.getLines()) {
            if (line.getLineId() != null && !line.getLineId().isBlank()) {
                requireRef(line.getLineId(), "lineId", 128);
                require(lineIds.add(line.getLineId()), "duplicate stock transfer lineId");
            }
            Integer lineNumber = line.getLineNumber() == null ? generatedLineNumber : line.getLineNumber();
            require(lineNumber > 0 && lineNumbers.add(lineNumber),
                    "lineNumber must be positive and unique");
            requireRef(line.getCanonicalSkuId(), "canonicalSkuId", 128);
            normalizeQuantity(line.getRequestedQuantity());
            require(line.getUomCode() != null && SAFE_CODE.matcher(line.getUomCode()).matches(),
                    "uomCode must be an uppercase code");
            if (line.getRemark() != null) {
                require(line.getRemark().length() <= 255, "line remark must be 255 characters or less");
            }
            generatedLineNumber += 10;
        }
        if (command.getRemark() != null) {
            require(command.getRemark().length() <= 255, "remark must be 255 characters or less");
        }
    }

    private static void validateOutboundBatch(StockTransferCommand command) {
        requireRef(command.getOrderId(), "orderId", 128);
        StockTransferCommand.OutboundBatchDefinition batch = requireNonNull(command.getOutboundBatch(),
                "outboundBatch is required");
        if (batch.getBatchId() != null && !batch.getBatchId().isBlank()) {
            requireRef(batch.getBatchId(), "outboundBatch.batchId", 128);
        }
        if (batch.getBatchNo() != null && !batch.getBatchNo().isBlank()) {
            require(SAFE_CODE.matcher(batch.getBatchNo()).matches(),
                    "outboundBatch.batchNo must be an uppercase code");
        }
        require(batch.getLines() != null && !batch.getLines().isEmpty() && batch.getLines().size() <= 200,
                "outboundBatch must contain between 1 and 200 lines");
        Set<Integer> lineNumbers = new LinkedHashSet<>();
        Set<String> executionLineIds = new LinkedHashSet<>();
        for (StockTransferCommand.OutboundLineDefinition line : batch.getLines()) {
            require(lineNumbers.add(requirePositive(line.getLineNumber(), "lineNumber")),
                    "duplicate outbound batch lineNumber");
            if (line.getExecutionLineId() != null && !line.getExecutionLineId().isBlank()) {
                requireRef(line.getExecutionLineId(), "executionLineId", 128);
                require(executionLineIds.add(line.getExecutionLineId()), "duplicate outbound executionLineId");
            }
            normalizeQuantity(line.getQuantity());
            requireRef(line.getSourceLocationId(), "sourceLocationId", 128);
            requireRef(line.getTargetLocationId(), "targetLocationId", 128);
            require(line.getSourceStockStatus() != null && SAFE_CODE.matcher(line.getSourceStockStatus()).matches(),
                    "sourceStockStatus must be an uppercase code");
            require(line.getSourceQualityStatus() != null && SAFE_CODE.matcher(line.getSourceQualityStatus()).matches(),
                    "sourceQualityStatus must be an uppercase code");
            require(line.getTargetStockStatus() != null && SAFE_CODE.matcher(line.getTargetStockStatus()).matches(),
                    "targetStockStatus must be an uppercase code");
            require(line.getTargetQualityStatus() != null && SAFE_CODE.matcher(line.getTargetQualityStatus()).matches(),
                    "targetQualityStatus must be an uppercase code");
            require(line.getSourceStockStatus().equals(line.getTargetStockStatus()),
                    "sourceStockStatus and targetStockStatus must match inventory transfer contract");
            require(line.getSourceQualityStatus().equals(line.getTargetQualityStatus()),
                    "sourceQualityStatus and targetQualityStatus must match inventory transfer contract");
            if (line.getLotId() != null && !line.getLotId().isBlank()) {
                requireRef(line.getLotId(), "lotId", 128);
            }
        }
    }

    private static void validateReceiptBatch(StockTransferCommand command) {
        requireRef(command.getOrderId(), "orderId", 128);
        StockTransferCommand.ReceiptBatchDefinition batch = requireNonNull(command.getReceiptBatch(),
                "receiptBatch is required");
        if (batch.getBatchId() != null && !batch.getBatchId().isBlank()) {
            requireRef(batch.getBatchId(), "receiptBatch.batchId", 128);
        }
        if (batch.getBatchNo() != null && !batch.getBatchNo().isBlank()) {
            require(SAFE_CODE.matcher(batch.getBatchNo()).matches(),
                    "receiptBatch.batchNo must be an uppercase code");
        }
        require(batch.getLines() != null && !batch.getLines().isEmpty() && batch.getLines().size() <= 200,
                "receiptBatch must contain between 1 and 200 lines");
        Set<String> outboundIds = new LinkedHashSet<>();
        Set<String> executionIds = new LinkedHashSet<>();
        for (StockTransferCommand.ReceiptLineDefinition line : batch.getLines()) {
            requireRef(line.getOutboundExecutionLineId(), "outboundExecutionLineId", 128);
            require(outboundIds.add(line.getOutboundExecutionLineId()),
                    "duplicate outboundExecutionLineId in receipt batch");
            if (line.getExecutionLineId() != null && !line.getExecutionLineId().isBlank()) {
                requireRef(line.getExecutionLineId(), "executionLineId", 128);
                require(executionIds.add(line.getExecutionLineId()), "duplicate receipt executionLineId");
            }
            normalizeQuantity(line.getQuantity());
        }
    }

    private static String requireRef(String value, String field, int maxLength) {
        require(value != null && value.length() <= maxLength && SAFE_REF.matcher(value).matches(),
                field + " must be a safe opaque reference");
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    record Stage(String code, String label) {
    }

    private record Totals(BigDecimal requested, BigDecimal outbound, BigDecimal received) {
    }

    private record OrderProgress(String requestStatus, String orderStatus, Long orderVersion, Stage stage) {
    }
}
