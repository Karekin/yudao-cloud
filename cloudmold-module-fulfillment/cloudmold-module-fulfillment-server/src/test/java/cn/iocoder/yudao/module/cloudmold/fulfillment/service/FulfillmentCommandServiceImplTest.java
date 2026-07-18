package cn.iocoder.yudao.module.cloudmold.fulfillment.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.*;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.*;
import cn.iocoder.yudao.module.cloudmold.order.api.*;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FulfillmentCommandServiceImplTest {

    private final FulfillmentOperationMapper operationMapper = mock(FulfillmentOperationMapper.class);
    private final FulfillmentOrderMapper fulfillmentMapper = mock(FulfillmentOrderMapper.class);
    private final FulfillmentItemMapper itemMapper = mock(FulfillmentItemMapper.class);
    private final ShipmentMapper shipmentMapper = mock(ShipmentMapper.class);
    private final ShipmentItemMapper shipmentItemMapper = mock(ShipmentItemMapper.class);
    private final TrackingEventMapper trackingEventMapper = mock(TrackingEventMapper.class);
    private final FulfillmentStatusHistoryMapper historyMapper = mock(FulfillmentStatusHistoryMapper.class);
    private final OrderQueryApi orderQueryApi = mock(OrderQueryApi.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final FulfillmentCommandServiceImpl service = new FulfillmentCommandServiceImpl(operationMapper,
            fulfillmentMapper, itemMapper, shipmentMapper, shipmentItemMapper, trackingEventMapper, historyMapper,
            orderQueryApi, outboxAppender);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(operationMapper.selectLastInsertId()).thenReturn(21L);
        when(operationMapper.markSucceeded(anyLong(), anyLong(), anyString(), anyString(), any())).thenReturn(1);
        when(fulfillmentMapper.insert(any(FulfillmentOrderDO.class))).thenReturn(1);
        when(itemMapper.insert(any(FulfillmentItemDO.class))).thenReturn(1);
        when(shipmentMapper.insert(any(ShipmentDO.class))).thenReturn(1);
        when(shipmentItemMapper.insert(any(ShipmentItemDO.class))).thenReturn(1);
        when(trackingEventMapper.insert(any(TrackingEventDO.class))).thenReturn(1);
        when(historyMapper.insert(any(FulfillmentStatusHistoryDO.class))).thenReturn(1);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event-1", "a".repeat(64), false));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldCreateCompleteSellerWarehouseSuborder() {
        claimNewOperation();
        when(orderQueryApi.requireFulfillableOrder("order-1")).thenReturn(orderView());

        FulfillmentCommandResult result = service.execute(createCommand());

        assertThat(result.getCurrentStatus()).isEqualTo("CREATED");
        assertThat(result.getAggregateVersion()).isEqualTo(1L);
        assertThat(result.getItems()).hasSize(2);
        verify(itemMapper, times(2)).insert(any(FulfillmentItemDO.class));
        verify(outboxAppender).append(argThat(event -> event.getEventType().equals("fulfillment.status.changed")
                && event.getAggregateVersion() == 1L
                && event.getSchemaVersion() == 3
                && event.getPayload().get("current_status").equals("CREATED")
                && event.getPayload().get("delivery_promise_version_ref").equals("PROMISE_V1")
                && ((List<java.util.Map<String, Object>>) event.getPayload().get("items")).stream()
                        .map(item -> item.get("variable_fulfillment_cost_minor"))
                        .toList().equals(List.of(12L, 18L))
                && ((List<?>) event.getPayload().get("items")).size() == 2));
    }

    @Test
    void shouldRejectIncompleteDeliveryPromiseSnapshot() {
        claimNewOperation();
        when(orderQueryApi.requireFulfillableOrder("order-1")).thenReturn(orderView());
        FulfillmentCommand command = createCommand();
        command.setPromisedDeliveryAt(null);

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("delivery promise version and promisedDeliveryAt must be provided together");
        verify(fulfillmentMapper, never()).insert(any(FulfillmentOrderDO.class));
    }

    @Test
    void shouldRejectItemSnapshotThatDoesNotMatchOrder() {
        claimNewOperation();
        when(orderQueryApi.requireFulfillableOrder("order-1")).thenReturn(orderView());
        FulfillmentCommand command = createCommand();
        command.getItems().get(0).setQuantity(new BigDecimal("2"));

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("first slice requires full order-item quantity fulfillment");
        verify(fulfillmentMapper, never()).insert(any(FulfillmentOrderDO.class));
    }

    @Test
    void shouldCreateShipmentAndImmutableTrackingMilestone() {
        claimNewOperation();
        when(fulfillmentMapper.selectForUpdate(1L, "fulfillment-1"))
                .thenReturn(fulfillment("CREATED", 1L));
        when(itemMapper.selectByFulfillment(1L, "fulfillment-1")).thenReturn(items());
        when(shipmentMapper.selectByFulfillment(1L, "fulfillment-1")).thenReturn(null);
        when(fulfillmentMapper.transition(eq(1L), eq("fulfillment-1"), eq(1L), eq("CREATED"),
                eq("SHIPPED"), any()))
                .thenReturn(1);

        FulfillmentCommandResult result = service.execute(transition(FulfillmentOperation.SHIP, 1L)
                .setCarrierCode("INTERNAL_TEST").setWaybillNo("WB-001"));

        assertThat(result.getCurrentStatus()).isEqualTo("SHIPPED");
        assertThat(result.getShipmentId()).isNotBlank();
        assertThat(result.getCarrierCode()).isEqualTo("INTERNAL_TEST");
        verify(shipmentItemMapper, times(2)).insert(any(ShipmentItemDO.class));
        verify(trackingEventMapper).insert(argThat((TrackingEventDO event) ->
                event.getTrackingStatus().equals("SHIPPED")));
    }

    @Test
    void shouldReturnImmutableFirstResultOnReplayAfterOrderMovedOn() {
        AtomicReference<String> requestHash = new AtomicReference<>();
        when(operationMapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> { requestHash.set(invocation.getArgument(3)); return 0; });
        FulfillmentCommandResult first = FulfillmentCommandResult.builder().operationId(21L)
                .fulfillmentId("fulfillment-1").fulfillmentNo("CMF1").currentStatus("CREATED")
                .aggregateVersion(1L).duplicate(false).build();
        when(operationMapper.selectForUpdate(21L, 1L)).thenAnswer(ignored -> new FulfillmentOperationDO()
                .setOperationId(21L).setTenantId(1L).setAttemptToken("existing")
                .setRequestHash(requestHash.get()).setStatus(10).setResultJson(JsonUtils.toJsonString(first)));

        FulfillmentCommandResult replay = service.execute(createCommand());

        assertThat(replay.getDuplicate()).isTrue();
        assertThat(replay.getFulfillmentId()).isEqualTo("fulfillment-1");
        verify(orderQueryApi, never()).requireFulfillableOrder(anyString());
    }

    @Test
    void shouldIncludeDeliveryPromiseSnapshotInIdempotencyFingerprint() {
        claimNewOperation();
        when(orderQueryApi.requireFulfillableOrder("order-1")).thenReturn(orderView());

        service.execute(createCommand());
        FulfillmentCommand changedPromise = createCommand();
        changedPromise.setPromisedDeliveryAt(Instant.parse("2026-07-12T13:00:00Z"));
        service.execute(changedPromise);

        org.mockito.ArgumentCaptor<String> hashes = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(operationMapper, times(2)).insertOrResolve(anyLong(), anyString(), anyString(),
                hashes.capture(), anyString(), any());
        assertThat(hashes.getAllValues()).hasSize(2).doesNotHaveDuplicates();
    }

    private AtomicReference<String> claimNewOperation() {
        AtomicReference<String> attempt = new AtomicReference<>();
        when(operationMapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> { attempt.set(invocation.getArgument(4)); return 1; });
        when(operationMapper.selectForUpdate(21L, 1L)).thenAnswer(ignored -> new FulfillmentOperationDO()
                .setOperationId(21L).setTenantId(1L).setAttemptToken(attempt.get()).setStatus(0));
        return attempt;
    }

    private static FulfillmentCommand createCommand() {
        return FulfillmentCommand.builder().operation(FulfillmentOperation.CREATE)
                .idempotencyKey("fulfillment-run-1-create").runId("fulfillment-run-1").orderId("order-1")
                .sellerId("INTERNAL_COMPANY").warehouseId("WH-DEMO")
                .deliveryPromiseVersionRef("PROMISE_V1")
                .promisedDeliveryAt(Instant.parse("2026-07-12T12:00:00Z"))
                .items(List.of(new FulfillmentLineCommand("item-1", "sku-1", BigDecimal.ONE, "res-1", 12L),
                        new FulfillmentLineCommand("item-2", "sku-2", BigDecimal.ONE, "res-2", 18L)))
                .correlationId("6f9619ff-8b86-d011-b42d-00cf4fc964ff")
                .occurredAt(Instant.parse("2026-07-12T00:00:00Z")).build();
    }

    private static FulfillmentCommand transition(FulfillmentOperation operation, long version) {
        return FulfillmentCommand.builder().operation(operation)
                .idempotencyKey("fulfillment-run-1-" + operation.name()).runId("fulfillment-run-1")
                .fulfillmentId("fulfillment-1").expectedVersion(version)
                .correlationId("6f9619ff-8b86-d011-b42d-00cf4fc964ff")
                .occurredAt(Instant.parse("2026-07-12T00:00:01Z")).build();
    }

    private static OrderFulfillmentView orderView() {
        return OrderFulfillmentView.builder().orderId("order-1").orderNo("CMO1")
                .status("PAYMENT_CONFIRMED").aggregateVersion(3L)
                .items(List.of(orderItem("item-1", "sku-1", "res-1"),
                        orderItem("item-2", "sku-2", "res-2"))).build();
    }

    private static OrderLineView orderItem(String itemId, String skuId, String reservationId) {
        return OrderLineView.builder().orderItemId(itemId).canonicalSkuId(skuId).quantity(BigDecimal.ONE)
                .unitPriceMinor(100L).lineAmountMinor(100L).reservationId(reservationId).build();
    }

    private static FulfillmentOrderDO fulfillment(String status, long version) {
        return new FulfillmentOrderDO().setFulfillmentId("fulfillment-1").setTenantId(1L)
                .setFulfillmentNo("CMF1").setRunId("fulfillment-run-1").setOrderId("order-1")
                .setOrderNo("CMO1").setSellerId("INTERNAL_COMPANY").setWarehouseId("WH-DEMO")
                .setDeliveryPromiseVersionRef("PROMISE_V1")
                .setPromisedDeliveryAt(java.time.LocalDateTime.parse("2026-07-12T12:00:00"))
                .setPromiseFrozenAt(java.time.LocalDateTime.parse("2026-07-12T00:00:00"))
                .setStatus(status).setVersion(version);
    }

    private static List<FulfillmentItemDO> items() {
        return List.of(new FulfillmentItemDO().setFulfillmentItemId("fi-1").setOrderItemId("item-1")
                        .setCanonicalSkuId("sku-1").setQuantity(BigDecimal.ONE).setReservationId("res-1")
                        .setVariableFulfillmentCostMinor(12L),
                new FulfillmentItemDO().setFulfillmentItemId("fi-2").setOrderItemId("item-2")
                        .setCanonicalSkuId("sku-2").setQuantity(BigDecimal.ONE).setReservationId("res-2")
                        .setVariableFulfillmentCostMinor(18L));
    }
}
