package cn.iocoder.yudao.module.cloudmold.order.service.cancellation;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryReservationQueryApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryReservationView;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.*;
import cn.iocoder.yudao.module.cloudmold.order.api.*;
import cn.iocoder.yudao.module.cloudmold.order.api.cancellation.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.OrderHeaderDO;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.OrderItemDO;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.cancellation.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.OrderHeaderMapper;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.OrderItemMapper;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.cancellation.*;
import cn.iocoder.yudao.module.cloudmold.payment.api.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderCancellationSagaCommandServiceImpl
        implements OrderCancellationSagaCommandApi, OrderCancellationSagaQueryApi {

    private static final int OPERATION_SUCCEEDED = 10;
    private static final int DEFAULT_MAX_ATTEMPTS = 8;

    private final OrderCancellationSagaOperationMapper operationMapper;
    private final OrderCancellationSagaMapper sagaMapper;
    private final OrderCancellationSagaItemMapper sagaItemMapper;
    private final OrderCancellationSagaFulfillmentMapper sagaFulfillmentMapper;
    private final OrderHeaderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderCommandApi orderCommandApi;
    private final InventoryReservationQueryApi reservationQueryApi;
    private final PaymentCancellationQueryApi paymentQueryApi;
    private final FulfillmentCancellationQueryApi fulfillmentQueryApi;
    private final FulfillmentCommandApi fulfillmentCommandApi;
    private final OrderCancellationSagaCheckpointService checkpointService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderCancellationSagaView execute(OrderCancellationSagaCommand command) {
        validateCommon(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = fingerprint(tenantId, command);
        String attemptToken = UUID.randomUUID().toString();
        operationMapper.insertOrResolve(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                requestHash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve cancellation Saga operation");
        OrderCancellationSagaOperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "cancellation Saga operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(operation.getRequestHash(), requestHash),
                    "idempotency key conflicts with different cancellation Saga payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing cancellation Saga operation is not complete");
            OrderCancellationSagaView replay = JsonUtils.parseObject(operation.getResultJson(),
                    OrderCancellationSagaView.class);
            replay.setDuplicate(true);
            return replay;
        }

        OrderCancellationSagaView result = command.getOperation() == OrderCancellationSagaOperation.START
                ? start(tenantId, command, now)
                : retry(tenantId, command, now);
        require(operationMapper.markSucceeded(operationId, tenantId, result.getSagaId(),
                JsonUtils.toJsonString(result), now) == 1, "cancellation Saga operation completion conflict");
        return result;
    }

    @Override
    public OrderCancellationSagaView get(String sagaId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireText(sagaId, "sagaId", 36);
        OrderCancellationSagaDO saga = sagaMapper.selectTenantSaga(tenantId, sagaId);
        require(saga != null, "cancellation Saga does not exist");
        return view(saga, false);
    }

    private OrderCancellationSagaView start(Long tenantId, OrderCancellationSagaCommand command,
                                            LocalDateTime now) {
        require(command.getSagaId() == null, "START does not accept sagaId");
        require(command.getExpectedVersion() == null, "START does not accept expectedVersion");
        requireText(command.getOrderId(), "orderId", 36);
        requireText(command.getReason(), "reason", 256);
        requireResponsibility(command.getResponsibilityParty(), command.getResponsibilityCode());
        OrderHeaderDO order = orderMapper.selectForUpdate(tenantId, command.getOrderId());
        require(order != null, "canonical order does not exist");
        String mode = Objects.requireNonNullElse(command.getCancellationMode(), "UNPAID_RESERVED");
        require("UNPAID_RESERVED".equals(mode) || "PAID_UNSHIPPED".equals(mode),
                "unsupported cancellationMode");
        PaymentCancellationView payment = null;
        FulfillmentCommandResult fulfillment = null;
        if ("PAID_UNSHIPPED".equals(mode)) {
            require("PAYMENT_CONFIRMED".equals(order.getStatus()),
                    "paid cancellation requires PAYMENT_CONFIRMED");
            requireText(order.getPaymentId(), "paymentId", 36);
            require(order.getShipmentId() == null, "shipped order cannot enter paid cancellation");
            payment = paymentQueryApi.requireCaptured(order.getOrderId(), order.getPaymentId());
            require(Objects.equals(payment.getCapturedAmountMinor(), order.getPayableAmountMinor())
                            && Objects.equals(payment.getCurrencyCode(), order.getCurrencyCode()),
                    "captured Payment does not reconcile with canonical Order");
            require(Boolean.TRUE.equals(payment.getTestMode()),
                    "paid cancellation first slice supports INTERNAL_TEST Payment only");
            fulfillment = fulfillmentQueryApi.requireCreatedByOrder(order.getOrderId());
        } else {
            require("INVENTORY_RESERVED".equals(order.getStatus()),
                    "durable unpaid cancellation requires INVENTORY_RESERVED");
            require(order.getPaymentId() == null && order.getFulfillmentId() == null
                            && order.getShipmentId() == null,
                    "durable pre-payment cancellation cannot own paid or fulfilled order");
        }
        require(sagaMapper.selectByOrder(tenantId, order.getOrderId()) == null,
                "canonical order already has a cancellation Saga");
        List<OrderItemDO> orderItems = orderItemMapper.selectByOrder(tenantId, order.getOrderId());
        require(!orderItems.isEmpty(), "canonical order has no items");

        String sagaId = UUID.randomUUID().toString();
        List<OrderCancellationSagaItemDO> sagaItems = new ArrayList<>();
        int expectedReservations = 0;
        int releasedReservations = 0;
        int sequence = 1;
        for (OrderItemDO orderItem : orderItems) {
            InventoryReservationView reservation = null;
            requireText(orderItem.getReservationId(), "reservationId", 36);
            reservation = reservationQueryApi.requireForCancellation(orderItem.getReservationId(),
                    "TRADE_ORDER", order.getOrderId(), orderItem.getOrderItemId());
            require(Objects.equals(orderItem.getCanonicalSkuId(), reservation.getCanonicalSkuId()),
                    "reservation SKU does not match canonical order item");
            require(orderItem.getQuantity().compareTo(reservation.getQuantity()) == 0,
                    "reservation quantity does not match canonical order item");
            expectedReservations++;
            if ("RELEASED".equals(reservation.getStatus())) releasedReservations++;
            long itemOffset = "PAID_UNSHIPPED".equals(mode) ? sequence++ + 2L : sequence++;
            LocalDateTime itemOccurredAt = LocalDateTime.ofInstant(command.getOccurredAt().plusSeconds(itemOffset),
                    ZoneOffset.UTC);
            sagaItems.add(item(tenantId, sagaId, orderItem, reservation, itemOccurredAt, now));
        }

        String activeStep = "PAID_UNSHIPPED".equals(mode) ? "CANCEL_FULFILLMENT"
                : releasedReservations < expectedReservations ? "RELEASE_RESERVATIONS" : "CANCEL_ORDER";
        LocalDateTime occurredAt = LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC);
        OrderCancellationSagaDO saga = new OrderCancellationSagaDO().setSagaId(sagaId).setCancellationMode(mode)
                .setTenantId(tenantId)
                .setIdempotencyKey(command.getIdempotencyKey()).setRequestHash(fingerprint(tenantId, command))
                .setRunId(command.getRunId()).setOrderId(order.getOrderId()).setOrderNo(order.getOrderNo())
                .setOrderStatusAtRequest(order.getStatus()).setOrderVersionAtRequest(order.getVersion())
                .setStatus("REQUESTED").setActiveStep(activeStep)
                .setExpectedReservationCount(expectedReservations).setReleasedReservationCount(releasedReservations)
                .setPaymentId(payment == null ? null : payment.getPaymentId())
                .setPaymentVersionAtRequest(payment == null ? null : payment.getAggregateVersion())
                .setPaymentStatus(payment == null ? null : payment.getStatus())
                .setExpectedFulfillmentCount(fulfillment == null ? 0 : 1).setCancelledFulfillmentCount(0)
                .setAttemptCount(0).setMaxAttempts(DEFAULT_MAX_ATTEMPTS).setVersion(1L)
                .setReason(command.getReason()).setCorrelationId(command.getCorrelationId())
                .setResponsibilityParty(command.getResponsibilityParty())
                .setResponsibilityCode(command.getResponsibilityCode())
                .setCausationId(command.getCausationId()).setOccurredAt(occurredAt)
                .setFinalizeOccurredAt(LocalDateTime.ofInstant(command.getOccurredAt().plusSeconds(
                        orderItems.size() + ("PAID_UNSHIPPED".equals(mode) ? 4L : 2L)), ZoneOffset.UTC))
                .setPaymentRefundOccurredAt("PAID_UNSHIPPED".equals(mode)
                        ? LocalDateTime.ofInstant(command.getOccurredAt().plusSeconds(2), ZoneOffset.UTC) : null)
                .setCreatedAt(now).setUpdatedAt(now);
        sagaMapper.insert(saga);
        sagaItems.forEach(sagaItemMapper::insert);

        OrderCancellationSagaFulfillmentDO sagaFulfillment = null;
        if (fulfillment != null) {
            sagaFulfillment = new OrderCancellationSagaFulfillmentDO()
                    .setSagaFulfillmentId(UUID.randomUUID().toString()).setTenantId(tenantId).setSagaId(sagaId)
                    .setFulfillmentId(fulfillment.getFulfillmentId())
                    .setFulfillmentVersionAtRequest(fulfillment.getAggregateVersion())
                    .setStatusAtRequest("CREATED")
                    .setRequestIdempotencyKey("cancel-saga:" + sagaId + ":fulfillment:"
                            + fulfillment.getFulfillmentId() + ":request")
                    .setFinalizeIdempotencyKey("cancel-saga:" + sagaId + ":fulfillment:"
                            + fulfillment.getFulfillmentId() + ":finalize")
                    .setStatus("FENCED").setAttemptCount(0)
                    .setOccurredAt(LocalDateTime.ofInstant(command.getOccurredAt().plusSeconds(1), ZoneOffset.UTC))
                    .setCreatedAt(now).setUpdatedAt(now);
            sagaFulfillmentMapper.insert(sagaFulfillment);
        }

        OrderCommandResult fenced = orderCommandApi.execute(OrderCommand.builder()
                .operation(OrderOperation.REQUEST_CANCELLATION)
                .idempotencyKey("cancel-saga:" + sagaId + ":request")
                .runId(command.getRunId()).orderId(order.getOrderId()).expectedVersion(order.getVersion())
                .cancellationSagaId(sagaId).reason(command.getReason())
                .responsibilityParty(command.getResponsibilityParty())
                .responsibilityCode(command.getResponsibilityCode())
                .cancellationMode(mode).cancellationStepOrdinal(0)
                .fulfillmentId(fulfillment == null ? null : fulfillment.getFulfillmentId())
                .correlationId(command.getCorrelationId()).causationId(command.getCausationId())
                .occurredAt(command.getOccurredAt()).build());
        require("CANCELLATION_PENDING".equals(fenced.getCurrentStatus()),
                "canonical order cancellation fence was not established");
        if (sagaFulfillment != null) {
            FulfillmentCommandResult fulfillmentFenced = fulfillmentCommandApi.execute(FulfillmentCommand.builder()
                    .operation(FulfillmentOperation.REQUEST_CANCELLATION)
                    .idempotencyKey(sagaFulfillment.getRequestIdempotencyKey()).runId(command.getRunId())
                    .fulfillmentId(sagaFulfillment.getFulfillmentId())
                    .expectedVersion(sagaFulfillment.getFulfillmentVersionAtRequest())
                    .orderId(order.getOrderId()).reason(command.getReason()).cancellationSagaId(sagaId)
                    .cancellationStepOrdinal(1).correlationId(command.getCorrelationId())
                    .causationId(command.getCausationId()).occurredAt(command.getOccurredAt()).build());
            require("CANCELLATION_PENDING".equals(fulfillmentFenced.getCurrentStatus()),
                    "Fulfillment cancellation fence was not established");
        }
        checkpointService.appendInitial(saga, now);
        return view(saga, false);
    }

    private OrderCancellationSagaView retry(Long tenantId, OrderCancellationSagaCommand command,
                                            LocalDateTime now) {
        requireText(command.getSagaId(), "sagaId", 36);
        require(command.getExpectedVersion() != null && command.getExpectedVersion() > 0,
                "RETRY requires expectedVersion");
        require(command.getOrderId() == null, "RETRY does not accept orderId");
        OrderCancellationSagaDO saga = checkpointService.retryManually(tenantId, command.getSagaId(),
                command.getExpectedVersion(), now);
        return view(saga, false);
    }

    private OrderCancellationSagaItemDO item(Long tenantId, String sagaId, OrderItemDO orderItem,
                                              InventoryReservationView reservation,
                                              LocalDateTime occurredAt, LocalDateTime now) {
        boolean required = reservation != null;
        return new OrderCancellationSagaItemDO().setSagaItemId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setSagaId(sagaId).setOrderItemId(orderItem.getOrderItemId())
                .setReservationId(required ? reservation.getReservationId() : null)
                .setOwnerId(required ? reservation.getOwnerId() : null)
                .setCanonicalSkuId(orderItem.getCanonicalSkuId())
                .setWarehouseId(required ? reservation.getWarehouseId() : null)
                .setStockStatus(required ? reservation.getStockStatus() : null)
                .setQualityStatus(required ? reservation.getQualityStatus() : null)
                .setUomCode(required ? reservation.getUomCode() : null).setQuantity(orderItem.getQuantity())
                .setReleaseIdempotencyKey(required
                        ? "cancel-saga:" + sagaId + ":release:" + reservation.getReservationId() : null)
                .setStatus(!required ? "NOT_REQUIRED" : reservation.getStatus().equals("RELEASED")
                        ? "RELEASED" : "PENDING")
                .setAttemptCount(0).setOccurredAt(occurredAt).setCreatedAt(now).setUpdatedAt(now)
                .setReleasedAt(required && "RELEASED".equals(reservation.getStatus()) ? now : null);
    }

    private OrderCancellationSagaView view(OrderCancellationSagaDO saga, boolean duplicate) {
        List<OrderCancellationSagaItemView> items = sagaItemMapper.selectBySaga(saga.getTenantId(), saga.getSagaId())
                .stream().map(item -> OrderCancellationSagaItemView.builder()
                        .sagaItemId(item.getSagaItemId()).orderItemId(item.getOrderItemId())
                        .reservationId(item.getReservationId()).canonicalSkuId(item.getCanonicalSkuId())
                        .quantity(item.getQuantity()).status(item.getStatus()).attemptCount(item.getAttemptCount())
                        .inventoryOperationId(item.getInventoryOperationId()).build()).toList();
        OrderCancellationSagaFulfillmentDO fulfillment = sagaFulfillmentMapper.selectBySaga(
                saga.getTenantId(), saga.getSagaId());
        return OrderCancellationSagaView.builder().sagaId(saga.getSagaId())
                .cancellationMode(saga.getCancellationMode()).runId(saga.getRunId())
                .orderId(saga.getOrderId()).orderNo(saga.getOrderNo())
                .orderStatusAtRequest(saga.getOrderStatusAtRequest())
                .orderVersionAtRequest(saga.getOrderVersionAtRequest()).status(saga.getStatus())
                .activeStep(saga.getActiveStep())
                .responsibilityParty(saga.getResponsibilityParty())
                .responsibilityCode(saga.getResponsibilityCode())
                .expectedReservationCount(saga.getExpectedReservationCount())
                .releasedReservationCount(saga.getReleasedReservationCount()).attemptCount(saga.getAttemptCount())
                .maxAttempts(saga.getMaxAttempts()).aggregateVersion(saga.getVersion()).reason(saga.getReason())
                .lastErrorCode(saga.getLastErrorCode()).lastErrorMessage(saga.getLastErrorMessage())
                .paymentId(saga.getPaymentId()).paymentRefundTransactionId(saga.getPaymentRefundTransactionId())
                .paymentStatus(saga.getPaymentStatus())
                .fulfillmentId(fulfillment == null ? null : fulfillment.getFulfillmentId())
                .fulfillmentStatus(fulfillment == null ? null : fulfillment.getStatus())
                .expectedFulfillmentCount(saga.getExpectedFulfillmentCount())
                .cancelledFulfillmentCount(saga.getCancelledFulfillmentCount())
                .nextRetryAt(instant(saga.getNextRetryAt())).completedAt(instant(saga.getCompletedAt()))
                .items(items).duplicate(duplicate).build();
    }

    private static void validateCommon(OrderCancellationSagaCommand command) {
        require(command != null && command.getOperation() != null, "cancellation Saga operation is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        require(command.getIdempotencyKey().length() >= 8, "idempotencyKey is too short");
        requireText(command.getRunId(), "runId", 64);
        require(command.getOccurredAt() != null, "occurredAt is required");
        requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) requireUuid(command.getCausationId(), "causationId");
    }

    private static String fingerprint(Long tenantId, OrderCancellationSagaCommand command) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("tenant_id", tenantId); value.put("operation", command.getOperation());
        value.put("cancellation_mode", command.getCancellationMode());
        value.put("responsibility_party", command.getResponsibilityParty());
        value.put("responsibility_code", command.getResponsibilityCode());
        value.put("run_id", command.getRunId()); value.put("saga_id", command.getSagaId());
        value.put("expected_version", command.getExpectedVersion()); value.put("order_id", command.getOrderId());
        value.put("reason", command.getReason()); value.put("correlation_id", command.getCorrelationId());
        value.put("causation_id", command.getCausationId()); value.put("occurred_at", command.getOccurredAt());
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(value));
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
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

    private static void requireResponsibility(String party, String code) {
        require(OrderCancellationResponsibilityParty.isSupported(party),
                "responsibilityParty is unsupported");
        require(OrderCancellationResponsibilityCode.matches(party, code),
                "responsibilityCode does not belong to responsibilityParty");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
