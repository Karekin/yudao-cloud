package cn.iocoder.yudao.module.cloudmold.order.service.cancellation;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.inventory.api.*;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.*;
import cn.iocoder.yudao.module.cloudmold.order.api.*;
import cn.iocoder.yudao.module.cloudmold.order.api.cancellation.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.cancellation.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.cancellation.*;
import cn.iocoder.yudao.module.cloudmold.payment.api.PaymentCancellationQueryApi;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderCancellationSagaCommandServiceImplTest {
    private final OrderCancellationSagaOperationMapper operationMapper = mock(OrderCancellationSagaOperationMapper.class);
    private final OrderCancellationSagaMapper sagaMapper = mock(OrderCancellationSagaMapper.class);
    private final OrderCancellationSagaItemMapper sagaItemMapper = mock(OrderCancellationSagaItemMapper.class);
    private final OrderCancellationSagaFulfillmentMapper sagaFulfillmentMapper =
            mock(OrderCancellationSagaFulfillmentMapper.class);
    private final OrderHeaderMapper orderMapper = mock(OrderHeaderMapper.class);
    private final OrderItemMapper orderItemMapper = mock(OrderItemMapper.class);
    private final OrderCommandApi orderApi = mock(OrderCommandApi.class);
    private final InventoryReservationQueryApi reservationApi = mock(InventoryReservationQueryApi.class);
    private final PaymentCancellationQueryApi paymentQueryApi = mock(PaymentCancellationQueryApi.class);
    private final FulfillmentCancellationQueryApi fulfillmentQueryApi = mock(FulfillmentCancellationQueryApi.class);
    private final FulfillmentCommandApi fulfillmentCommandApi = mock(FulfillmentCommandApi.class);
    private final OrderCancellationSagaCheckpointService checkpoint = mock(OrderCancellationSagaCheckpointService.class);
    private final OrderCancellationSagaCommandServiceImpl service = new OrderCancellationSagaCommandServiceImpl(
            operationMapper, sagaMapper, sagaItemMapper, sagaFulfillmentMapper, orderMapper, orderItemMapper,
            orderApi, reservationApi, paymentQueryApi, fulfillmentQueryApi, fulfillmentCommandApi, checkpoint);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldAtomicallyCreateSagaAndFenceOrder() {
        AtomicReference<String> attempt = new AtomicReference<>();
        AtomicReference<OrderCancellationSagaDO> storedSaga = new AtomicReference<>();
        List<OrderCancellationSagaItemDO> storedItems = new ArrayList<>();
        when(operationMapper.insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> { attempt.set(invocation.getArgument(4)); return 1; });
        when(operationMapper.selectLastInsertId()).thenReturn(1L);
        when(operationMapper.selectForUpdate(1L, 1L)).thenAnswer(ignored ->
                new OrderCancellationSagaOperationDO().setOperationId(1L).setAttemptToken(attempt.get()).setStatus(0));
        when(operationMapper.markSucceeded(anyLong(), anyLong(), anyString(), anyString(), any())).thenReturn(1);
        when(orderMapper.selectForUpdate(1L, "70000000-0000-4000-8000-000000000010"))
                .thenReturn(new OrderHeaderDO().setOrderId("70000000-0000-4000-8000-000000000010")
                        .setTenantId(1L).setOrderNo("CMO1").setRunId("saga-run-1")
                        .setStatus("INVENTORY_RESERVED").setVersion(2L));
        when(orderItemMapper.selectByOrder(1L, "70000000-0000-4000-8000-000000000010"))
                .thenReturn(List.of(new OrderItemDO().setOrderItemId("70000000-0000-4000-8000-000000000011")
                        .setCanonicalSkuId("sku-1").setQuantity(new BigDecimal("2.000000"))
                        .setReservationId("70000000-0000-4000-8000-000000000012")));
        when(reservationApi.requireForCancellation(anyString(), eq("TRADE_ORDER"), anyString(), anyString()))
                .thenReturn(InventoryReservationView.builder()
                        .reservationId("70000000-0000-4000-8000-000000000012")
                        .businessType("TRADE_ORDER").businessId("70000000-0000-4000-8000-000000000010")
                        .businessItemId("70000000-0000-4000-8000-000000000011")
                        .quantity(new BigDecimal("2.000000")).status("ACTIVE").version(1L)
                        .ownerId("owner-1").canonicalSkuId("sku-1").warehouseId("warehouse-1")
                        .stockStatus("SELLABLE").qualityStatus("QUALIFIED").uomCode("PIECE").build());
        when(sagaMapper.insert(any(OrderCancellationSagaDO.class))).thenAnswer(invocation -> {
            storedSaga.set(invocation.getArgument(0)); return 1;
        });
        when(sagaItemMapper.insert(any(OrderCancellationSagaItemDO.class))).thenAnswer(invocation -> {
            storedItems.add(invocation.getArgument(0)); return 1;
        });
        when(sagaItemMapper.selectBySaga(eq(1L), anyString())).thenAnswer(ignored -> storedItems);
        when(orderApi.execute(any())).thenReturn(OrderCommandResult.builder()
                .currentStatus("CANCELLATION_PENDING").aggregateVersion(3L).build());

        OrderCancellationSagaView result = service.execute(OrderCancellationSagaCommand.builder()
                .operation(OrderCancellationSagaOperation.START).idempotencyKey("saga-run-1-start")
                .runId("saga-run-1").orderId("70000000-0000-4000-8000-000000000010")
                .reason("buyer cancelled before payment")
                .correlationId("70000000-0000-4000-8000-000000000001")
                .occurredAt(Instant.parse("2026-07-12T16:00:00Z")).build());

        assertThat(result.getStatus()).isEqualTo("REQUESTED");
        assertThat(result.getExpectedReservationCount()).isEqualTo(1);
        assertThat(storedSaga.get().getOrderStatusAtRequest()).isEqualTo("INVENTORY_RESERVED");
        assertThat(storedItems).singleElement().satisfies(item -> {
            assertThat(item.getStatus()).isEqualTo("PENDING");
            assertThat(item.getReleaseIdempotencyKey()).contains(result.getSagaId());
        });
        verify(orderApi).execute(argThat(command -> command.getOperation() == OrderOperation.REQUEST_CANCELLATION
                && command.getExpectedVersion() == 2L
                && command.getCancellationSagaId().equals(result.getSagaId())));
        verify(checkpoint).appendInitial(same(storedSaga.get()), any());
    }
}
