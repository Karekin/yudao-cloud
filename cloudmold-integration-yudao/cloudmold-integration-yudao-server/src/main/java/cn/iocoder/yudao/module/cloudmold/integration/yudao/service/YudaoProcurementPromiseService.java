package cn.iocoder.yudao.module.cloudmold.integration.yudao.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoProcurementPromiseApi;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.dal.YudaoPurchasePromiseMapper;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.dal.YudaoPurchasePromiseRow;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.procurement.LegacyProcurementReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class YudaoProcurementPromiseService implements YudaoProcurementPromiseApi {

    private static final String OPERATION_TYPE = "UPSERT_PURCHASE_PROMISE";
    private static final String SOURCE_SYSTEM = "cloudmold-integration-yudao";
    private static final Set<String> ALLOWED_STATUS = Set.of("ACTIVE", "CANCELLED");

    private final LegacyProcurementReadPort procurementReadPort;
    private final YudaoPurchasePromiseMapper promiseMapper;
    private final YudaoCommandOperationService operationService;
    private final OutboxAppender outboxAppender;

    @Override
    public PurchasePromiseView savePurchasePromise(PurchasePromiseCommand command) {
        validate(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        operationService.executeBoolean(OPERATION_TYPE, command.idempotencyKey(), command, () -> {
            upsert(tenantId, command);
            return Boolean.TRUE;
        });
        YudaoPurchasePromiseRow row = promiseMapper.selectByLineId(tenantId, command.purchaseOrderLineId());
        require(row != null, "purchase promise does not exist");
        return toView(row);
    }

    @Override
    public PurchasePromiseView getPurchasePromise(Long purchaseOrderLineId) {
        require(purchaseOrderLineId != null && purchaseOrderLineId > 0, "purchaseOrderLineId must be positive");
        YudaoPurchasePromiseRow row = promiseMapper.selectByLineId(
                TenantContextHolder.getRequiredTenantId(), purchaseOrderLineId);
        require(row != null, "purchase promise does not exist");
        return toView(row);
    }

    @Override
    public List<PurchaseOrderLineView> listPurchaseOrderLines(Long purchaseOrderId) {
        require(purchaseOrderId != null && purchaseOrderId > 0, "purchaseOrderId must be positive");
        LegacyProcurementReadPort.PurchaseOrderSnapshot order = procurementReadPort.getPurchaseOrder(purchaseOrderId);
        return order.lines().stream()
                .map(line -> new PurchaseOrderLineView(
                        line.purchaseOrderLineId(), line.purchaseOrderId(), line.productId(), line.productUnitId(),
                        line.orderedQuantity(), line.receivedQuantity(), line.returnedQuantity()))
                .toList();
    }

    private void upsert(Long tenantId, PurchasePromiseCommand command) {
        LegacyProcurementReadPort.PurchaseOrderSnapshot order = procurementReadPort.getPurchaseOrder(command.purchaseOrderId());
        LegacyProcurementReadPort.PurchaseOrderLineSnapshot line = order.lines().stream()
                .filter(item -> Objects.equals(item.purchaseOrderLineId(), command.purchaseOrderLineId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("purchase order line does not belong to purchase order"));

        OffsetDateTime promisedAt = OffsetDateTime.parse(command.promisedReceiptAt(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String timezone = validateTimeZone(command.promiseTimezone());
        int graceMinutes = normalizeNonNegative(command.graceMinutes(), "graceMinutes");
        int pauseMinutes = normalizeNonNegative(command.pauseMinutes(), "pauseMinutes");
        String status = normalizeStatus(command.status());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        YudaoPurchasePromiseRow existing = promiseMapper.selectForUpdateByLineId(tenantId, command.purchaseOrderLineId());
        Long nextVersion = existing == null ? 1L : existing.getVersion() + 1L;
        String promiseId = existing == null ? UUID.randomUUID().toString() : existing.getPromiseId();
        String promiseKey = "ERP_PO_LINE:" + command.purchaseOrderId() + ":" + command.purchaseOrderLineId();
        String previousStatus = existing == null ? null : existing.getStatus();
        YudaoPurchasePromiseRow row = new YudaoPurchasePromiseRow()
                .setPromiseId(promiseId)
                .setPromiseKey(promiseKey)
                .setTenantId(tenantId)
                .setPurchaseOrderId(order.purchaseOrderId())
                .setPurchaseOrderNo(order.purchaseOrderNo())
                .setPurchaseOrderLineId(line.purchaseOrderLineId())
                .setSupplierId(order.supplierId())
                .setProductId(line.productId())
                .setProductUnitId(line.productUnitId())
                .setOrderedQuantity(line.orderedQuantity())
                .setPromisedReceiptAt(LocalDateTime.ofInstant(promisedAt.toInstant(), ZoneOffset.UTC))
                .setPromiseTimezone(timezone)
                .setGraceMinutes(graceMinutes)
                .setPauseMinutes(pauseMinutes)
                .setPromiseFrozenAt(now)
                .setStatus(status)
                .setVersion(nextVersion)
                .setRunId(command.runId())
                .setReason(trimToNull(command.reason()))
                .setCreatedAt(existing == null ? now : existing.getCreatedAt())
                .setUpdatedAt(now);

        if (existing == null) {
            require(promiseMapper.insert(row) == 1, "failed to insert purchase promise");
        } else {
            require(promiseMapper.update(row, existing.getVersion()) == 1, "purchase promise version conflict");
        }
        appendEvent(tenantId, row, previousStatus, command.idempotencyKey());
    }

    private void appendEvent(Long tenantId, YudaoPurchasePromiseRow row, String previousStatus, String idempotencyKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", row.getRunId());
        payload.put("promise_id", row.getPromiseId());
        payload.put("promise_key", row.getPromiseKey());
        payload.put("purchase_order_id", row.getPurchaseOrderId());
        payload.put("purchase_order_no", row.getPurchaseOrderNo());
        payload.put("purchase_order_line_id", row.getPurchaseOrderLineId());
        payload.put("supplier_id", row.getSupplierId());
        payload.put("product_id", row.getProductId());
        payload.put("product_unit_id", row.getProductUnitId());
        payload.put("ordered_quantity", row.getOrderedQuantity().toPlainString());
        payload.put("promised_receipt_at", row.getPromisedReceiptAt().toInstant(ZoneOffset.UTC).toString());
        payload.put("promise_timezone", row.getPromiseTimezone());
        payload.put("grace_minutes", row.getGraceMinutes());
        payload.put("pause_minutes", row.getPauseMinutes());
        payload.put("promise_frozen_at", row.getPromiseFrozenAt().toInstant(ZoneOffset.UTC).toString());
        payload.put("previous_status", previousStatus);
        payload.put("current_status", row.getStatus());
        payload.put("reason", row.getReason());
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType("procurement.purchase_promise.status_changed")
                .schemaVersion(1)
                .sourceSystem(SOURCE_SYSTEM)
                .tenantId(tenantId)
                .aggregateType("procurement_purchase_promise")
                .aggregateId(row.getPromiseId())
                .aggregateVersion(row.getVersion())
                .eventSequence((short) 1)
                .occurredAt(row.getUpdatedAt().toInstant(ZoneOffset.UTC))
                .correlationId(UUID.randomUUID().toString())
                .causationId(null)
                .idempotencyKey(idempotencyKey)
                .payload(payload)
                .headers(Map.of("operation", OPERATION_TYPE))
                .destination("lakehouse")
                .build());
    }

    private static PurchasePromiseView toView(YudaoPurchasePromiseRow row) {
        return new PurchasePromiseView(
                row.getPromiseId(),
                row.getPromiseKey(),
                row.getPurchaseOrderId(),
                row.getPurchaseOrderNo(),
                row.getPurchaseOrderLineId(),
                row.getSupplierId(),
                row.getProductId(),
                row.getProductUnitId(),
                row.getOrderedQuantity(),
                row.getPromisedReceiptAt().toInstant(ZoneOffset.UTC).toString(),
                row.getPromiseTimezone(),
                row.getGraceMinutes(),
                row.getPauseMinutes(),
                row.getPromiseFrozenAt().toInstant(ZoneOffset.UTC).toString(),
                row.getStatus(),
                row.getVersion(),
                row.getRunId(),
                row.getReason());
    }

    private static void validate(PurchasePromiseCommand command) {
        require(command != null, "command is required");
        requireText(command.idempotencyKey(), "idempotencyKey", 128);
        requireText(command.runId(), "runId", 64);
        require(command.purchaseOrderId() != null && command.purchaseOrderId() > 0, "purchaseOrderId must be positive");
        require(command.purchaseOrderLineId() != null && command.purchaseOrderLineId() > 0,
                "purchaseOrderLineId must be positive");
        try {
            OffsetDateTime.parse(command.promisedReceiptAt(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("promisedReceiptAt must be an ISO-8601 offset datetime", ex);
        }
        validateTimeZone(command.promiseTimezone());
        normalizeNonNegative(command.graceMinutes(), "graceMinutes");
        normalizeNonNegative(command.pauseMinutes(), "pauseMinutes");
        normalizeStatus(command.status());
        if (trimToNull(command.reason()) != null && command.reason().length() > 255) {
            throw new IllegalArgumentException("reason must contain 0..255 characters");
        }
    }

    private static String validateTimeZone(String value) {
        requireText(value, "promiseTimezone", 64);
        try {
            ZoneId.of(value);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("promiseTimezone must be a valid IANA timezone", ex);
        }
        return value;
    }

    private static int normalizeNonNegative(Integer value, String field) {
        int normalized = value == null ? 0 : value;
        if (normalized < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
        return normalized;
    }

    private static String normalizeStatus(String status) {
        String normalized = status == null ? "ACTIVE" : status.trim().toUpperCase();
        require(ALLOWED_STATUS.contains(normalized), "status must be ACTIVE or CANCELLED");
        return normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank() && value.length() <= maxLength,
                field + " must contain 1.." + maxLength + " characters");
    }
}
