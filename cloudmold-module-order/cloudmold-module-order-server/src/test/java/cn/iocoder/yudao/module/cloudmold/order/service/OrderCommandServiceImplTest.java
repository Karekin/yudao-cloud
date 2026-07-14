package cn.iocoder.yudao.module.cloudmold.order.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.FulfillmentCancellationQueryApi;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.FulfillmentShipmentValidationApi;
import cn.iocoder.yudao.module.cloudmold.listing.api.*;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryReservationQueryApi;
import cn.iocoder.yudao.module.cloudmold.order.api.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.*;
import cn.iocoder.yudao.module.cloudmold.payment.api.PaymentCancellationQueryApi;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderCommandServiceImplTest {

    private final OrderOperationMapper operationMapper = mock(OrderOperationMapper.class);
    private final OrderHeaderMapper orderMapper = mock(OrderHeaderMapper.class);
    private final OrderItemMapper itemMapper = mock(OrderItemMapper.class);
    private final OrderStatusHistoryMapper historyMapper = mock(OrderStatusHistoryMapper.class);
    private final CatalogSkuValidationApi catalogApi = mock(CatalogSkuValidationApi.class);
    private final FulfillmentShipmentValidationApi fulfillmentApi = mock(FulfillmentShipmentValidationApi.class);
    private final ListingQueryApi listingApi = mock(ListingQueryApi.class);
    private final InventoryReservationQueryApi reservationApi = mock(InventoryReservationQueryApi.class);
    private final PaymentCancellationQueryApi paymentCancellationApi = mock(PaymentCancellationQueryApi.class);
    private final FulfillmentCancellationQueryApi fulfillmentCancellationApi =
            mock(FulfillmentCancellationQueryApi.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final OrderCommandServiceImpl service = new OrderCommandServiceImpl(operationMapper, orderMapper,
            itemMapper, historyMapper, catalogApi, fulfillmentApi, listingApi, reservationApi,
            paymentCancellationApi, fulfillmentCancellationApi, outboxAppender);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(operationMapper.selectLastInsertId()).thenReturn(11L);
        when(operationMapper.markSucceeded(anyLong(), anyLong(), anyString(), anyString(), any())).thenReturn(1);
        when(orderMapper.insert(any(OrderHeaderDO.class))).thenReturn(1);
        when(itemMapper.insert(any(OrderItemDO.class))).thenReturn(1);
        when(historyMapper.insert(any(OrderStatusHistoryDO.class))).thenReturn(1);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event-1", "a".repeat(64), false));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldPlaceMultiLineOrderWithMoneyInvariant() {
        AtomicReference<String> attempt = claimNewOperation();

        OrderCommandResult result = service.execute(placeCommand());

        assertThat(attempt).hasValueSatisfying(value -> assertThat(value).isNotBlank());
        assertThat(result.getCurrentStatus()).isEqualTo("PLACED");
        assertThat(result.getAggregateVersion()).isEqualTo(1L);
        assertThat(result.getProductAmountMinor()).isEqualTo(500L);
        assertThat(result.getPayableAmountMinor()).isEqualTo(530L);
        assertThat(result.getItems()).hasSize(2);
        verify(catalogApi).requireActiveSku("sku-1");
        verify(catalogApi).requireActiveSku("sku-2");
        verify(itemMapper, times(2)).insert(any(OrderItemDO.class));
        verify(outboxAppender).append(argThat(event -> event.getEventType().equals("order.status.changed")
                && event.getAggregateVersion() == 1L
                && ((List<?>) event.getPayload().get("items")).size() == 2
                && event.getPayload().get("payable_amount_minor").equals(530L)));
    }

    @Test
    void shouldSnapshotPublishedListingOfferForNewCommerceFlow() {
        claimNewOperation();
        OrderCommand command = placeCommand();
        command.setOperation(OrderOperation.PLACE_FROM_LISTING);
        command.getItems().get(0).setListingId("listing-1");
        command.getItems().get(0).setListingOfferId("offer-1");
        command.getItems().get(1).setListingId("listing-1");
        command.getItems().get(1).setListingOfferId("offer-2");
        when(listingApi.requirePublishedOffer(any())).thenAnswer(invocation -> {
            PublishedOfferValidationCommand requested = invocation.getArgument(0);
            return PublishedListingOfferView.builder().listingId(requested.getListingId())
                    .listingNo("CML1").listingOfferId(requested.getListingOfferId())
                    .channelCode("YSHOPPING_INTERNAL").shopId("YSHOPPING_INTERNAL")
                    .canonicalSpuId("spu-1").canonicalSkuId(requested.getCanonicalSkuId())
                    .listingRevision(1).listingVersion(6L).priceMinor(requested.getExpectedPriceMinor())
                    .currencyCode("CNY").build();
        });

        OrderCommandResult result = service.execute(command);

        assertThat(result.getItems()).allSatisfy(item -> {
            assertThat(item.getListingId()).isEqualTo("listing-1");
            assertThat(item.getListingRevision()).isEqualTo(1);
            assertThat(item.getListingVersion()).isEqualTo(6L);
            assertThat(item.getChannelCode()).isEqualTo("YSHOPPING_INTERNAL");
        });
        verify(listingApi, times(2)).requirePublishedOffer(any());
        verify(outboxAppender).append(argThat(event -> event.getSchemaVersion() == 2));
    }

    @Test
    void shouldReturnImmutableFirstResultOnReplay() {
        AtomicReference<String> requestHash = new AtomicReference<>();
        when(operationMapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> { requestHash.set(invocation.getArgument(3)); return 0; });
        OrderCommandResult first = OrderCommandResult.builder().operationId(11L).orderId("order-1")
                .orderNo("CMO1").currentStatus("PLACED").aggregateVersion(1L).duplicate(false).build();
        when(operationMapper.selectForUpdate(11L, 1L)).thenAnswer(ignored -> new OrderOperationDO()
                .setOperationId(11L).setTenantId(1L).setAttemptToken("existing")
                .setRequestHash(requestHash.get()).setStatus(10).setResultJson(JsonUtils.toJsonString(first)));

        OrderCommandResult replay = service.execute(placeCommand());

        assertThat(replay.getDuplicate()).isTrue();
        assertThat(replay.getOrderId()).isEqualTo("order-1");
        verify(orderMapper, never()).insert(any(OrderHeaderDO.class));
    }

    @Test
    void shouldBindEveryReservationBeforeInventoryConfirmation() {
        claimNewOperation();
        OrderHeaderDO order = order("PLACED", 1L);
        List<OrderItemDO> items = List.of(item("item-1", "sku-1"), item("item-2", "sku-2"));
        when(orderMapper.selectForUpdate(1L, "order-1")).thenReturn(order);
        when(itemMapper.selectByOrder(1L, "order-1")).thenReturn(items, List.of(
                item("item-1", "sku-1").setReservationId("res-1"),
                item("item-2", "sku-2").setReservationId("res-2")));
        when(itemMapper.bindReservation(anyLong(), anyString(), anyString(), anyString(), any())).thenReturn(1);
        when(orderMapper.transition(anyLong(), anyString(), anyLong(), anyString(), anyString(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(1);
        OrderCommand command = transitionCommand(OrderOperation.CONFIRM_INVENTORY, 1L);
        command.setReservationReferences(List.of(new OrderLineReference("item-1", "res-1"),
                new OrderLineReference("item-2", "res-2")));

        OrderCommandResult result = service.execute(command);

        assertThat(result.getCurrentStatus()).isEqualTo("INVENTORY_RESERVED");
        assertThat(result.getItems()).extracting(OrderLineView::getReservationId)
                .containsExactly("res-1", "res-2");
        verify(itemMapper, times(2)).bindReservation(eq(1L), eq("order-1"), anyString(), anyString(), any());
    }

    @Test
    void shouldRejectSkippedStateTransition() {
        claimNewOperation();
        when(orderMapper.selectForUpdate(1L, "order-1")).thenReturn(order("PLACED", 1L));

        assertThatThrownBy(() -> service.execute(transitionCommand(OrderOperation.CONFIRM_PAYMENT, 1L)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("PAYMENT_CONFIRMED requires INVENTORY_RESERVED");
    }

    @Test
    void shouldRequireEveryReservationReleasedBeforeCancellingReservedOrder() {
        claimNewOperation();
        when(orderMapper.selectForUpdate(1L, "order-1")).thenReturn(order("INVENTORY_RESERVED", 2L));
        when(itemMapper.selectByOrder(1L, "order-1")).thenReturn(List.of(
                item("item-1", "sku-1").setReservationId("res-1"),
                item("item-2", "sku-2").setReservationId("res-2")));
        when(orderMapper.transition(eq(1L), eq("order-1"), eq(2L), eq("INVENTORY_RESERVED"),
                eq("CANCELLED"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any())).thenReturn(1);
        OrderCommand command = transitionCommand(OrderOperation.CANCEL, 2L);
        command.setReason("buyer cancelled before fulfillment");

        OrderCommandResult result = service.execute(command);

        assertThat(result.getCurrentStatus()).isEqualTo("CANCELLED");
        verify(reservationApi).requireReleased("res-1", "TRADE_ORDER", "order-1", "item-1");
        verify(reservationApi).requireReleased("res-2", "TRADE_ORDER", "order-1", "item-2");
    }

    @Test
    void shouldFenceReservedOrderBeforeDurableCancellation() {
        claimNewOperation();
        String sagaId = "70000000-0000-4000-8000-000000000001";
        when(orderMapper.selectForUpdate(1L, "order-1")).thenReturn(order("INVENTORY_RESERVED", 2L));
        when(itemMapper.selectByOrder(1L, "order-1")).thenReturn(List.of(
                item("item-1", "sku-1").setReservationId("res-1")));
        when(orderMapper.transition(eq(1L), eq("order-1"), eq(2L), eq("INVENTORY_RESERVED"),
                eq("CANCELLATION_PENDING"), isNull(), isNull(), isNull(), isNull(), eq(sagaId),
                eq("INVENTORY_RESERVED"), any())).thenReturn(1);
        OrderCommand command = transitionCommand(OrderOperation.REQUEST_CANCELLATION, 2L);
        command.setCancellationSagaId(sagaId);
        command.setReason("buyer cancelled before payment");

        OrderCommandResult result = service.execute(command);

        assertThat(result.getCurrentStatus()).isEqualTo("CANCELLATION_PENDING");
        assertThat(result.getCancellationSagaId()).isEqualTo(sagaId);
        assertThat(result.getPreCancellationStatus()).isEqualTo("INVENTORY_RESERVED");
    }

    @Test
    void shouldFinalizeDurableCancellationOnlyAfterRelease() {
        claimNewOperation();
        String sagaId = "70000000-0000-4000-8000-000000000001";
        OrderHeaderDO fenced = order("CANCELLATION_PENDING", 3L)
                .setCancellationSagaId(sagaId).setPreCancellationStatus("INVENTORY_RESERVED");
        when(orderMapper.selectForUpdate(1L, "order-1")).thenReturn(fenced);
        when(itemMapper.selectByOrder(1L, "order-1")).thenReturn(List.of(
                item("item-1", "sku-1").setReservationId("res-1")));
        when(orderMapper.transition(eq(1L), eq("order-1"), eq(3L), eq("CANCELLATION_PENDING"),
                eq("CANCELLED"), isNull(), isNull(), isNull(), isNull(), eq(sagaId), isNull(), any()))
                .thenReturn(1);
        OrderCommand command = transitionCommand(OrderOperation.FINALIZE_CANCELLATION, 3L);
        command.setCancellationSagaId(sagaId);
        command.setReason("all compensations completed");

        OrderCommandResult result = service.execute(command);

        assertThat(result.getCurrentStatus()).isEqualTo("CANCELLED");
        verify(reservationApi).requireReleased("res-1", "TRADE_ORDER", "order-1", "item-1");
    }

    @Test
    void shouldRejectPaymentAfterCancellationFence() {
        claimNewOperation();
        when(orderMapper.selectForUpdate(1L, "order-1"))
                .thenReturn(order("CANCELLATION_PENDING", 3L));

        assertThatThrownBy(() -> service.execute(transitionCommand(OrderOperation.CONFIRM_PAYMENT, 3L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PAYMENT_CONFIRMED requires INVENTORY_RESERVED");
    }

    @Test
    void shouldRequireCanonicalShipmentBeforeOrderShip() {
        claimNewOperation();
        when(orderMapper.selectForUpdate(1L, "order-1")).thenReturn(order("PAYMENT_CONFIRMED", 3L));
        when(itemMapper.selectByOrder(1L, "order-1")).thenReturn(List.of(
                item("item-1", "sku-1").setReservationId("res-1").setListingId("listing-1")
                        .setListingOfferId("offer-1")));
        when(orderMapper.transition(eq(1L), eq("order-1"), eq(3L), eq("PAYMENT_CONFIRMED"),
                eq("SHIPPED"), isNull(), eq("fulfillment-1"), eq("shipment-1"), isNull(),
                isNull(), isNull(), any()))
                .thenReturn(1);
        OrderCommand command = transitionCommand(OrderOperation.SHIP_WITH_FULFILLMENT, 3L);
        command.setFulfillmentId("fulfillment-1");
        command.setShipmentId("shipment-1");

        OrderCommandResult result = service.execute(command);

        assertThat(result.getCurrentStatus()).isEqualTo("SHIPPED");
        assertThat(result.getFulfillmentId()).isEqualTo("fulfillment-1");
        verify(fulfillmentApi).requireShipped("order-1", "fulfillment-1", "shipment-1");
    }

    private AtomicReference<String> claimNewOperation() {
        AtomicReference<String> attempt = new AtomicReference<>();
        when(operationMapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> { attempt.set(invocation.getArgument(4)); return 1; });
        when(operationMapper.selectForUpdate(11L, 1L)).thenAnswer(ignored -> new OrderOperationDO()
                .setOperationId(11L).setTenantId(1L).setAttemptToken(attempt.get()).setStatus(0));
        return attempt;
    }

    private static OrderCommand placeCommand() {
        return OrderCommand.builder().operation(OrderOperation.PLACE).idempotencyKey("order-run-1-place")
                .runId("order-run-1").buyerId("buyer-1")
                .items(List.of(new OrderLineCommand("sku-1", new BigDecimal("2"), 100L),
                        new OrderLineCommand("sku-2", BigDecimal.ONE, 300L)))
                .shippingAmountMinor(50L).discountAmountMinor(20L).currencyCode("CNY")
                .correlationId("6f9619ff-8b86-d011-b42d-00cf4fc964ff").occurredAt(Instant.parse("2026-07-12T00:00:00Z"))
                .build();
    }

    private static OrderCommand transitionCommand(OrderOperation operation, long version) {
        return OrderCommand.builder().operation(operation).idempotencyKey("order-run-1-" + operation.name())
                .runId("order-run-1").orderId("order-1").expectedVersion(version)
                .correlationId("6f9619ff-8b86-d011-b42d-00cf4fc964ff").occurredAt(Instant.parse("2026-07-12T00:00:01Z"))
                .build();
    }

    private static OrderHeaderDO order(String status, long version) {
        return new OrderHeaderDO().setOrderId("order-1").setTenantId(1L).setOrderNo("CMO1")
                .setRunId("order-run-1").setBuyerId("buyer-1").setStatus(status).setTotalQuantity(new BigDecimal("3"))
                .setProductAmountMinor(500L).setShippingAmountMinor(50L).setDiscountAmountMinor(20L)
                .setPayableAmountMinor(530L).setCurrencyCode("CNY").setVersion(version);
    }

    private static OrderItemDO item(String itemId, String skuId) {
        return new OrderItemDO().setOrderItemId(itemId).setTenantId(1L).setOrderId("order-1")
                .setCanonicalSkuId(skuId).setQuantity(BigDecimal.ONE).setUnitPriceMinor(100L).setLineAmountMinor(100L);
    }
}
