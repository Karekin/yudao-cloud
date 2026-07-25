package cn.iocoder.yudao.module.cloudmold.fulfillment.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.*;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.*;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryReservationQueryApi;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderFulfillmentView;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderLineView;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FulfillmentCommandServiceImpl implements FulfillmentCommandApi {

    static final int OPERATION_SUCCEEDED = 10;

    private final FulfillmentOperationMapper operationMapper;
    private final FulfillmentOrderMapper fulfillmentMapper;
    private final FulfillmentItemMapper itemMapper;
    private final ShipmentMapper shipmentMapper;
    private final ShipmentItemMapper shipmentItemMapper;
    private final TrackingEventMapper trackingEventMapper;
    private final FulfillmentStatusHistoryMapper historyMapper;
    private final OrderQueryApi orderQueryApi;
    private final InventoryReservationQueryApi inventoryReservationQueryApi;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FulfillmentCommandResult execute(FulfillmentCommand command) {
        validateCommon(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (command.getOperation() == FulfillmentOperation.CREATE) {
            validateCreate(command);
        } else {
            requireText(command.getFulfillmentId(), "fulfillmentId", 36);
            require(command.getExpectedVersion() != null && command.getExpectedVersion() > 0,
                    "expectedVersion must be positive");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = fingerprint(tenantId, command);
        String attemptToken = UUID.randomUUID().toString();
        operationMapper.insertOrResolve(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                requestHash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve fulfillment operation");
        FulfillmentOperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "fulfillment operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(operation.getRequestHash(), requestHash),
                    "idempotency key conflicts with different fulfillment payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing fulfillment operation is not complete");
            FulfillmentCommandResult replay = JsonUtils.parseObject(operation.getResultJson(),
                    FulfillmentCommandResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        FulfillmentCommandResult result = command.getOperation() == FulfillmentOperation.CREATE
                ? create(tenantId, operationId, command, now)
                : transition(tenantId, operationId, command, now);
        require(operationMapper.markSucceeded(operationId, tenantId, result.getFulfillmentId(),
                JsonUtils.toJsonString(result), now) == 1, "fulfillment operation completion conflict");
        return result;
    }

    private FulfillmentCommandResult create(Long tenantId, Long operationId, FulfillmentCommand command,
                                            LocalDateTime now) {
        OrderFulfillmentView order = orderQueryApi.requireFulfillableOrder(command.getOrderId());
        validateOrderItems(command.getItems(), order.getItems());
        String fulfillmentId = UUID.randomUUID().toString();
        String fulfillmentNo = "CMF" + fulfillmentId.replace("-", "").substring(0, 20)
                .toUpperCase(Locale.ROOT);
        FulfillmentOrderDO fulfillment = new FulfillmentOrderDO().setFulfillmentId(fulfillmentId)
                .setTenantId(tenantId).setFulfillmentNo(fulfillmentNo).setRunId(command.getRunId())
                .setOrderId(order.getOrderId()).setOrderNo(order.getOrderNo()).setSellerId(command.getSellerId())
                .setWarehouseId(command.getWarehouseId())
                .setDeliveryPromiseVersionRef(command.getDeliveryPromiseVersionRef())
                .setPromisedDeliveryAt(command.getPromisedDeliveryAt() == null ? null
                        : LocalDateTime.ofInstant(command.getPromisedDeliveryAt(), ZoneOffset.UTC))
                .setPromiseFrozenAt(command.getPromisedDeliveryAt() == null ? null
                        : LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC))
                .setStatus("CREATED").setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        fulfillmentMapper.insert(fulfillment);
        List<FulfillmentItemDO> items = command.getItems().stream().map(item -> new FulfillmentItemDO()
                .setFulfillmentItemId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setFulfillmentId(fulfillmentId).setOrderItemId(item.getOrderItemId())
                .setCanonicalSkuId(item.getCanonicalSkuId()).setQuantity(item.getQuantity())
                .setReservationId(item.getReservationId())
                .setVariableFulfillmentCostMinor(item.getVariableFulfillmentCostMinor())
                .setCreatedAt(now).setUpdatedAt(now)).toList();
        items.forEach(itemMapper::insert);
        appendHistory(tenantId, operationId, fulfillmentId, 1L, null, "CREATED", command, now);
        appendEvent(tenantId, fulfillment, items, null, null, "CREATED", command);
        return result(operationId, fulfillment, items, null, null, false);
    }

    private FulfillmentCommandResult transition(Long tenantId, Long operationId, FulfillmentCommand command,
                                                LocalDateTime now) {
        FulfillmentOrderDO fulfillment = fulfillmentMapper.selectForUpdate(tenantId, command.getFulfillmentId());
        require(fulfillment != null, "canonical fulfillment does not exist");
        require(Objects.equals(fulfillment.getVersion(), command.getExpectedVersion()),
                "canonical fulfillment version conflict");
        Transition transition = transition(command.getOperation(), fulfillment.getStatus());
        List<FulfillmentItemDO> items = itemMapper.selectByFulfillment(tenantId, fulfillment.getFulfillmentId());
        require(!items.isEmpty(), "canonical fulfillment has no items");
        ShipmentDO shipment = shipmentMapper.selectByFulfillment(tenantId, fulfillment.getFulfillmentId());
        LocalDateTime occurredAt = LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC);

        if (command.getOperation() == FulfillmentOperation.REQUEST_CANCELLATION
                || command.getOperation() == FulfillmentOperation.FINALIZE_CANCELLATION) {
            requireText(command.getCancellationSagaId(), "cancellationSagaId", 36);
            require(shipment == null, "Fulfillment with shipment cannot be cancelled");
            String preCancellationStatus = command.getOperation() == FulfillmentOperation.REQUEST_CANCELLATION
                    ? fulfillment.getStatus() : null;
            if (command.getOperation() == FulfillmentOperation.FINALIZE_CANCELLATION) {
                require(Objects.equals(command.getCancellationSagaId(), fulfillment.getCancellationSagaId()),
                        "cancellation Saga does not own Fulfillment fence");
            }
            require(fulfillmentMapper.transitionCancellation(tenantId, fulfillment.getFulfillmentId(),
                    fulfillment.getVersion(), transition.expectedStatus(), transition.nextStatus(),
                    command.getCancellationSagaId(), preCancellationStatus, now) == 1,
                    "canonical Fulfillment cancellation transition conflict");
            String previous = fulfillment.getStatus();
            fulfillment.setStatus(transition.nextStatus()).setVersion(fulfillment.getVersion() + 1)
                    .setCancellationSagaId(command.getCancellationSagaId()).setUpdatedAt(now);
            if (preCancellationStatus != null) fulfillment.setPreCancellationStatus(preCancellationStatus);
            appendHistory(tenantId, operationId, fulfillment.getFulfillmentId(), fulfillment.getVersion(), previous,
                    transition.nextStatus(), command, now);
            appendEvent(tenantId, fulfillment, items, null, previous, transition.nextStatus(), command);
            return result(operationId, fulfillment, items, null, previous, false);
        }

        if (command.getOperation() == FulfillmentOperation.SHIP) {
            require(shipment == null, "canonical fulfillment already has a shipment");
            items.forEach(item -> inventoryReservationQueryApi.requireCommitted(item.getReservationId(),
                    fulfillment.getOrderId(), item.getOrderItemId()));
            requireText(command.getCarrierCode(), "carrierCode", 32);
            requireText(command.getWaybillNo(), "waybillNo", 64);
            shipment = new ShipmentDO().setShipmentId(UUID.randomUUID().toString()).setTenantId(tenantId)
                    .setFulfillmentId(fulfillment.getFulfillmentId()).setCarrierCode(command.getCarrierCode())
                    .setWaybillNo(command.getWaybillNo()).setStatus("SHIPPED").setShippedAt(occurredAt)
                    .setCreatedAt(now).setUpdatedAt(now);
            shipmentMapper.insert(shipment);
            ShipmentDO finalShipment = shipment;
            items.forEach(item -> shipmentItemMapper.insert(new ShipmentItemDO()
                    .setShipmentItemId(UUID.randomUUID().toString()).setTenantId(tenantId)
                    .setShipmentId(finalShipment.getShipmentId()).setFulfillmentItemId(item.getFulfillmentItemId())
                    .setQuantity(item.getQuantity()).setCreatedAt(now)));
            insertTracking(tenantId, shipment, command, "SHIPPED", occurredAt, now);
        } else {
            require(shipment != null, "canonical fulfillment shipment is missing");
            require(command.getCarrierCode() == null || command.getCarrierCode().equals(shipment.getCarrierCode()),
                    "carrierCode is immutable after shipment");
            require(command.getWaybillNo() == null || command.getWaybillNo().equals(shipment.getWaybillNo()),
                    "waybillNo is immutable after shipment");
            String expectedShipmentStatus = command.getOperation() == FulfillmentOperation.MARK_IN_TRANSIT
                    ? "SHIPPED" : "IN_TRANSIT";
            require(shipmentMapper.transition(tenantId, shipment.getShipmentId(), expectedShipmentStatus,
                    transition.nextStatus(), command.getOperation() == FulfillmentOperation.MARK_IN_TRANSIT
                            ? occurredAt : null,
                    command.getOperation() == FulfillmentOperation.DELIVER ? occurredAt : null, now) == 1,
                    "canonical shipment transition conflict");
            shipment.setStatus(transition.nextStatus()).setUpdatedAt(now);
            if (command.getOperation() == FulfillmentOperation.MARK_IN_TRANSIT) shipment.setInTransitAt(occurredAt);
            if (command.getOperation() == FulfillmentOperation.DELIVER) shipment.setDeliveredAt(occurredAt);
            insertTracking(tenantId, shipment, command, transition.nextStatus(), occurredAt, now);
        }

        require(fulfillmentMapper.transition(tenantId, fulfillment.getFulfillmentId(), fulfillment.getVersion(),
                transition.expectedStatus(), transition.nextStatus(), now) == 1,
                "canonical fulfillment transition conflict");
        String previous = fulfillment.getStatus();
        fulfillment.setStatus(transition.nextStatus()).setVersion(fulfillment.getVersion() + 1).setUpdatedAt(now);
        appendHistory(tenantId, operationId, fulfillment.getFulfillmentId(), fulfillment.getVersion(), previous,
                transition.nextStatus(), command, now);
        appendEvent(tenantId, fulfillment, items, shipment, previous, transition.nextStatus(), command);
        return result(operationId, fulfillment, items, shipment, previous, false);
    }

    private void insertTracking(Long tenantId, ShipmentDO shipment, FulfillmentCommand command,
                                String status, LocalDateTime occurredAt, LocalDateTime now) {
        String providerKey = command.getIdempotencyKey() + ":" + status;
        trackingEventMapper.insert(new TrackingEventDO().setTrackingEventId(UUID.randomUUID().toString())
                .setTenantId(tenantId).setShipmentId(shipment.getShipmentId()).setProviderEventKey(providerKey)
                .setTrackingStatus(status).setContent(command.getReason()).setOccurredAt(occurredAt)
                .setReceivedAt(now).setCreatedAt(now));
    }

    private void appendHistory(Long tenantId, Long operationId, String fulfillmentId, Long version,
                               String previous, String current, FulfillmentCommand command, LocalDateTime now) {
        historyMapper.insert(new FulfillmentStatusHistoryDO().setTenantId(tenantId)
                .setFulfillmentId(fulfillmentId).setAggregateVersion(version).setPreviousStatus(previous)
                .setCurrentStatus(current).setOperationId(operationId).setReason(command.getReason())
                .setOccurredAt(LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC)).setCreatedAt(now));
    }

    private void appendEvent(Long tenantId, FulfillmentOrderDO fulfillment, List<FulfillmentItemDO> items,
                             ShipmentDO shipment, String previous, String current, FulfillmentCommand command) {
        List<Map<String, Object>> eventItems = items.stream().map(item -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("fulfillment_item_id", item.getFulfillmentItemId());
            value.put("order_item_id", item.getOrderItemId());
            value.put("canonical_sku_id", item.getCanonicalSkuId());
            value.put("quantity", item.getQuantity().toPlainString());
            value.put("reservation_id", item.getReservationId());
            value.put("variable_fulfillment_cost_minor", item.getVariableFulfillmentCostMinor());
            return value;
        }).toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", command.getRunId());
        payload.put("fulfillment_id", fulfillment.getFulfillmentId());
        payload.put("fulfillment_no", fulfillment.getFulfillmentNo());
        payload.put("order_id", fulfillment.getOrderId());
        payload.put("order_no", fulfillment.getOrderNo());
        payload.put("seller_id", fulfillment.getSellerId());
        payload.put("warehouse_id", fulfillment.getWarehouseId());
        payload.put("delivery_promise_version_ref", fulfillment.getDeliveryPromiseVersionRef());
        payload.put("promised_delivery_at", fulfillment.getPromisedDeliveryAt() == null
                ? null : fulfillment.getPromisedDeliveryAt().toInstant(ZoneOffset.UTC).toString());
        payload.put("promise_frozen_at", fulfillment.getPromiseFrozenAt() == null
                ? null : fulfillment.getPromiseFrozenAt().toInstant(ZoneOffset.UTC).toString());
        payload.put("previous_status", previous);
        payload.put("current_status", current);
        payload.put("shipment_id", shipment == null ? null : shipment.getShipmentId());
        payload.put("carrier_code", shipment == null ? null : shipment.getCarrierCode());
        payload.put("waybill_no", shipment == null ? null : shipment.getWaybillNo());
        payload.put("shipped_at", shipment == null || shipment.getShippedAt() == null
                ? null : shipment.getShippedAt().toInstant(ZoneOffset.UTC).toString());
        payload.put("in_transit_at", shipment == null || shipment.getInTransitAt() == null
                ? null : shipment.getInTransitAt().toInstant(ZoneOffset.UTC).toString());
        payload.put("delivered_at", shipment == null || shipment.getDeliveredAt() == null
                ? null : shipment.getDeliveredAt().toInstant(ZoneOffset.UTC).toString());
        payload.put("reason", command.getReason());
        payload.put("items", eventItems);
        payload.put("cancellation_saga_id", command.getCancellationSagaId());
        payload.put("step_ordinal", command.getCancellationStepOrdinal());
        outboxAppender.append(AppendDomainEventCommand.builder().eventType("fulfillment.status.changed")
                .schemaVersion(resolveSchemaVersion(fulfillment, command))
                .sourceSystem("cloudmold-fulfillment").tenantId(tenantId)
                .aggregateType("fulfillment_order").aggregateId(fulfillment.getFulfillmentId())
                .aggregateVersion(fulfillment.getVersion()).eventSequence((short) 1)
                .occurredAt(command.getOccurredAt()).correlationId(command.getCorrelationId())
                .causationId(command.getCausationId()).idempotencyKey(command.getIdempotencyKey())
                .payload(payload).headers(Map.of("operation", command.getOperation().name()))
                .destination("lakehouse").build());
    }

    private static FulfillmentCommandResult result(Long operationId, FulfillmentOrderDO fulfillment,
                                                    List<FulfillmentItemDO> items, ShipmentDO shipment,
                                                    String previous, boolean duplicate) {
        return FulfillmentCommandResult.builder().operationId(operationId)
                .fulfillmentId(fulfillment.getFulfillmentId()).fulfillmentNo(fulfillment.getFulfillmentNo())
                .shipmentId(shipment == null ? null : shipment.getShipmentId()).orderId(fulfillment.getOrderId())
                .previousStatus(previous).currentStatus(fulfillment.getStatus())
                .cancellationSagaId(fulfillment.getCancellationSagaId())
                .aggregateVersion(fulfillment.getVersion()).sellerId(fulfillment.getSellerId())
                .warehouseId(fulfillment.getWarehouseId())
                .carrierCode(shipment == null ? null : shipment.getCarrierCode())
                .waybillNo(shipment == null ? null : shipment.getWaybillNo())
                .items(items.stream().map(FulfillmentCommandServiceImpl::lineView).toList())
                .duplicate(duplicate).build();
    }

    private static FulfillmentLineView lineView(FulfillmentItemDO item) {
        return FulfillmentLineView.builder().fulfillmentItemId(item.getFulfillmentItemId())
                .orderItemId(item.getOrderItemId()).canonicalSkuId(item.getCanonicalSkuId())
                .quantity(item.getQuantity()).reservationId(item.getReservationId()).build();
    }

    private static void validateOrderItems(List<FulfillmentLineCommand> requested, List<OrderLineView> orderItems) {
        require(requested.size() == orderItems.size(), "first slice requires the complete order in one fulfillment");
        Map<String, OrderLineView> byOrderItem = orderItems.stream()
                .collect(Collectors.toMap(OrderLineView::getOrderItemId, Function.identity()));
        for (FulfillmentLineCommand item : requested) {
            OrderLineView source = byOrderItem.remove(item.getOrderItemId());
            require(source != null, "fulfillment item does not belong to canonical order");
            require(Objects.equals(source.getCanonicalSkuId(), item.getCanonicalSkuId()),
                    "fulfillment SKU does not match canonical order item");
            require(source.getQuantity().compareTo(item.getQuantity()) == 0,
                    "first slice requires full order-item quantity fulfillment");
            require(Objects.equals(source.getReservationId(), item.getReservationId()),
                    "fulfillment reservation does not match canonical order item");
        }
        require(byOrderItem.isEmpty(), "canonical order has unassigned fulfillment items");
    }

    private static Transition transition(FulfillmentOperation operation, String status) {
        return switch (operation) {
            case SHIP -> requireTransition(status, "CREATED", "SHIPPED");
            case MARK_IN_TRANSIT -> requireTransition(status, "SHIPPED", "IN_TRANSIT");
            case DELIVER -> requireTransition(status, "IN_TRANSIT", "DELIVERED");
            case REQUEST_CANCELLATION -> requireTransition(status, "CREATED", "CANCELLATION_PENDING");
            case FINALIZE_CANCELLATION -> requireTransition(status, "CANCELLATION_PENDING", "CANCELLED");
            case CREATE -> throw new IllegalArgumentException("CREATE is not a transition");
        };
    }

    private static Transition requireTransition(String actual, String expected, String next) {
        require(expected.equals(actual), next + " requires " + expected);
        return new Transition(expected, next);
    }

    private static void validateCommon(FulfillmentCommand command) {
        require(command != null && command.getOperation() != null, "fulfillment operation is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        require(command.getIdempotencyKey().length() >= 8, "idempotencyKey is too short");
        requireText(command.getRunId(), "runId", 64);
        require(command.getOccurredAt() != null, "occurredAt is required");
        requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) requireUuid(command.getCausationId(), "causationId");
    }

    private static void validateCreate(FulfillmentCommand command) {
        require(command.getFulfillmentId() == null, "CREATE does not accept fulfillmentId");
        requireText(command.getOrderId(), "orderId", 36);
        requireText(command.getSellerId(), "sellerId", 128);
        requireText(command.getWarehouseId(), "warehouseId", 128);
        require((command.getDeliveryPromiseVersionRef() == null) == (command.getPromisedDeliveryAt() == null),
                "delivery promise version and promisedDeliveryAt must be provided together");
        if (command.getPromisedDeliveryAt() != null) {
            requireText(command.getDeliveryPromiseVersionRef(), "deliveryPromiseVersionRef", 64);
            require(command.getPromisedDeliveryAt().isAfter(command.getOccurredAt()),
                    "promisedDeliveryAt must be after occurredAt");
        }
        require(command.getItems() != null && !command.getItems().isEmpty() && command.getItems().size() <= 100,
                "CREATE requires 1 to 100 items");
        Set<String> orderItemIds = new HashSet<>();
        boolean anyVariableCost = command.getItems().stream()
                .anyMatch(item -> item != null && item.getVariableFulfillmentCostMinor() != null);
        for (FulfillmentLineCommand item : command.getItems()) {
            require(item != null, "fulfillment item is required");
            requireText(item.getOrderItemId(), "orderItemId", 36);
            require(orderItemIds.add(item.getOrderItemId()), "duplicate order item in fulfillment");
            requireText(item.getCanonicalSkuId(), "canonicalSkuId", 128);
            requireText(item.getReservationId(), "reservationId", 36);
            require(item.getQuantity() != null && item.getQuantity().signum() > 0
                            && item.getQuantity().stripTrailingZeros().scale() <= 0,
                    "first slice requires positive whole-piece quantity");
            if (anyVariableCost || item.getVariableFulfillmentCostMinor() != null) {
                require(item.getVariableFulfillmentCostMinor() != null
                                && item.getVariableFulfillmentCostMinor() >= 0,
                        "variableFulfillmentCostMinor must be nonnegative when profitability inputs are provided");
            }
        }
    }

    private static String fingerprint(Long tenantId, FulfillmentCommand command) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("tenant_id", tenantId); value.put("operation", command.getOperation());
        value.put("run_id", command.getRunId()); value.put("fulfillment_id", command.getFulfillmentId());
        value.put("expected_version", command.getExpectedVersion()); value.put("order_id", command.getOrderId());
        value.put("seller_id", command.getSellerId()); value.put("warehouse_id", command.getWarehouseId());
        value.put("delivery_promise_version_ref", command.getDeliveryPromiseVersionRef());
        value.put("promised_delivery_at", command.getPromisedDeliveryAt());
        value.put("items", command.getItems()); value.put("carrier_code", command.getCarrierCode());
        value.put("waybill_no", command.getWaybillNo()); value.put("reason", command.getReason());
        value.put("cancellation_saga_id", command.getCancellationSagaId());
        value.put("cancellation_step_ordinal", command.getCancellationStepOrdinal());
        value.put("occurred_at", command.getOccurredAt());
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(value));
    }

    private static void requireUuid(String value, String field) {
        requireText(value, field, 36);
        try { UUID.fromString(value); } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(field + " must be a UUID", error);
        }
    }

    private static void requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank() && value.length() <= maxLength, field + " is required");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private record Transition(String expectedStatus, String nextStatus) {}

    private static int resolveSchemaVersion(FulfillmentOrderDO fulfillment, FulfillmentCommand command) {
        if (command.getCancellationSagaId() != null) {
            return 2;
        }
        return fulfillment.getPromisedDeliveryAt() == null ? 1 : 3;
    }
}
