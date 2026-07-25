package cn.iocoder.yudao.module.cloudmold.order.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.FulfillmentCancellationQueryApi;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.FulfillmentShipmentValidationApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.*;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryReservationQueryApi;
import cn.iocoder.yudao.module.cloudmold.order.api.*;
import cn.iocoder.yudao.module.cloudmold.order.api.cancellation.OrderCancellationResponsibilityCode;
import cn.iocoder.yudao.module.cloudmold.order.api.cancellation.OrderCancellationResponsibilityParty;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.*;
import cn.iocoder.yudao.module.cloudmold.payment.api.PaymentCancellationQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderCommandServiceImpl implements OrderCommandApi, OrderQueryApi {

    static final int OPERATION_SUCCEEDED = 10;
    private static final String CURRENCY_CNY = "CNY";

    private final OrderOperationMapper operationMapper;
    private final OrderHeaderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final OrderBenefitApplicationMapper benefitApplicationMapper;
    private final OrderBenefitAllocationMapper benefitAllocationMapper;
    private final OrderBenefitFundingMapper benefitFundingMapper;
    private final OrderStatusHistoryMapper historyMapper;
    private final CatalogSkuValidationApi catalogSkuValidationApi;
    private final FulfillmentShipmentValidationApi fulfillmentValidationApi;
    private final ListingQueryApi listingQueryApi;
    private final InventoryReservationQueryApi inventoryReservationQueryApi;
    private final PaymentCancellationQueryApi paymentCancellationQueryApi;
    private final FulfillmentCancellationQueryApi fulfillmentCancellationQueryApi;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderCommandResult execute(OrderCommand command) {
        validateCommon(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (isPlace(command.getOperation())) {
            validatePlace(command);
            command.getItems().forEach(item -> catalogSkuValidationApi.requireActiveSku(item.getCanonicalSkuId()));
        } else {
            requireText(command.getOrderId(), "orderId", 36);
            require(command.getExpectedVersion() != null && command.getExpectedVersion() > 0,
                    "expectedVersion must be positive");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = fingerprint(tenantId, command);
        String attemptToken = UUID.randomUUID().toString();
        operationMapper.insertOrResolve(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                requestHash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve order operation");
        OrderOperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "order operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(operation.getRequestHash(), requestHash),
                    "idempotency key conflicts with different order payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing order operation is not complete");
            OrderCommandResult replay = JsonUtils.parseObject(operation.getResultJson(), OrderCommandResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        OrderCommandResult result = isPlace(command.getOperation())
                ? place(tenantId, operationId, command, now)
                : transition(tenantId, operationId, command, now);
        require(operationMapper.markSucceeded(operationId, tenantId, result.getOrderId(),
                JsonUtils.toJsonString(result), now) == 1, "order operation completion conflict");
        return result;
    }

    @Override
    public OrderPaymentView requirePayableOrder(String orderId, Long amountMinor, String currencyCode) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireText(orderId, "orderId", 36);
        require(amountMinor != null && amountMinor >= 0, "amountMinor must be nonnegative");
        require(CURRENCY_CNY.equals(currencyCode), "first slice supports CNY only");
        OrderHeaderDO order = orderMapper.selectForUpdate(tenantId, orderId);
        require(order != null, "canonical order does not exist");
        require("INVENTORY_RESERVED".equals(order.getStatus()), "canonical order is not payable");
        require(Objects.equals(order.getPayableAmountMinor(), amountMinor), "payment amount does not match order");
        require(Objects.equals(order.getCurrencyCode(), currencyCode), "payment currency does not match order");
        return OrderPaymentView.builder().orderId(order.getOrderId()).orderNo(order.getOrderNo())
                .runId(order.getRunId()).buyerId(order.getBuyerId()).status(order.getStatus())
                .payableAmountMinor(order.getPayableAmountMinor())
                .currencyCode(order.getCurrencyCode()).aggregateVersion(order.getVersion()).build();
    }

    @Override
    public OrderFulfillmentView requireFulfillableOrder(String orderId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireText(orderId, "orderId", 36);
        OrderHeaderDO order = orderMapper.selectForUpdate(tenantId, orderId);
        require(order != null, "canonical order does not exist");
        require("PAYMENT_CONFIRMED".equals(order.getStatus()), "canonical order is not fulfillable");
        List<OrderItemDO> items = itemMapper.selectByOrder(tenantId, orderId);
        require(!items.isEmpty(), "canonical order has no items");
        require(items.stream().allMatch(item -> item.getReservationId() != null),
                "canonical order items are not fully reserved");
        return OrderFulfillmentView.builder().orderId(order.getOrderId()).orderNo(order.getOrderNo())
                .status(order.getStatus()).aggregateVersion(order.getVersion())
                .items(items.stream().map(OrderCommandServiceImpl::lineView).toList()).build();
    }

    @Override
    public OrderAttributionView requireAttributedOrder(String orderId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireText(orderId, "orderId", 36);
        OrderHeaderDO order = orderMapper.selectForUpdate(tenantId, orderId);
        require(order != null, "canonical order does not exist");
        require(order.getPaymentId() != null, "canonical order does not have a paid payment reference");
        require(Set.of("PAYMENT_CONFIRMED", "SHIPPED", "DELIVERED", "COMPLETED", "RETURNED")
                .contains(order.getStatus()), "canonical order is not in a paid status");
        List<OrderItemDO> items = itemMapper.selectByOrder(tenantId, orderId);
        require(!items.isEmpty(), "canonical order has no items");
        ListingAttribution attribution = resolveListingAttribution(order, items);
        return OrderAttributionView.builder()
                .orderId(order.getOrderId())
                .orderNo(order.getOrderNo())
                .buyerId(order.getBuyerId())
                .status(order.getStatus())
                .paymentId(order.getPaymentId())
                .aggregateVersion(order.getVersion())
                .merchantId(attribution.merchantId())
                .shopId(attribution.shopId())
                .channelCode(attribution.channelCode())
                .build();
    }

    private OrderCommandResult place(Long tenantId, Long operationId, OrderCommand command, LocalDateTime now) {
        String orderId = UUID.randomUUID().toString();
        String orderNo = "CMO" + orderId.replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
        List<OrderItemDO> items = new ArrayList<>();
        Map<String, Long> lineDiscounts = calculateLineDiscounts(command);
        long productAmount = 0;
        BigDecimal totalQuantity = BigDecimal.ZERO;
        for (int index = 0; index < command.getItems().size(); index++) {
            OrderLineCommand line = command.getItems().get(index);
            String lineKey = resolveLineKey(line, index);
            PublishedListingOfferView offer = null;
            if (command.getOperation() == OrderOperation.PLACE_FROM_LISTING) {
                offer = listingQueryApi.requirePublishedOffer(PublishedOfferValidationCommand.builder()
                        .listingId(line.getListingId()).listingOfferId(line.getListingOfferId())
                        .canonicalSkuId(line.getCanonicalSkuId()).expectedPriceMinor(line.getUnitPriceMinor())
                        .currencyCode(command.getCurrencyCode()).build());
            }
            long lineAmount = Math.multiplyExact(line.getUnitPriceMinor(), line.getQuantity().longValueExact());
            long lineDiscount = lineDiscounts.getOrDefault(lineKey, 0L);
            long lineNet = Math.subtractExact(lineAmount, lineDiscount);
            productAmount = Math.addExact(productAmount, lineAmount);
            totalQuantity = totalQuantity.add(line.getQuantity());
            items.add(new OrderItemDO().setOrderItemId(UUID.randomUUID().toString()).setTenantId(tenantId)
                    .setOrderId(orderId).setLineKey(lineKey).setCanonicalSkuId(line.getCanonicalSkuId())
                    .setQuantity(line.getQuantity()).setUnitPriceMinor(line.getUnitPriceMinor())
                    .setLineAmountMinor(lineAmount).setDiscountAmountMinor(lineDiscount).setNetAmountMinor(lineNet)
                    .setMerchandiseCostMinor(line.getMerchandiseCostMinor())
                    .setListingId(offer == null ? null : offer.getListingId())
                    .setListingOfferId(offer == null ? null : offer.getListingOfferId())
                    .setListingRevision(offer == null ? null : offer.getListingRevision())
                    .setListingVersion(offer == null ? null : offer.getListingVersion())
                    .setChannelCode(offer == null ? null : offer.getChannelCode())
                    .setShopId(offer == null ? null : offer.getShopId())
                    .setCreatedAt(now).setUpdatedAt(now));
        }
        long payable = Math.subtractExact(Math.addExact(productAmount, command.getShippingAmountMinor()),
                command.getDiscountAmountMinor());
        require(payable >= 0, "payable amount cannot be negative");
        OrderHeaderDO order = new OrderHeaderDO().setOrderId(orderId).setTenantId(tenantId).setOrderNo(orderNo)
                .setRunId(command.getRunId()).setBuyerId(command.getBuyerId()).setStatus("PLACED")
                .setAddressRef(command.getAddressRef())
                .setAddressSnapshotVersion(command.getAddressSnapshotVersion())
                .setDestinationRegionCode(command.getDestinationRegionCode())
                .setTotalQuantity(totalQuantity).setProductAmountMinor(productAmount)
                .setShippingAmountMinor(command.getShippingAmountMinor())
                .setDiscountAmountMinor(command.getDiscountAmountMinor()).setPayableAmountMinor(payable)
                .setCurrencyCode(command.getCurrencyCode()).setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        orderMapper.insert(order);
        items.forEach(itemMapper::insert);
        List<BenefitEventFact> benefitFacts = persistBenefits(tenantId, operationId, orderId, command, items, now);
        appendHistory(tenantId, operationId, order, null, "PLACED", command, now);
        appendEvent(tenantId, order, items, null, "PLACED", command, 1L);
        appendBenefitEvents(tenantId, order, command, benefitFacts);
        return result(operationId, order, items, benefitViews(benefitFacts), null, false);
    }

    private List<BenefitEventFact> persistBenefits(Long tenantId, Long operationId, String orderId,
                                                   OrderCommand command, List<OrderItemDO> items,
                                                   LocalDateTime now) {
        if (benefitApplications(command).isEmpty()) return List.of();
        Map<String, OrderItemDO> itemByLineKey = new HashMap<>();
        items.forEach(item -> itemByLineKey.put(item.getLineKey(), item));
        List<BenefitEventFact> facts = new ArrayList<>();
        LocalDateTime occurredAt = LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC);
        for (OrderBenefitApplicationCommand requested : command.getBenefitApplications()) {
            OrderBenefitApplicationDO application = new OrderBenefitApplicationDO()
                    .setBenefitApplicationId(UUID.randomUUID().toString()).setTenantId(tenantId).setOrderId(orderId)
                    .setApplicationKey(requested.getApplicationKey()).setBenefitType(requested.getBenefitType())
                    .setBenefitSourceType(requested.getBenefitSourceType())
                    .setBenefitSourceId(requested.getBenefitSourceId())
                    .setBenefitSourceVersion(requested.getBenefitSourceVersion())
                    .setEntitlementId(requested.getEntitlementId()).setAmountMinor(requested.getAmountMinor())
                    .setCurrencyCode(CURRENCY_CNY)
                    .setCalculationDigest(requested.getCalculationDigest().toLowerCase(Locale.ROOT))
                    .setOperationId(operationId).setVersion(1L).setOccurredAt(occurredAt).setCreatedAt(now);
            require(benefitApplicationMapper.insert(application) == 1, "failed to persist order benefit application");
            List<BenefitAllocationEventFact> allocationFacts = new ArrayList<>();
            for (OrderBenefitAllocationCommand requestedAllocation : requested.getAllocations()) {
                OrderItemDO item = itemByLineKey.get(requestedAllocation.getLineKey());
                OrderBenefitAllocationDO allocation = new OrderBenefitAllocationDO()
                        .setBenefitAllocationId(UUID.randomUUID().toString()).setTenantId(tenantId).setOrderId(orderId)
                        .setBenefitApplicationId(application.getBenefitApplicationId())
                        .setAllocationKey(requestedAllocation.getAllocationKey()).setOrderItemId(item.getOrderItemId())
                        .setLineKey(item.getLineKey()).setAmountMinor(requestedAllocation.getAmountMinor())
                        .setCurrencyCode(CURRENCY_CNY).setCreatedAt(now);
                require(benefitAllocationMapper.insert(allocation) == 1,
                        "failed to persist order benefit allocation");
                List<OrderBenefitFundingDO> fundingFacts = new ArrayList<>();
                for (OrderBenefitFundingCommand requestedFunding : requestedAllocation.getFunding()) {
                    OrderBenefitFundingDO funding = new OrderBenefitFundingDO()
                            .setBenefitFundingId(UUID.randomUUID().toString()).setTenantId(tenantId).setOrderId(orderId)
                            .setBenefitApplicationId(application.getBenefitApplicationId())
                            .setBenefitAllocationId(allocation.getBenefitAllocationId())
                            .setFundingKey(requestedFunding.getFundingKey())
                            .setFunderType(requestedFunding.getFunderType())
                            .setFunderId(requestedFunding.getFunderId()).setAmountMinor(requestedFunding.getAmountMinor())
                            .setCurrencyCode(CURRENCY_CNY).setCreatedAt(now);
                    require(benefitFundingMapper.insert(funding) == 1,
                            "failed to persist order benefit funding");
                    fundingFacts.add(funding);
                }
                allocationFacts.add(new BenefitAllocationEventFact(allocation, fundingFacts));
            }
            facts.add(new BenefitEventFact(application, allocationFacts));
        }
        return facts;
    }

    private OrderCommandResult transition(Long tenantId, Long operationId, OrderCommand command, LocalDateTime now) {
        OrderHeaderDO order = orderMapper.selectForUpdate(tenantId, command.getOrderId());
        require(order != null, "canonical order does not exist");
        require(Objects.equals(order.getVersion(), command.getExpectedVersion()), "canonical order version conflict");
        Transition transition = transition(command.getOperation(), order.getStatus());
        List<OrderItemDO> items = itemMapper.selectByOrder(tenantId, order.getOrderId());
        require(!items.isEmpty(), "canonical order has no items");
        if (command.getOperation() == OrderOperation.CONFIRM_INVENTORY) {
            bindReservations(tenantId, order.getOrderId(), items, command.getReservationReferences(), now);
            items = itemMapper.selectByOrder(tenantId, order.getOrderId());
        }
        if (command.getOperation() == OrderOperation.CONFIRM_PAYMENT) {
            requireText(command.getPaymentId(), "paymentId", 36);
        }
        if (command.getOperation() == OrderOperation.CONFIRM_REFUND) {
            requireText(command.getRefundId(), "refundId", 128);
        }
        boolean listingBackedOrder = items.stream().anyMatch(item -> item.getListingId() != null);
        if (command.getOperation() == OrderOperation.SHIP) {
            require(!listingBackedOrder, "listing-backed order requires SHIP_WITH_FULFILLMENT");
        }
        if (command.getOperation() == OrderOperation.SHIP_WITH_FULFILLMENT) {
            require(listingBackedOrder, "SHIP_WITH_FULFILLMENT requires listing-backed order");
            requireText(command.getFulfillmentId(), "fulfillmentId", 36);
            requireText(command.getShipmentId(), "shipmentId", 36);
            fulfillmentValidationApi.requireShipped(order.getOrderId(), command.getFulfillmentId(),
                    command.getShipmentId());
        }
        if (command.getOperation() == OrderOperation.COMPLETE) {
            require(!listingBackedOrder, "listing-backed order requires COMPLETE_AFTER_DELIVERY");
        }
        if (command.getOperation() == OrderOperation.COMPLETE_AFTER_DELIVERY) {
            require(listingBackedOrder, "COMPLETE_AFTER_DELIVERY requires listing-backed order");
            requireText(order.getFulfillmentId(), "fulfillmentId", 36);
            fulfillmentValidationApi.requireDelivered(order.getOrderId(), order.getFulfillmentId());
        }
        if (command.getOperation() == OrderOperation.CANCEL) {
            requireText(command.getReason(), "reason", 256);
            if ("INVENTORY_RESERVED".equals(order.getStatus())) {
                for (OrderItemDO item : items) {
                    requireText(item.getReservationId(), "reservationId", 36);
                    inventoryReservationQueryApi.requireReleased(item.getReservationId(), "TRADE_ORDER",
                            order.getOrderId(), item.getOrderItemId());
                }
            }
        }
        String preCancellationStatus = null;
        if (command.getOperation() == OrderOperation.REQUEST_CANCELLATION) {
            requireText(command.getCancellationSagaId(), "cancellationSagaId", 36);
            requireText(command.getReason(), "reason", 256);
            requireResponsibility(command.getResponsibilityParty(), command.getResponsibilityCode());
            String mode = Objects.requireNonNullElse(command.getCancellationMode(), "UNPAID_RESERVED");
            if ("PAID_UNSHIPPED".equals(mode)) {
                require("PAYMENT_CONFIRMED".equals(order.getStatus()),
                        "paid cancellation requires PAYMENT_CONFIRMED");
                requireText(order.getPaymentId(), "paymentId", 36);
                requireText(command.getFulfillmentId(), "fulfillmentId", 36);
                require(order.getShipmentId() == null, "shipped order cannot enter paid cancellation");
                paymentCancellationQueryApi.requireCaptured(order.getOrderId(), order.getPaymentId());
            } else {
                require("UNPAID_RESERVED".equals(mode), "unsupported cancellationMode");
                require(order.getPaymentId() == null && order.getFulfillmentId() == null
                                && order.getShipmentId() == null,
                        "pre-payment cancellation Saga cannot own a paid or fulfilled order");
            }
            preCancellationStatus = order.getStatus();
        } else if (command.getOperation() == OrderOperation.FINALIZE_CANCELLATION) {
            require(order.getCancellationResponsibilityParty() != null
                    && order.getCancellationResponsibilityCode() != null,
                    "cancellation responsibility is missing on canonical order fence");
        }
        if (command.getOperation() == OrderOperation.FINALIZE_CANCELLATION) {
            requireText(command.getCancellationSagaId(), "cancellationSagaId", 36);
            require(Objects.equals(order.getCancellationSagaId(), command.getCancellationSagaId()),
                    "cancellation Saga does not own canonical order fence");
            String mode = Objects.requireNonNullElse(command.getCancellationMode(), "UNPAID_RESERVED");
            require(("UNPAID_RESERVED".equals(mode) && "INVENTORY_RESERVED".equals(order.getPreCancellationStatus()))
                            || ("PAID_UNSHIPPED".equals(mode)
                            && "PAYMENT_CONFIRMED".equals(order.getPreCancellationStatus())),
                    "canonical order cancellation source status is invalid");
            if ("PAID_UNSHIPPED".equals(mode)) {
                requireText(order.getPaymentId(), "paymentId", 36);
                requireText(order.getFulfillmentId(), "fulfillmentId", 36);
                requireText(command.getRefundId(), "refundId", 128);
                paymentCancellationQueryApi.requireRefunded(order.getOrderId(), order.getPaymentId());
                fulfillmentCancellationQueryApi.requireCancelled(order.getOrderId(), order.getFulfillmentId(),
                        command.getCancellationSagaId());
            } else {
                require(order.getPaymentId() == null && order.getFulfillmentId() == null
                                && order.getShipmentId() == null,
                        "pre-payment cancellation cannot finalize a paid or fulfilled order");
            }
            if ("INVENTORY_RESERVED".equals(order.getPreCancellationStatus())) {
                for (OrderItemDO item : items) {
                    requireText(item.getReservationId(), "reservationId", 36);
                    inventoryReservationQueryApi.requireReleased(item.getReservationId(), "TRADE_ORDER",
                            order.getOrderId(), item.getOrderItemId());
                }
            }
        }
        require(orderMapper.transition(tenantId, order.getOrderId(), order.getVersion(), transition.expectedStatus(),
                transition.nextStatus(), command.getPaymentId(), command.getFulfillmentId(), command.getShipmentId(),
                command.getRefundId(), command.getCancellationSagaId(), preCancellationStatus,
                command.getResponsibilityParty(), command.getResponsibilityCode(), now) == 1,
                "canonical order transition conflict");
        String previous = order.getStatus();
        order.setStatus(transition.nextStatus()).setVersion(order.getVersion() + 1).setUpdatedAt(now);
        if (command.getPaymentId() != null) order.setPaymentId(command.getPaymentId());
        if (command.getFulfillmentId() != null) order.setFulfillmentId(command.getFulfillmentId());
        if (command.getShipmentId() != null) order.setShipmentId(command.getShipmentId());
        if (command.getRefundId() != null) order.setRefundId(command.getRefundId());
        if (command.getCancellationSagaId() != null) order.setCancellationSagaId(command.getCancellationSagaId());
        if (preCancellationStatus != null) order.setPreCancellationStatus(preCancellationStatus);
        if (command.getResponsibilityParty() != null) {
            order.setCancellationResponsibilityParty(command.getResponsibilityParty());
        }
        if (command.getResponsibilityCode() != null) {
            order.setCancellationResponsibilityCode(command.getResponsibilityCode());
        }
        appendHistory(tenantId, operationId, order, previous, transition.nextStatus(), command, now);
        appendEvent(tenantId, order, items, previous, transition.nextStatus(), command, order.getVersion());
        return result(operationId, order, items, loadBenefitViews(tenantId, order.getOrderId()), previous, false);
    }

    private void bindReservations(Long tenantId, String orderId, List<OrderItemDO> items,
                                  List<OrderLineReference> references, LocalDateTime now) {
        require(references != null && references.size() == items.size(),
                "every order item requires one reservation reference");
        Map<String, String> byItem = new HashMap<>();
        for (OrderLineReference reference : references) {
            require(reference != null, "reservation reference is required");
            requireText(reference.getOrderItemId(), "orderItemId", 36);
            requireText(reference.getReservationId(), "reservationId", 36);
            require(byItem.put(reference.getOrderItemId(), reference.getReservationId()) == null,
                    "duplicate order item reservation reference");
        }
        for (OrderItemDO item : items) {
            String reservationId = byItem.get(item.getOrderItemId());
            require(reservationId != null, "reservation reference does not match order items");
            require(itemMapper.bindReservation(tenantId, orderId, item.getOrderItemId(), reservationId, now) == 1,
                    "order item reservation binding conflict");
        }
    }

    private void appendHistory(Long tenantId, Long operationId, OrderHeaderDO order,
                               String previous, String current, OrderCommand command, LocalDateTime now) {
        historyMapper.insert(new OrderStatusHistoryDO().setTenantId(tenantId).setOrderId(order.getOrderId())
                .setAggregateVersion(order.getVersion()).setPreviousStatus(previous).setCurrentStatus(current)
                .setOperationId(operationId).setReason(command.getReason())
                .setCancellationResponsibilityParty(order.getCancellationResponsibilityParty())
                .setCancellationResponsibilityCode(order.getCancellationResponsibilityCode())
                .setOccurredAt(LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC)).setCreatedAt(now));
    }

    private void appendEvent(Long tenantId, OrderHeaderDO order, List<OrderItemDO> items,
                             String previous, String current, OrderCommand command, Long version) {
        List<Map<String, Object>> eventItems = items.stream().map(item -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("order_item_id", item.getOrderItemId());
            value.put("canonical_sku_id", item.getCanonicalSkuId());
            value.put("quantity", item.getQuantity().toPlainString());
            value.put("unit_price_minor", item.getUnitPriceMinor());
            value.put("line_amount_minor", item.getLineAmountMinor());
            value.put("merchandise_cost_minor", item.getMerchandiseCostMinor());
            value.put("reservation_id", item.getReservationId());
            value.put("listing_id", item.getListingId());
            value.put("listing_offer_id", item.getListingOfferId());
            value.put("listing_revision", item.getListingRevision());
            value.put("listing_version", item.getListingVersion());
            value.put("channel_code", item.getChannelCode());
            value.put("shop_id", item.getShopId());
            return value;
        }).toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", command.getRunId());
        payload.put("order_id", order.getOrderId());
        payload.put("order_no", order.getOrderNo());
        payload.put("buyer_id", order.getBuyerId());
        boolean hasCanonicalAddress = order.getAddressRef() != null;
        if (hasCanonicalAddress) {
            payload.put("address_ref", order.getAddressRef());
            payload.put("address_snapshot_version", order.getAddressSnapshotVersion());
            payload.put("destination_region_code", order.getDestinationRegionCode());
        }
        payload.put("previous_status", previous);
        payload.put("current_status", current);
        payload.put("product_amount_minor", order.getProductAmountMinor());
        payload.put("shipping_amount_minor", order.getShippingAmountMinor());
        payload.put("discount_amount_minor", order.getDiscountAmountMinor());
        payload.put("payable_amount_minor", order.getPayableAmountMinor());
        payload.put("currency_code", order.getCurrencyCode());
        payload.put("payment_id", order.getPaymentId());
        payload.put("fulfillment_id", order.getFulfillmentId());
        payload.put("shipment_id", order.getShipmentId());
        payload.put("refund_id", order.getRefundId());
        payload.put("cancellation_saga_id", order.getCancellationSagaId());
        payload.put("pre_cancellation_status", order.getPreCancellationStatus());
        payload.put("cancellation_mode", command.getCancellationMode());
        payload.put("responsibility_party", order.getCancellationResponsibilityParty());
        payload.put("responsibility_code", order.getCancellationResponsibilityCode());
        payload.put("step_ordinal", command.getCancellationStepOrdinal());
        payload.put("reason", command.getReason());
        payload.put("items", eventItems);
        outboxAppender.append(AppendDomainEventCommand.builder().eventType("order.status.changed")
                .schemaVersion(hasCanonicalAddress ? 4
                        : "PAID_UNSHIPPED".equals(command.getCancellationMode()) ? 3
                        : items.stream().allMatch(item -> item.getListingId() != null) ? 2 : 1)
                .sourceSystem("cloudmold-order").tenantId(tenantId)
                .aggregateType("order").aggregateId(order.getOrderId()).aggregateVersion(version)
                .eventSequence((short) 1).occurredAt(command.getOccurredAt()).correlationId(command.getCorrelationId())
                .causationId(command.getCausationId()).idempotencyKey(command.getIdempotencyKey())
                .payload(payload).headers(Map.of("operation", command.getOperation().name()))
                .destination("lakehouse").build());
    }

    private void appendBenefitEvents(Long tenantId, OrderHeaderDO order, OrderCommand command,
                                     List<BenefitEventFact> facts) {
        for (int index = 0; index < facts.size(); index++) {
            BenefitEventFact fact = facts.get(index);
            OrderBenefitApplicationDO application = fact.application();
            List<Map<String, Object>> allocations = fact.allocations().stream().map(allocationFact -> {
                OrderBenefitAllocationDO allocation = allocationFact.allocation();
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("benefit_allocation_id", allocation.getBenefitAllocationId());
                value.put("allocation_key", allocation.getAllocationKey());
                value.put("order_item_id", allocation.getOrderItemId());
                value.put("line_key", allocation.getLineKey());
                value.put("amount_minor", allocation.getAmountMinor());
                value.put("currency_code", allocation.getCurrencyCode());
                value.put("funding", allocationFact.funding().stream().map(funding -> {
                    Map<String, Object> fundingValue = new LinkedHashMap<>();
                    fundingValue.put("benefit_funding_id", funding.getBenefitFundingId());
                    fundingValue.put("funding_key", funding.getFundingKey());
                    fundingValue.put("funder_type", funding.getFunderType());
                    fundingValue.put("funder_id", funding.getFunderId());
                    fundingValue.put("amount_minor", funding.getAmountMinor());
                    fundingValue.put("currency_code", funding.getCurrencyCode());
                    return fundingValue;
                }).toList());
                return value;
            }).toList();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("run_id", command.getRunId());
            payload.put("order_id", order.getOrderId());
            payload.put("order_no", order.getOrderNo());
            payload.put("benefit_application_id", application.getBenefitApplicationId());
            payload.put("application_key", application.getApplicationKey());
            payload.put("benefit_type", application.getBenefitType());
            payload.put("benefit_source_type", application.getBenefitSourceType());
            payload.put("benefit_source_id", application.getBenefitSourceId());
            payload.put("benefit_source_version", application.getBenefitSourceVersion());
            payload.put("entitlement_id", application.getEntitlementId());
            payload.put("amount_minor", application.getAmountMinor());
            payload.put("currency_code", application.getCurrencyCode());
            payload.put("calculation_digest", application.getCalculationDigest());
            payload.put("allocations", allocations);
            String eventIdempotency = "order-benefit-" + DigestUtil.sha256Hex(
                    tenantId + ":" + order.getOrderId() + ":" + application.getApplicationKey()).substring(0, 48);
            outboxAppender.append(AppendDomainEventCommand.builder()
                    .eventType("order.benefit_application.recorded").schemaVersion(1)
                    .sourceSystem("cloudmold-order").tenantId(tenantId)
                    .aggregateType("order").aggregateId(order.getOrderId()).aggregateVersion(1L)
                    .eventSequence((short) (index + 2)).occurredAt(command.getOccurredAt())
                    .correlationId(command.getCorrelationId()).causationId(command.getCausationId())
                    .idempotencyKey(eventIdempotency).payload(payload)
                    .headers(Map.of("operation", command.getOperation().name(), "immutable", true))
                    .destination("lakehouse").build());
        }
    }

    private static List<OrderBenefitApplicationView> benefitViews(List<BenefitEventFact> facts) {
        return facts.stream().map(fact -> OrderBenefitApplicationView.builder()
                .benefitApplicationId(fact.application().getBenefitApplicationId())
                .applicationKey(fact.application().getApplicationKey())
                .benefitType(fact.application().getBenefitType())
                .benefitSourceType(fact.application().getBenefitSourceType())
                .benefitSourceId(fact.application().getBenefitSourceId())
                .benefitSourceVersion(fact.application().getBenefitSourceVersion())
                .entitlementId(fact.application().getEntitlementId())
                .amountMinor(fact.application().getAmountMinor()).currencyCode(fact.application().getCurrencyCode())
                .calculationDigest(fact.application().getCalculationDigest()).version(fact.application().getVersion())
                .allocations(fact.allocations().stream().map(allocationFact -> OrderBenefitAllocationView.builder()
                        .benefitAllocationId(allocationFact.allocation().getBenefitAllocationId())
                        .allocationKey(allocationFact.allocation().getAllocationKey())
                        .orderItemId(allocationFact.allocation().getOrderItemId())
                        .lineKey(allocationFact.allocation().getLineKey())
                        .amountMinor(allocationFact.allocation().getAmountMinor())
                        .currencyCode(allocationFact.allocation().getCurrencyCode())
                        .funding(allocationFact.funding().stream().map(funding -> OrderBenefitFundingView.builder()
                                .benefitFundingId(funding.getBenefitFundingId()).fundingKey(funding.getFundingKey())
                                .funderType(funding.getFunderType()).funderId(funding.getFunderId())
                                .amountMinor(funding.getAmountMinor()).currencyCode(funding.getCurrencyCode()).build())
                                .toList())
                        .build()).toList())
                .build()).toList();
    }

    private List<OrderBenefitApplicationView> loadBenefitViews(Long tenantId, String orderId) {
        List<OrderBenefitApplicationDO> applications = benefitApplicationMapper.selectByOrder(tenantId, orderId);
        if (applications.isEmpty()) return List.of();
        Map<String, List<OrderBenefitFundingDO>> fundingByAllocation = benefitFundingMapper
                .selectByOrder(tenantId, orderId).stream()
                .collect(java.util.stream.Collectors.groupingBy(OrderBenefitFundingDO::getBenefitAllocationId,
                        LinkedHashMap::new, java.util.stream.Collectors.toList()));
        Map<String, List<OrderBenefitAllocationDO>> allocationsByApplication = benefitAllocationMapper
                .selectByOrder(tenantId, orderId).stream()
                .collect(java.util.stream.Collectors.groupingBy(OrderBenefitAllocationDO::getBenefitApplicationId,
                        LinkedHashMap::new, java.util.stream.Collectors.toList()));
        return applications.stream().map(application -> OrderBenefitApplicationView.builder()
                .benefitApplicationId(application.getBenefitApplicationId())
                .applicationKey(application.getApplicationKey()).benefitType(application.getBenefitType())
                .benefitSourceType(application.getBenefitSourceType())
                .benefitSourceId(application.getBenefitSourceId())
                .benefitSourceVersion(application.getBenefitSourceVersion())
                .entitlementId(application.getEntitlementId()).amountMinor(application.getAmountMinor())
                .currencyCode(application.getCurrencyCode()).calculationDigest(application.getCalculationDigest())
                .version(application.getVersion())
                .allocations(allocationsByApplication.getOrDefault(application.getBenefitApplicationId(), List.of())
                        .stream().map(allocation -> OrderBenefitAllocationView.builder()
                                .benefitAllocationId(allocation.getBenefitAllocationId())
                                .allocationKey(allocation.getAllocationKey()).orderItemId(allocation.getOrderItemId())
                                .lineKey(allocation.getLineKey()).amountMinor(allocation.getAmountMinor())
                                .currencyCode(allocation.getCurrencyCode())
                                .funding(fundingByAllocation.getOrDefault(allocation.getBenefitAllocationId(), List.of())
                                        .stream().map(funding -> OrderBenefitFundingView.builder()
                                                .benefitFundingId(funding.getBenefitFundingId())
                                                .fundingKey(funding.getFundingKey())
                                                .funderType(funding.getFunderType()).funderId(funding.getFunderId())
                                                .amountMinor(funding.getAmountMinor())
                                                .currencyCode(funding.getCurrencyCode()).build()).toList())
                                .build()).toList())
                .build()).toList();
    }

    private static OrderCommandResult result(Long operationId, OrderHeaderDO order, List<OrderItemDO> items,
                                             List<OrderBenefitApplicationView> benefitApplications,
                                             String previous, boolean duplicate) {
        return OrderCommandResult.builder().operationId(operationId).orderId(order.getOrderId())
                .orderNo(order.getOrderNo()).previousStatus(previous).currentStatus(order.getStatus())
                .aggregateVersion(order.getVersion()).productAmountMinor(order.getProductAmountMinor())
                .shippingAmountMinor(order.getShippingAmountMinor()).discountAmountMinor(order.getDiscountAmountMinor())
                .payableAmountMinor(order.getPayableAmountMinor()).currencyCode(order.getCurrencyCode())
                .paymentId(order.getPaymentId()).refundId(order.getRefundId())
                .fulfillmentId(order.getFulfillmentId()).shipmentId(order.getShipmentId())
                .cancellationSagaId(order.getCancellationSagaId())
                .preCancellationStatus(order.getPreCancellationStatus())
                .items(items.stream().map(OrderCommandServiceImpl::lineView).toList())
                .benefitApplications(benefitApplications).duplicate(duplicate).build();
    }

    private static OrderLineView lineView(OrderItemDO item) {
        return OrderLineView.builder().orderItemId(item.getOrderItemId()).lineKey(item.getLineKey())
                .canonicalSkuId(item.getCanonicalSkuId())
                .quantity(item.getQuantity()).unitPriceMinor(item.getUnitPriceMinor())
                .lineAmountMinor(item.getLineAmountMinor()).discountAmountMinor(item.getDiscountAmountMinor())
                .netAmountMinor(item.getNetAmountMinor()).reservationId(item.getReservationId())
                .listingId(item.getListingId()).listingOfferId(item.getListingOfferId())
                .listingRevision(item.getListingRevision()).listingVersion(item.getListingVersion())
                .channelCode(item.getChannelCode()).shopId(item.getShopId()).build();
    }

    private ListingAttribution resolveListingAttribution(OrderHeaderDO order, List<OrderItemDO> items) {
        List<OrderItemDO> listingBacked = items.stream()
                .filter(item -> item.getListingId() != null && item.getListingOfferId() != null)
                .toList();
        if (listingBacked.isEmpty()) {
            return new ListingAttribution(null, uniqueNonBlank(items.stream().map(OrderItemDO::getShopId).toList()),
                    uniqueNonBlank(items.stream().map(OrderItemDO::getChannelCode).toList()));
        }
        List<PublishedListingOfferView> offers = listingBacked.stream()
                .map(item -> listingQueryApi.requirePublishedOffer(PublishedOfferValidationCommand.builder()
                        .listingId(item.getListingId())
                        .listingOfferId(item.getListingOfferId())
                        .canonicalSkuId(item.getCanonicalSkuId())
                        .expectedPriceMinor(item.getUnitPriceMinor())
                        .currencyCode(order.getCurrencyCode())
                        .build()))
                .toList();
        return new ListingAttribution(
                uniqueNonBlank(offers.stream().map(PublishedListingOfferView::getMerchantId).toList()),
                uniqueNonBlank(offers.stream().map(PublishedListingOfferView::getShopId).toList()),
                uniqueNonBlank(offers.stream().map(PublishedListingOfferView::getChannelCode).toList()));
    }

    private static String uniqueNonBlank(List<String> values) {
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                unique.add(value);
            }
        }
        return unique.size() == 1 ? unique.iterator().next() : null;
    }

    private record ListingAttribution(String merchantId, String shopId, String channelCode) {
    }

    private static Transition transition(OrderOperation operation, String status) {
        return switch (operation) {
            case CONFIRM_INVENTORY -> requireTransition(status, "PLACED", "INVENTORY_RESERVED");
            case CONFIRM_PAYMENT -> requireTransition(status, "INVENTORY_RESERVED", "PAYMENT_CONFIRMED");
            case SHIP, SHIP_WITH_FULFILLMENT -> requireTransition(status, "PAYMENT_CONFIRMED", "SHIPPED");
            case COMPLETE, COMPLETE_AFTER_DELIVERY -> requireTransition(status, "SHIPPED", "COMPLETED");
            case CONFIRM_REFUND -> requireTransition(status, "COMPLETED", "REFUNDED");
            case RETURN -> requireTransition(status, "REFUNDED", "RETURNED");
            case CANCEL -> {
                require("PLACED".equals(status) || "INVENTORY_RESERVED".equals(status),
                        "CANCEL requires PLACED or INVENTORY_RESERVED");
                yield new Transition(status, "CANCELLED");
            }
            case REQUEST_CANCELLATION -> {
                require("INVENTORY_RESERVED".equals(status) || "PAYMENT_CONFIRMED".equals(status),
                        "REQUEST_CANCELLATION requires INVENTORY_RESERVED or PAYMENT_CONFIRMED");
                yield new Transition(status, "CANCELLATION_PENDING");
            }
            case FINALIZE_CANCELLATION -> requireTransition(status, "CANCELLATION_PENDING", "CANCELLED");
            case PLACE, PLACE_FROM_LISTING -> throw new IllegalArgumentException("PLACE is not a transition");
        };
    }

    private static Transition requireTransition(String actual, String expected, String next) {
        require(expected.equals(actual), next + " requires " + expected);
        return new Transition(expected, next);
    }

    private static void validateCommon(OrderCommand command) {
        require(command != null && command.getOperation() != null, "order operation is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        require(command.getIdempotencyKey().length() >= 8, "idempotencyKey is too short");
        requireText(command.getRunId(), "runId", 64);
        require(command.getOccurredAt() != null, "occurredAt is required");
        requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) requireUuid(command.getCausationId(), "causationId");
    }

    private static void validatePlace(OrderCommand command) {
        require(command.getOrderId() == null, "PLACE does not accept orderId");
        requireText(command.getBuyerId(), "buyerId", 128);
        if (command.getOperation() == OrderOperation.PLACE_FROM_LISTING) {
            requireUuid(command.getAddressRef(), "addressRef");
            require(command.getAddressSnapshotVersion() != null
                            && command.getAddressSnapshotVersion() > 0,
                    "addressSnapshotVersion must be positive");
            requireText(command.getDestinationRegionCode(), "destinationRegionCode", 32);
        }
        require(command.getItems() != null && !command.getItems().isEmpty() && command.getItems().size() <= 100,
                "PLACE requires 1 to 100 items");
        require(CURRENCY_CNY.equals(command.getCurrencyCode()), "first slice supports CNY only");
        require(command.getShippingAmountMinor() != null && command.getShippingAmountMinor() >= 0,
                "shippingAmountMinor must be nonnegative");
        require(command.getDiscountAmountMinor() != null && command.getDiscountAmountMinor() >= 0,
                "discountAmountMinor must be nonnegative");
        Set<String> skuIds = new HashSet<>();
        Set<String> lineKeys = new HashSet<>();
        boolean hasBenefits = !benefitApplications(command).isEmpty();
        boolean anyMerchandiseCost = command.getItems().stream()
                .anyMatch(item -> item != null && item.getMerchandiseCostMinor() != null);
        for (int index = 0; index < command.getItems().size(); index++) {
            OrderLineCommand item = command.getItems().get(index);
            require(item != null, "order item is required");
            requireText(item.getCanonicalSkuId(), "canonicalSkuId", 128);
            require(skuIds.add(item.getCanonicalSkuId()), "duplicate canonical SKU in one order");
            require(item.getQuantity() != null && item.getQuantity().scale() <= 6
                    && item.getQuantity().stripTrailingZeros().scale() <= 0 && item.getQuantity().signum() > 0,
                    "first slice requires positive whole-piece quantity");
            require(item.getUnitPriceMinor() != null && item.getUnitPriceMinor() >= 0,
                    "unitPriceMinor must be nonnegative");
            if (anyMerchandiseCost || item.getMerchandiseCostMinor() != null) {
                require(item.getMerchandiseCostMinor() != null && item.getMerchandiseCostMinor() >= 0,
                        "merchandiseCostMinor must be nonnegative when profitability inputs are provided");
            }
            if (hasBenefits) requireText(item.getLineKey(), "lineKey", 128);
            if (item.getLineKey() != null) requireText(item.getLineKey(), "lineKey", 128);
            require(lineKeys.add(resolveLineKey(item, index)), "duplicate lineKey in one order");
            if (command.getOperation() == OrderOperation.PLACE_FROM_LISTING) {
                requireText(item.getListingId(), "listingId", 36);
                requireText(item.getListingOfferId(), "listingOfferId", 36);
            }
        }
        calculateLineDiscounts(command);
    }

    private static Map<String, Long> calculateLineDiscounts(OrderCommand command) {
        List<OrderBenefitApplicationCommand> applications = benefitApplications(command);
        if (command.getDiscountAmountMinor() == 0) {
            require(applications.isEmpty(), "zero order discount cannot have benefit applications");
            return Map.of();
        }
        require(!applications.isEmpty() && applications.size() <= 100,
                "positive order discount requires 1 to 100 benefit applications");
        Map<String, Long> grossByLine = new HashMap<>();
        for (OrderLineCommand line : command.getItems()) {
            long gross = Math.multiplyExact(line.getUnitPriceMinor(), line.getQuantity().longValueExact());
            grossByLine.put(line.getLineKey(), gross);
        }
        Set<String> applicationKeys = new HashSet<>();
        Set<String> sourceVersions = new HashSet<>();
        Set<String> allocationKeys = new HashSet<>();
        Set<String> fundingKeys = new HashSet<>();
        Map<String, Long> discountByLine = new HashMap<>();
        long applicationTotal = 0;
        for (OrderBenefitApplicationCommand application : applications) {
            require(application != null, "benefit application is required");
            requireText(application.getApplicationKey(), "applicationKey", 128);
            require(applicationKeys.add(application.getApplicationKey()), "duplicate benefit applicationKey");
            require(Set.of("COUPON", "PROMOTION", "ALLOWANCE", "CAMPAIGN")
                    .contains(application.getBenefitType()), "unsupported benefitType");
            requireCode(application.getBenefitSourceType(), "benefitSourceType", 32);
            requireText(application.getBenefitSourceId(), "benefitSourceId", 128);
            require(application.getBenefitSourceVersion() != null && application.getBenefitSourceVersion() > 0,
                    "benefitSourceVersion must be positive");
            String sourceVersion = application.getBenefitSourceType() + ":" + application.getBenefitSourceId()
                    + ":" + application.getBenefitSourceVersion();
            require(sourceVersions.add(sourceVersion), "duplicate benefit source version in one order");
            if (application.getEntitlementId() != null) {
                requireText(application.getEntitlementId(), "entitlementId", 128);
                require("COUPON_ENTITLEMENT".equals(application.getBenefitSourceType())
                                && application.getEntitlementId().equals(application.getBenefitSourceId()),
                        "entitlement benefit must snapshot its own source ID and version");
            }
            require(application.getAmountMinor() != null && application.getAmountMinor() > 0,
                    "benefit application amountMinor must be positive");
            require(application.getCalculationDigest() != null
                            && application.getCalculationDigest().matches("[0-9a-fA-F]{64}"),
                    "calculationDigest must be a SHA-256 hex digest");
            require(application.getAllocations() != null && !application.getAllocations().isEmpty()
                            && application.getAllocations().size() <= 100,
                    "benefit application requires 1 to 100 allocations");
            long allocationTotal = 0;
            for (OrderBenefitAllocationCommand allocation : application.getAllocations()) {
                require(allocation != null, "benefit allocation is required");
                requireText(allocation.getAllocationKey(), "allocationKey", 128);
                require(allocationKeys.add(allocation.getAllocationKey()), "duplicate benefit allocationKey");
                requireText(allocation.getLineKey(), "allocation lineKey", 128);
                require(grossByLine.containsKey(allocation.getLineKey()),
                        "benefit allocation lineKey does not match an order line");
                require(allocation.getAmountMinor() != null && allocation.getAmountMinor() > 0,
                        "benefit allocation amountMinor must be positive");
                require(allocation.getFunding() != null && !allocation.getFunding().isEmpty()
                                && allocation.getFunding().size() <= 10,
                        "benefit allocation requires 1 to 10 funding shares");
                long fundingTotal = 0;
                for (OrderBenefitFundingCommand funding : allocation.getFunding()) {
                    require(funding != null, "benefit funding is required");
                    requireText(funding.getFundingKey(), "fundingKey", 128);
                    require(fundingKeys.add(funding.getFundingKey()), "duplicate benefit fundingKey");
                    require(Set.of("PLATFORM", "MERCHANT", "PARTNER").contains(funding.getFunderType()),
                            "unsupported funderType");
                    requireText(funding.getFunderId(), "funderId", 128);
                    require(funding.getAmountMinor() != null && funding.getAmountMinor() > 0,
                            "benefit funding amountMinor must be positive");
                    fundingTotal = Math.addExact(fundingTotal, funding.getAmountMinor());
                }
                require(fundingTotal == allocation.getAmountMinor(),
                        "benefit funding must equal allocation amount");
                allocationTotal = Math.addExact(allocationTotal, allocation.getAmountMinor());
                discountByLine.merge(allocation.getLineKey(), allocation.getAmountMinor(), Math::addExact);
            }
            require(allocationTotal == application.getAmountMinor(),
                    "benefit allocations must equal application amount");
            applicationTotal = Math.addExact(applicationTotal, application.getAmountMinor());
        }
        require(applicationTotal == command.getDiscountAmountMinor(),
                "benefit applications must equal order discount amount");
        discountByLine.forEach((lineKey, discount) -> require(discount <= grossByLine.get(lineKey),
                "order line discount cannot exceed gross amount"));
        return discountByLine;
    }

    private static List<OrderBenefitApplicationCommand> benefitApplications(OrderCommand command) {
        return command.getBenefitApplications() == null ? List.of() : command.getBenefitApplications();
    }

    private static String resolveLineKey(OrderLineCommand line, int index) {
        return line.getLineKey() == null ? "legacy-" + (index + 1) : line.getLineKey();
    }

    private static boolean isPlace(OrderOperation operation) {
        return operation == OrderOperation.PLACE || operation == OrderOperation.PLACE_FROM_LISTING;
    }

    private static String fingerprint(Long tenantId, OrderCommand command) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("tenant_id", tenantId); value.put("operation", command.getOperation());
        value.put("run_id", command.getRunId()); value.put("order_id", command.getOrderId());
        value.put("expected_version", command.getExpectedVersion()); value.put("buyer_id", command.getBuyerId());
        value.put("address_ref", command.getAddressRef());
        value.put("address_snapshot_version", command.getAddressSnapshotVersion());
        value.put("destination_region_code", command.getDestinationRegionCode());
        value.put("items", command.getItems()); value.put("shipping", command.getShippingAmountMinor());
        value.put("discount", command.getDiscountAmountMinor()); value.put("currency", command.getCurrencyCode());
        value.put("benefit_applications", command.getBenefitApplications());
        value.put("reservations", command.getReservationReferences()); value.put("payment_id", command.getPaymentId());
        value.put("fulfillment_id", command.getFulfillmentId()); value.put("shipment_id", command.getShipmentId());
        value.put("refund_id", command.getRefundId()); value.put("reason", command.getReason());
        value.put("cancellation_saga_id", command.getCancellationSagaId());
        value.put("cancellation_mode", command.getCancellationMode());
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

    private static void requireResponsibility(String party, String code) {
        require(OrderCancellationResponsibilityParty.isSupported(party),
                "responsibilityParty is unsupported");
        require(OrderCancellationResponsibilityCode.matches(party, code),
                "responsibilityCode does not belong to responsibilityParty");
    }

    private static void requireCode(String value, String field, int maxLength) {
        requireText(value, field, maxLength);
        require(value.matches("[A-Z][A-Z0-9_]*"), field + " must be an uppercase code");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private record Transition(String expectedStatus, String nextStatus) {}

    private record BenefitEventFact(OrderBenefitApplicationDO application,
                                    List<BenefitAllocationEventFact> allocations) {}

    private record BenefitAllocationEventFact(OrderBenefitAllocationDO allocation,
                                              List<OrderBenefitFundingDO> funding) {}
}
