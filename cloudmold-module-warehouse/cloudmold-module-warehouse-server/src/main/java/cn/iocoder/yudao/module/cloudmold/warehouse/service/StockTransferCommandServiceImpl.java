package cn.iocoder.yudao.module.cloudmold.warehouse.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.*;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.StockTransferStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class StockTransferCommandServiceImpl implements StockTransferCommandApi {

    static final int OPERATION_SUCCEEDED = 10;
    static final String REQUEST_APPROVED_EVENT = "stock_transfer.request.approved";
    static final String ORDER_PREPARED_EVENT = "stock_transfer.order.prepared";
    static final String REQUEST_AGGREGATE_TYPE = "stock_transfer_request";
    static final String ORDER_AGGREGATE_TYPE = "stock_transfer_order";
    static final String REQUEST_STAGE_CODE = "REQUEST_APPROVED";
    static final String ORDER_STAGE_CODE = "TRANSFER_OUTBOUND";
    static final String ORDER_STAGE_LABEL = "等待调拨出库";
    static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    static final Pattern SAFE_CODE = Pattern.compile("[A-Z][A-Z0-9_-]{0,63}");

    private final StockTransferStoreMapper mapper;
    private final WarehouseReferenceValidationApi warehouseValidationApi;
    private final CatalogSkuValidationApi catalogSkuValidationApi;
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
        };
        result.setOperationId(operationId);
        require(mapper.markOperationSucceeded(operationId, tenantId, result.getRequestId(),
                        JsonUtils.toJsonString(result), now) == 1,
                "stock transfer operation completion conflict");
        return result;
    }

    private StockTransferResult createApprovedRequest(Long tenantId, Long operationId,
                                                      StockTransferCommand command, LocalDateTime now) {
        warehouseValidationApi.requireActiveWarehouse(command.getSourceWarehouseId());
        warehouseValidationApi.requireActiveWarehouse(command.getTargetWarehouseId());
        require(!command.getSourceWarehouseId().equals(command.getTargetWarehouseId()),
                "sourceWarehouseId and targetWarehouseId must differ");
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

        int sequence = 1;
        for (StockTransferCommand.LineDefinition line : command.getLines()) {
            catalogSkuValidationApi.requireActiveSku(line.getCanonicalSkuId());
            Integer lineNumber = line.getLineNumber() == null ? sequence * 10 : line.getLineNumber();
            BigDecimal normalizedQuantity = normalizeQuantity(line.getRequestedQuantity());
            String requestLineId = line.getLineId() == null || line.getLineId().isBlank()
                    ? requestId + ":line:" + lineNumber : line.getLineId();
            String orderLineId = orderId + ":line:" + lineNumber;
            require(mapper.insertRequestLine(new StockTransferRequestLineDO()
                            .setLineId(requestLineId).setTenantId(tenantId).setRequestId(requestId)
                            .setLineNumber(lineNumber).setCanonicalSkuId(line.getCanonicalSkuId())
                            .setRequestedQuantity(normalizedQuantity).setUomCode(line.getUomCode())
                            .setRemark(line.getRemark()).setCreatedAt(now).setUpdatedAt(now)) == 1,
                    "failed to persist stock transfer request line");
            require(mapper.insertOrderLine(new StockTransferOrderLineDO()
                            .setLineId(orderLineId).setTenantId(tenantId).setOrderId(orderId)
                            .setLineNumber(lineNumber).setCanonicalSkuId(line.getCanonicalSkuId())
                            .setRequestedQuantity(normalizedQuantity).setUomCode(line.getUomCode())
                            .setRemark(line.getRemark()).setCreatedAt(now).setUpdatedAt(now)) == 1,
                    "failed to persist stock transfer order line");
            sequence++;
        }

        require(mapper.insertStatusHistory(new StockTransferStatusHistoryDO()
                        .setHistoryId(UUID.randomUUID().toString()).setTenantId(tenantId).setOperationId(operationId)
                        .setBusinessObjectType("REQUEST").setBusinessObjectId(requestId)
                        .setStatus("APPROVED").setStatusVersion(1L)
                        .setStageCode(REQUEST_STAGE_CODE).setStageLabel("Transfer request approved")
                        .setChangedAt(now).setCreatedAt(now)) == 1,
                "failed to persist stock transfer request history");
        require(mapper.insertStatusHistory(new StockTransferStatusHistoryDO()
                        .setHistoryId(UUID.randomUUID().toString()).setTenantId(tenantId).setOperationId(operationId)
                        .setBusinessObjectType("ORDER").setBusinessObjectId(orderId)
                        .setStatus("PREPARE").setStatusVersion(1L)
                        .setStageCode(ORDER_STAGE_CODE).setStageLabel(ORDER_STAGE_LABEL)
                        .setChangedAt(now).setCreatedAt(now)) == 1,
                "failed to persist stock transfer order history");

        appendRequestEvent(tenantId, command, request);
        appendOrderEvent(tenantId, command, request, order);
        return StockTransferResult.builder()
                .requestId(requestId).requestCode(requestCode).requestStatus(request.getStatus())
                .orderId(orderId).orderCode(orderCode).orderStatus(order.getStatus())
                .currentStageCode(ORDER_STAGE_CODE).currentStageLabel(ORDER_STAGE_LABEL)
                .aggregateVersion(1L).duplicate(false).build();
    }

    private void appendRequestEvent(Long tenantId, StockTransferCommand command, StockTransferRequestDO request) {
        Map<String, Object> payload = headerPayload(request);
        payload.put("current_status", request.getStatus());
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(REQUEST_APPROVED_EVENT).schemaVersion(1)
                .sourceSystem("cloudmold-warehouse").tenantId(tenantId)
                .aggregateType(REQUEST_AGGREGATE_TYPE).aggregateId(request.getRequestId())
                .aggregateVersion(request.getVersion()).eventSequence((short) 1)
                .occurredAt(command.getOccurredAt()).traceId(traceId(command))
                .correlationId(command.getCorrelationId()).causationId(command.getCausationId())
                .idempotencyKey(command.getIdempotencyKey() + ":1").payload(payload)
                .headers(Map.of("status", request.getStatus()))
                .destination("lakehouse").build());
    }

    private void appendOrderEvent(Long tenantId, StockTransferCommand command,
                                  StockTransferRequestDO request, StockTransferOrderDO order) {
        Map<String, Object> payload = headerPayload(request);
        payload.put("order_id", order.getOrderId());
        payload.put("order_code", order.getOrderCode());
        payload.put("current_status", order.getStatus());
        payload.put("current_stage_code", ORDER_STAGE_CODE);
        payload.put("current_stage_label", ORDER_STAGE_LABEL);
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(ORDER_PREPARED_EVENT).schemaVersion(1)
                .sourceSystem("cloudmold-warehouse").tenantId(tenantId)
                .aggregateType(ORDER_AGGREGATE_TYPE).aggregateId(order.getOrderId())
                .aggregateVersion(order.getVersion()).eventSequence((short) 2)
                .occurredAt(command.getOccurredAt()).traceId(traceId(command))
                .correlationId(command.getCorrelationId()).causationId(command.getCausationId())
                .idempotencyKey(command.getIdempotencyKey() + ":2").payload(payload)
                .headers(Map.of("status", order.getStatus(), "stage_code", ORDER_STAGE_CODE))
                .destination("lakehouse").build());
    }

    private static Map<String, Object> headerPayload(StockTransferRequestDO request) {
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
        return payload;
    }

    static String fingerprint(Long tenantId, StockTransferCommand command) {
        return DigestUtil.sha256Hex(tenantId + "\n" + JsonUtils.toJsonString(command));
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

    private static BigDecimal normalizeQuantity(BigDecimal value) {
        require(value != null && value.signum() > 0 && value.scale() <= 6 && value.precision() <= 24,
                "requestedQuantity must be a positive DECIMAL(24,6)");
        return value.setScale(6);
    }

    private static void validate(StockTransferCommand command) {
        require(command != null, "stock transfer command is required");
        require(command.getOperation() == StockTransferOperation.CREATE_APPROVED_REQUEST,
                "unsupported stock transfer operation");
        requireRef(command.getIdempotencyKey(), "idempotencyKey", 128);
        require(command.getOccurredAt() != null, "occurredAt is required");
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

    private static void requireRef(String value, String field, int maxLength) {
        require(value != null && value.length() <= maxLength && SAFE_REF.matcher(value).matches(),
                field + " must be a safe opaque reference");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
