package cn.iocoder.yudao.module.cloudmold.fulfillment.service.returning;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.*;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.returning.*;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.returning.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReturnFulfillmentCommandServiceImpl implements ReturnFulfillmentCommandApi, ReturnFulfillmentQueryApi {
    private static final int OPERATION_SUCCEEDED = 10;

    private final ReturnFulfillmentOperationMapper operationMapper;
    private final ReturnFulfillmentMapper fulfillmentMapper;
    private final ReturnFulfillmentItemMapper itemMapper;
    private final ReturnFulfillmentHistoryMapper historyMapper;
    private final ReturnShipmentMapper shipmentMapper;
    private final ReturnShipmentItemMapper shipmentItemMapper;
    private final ReturnTrackingEventMapper trackingMapper;
    private final ReturnInspectionMapper inspectionMapper;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnFulfillmentView execute(ReturnFulfillmentCommand command) {
        validateCommon(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String hash = DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(tenantId, command)));
        String token = UUID.randomUUID().toString();
        operationMapper.insertOrResolve(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                hash, token, now);
        Long operationId = operationMapper.lastInsertId();
        require(operationId != null, "failed to resolve return fulfillment operation");
        ReturnFulfillmentOperationDO operation = operationMapper.selectForUpdate(tenantId, operationId);
        require(operation != null, "return fulfillment operation disappeared");
        if (!token.equals(operation.getAttemptToken())) {
            require(Objects.equals(hash, operation.getRequestHash()),
                    "idempotency key conflicts with different return fulfillment payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing return fulfillment operation is incomplete");
            ReturnFulfillmentView replay = JsonUtils.parseObject(operation.getResultJson(), ReturnFulfillmentView.class);
            replay.setDuplicate(true);
            return replay;
        }
        ReturnFulfillmentView result = command.getOperation() == ReturnFulfillmentOperation.CREATE
                ? create(tenantId, operationId, command, now) : transition(tenantId, operationId, command, now);
        require(operationMapper.markSucceeded(tenantId, operationId, result.getReturnFulfillmentId(),
                JsonUtils.toJsonString(result), now) == 1, "return fulfillment operation completion conflict");
        return result;
    }

    @Override
    public ReturnFulfillmentView getForAfterSale(String afterSaleId, String returnFulfillmentId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ReturnFulfillmentDO fulfillment = fulfillmentMapper.selectTenant(tenantId, returnFulfillmentId);
        require(fulfillment != null && Objects.equals(afterSaleId, fulfillment.getAfterSaleId()),
                "return Fulfillment does not belong to after sale");
        return view(null, fulfillment, shipmentMapper.selectByFulfillment(tenantId, returnFulfillmentId),
                requireSingleItem(tenantId, returnFulfillmentId),
                inspectionMapper.selectByFulfillment(tenantId, returnFulfillmentId), false);
    }

    @Override
    public ReturnFulfillmentView requireInspectionAccepted(String afterSaleId, String returnFulfillmentId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ReturnFulfillmentDO fulfillment = fulfillmentMapper.selectTenant(tenantId, returnFulfillmentId);
        require(fulfillment != null && Objects.equals(afterSaleId, fulfillment.getAfterSaleId()),
                "return Fulfillment does not belong to after sale");
        require("INSPECTION_ACCEPTED".equals(fulfillment.getStatus()),
                "return Fulfillment inspection has not been accepted");
        ReturnInspectionDO inspection = inspectionMapper.selectByFulfillment(tenantId, returnFulfillmentId);
        ReturnFulfillmentItemDO item = requireSingleItem(tenantId, returnFulfillmentId);
        require(inspection != null && "QUALIFIED".equals(inspection.getQualityStatus())
                        && inspection.getAcceptedQuantity().compareTo(item.getQuantity()) == 0,
                "qualified return inspection is incomplete");
        return view(null, fulfillment, shipmentMapper.selectByFulfillment(tenantId, returnFulfillmentId),
                item, inspection, false);
    }

    private ReturnFulfillmentView create(Long tenantId, Long operationId, ReturnFulfillmentCommand command,
                                         LocalDateTime now) {
        require(command.getReturnFulfillmentId() == null && command.getExpectedVersion() == null,
                "CREATE does not accept aggregate identity or version");
        requireText(command.getAfterSaleId(), "afterSaleId", 36);
        requireText(command.getAfterSaleItemId(), "afterSaleItemId", 36);
        requireText(command.getOrderId(), "orderId", 36);
        requireText(command.getOrderItemId(), "orderItemId", 36);
        requireText(command.getCanonicalSkuId(), "canonicalSkuId", 128);
        require(command.getQuantity() != null && command.getQuantity().signum() > 0
                        && command.getQuantity().stripTrailingZeros().scale() <= 0,
                "first slice requires positive whole-piece quantity");
        requireText(command.getOwnerId(), "ownerId", 128);
        requireText(command.getWarehouseId(), "warehouseId", 128);
        require("PCS".equals(command.getUomCode()), "first slice supports PCS only");
        require(fulfillmentMapper.selectByAfterSale(tenantId, command.getAfterSaleId()) == null,
                "after sale already owns a return Fulfillment");
        String id = UUID.randomUUID().toString();
        ReturnFulfillmentDO fulfillment = new ReturnFulfillmentDO().setReturnFulfillmentId(id)
                .setTenantId(tenantId).setReturnFulfillmentNo("CMR" + compact(id)).setRunId(command.getRunId())
                .setAfterSaleId(command.getAfterSaleId()).setOrderId(command.getOrderId())
                .setOwnerId(command.getOwnerId()).setWarehouseId(command.getWarehouseId())
                .setUomCode(command.getUomCode()).setStatus("CREATED").setVersion(1L)
                .setCorrelationId(command.getCorrelationId()).setCausationId(command.getCausationId())
                .setCreatedAt(now).setUpdatedAt(now);
        fulfillmentMapper.insert(fulfillment);
        ReturnFulfillmentItemDO item = new ReturnFulfillmentItemDO()
                .setReturnFulfillmentItemId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setReturnFulfillmentId(id).setAfterSaleItemId(command.getAfterSaleItemId())
                .setOrderItemId(command.getOrderItemId()).setCanonicalSkuId(command.getCanonicalSkuId())
                .setQuantity(command.getQuantity()).setCreatedAt(now);
        itemMapper.insert(item);
        appendHistoryAndEvent(operationId, fulfillment, null, command.getOccurredAt(), now);
        return view(operationId, fulfillment, null, item, null, false);
    }

    private ReturnFulfillmentView transition(Long tenantId, Long operationId, ReturnFulfillmentCommand command,
                                             LocalDateTime now) {
        requireText(command.getReturnFulfillmentId(), "returnFulfillmentId", 36);
        require(command.getExpectedVersion() != null && command.getExpectedVersion() > 0,
                "expectedVersion is required");
        ReturnFulfillmentDO fulfillment = fulfillmentMapper.selectForUpdate(tenantId,
                command.getReturnFulfillmentId());
        require(fulfillment != null, "return Fulfillment does not exist");
        require(Objects.equals(command.getExpectedVersion(), fulfillment.getVersion()),
                "return Fulfillment version conflict");
        String previous = fulfillment.getStatus();
        String next = next(command.getOperation(), previous);
        ReturnFulfillmentItemDO item = requireSingleItem(tenantId, fulfillment.getReturnFulfillmentId());
        LocalDateTime occurredAt = LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC);
        ReturnShipmentDO shipment = shipmentMapper.selectByFulfillment(tenantId, fulfillment.getReturnFulfillmentId());
        ReturnInspectionDO inspection = inspectionMapper.selectByFulfillment(tenantId,
                fulfillment.getReturnFulfillmentId());
        if (command.getOperation() == ReturnFulfillmentOperation.HAND_OVER) {
            require(shipment == null, "return shipment already exists");
            requireText(command.getCarrierCode(), "carrierCode", 64);
            requireText(command.getWaybillNo(), "waybillNo", 128);
            require(shipmentMapper.selectByWaybill(tenantId, command.getCarrierCode(), command.getWaybillNo()) == null,
                    "return waybill already belongs to another shipment");
            shipment = new ReturnShipmentDO().setReturnShipmentId(UUID.randomUUID().toString())
                    .setTenantId(tenantId).setReturnFulfillmentId(fulfillment.getReturnFulfillmentId())
                    .setCarrierCode(command.getCarrierCode()).setWaybillNo(command.getWaybillNo())
                    .setStatus("HANDED_OVER").setHandedOverAt(occurredAt).setCreatedAt(now).setUpdatedAt(now);
            shipmentMapper.insert(shipment);
            shipmentItemMapper.insert(new ReturnShipmentItemDO()
                    .setReturnShipmentItemId(UUID.randomUUID().toString()).setTenantId(tenantId)
                    .setReturnShipmentId(shipment.getReturnShipmentId())
                    .setReturnFulfillmentItemId(item.getReturnFulfillmentItemId())
                    .setQuantity(item.getQuantity()).setCreatedAt(now));
            appendTracking(fulfillment, shipment, "HANDED_OVER", null, occurredAt, now);
        } else {
            require(shipment != null, "return shipment does not exist");
            if (command.getOperation() == ReturnFulfillmentOperation.MARK_IN_TRANSIT) {
                require(shipmentMapper.transition(tenantId, shipment.getReturnShipmentId(), "HANDED_OVER",
                        "IN_TRANSIT", occurredAt, null, null, now) == 1, "return shipment transition conflict");
                shipment.setStatus("IN_TRANSIT").setInTransitAt(occurredAt).setUpdatedAt(now);
                appendTracking(fulfillment, shipment, "IN_TRANSIT", null, occurredAt, now);
            } else if (command.getOperation() == ReturnFulfillmentOperation.RECEIVE) {
                requireText(command.getOperatorId(), "receiverId", 128);
                require(shipmentMapper.transition(tenantId, shipment.getReturnShipmentId(), "IN_TRANSIT",
                        "RECEIVED", null, occurredAt, command.getOperatorId(), now) == 1,
                        "return shipment transition conflict");
                shipment.setStatus("RECEIVED").setReceivedAt(occurredAt)
                        .setReceiverId(command.getOperatorId()).setUpdatedAt(now);
                appendTracking(fulfillment, shipment, "RECEIVED", command.getOperatorId(), occurredAt, now);
            } else if (command.getOperation() == ReturnFulfillmentOperation.ACCEPT_INSPECTION) {
                require(inspection == null, "return inspection is immutable");
                require("QUALIFIED".equals(command.getQualityStatus()),
                        "first slice accepts QUALIFIED inspection only");
                requireText(command.getOperatorId(), "inspectorId", 128);
                inspection = new ReturnInspectionDO().setInspectionId(UUID.randomUUID().toString())
                        .setTenantId(tenantId).setReturnFulfillmentId(fulfillment.getReturnFulfillmentId())
                        .setReturnShipmentId(shipment.getReturnShipmentId())
                        .setReturnFulfillmentItemId(item.getReturnFulfillmentItemId())
                        .setReturnShipmentItemId(requireSingleShipmentItem(tenantId, shipment.getReturnShipmentId())
                                .getReturnShipmentItemId())
                        .setWarehouseId(fulfillment.getWarehouseId())
                        .setReceivedQuantity(item.getQuantity()).setAcceptedQuantity(item.getQuantity())
                        .setQualityStatus("QUALIFIED").setInspectorId(command.getOperatorId())
                        .setDecidedAt(occurredAt).setCreatedAt(now);
                inspectionMapper.insert(inspection);
            }
        }
        require(fulfillmentMapper.transition(tenantId, fulfillment.getReturnFulfillmentId(),
                fulfillment.getVersion(), previous, next, now) == 1, "return Fulfillment transition conflict");
        fulfillment.setStatus(next).setVersion(fulfillment.getVersion() + 1).setUpdatedAt(now);
        appendHistoryAndEvent(operationId, fulfillment, previous, command.getOccurredAt(), now);
        if (inspection != null && command.getOperation() == ReturnFulfillmentOperation.ACCEPT_INSPECTION) {
            appendInspectionEvent(fulfillment, shipment, item, inspection, command.getOccurredAt());
        }
        return view(operationId, fulfillment, shipment, item, inspection, false);
    }

    private void appendTracking(ReturnFulfillmentDO fulfillment, ReturnShipmentDO shipment,
                                String type, String operatorId, LocalDateTime occurredAt, LocalDateTime now) {
        trackingMapper.insert(new ReturnTrackingEventDO().setTenantId(fulfillment.getTenantId())
                .setReturnFulfillmentId(fulfillment.getReturnFulfillmentId())
                .setReturnShipmentId(shipment.getReturnShipmentId()).setEventType(type)
                .setOperatorId(operatorId)
                .setOccurredAt(occurredAt).setCreatedAt(now));
    }

    private void appendHistoryAndEvent(Long operationId, ReturnFulfillmentDO fulfillment, String previous,
                                       java.time.Instant occurredAt, LocalDateTime now) {
        historyMapper.insert(new ReturnFulfillmentHistoryDO().setTenantId(fulfillment.getTenantId())
                .setReturnFulfillmentId(fulfillment.getReturnFulfillmentId())
                .setAggregateVersion(fulfillment.getVersion()).setPreviousStatus(previous)
                .setCurrentStatus(fulfillment.getStatus()).setOperationId(operationId)
                .setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)).setCreatedAt(now));
        ReturnShipmentDO shipment = shipmentMapper.selectByFulfillment(fulfillment.getTenantId(),
                fulfillment.getReturnFulfillmentId());
        ReturnInspectionDO inspection = inspectionMapper.selectByFulfillment(fulfillment.getTenantId(),
                fulfillment.getReturnFulfillmentId());
        ReturnFulfillmentItemDO item = requireSingleItem(fulfillment.getTenantId(),
                fulfillment.getReturnFulfillmentId());
        Map<String, Object> payload = payload(fulfillment, shipment, item, inspection);
        payload.put("previous_status", previous);
        payload.put("current_status", fulfillment.getStatus());
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType("return_fulfillment.status.changed").schemaVersion(1)
                .sourceSystem("cloudmold-fulfillment").tenantId(fulfillment.getTenantId())
                .aggregateType("return_fulfillment").aggregateId(fulfillment.getReturnFulfillmentId())
                .aggregateVersion(fulfillment.getVersion()).eventSequence((short) 1).occurredAt(occurredAt)
                .correlationId(fulfillment.getCorrelationId()).causationId(fulfillment.getCausationId())
                .idempotencyKey("return-fulfillment:" + fulfillment.getReturnFulfillmentId()
                        + ":event:" + fulfillment.getVersion())
                .payload(payload).headers(Map.of("status", fulfillment.getStatus()))
                .destination("lakehouse").build());
    }

    private void appendInspectionEvent(ReturnFulfillmentDO fulfillment, ReturnShipmentDO shipment,
                                       ReturnFulfillmentItemDO item,
                                       ReturnInspectionDO inspection, java.time.Instant occurredAt) {
        Map<String, Object> payload = payload(fulfillment, shipment, item, inspection);
        payload.put("decision", "ACCEPTED");
        payload.put("accepted_quantity", inspection.getAcceptedQuantity().toPlainString());
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType("return_fulfillment.inspection.decided").schemaVersion(1)
                .sourceSystem("cloudmold-fulfillment").tenantId(fulfillment.getTenantId())
                .aggregateType("return_fulfillment_inspection").aggregateId(inspection.getInspectionId())
                .aggregateVersion(1L).eventSequence((short) 1).occurredAt(occurredAt)
                .correlationId(fulfillment.getCorrelationId()).causationId(fulfillment.getCausationId())
                .idempotencyKey("return-inspection:" + inspection.getInspectionId() + ":event:1")
                .payload(payload).headers(Map.of("quality_status", inspection.getQualityStatus()))
                .destination("lakehouse").build());
    }

    private static Map<String, Object> payload(ReturnFulfillmentDO f, ReturnShipmentDO s,
                                               ReturnFulfillmentItemDO item, ReturnInspectionDO i) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", f.getRunId());
        payload.put("return_fulfillment_id", f.getReturnFulfillmentId());
        payload.put("return_fulfillment_no", f.getReturnFulfillmentNo());
        payload.put("after_sale_id", f.getAfterSaleId());
        payload.put("return_fulfillment_item_id", item.getReturnFulfillmentItemId());
        payload.put("after_sale_item_id", item.getAfterSaleItemId());
        payload.put("order_id", f.getOrderId());
        payload.put("order_item_id", item.getOrderItemId());
        payload.put("canonical_sku_id", item.getCanonicalSkuId());
        payload.put("quantity", item.getQuantity().toPlainString());
        payload.put("owner_id", f.getOwnerId());
        payload.put("warehouse_id", f.getWarehouseId());
        payload.put("uom_code", f.getUomCode());
        payload.put("return_shipping_amount_minor", 0L);
        payload.put("currency_code", "CNY");
        payload.put("return_shipment_id", s == null ? null : s.getReturnShipmentId());
        payload.put("carrier_code", s == null ? null : s.getCarrierCode());
        payload.put("waybill_no", s == null ? null : s.getWaybillNo());
        payload.put("handed_over_at", instant(s == null ? null : s.getHandedOverAt()));
        payload.put("in_transit_at", instant(s == null ? null : s.getInTransitAt()));
        payload.put("received_at", instant(s == null ? null : s.getReceivedAt()));
        payload.put("receiver_id", s == null ? null : s.getReceiverId());
        payload.put("inspection_id", i == null ? null : i.getInspectionId());
        payload.put("quality_status", i == null ? null : i.getQualityStatus());
        payload.put("received_quantity", i == null ? null : i.getReceivedQuantity().toPlainString());
        payload.put("accepted_quantity", i == null ? null : i.getAcceptedQuantity().toPlainString());
        payload.put("rejected_quantity", i == null ? null : "0");
        payload.put("inspection_result", i == null ? null : "ACCEPTED");
        payload.put("inspector_id", i == null ? null : i.getInspectorId());
        payload.put("inspected_at", instant(i == null ? null : i.getDecidedAt()));
        return payload;
    }

    private static String instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC).toString();
    }

    private ReturnFulfillmentView view(Long operationId, ReturnFulfillmentDO f, ReturnShipmentDO s,
                                       ReturnFulfillmentItemDO item, ReturnInspectionDO i, boolean duplicate) {
        return ReturnFulfillmentView.builder().operationId(operationId)
                .returnFulfillmentId(f.getReturnFulfillmentId()).returnFulfillmentNo(f.getReturnFulfillmentNo())
                .returnShipmentId(s == null ? null : s.getReturnShipmentId())
                .inspectionId(i == null ? null : i.getInspectionId()).afterSaleId(f.getAfterSaleId())
                .afterSaleItemId(item.getAfterSaleItemId()).orderId(f.getOrderId()).orderItemId(item.getOrderItemId())
                .canonicalSkuId(item.getCanonicalSkuId()).quantity(item.getQuantity()).ownerId(f.getOwnerId())
                .warehouseId(f.getWarehouseId()).uomCode(f.getUomCode())
                .carrierCode(s == null ? null : s.getCarrierCode()).waybillNo(s == null ? null : s.getWaybillNo())
                .qualityStatus(i == null ? null : i.getQualityStatus()).currentStatus(f.getStatus())
                .aggregateVersion(f.getVersion()).duplicate(duplicate).build();
    }

    private ReturnFulfillmentItemDO requireSingleItem(Long tenantId, String returnFulfillmentId) {
        List<ReturnFulfillmentItemDO> items = itemMapper.selectByFulfillment(tenantId, returnFulfillmentId);
        require(items.size() == 1, "first slice requires exactly one return Fulfillment item");
        return items.get(0);
    }

    private ReturnShipmentItemDO requireSingleShipmentItem(Long tenantId, String returnShipmentId) {
        List<ReturnShipmentItemDO> items = shipmentItemMapper.selectByShipment(tenantId, returnShipmentId);
        require(items.size() == 1, "first slice requires exactly one return Shipment item");
        return items.get(0);
    }

    private static String next(ReturnFulfillmentOperation operation, String status) {
        return switch (operation) {
            case HAND_OVER -> requireTransition(status, "CREATED", "HANDED_OVER");
            case MARK_IN_TRANSIT -> requireTransition(status, "HANDED_OVER", "IN_TRANSIT");
            case RECEIVE -> requireTransition(status, "IN_TRANSIT", "RECEIVED");
            case ACCEPT_INSPECTION -> requireTransition(status, "RECEIVED", "INSPECTION_ACCEPTED");
            case CREATE -> throw new IllegalArgumentException("CREATE is not a transition");
        };
    }

    private static String requireTransition(String actual, String expected, String next) {
        require(expected.equals(actual), next + " requires " + expected);
        return next;
    }

    private static void validateCommon(ReturnFulfillmentCommand command) {
        require(command != null && command.getOperation() != null, "return fulfillment operation is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        require(command.getIdempotencyKey().length() >= 8, "idempotencyKey is too short");
        requireText(command.getRunId(), "runId", 64);
        requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) requireUuid(command.getCausationId(), "causationId");
        require(command.getOccurredAt() != null, "occurredAt is required");
    }

    private static String compact(String uuid) {
        return uuid.replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
    }

    private static void requireUuid(String value, String field) {
        requireText(value, field, 36);
        try { UUID.fromString(value); } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " must be a UUID", e);
        }
    }

    private static void requireText(String value, String field, int max) {
        require(value != null && !value.isBlank() && value.length() <= max, field + " is required");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
