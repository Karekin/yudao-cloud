package cn.iocoder.yudao.module.cloudmold.procurement.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.procurement.api.*;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.ProcurementOrder;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql.ProcurementMapper;
import cn.iocoder.yudao.module.cloudmold.procurement.service.actor.ProcurementActorPrincipalPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProcurementServiceImpl implements ProcurementCommandApi {
    private static final int OPERATION_SUCCEEDED = 10;
    private static final String SOURCE_SYSTEM = "cloudmold-procurement";
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> PROJECTION_SYSTEMS = Set.of("YUDAO_ERP", "YUDAO_WMS");
    private static final Set<String> PROJECTION_DOCUMENT_TYPES = Set.of("PURCHASE_ORDER", "MOVEMENT_ORDER");

    private final ProcurementMapper mapper;
    private final OutboxAppender outboxAppender;
    private final ProcurementActorPrincipalPort actorPrincipalPort;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcurementResult execute(ProcurementCommand command, String actorPrincipalId) {
        validateEnvelope(command);
        requireRef(actorPrincipalId, "actorPrincipalId", 128);
        actorPrincipalPort.requireActive(actorPrincipalId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(actorPrincipalId + "\n" + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve procurement operation");
        Operation operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "procurement operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with different procurement payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing procurement operation is incomplete");
            ProcurementResult replay = JsonUtils.parseObject(operation.getResultJson(), ProcurementResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        Outcome outcome = switch (command.getOperation()) {
            case CREATE_PURCHASE_ORDER -> createOrder(tenantId, command, actorPrincipalId, now);
            case DISPATCH_PURCHASE_ORDER -> dispatchOrder(tenantId, command, actorPrincipalId, now);
            case SUPPLIER_CONFIRM_PURCHASE_ORDER -> confirmOrder(tenantId, command, actorPrincipalId, now);
            case CANCEL_PURCHASE_ORDER -> cancelOrder(tenantId, command, actorPrincipalId, now);
            case CLOSE_PURCHASE_ORDER -> closeOrder(tenantId, command, actorPrincipalId, now);
        };
        appendEvent(tenantId, command, outcome);
        ProcurementResult result = ProcurementResult.builder()
                .operationId(operationId)
                .duplicate(false)
                .aggregateType(outcome.aggregateType())
                .aggregateId(outcome.aggregateId())
                .aggregateVersion(outcome.version())
                .status(outcome.status())
                .orderCode(stringValue(outcome.payload().get("order_code")))
                .sourceBusinessRef(stringValue(outcome.payload().get("source_business_ref")))
                .projectionSourceSystem(stringValue(outcome.payload().get("projection_source_system")))
                .projectionDocumentType(stringValue(outcome.payload().get("projection_document_type")))
                .projectionExternalDocumentId(stringValue(outcome.payload().get("projection_external_document_id")))
                .projectionExternalDocumentNo(stringValue(outcome.payload().get("projection_external_document_no")))
                .projectionDocumentStatus(stringValue(outcome.payload().get("projection_document_status")))
                .build();
        require(mapper.markOperationSucceeded(operationId, tenantId, outcome.aggregateType(),
                outcome.aggregateId(), JsonUtils.toJsonString(result), now) == 1,
                "procurement operation completion conflict");
        return result;
    }

    private Outcome createOrder(Long tenantId, ProcurementCommand command, String actorPrincipalId, LocalDateTime now) {
        ProcurementCommand.PurchaseOrderDefinition input = nonNull(command.getPurchaseOrder(), "purchaseOrder is required");
        String orderId = valueOrUuid(input.getOrderId());
        requireRef(input.getOrderCode(), "orderCode", 64);
        requireCode(input.getSourceBusinessType(), "sourceBusinessType");
        requireRef(input.getSourceBusinessRef(), "sourceBusinessRef", 128);
        requireRef(input.getSupplierRef(), "supplierRef", 128);
        requireRef(input.getCanonicalSkuId(), "canonicalSkuId", 128);
        requireRef(input.getCanonicalWarehouseId(), "canonicalWarehouseId", 128);
        requirePositive(input.getOrderedQuantity(), "orderedQuantity");
        requireCode(input.getUomCode(), "uomCode");
        require(input.getUnitCostMinor() != null && input.getUnitCostMinor() >= 0, "unitCostMinor must not be negative");
        require(input.getTotalAmountMinor() != null && input.getTotalAmountMinor() >= 0, "totalAmountMinor must not be negative");
        requireCurrency(input.getCurrencyCode());
        require(input.getLeadTimeDays() != null && input.getLeadTimeDays() >= 0, "leadTimeDays must not be negative");
        require(input.getRequiredDeliveryDate() != null, "requiredDeliveryDate is required");
        ProcurementOrder row = new ProcurementOrder()
                .setOrderId(orderId)
                .setTenantId(tenantId)
                .setOrderCode(input.getOrderCode())
                .setSourceBusinessType(upper(input.getSourceBusinessType()))
                .setSourceBusinessRef(input.getSourceBusinessRef())
                .setSupplierRef(input.getSupplierRef())
                .setCanonicalSkuId(input.getCanonicalSkuId())
                .setCanonicalWarehouseId(input.getCanonicalWarehouseId())
                .setOrderedQuantity(input.getOrderedQuantity())
                .setUomCode(upper(input.getUomCode()))
                .setUnitCostMinor(input.getUnitCostMinor())
                .setTotalAmountMinor(input.getTotalAmountMinor())
                .setCurrencyCode(upper(input.getCurrencyCode()))
                .setLeadTimeDays(input.getLeadTimeDays())
                .setRequiredDeliveryDate(input.getRequiredDeliveryDate())
                .setStatus("CREATED")
                .setCreatedByPrincipalId(actorPrincipalId)
                .setReasonCode(normalizeReasonCode(input.getReasonCode()))
                .setRemark(input.getRemark())
                .setVersion(1L)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        copyProjection(row, input.getProjection());
        require(mapper.insertOrder(row) == 1, "failed to persist procurement order");
        return outcome("procurement.order.created", orderId, 1L, "CREATED", row);
    }

    private Outcome dispatchOrder(Long tenantId, ProcurementCommand command, String actorPrincipalId, LocalDateTime now) {
        ProcurementCommand.PurchaseOrderDefinition input = nonNull(command.getPurchaseOrder(), "purchaseOrder is required");
        requireRef(input.getOrderId(), "orderId", 128);
        ProcurementOrder row = nonNull(mapper.selectOrderForUpdate(tenantId, input.getOrderId()), "procurement order not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("CREATED".equals(row.getStatus()), "only a created procurement order can be dispatched");
        String reasonCode = normalizeReasonCode(input.getReasonCode());
        require(mapper.dispatchOrder(tenantId, row.getOrderId(), row.getVersion(), actorPrincipalId, reasonCode, now) == 1,
                "procurement order dispatch conflict");
        row.setStatus("DISPATCHED").setVersion(row.getVersion() + 1).setDispatchedByPrincipalId(actorPrincipalId)
                .setDispatchedAt(now).setReasonCode(reasonCode);
        return outcome("procurement.order.dispatched", row.getOrderId(), row.getVersion(), row.getStatus(), row);
    }

    private Outcome confirmOrder(Long tenantId, ProcurementCommand command, String actorPrincipalId, LocalDateTime now) {
        ProcurementCommand.PurchaseOrderDefinition input = nonNull(command.getPurchaseOrder(), "purchaseOrder is required");
        requireRef(input.getOrderId(), "orderId", 128);
        ProcurementOrder row = nonNull(mapper.selectOrderForUpdate(tenantId, input.getOrderId()), "procurement order not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("DISPATCHED".equals(row.getStatus()), "only a dispatched procurement order can be supplier-confirmed");
        String reasonCode = normalizeReasonCode(input.getReasonCode());
        require(mapper.confirmSupplier(tenantId, row.getOrderId(), row.getVersion(), actorPrincipalId, reasonCode, now) == 1,
                "procurement order supplier confirmation conflict");
        row.setStatus("SUPPLIER_CONFIRMED").setVersion(row.getVersion() + 1)
                .setSupplierConfirmedByPrincipalId(actorPrincipalId).setSupplierConfirmedAt(now)
                .setReasonCode(reasonCode);
        return outcome("procurement.order.supplier_confirmed", row.getOrderId(), row.getVersion(), row.getStatus(), row);
    }

    private Outcome cancelOrder(Long tenantId, ProcurementCommand command, String actorPrincipalId, LocalDateTime now) {
        ProcurementCommand.PurchaseOrderDefinition input = nonNull(command.getPurchaseOrder(), "purchaseOrder is required");
        requireRef(input.getOrderId(), "orderId", 128);
        ProcurementOrder row = nonNull(mapper.selectOrderForUpdate(tenantId, input.getOrderId()), "procurement order not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require(!"CANCELLED".equals(row.getStatus()) && !"CLOSED".equals(row.getStatus()),
                "completed procurement order cannot be cancelled");
        String reasonCode = normalizeReasonCode(input.getReasonCode());
        require(mapper.cancelOrder(tenantId, row.getOrderId(), row.getVersion(), actorPrincipalId, reasonCode, now) == 1,
                "procurement order cancellation conflict");
        row.setStatus("CANCELLED").setVersion(row.getVersion() + 1).setCancelledByPrincipalId(actorPrincipalId)
                .setCancelledAt(now).setReasonCode(reasonCode);
        return outcome("procurement.order.cancelled", row.getOrderId(), row.getVersion(), row.getStatus(), row);
    }

    private Outcome closeOrder(Long tenantId, ProcurementCommand command, String actorPrincipalId, LocalDateTime now) {
        ProcurementCommand.PurchaseOrderDefinition input = nonNull(command.getPurchaseOrder(), "purchaseOrder is required");
        requireRef(input.getOrderId(), "orderId", 128);
        ProcurementOrder row = nonNull(mapper.selectOrderForUpdate(tenantId, input.getOrderId()), "procurement order not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("SUPPLIER_CONFIRMED".equals(row.getStatus()), "only a supplier-confirmed procurement order can be closed");
        String reasonCode = normalizeReasonCode(input.getReasonCode());
        require(mapper.closeOrder(tenantId, row.getOrderId(), row.getVersion(), actorPrincipalId, reasonCode, now) == 1,
                "procurement order close conflict");
        row.setStatus("CLOSED").setVersion(row.getVersion() + 1).setClosedByPrincipalId(actorPrincipalId)
                .setClosedAt(now).setReasonCode(reasonCode);
        return outcome("procurement.order.closed", row.getOrderId(), row.getVersion(), row.getStatus(), row);
    }

    private void copyProjection(ProcurementOrder row, ProcurementCommand.ProjectionDefinition projection) {
        if (projection == null) {
            return;
        }
        require(PROJECTION_SYSTEMS.contains(upper(projection.getSourceSystem())), "unsupported projection sourceSystem");
        require(PROJECTION_DOCUMENT_TYPES.contains(upper(projection.getDocumentType())), "unsupported projection documentType");
        requireRef(projection.getExternalDocumentId(), "projection externalDocumentId", 128);
        require("PREPARE".equals(upper(projection.getDocumentStatus())), "ERP/WMS projection must stay at PREPARE");
        requireSha256(projection.getEvidenceSha256(), "projection evidenceSha256");
        row.setProjectionSourceSystem(upper(projection.getSourceSystem()))
                .setProjectionDocumentType(upper(projection.getDocumentType()))
                .setProjectionExternalDocumentId(projection.getExternalDocumentId())
                .setProjectionExternalDocumentNo(projection.getExternalDocumentNo())
                .setProjectionDocumentStatus("PREPARE")
                .setProjectionEvidenceSha256(projection.getEvidenceSha256());
    }

    private Outcome outcome(String eventType, String orderId, Long version, String status, ProcurementOrder row) {
        return new Outcome(eventType, "procurement_order", orderId, version, status, payload(
                "order_id", row.getOrderId(),
                "order_code", row.getOrderCode(),
                "source_business_type", row.getSourceBusinessType(),
                "source_business_ref", row.getSourceBusinessRef(),
                "supplier_ref", row.getSupplierRef(),
                "canonical_sku_id", row.getCanonicalSkuId(),
                "canonical_warehouse_id", row.getCanonicalWarehouseId(),
                "ordered_quantity", row.getOrderedQuantity(),
                "uom_code", row.getUomCode(),
                "unit_cost_minor", row.getUnitCostMinor(),
                "total_amount_minor", row.getTotalAmountMinor(),
                "currency_code", row.getCurrencyCode(),
                "lead_time_days", row.getLeadTimeDays(),
                "required_delivery_date", row.getRequiredDeliveryDate().toString(),
                "projection_source_system", row.getProjectionSourceSystem(),
                "projection_document_type", row.getProjectionDocumentType(),
                "projection_external_document_id", row.getProjectionExternalDocumentId(),
                "projection_external_document_no", row.getProjectionExternalDocumentNo(),
                "projection_document_status", row.getProjectionDocumentStatus(),
                "current_status", status));
    }

    private void appendEvent(Long tenantId, ProcurementCommand command, Outcome outcome) {
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(outcome.eventType())
                .schemaVersion(1)
                .sourceSystem(SOURCE_SYSTEM)
                .tenantId(tenantId)
                .aggregateType(outcome.aggregateType())
                .aggregateId(outcome.aggregateId())
                .aggregateVersion(outcome.version())
                .eventSequence((short) 1)
                .occurredAt(command.getOccurredAt())
                .traceId(command.getRunId())
                .correlationId(command.getCorrelationId())
                .causationId(command.getCausationId())
                .idempotencyKey(command.getIdempotencyKey() + ":" + outcome.version())
                .payload(outcome.payload())
                .headers(Map.of("run_id", command.getRunId(), "status", outcome.status()))
                .destination("lakehouse")
                .build());
    }

    private static void validateEnvelope(ProcurementCommand command) {
        require(command != null, "procurement command is required");
        require(command.getOperation() != null, "operation is required");
        requireUuid(command.getCorrelationId(), "correlationId");
        require(command.getOccurredAt() != null, "occurredAt is required");
        requireRef(command.getIdempotencyKey(), "idempotencyKey", 128);
        if (command.getRunId() != null) {
            requireRef(command.getRunId(), "runId", 128);
        }
        if (command.getCausationId() != null) {
            requireRef(command.getCausationId(), "causationId", 128);
        }
    }

    private static String valueOrUuid(String value) {
        if (value == null) {
            return UUID.randomUUID().toString();
        }
        requireRef(value, "aggregate id", 128);
        return value;
    }

    private static String normalizeReasonCode(String value) {
        return value == null ? null : upper(value);
    }

    private static void requireExpectedVersion(Long expected, Long actual) {
        require(expected != null && expected.equals(actual), "aggregate version conflict");
    }

    private static void requireCurrency(String value) {
        require(value != null && upper(value).matches("[A-Z]{3}"), "currencyCode must be ISO-4217");
    }

    private static void requireUuid(String value, String field) {
        try {
            UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " must be a UUID", exception);
        }
    }

    private static void requireRef(String value, String field, int maxLength) {
        require(value != null && value.length() <= maxLength && SAFE_REF.matcher(value).matches(),
                field + " must be a safe opaque reference");
    }

    private static void requireCode(String value, String field) {
        String normalized = upper(value);
        require(normalized != null && SAFE_CODE.matcher(normalized).matches(),
                field + " must be an uppercase code");
    }

    private static void requireSha256(String value, String field) {
        require(value != null && SHA256.matcher(value).matches(),
                field + " must be a lowercase SHA-256");
    }

    private static void requirePositive(BigDecimal value, String field) {
        require(value != null && value.compareTo(BigDecimal.ZERO) > 0, field + " must be positive");
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static <T> T nonNull(T value, String message) {
        require(value != null, message);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static Map<String, Object> payload(Object... values) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            payload.put((String) values[i], values[i + 1]);
        }
        return payload;
    }

    private record Outcome(String eventType, String aggregateType, String aggregateId,
                           Long version, String status, Map<String, Object> payload) {
    }
}
