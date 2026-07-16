package cn.iocoder.yudao.module.cloudmold.order.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderAfterSaleSettlementCommand;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderAfterSaleSettlementResult;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderAfterSaleSettlementServiceImplTest {
    private final OrderHeaderMapper orderMapper = mock(OrderHeaderMapper.class);
    private final OrderItemMapper itemMapper = mock(OrderItemMapper.class);
    private final OrderReturnSettlementMapper settlementMapper = mock(OrderReturnSettlementMapper.class);
    private final OrderItemReturnSettlementMapper itemSettlementMapper =
            mock(OrderItemReturnSettlementMapper.class);
    private final OrderReturnEffectMapper effectMapper = mock(OrderReturnEffectMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final OrderAfterSaleSettlementServiceImpl service = new OrderAfterSaleSettlementServiceImpl(
            orderMapper, itemMapper, settlementMapper, itemSettlementMapper, effectMapper, outboxAppender);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(orderMapper.selectForUpdate(1L, "order-1")).thenReturn(order());
        when(itemMapper.selectByOrder(1L, "order-1")).thenReturn(List.of(item()));
        when(itemSettlementMapper.insert(any(OrderItemReturnSettlementDO.class))).thenReturn(1);
        when(itemSettlementMapper.updateById(any(OrderItemReturnSettlementDO.class))).thenReturn(1);
        when(settlementMapper.insert(any(OrderReturnSettlementDO.class))).thenReturn(1);
        when(settlementMapper.updateById(any(OrderReturnSettlementDO.class))).thenReturn(1);
        when(effectMapper.insert(any(OrderReturnEffectDO.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldRecordFirstPartialReturnWithoutTerminalizingOrder() {
        OrderItemReturnSettlementDO projected = itemSettlement(new BigDecimal("1"), 1000L, 33L, 967L, 1L);
        when(itemSettlementMapper.selectByOrder(1L, "order-1")).thenReturn(List.of(projected));

        OrderAfterSaleSettlementResult result = service.record(command(
                "after-sale-1", BigDecimal.ONE, 1000L, 33L, 967L));

        assertThat(result.getFullReturn()).isFalse();
        assertThat(result.getRemainingQuantity()).isEqualByComparingTo("2");
        assertThat(result.getOrderSettlementVersion()).isEqualTo(1L);
        assertThat(result.getItemSettlementVersion()).isEqualTo(1L);
        verify(settlementMapper).insert(argThat((OrderReturnSettlementDO value) -> "PARTIAL".equals(value.getStatus())
                && value.getReturnedQuantity().compareTo(BigDecimal.ONE) == 0
                && value.getRefundedNetAmountMinor() == 967L));
        ArgumentCaptor<AppendDomainEventCommand> event = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outboxAppender).append(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo("order.after_sale_settlement.recorded");
        assertThat(event.getValue().getPayload()).containsEntry("full_return", false)
                .containsEntry("settlement_status", "PARTIAL");
    }

    @Test
    void shouldAbsorbResidualAndMarkFullOnFinalPartialReturn() {
        OrderItemReturnSettlementDO existing = itemSettlement(new BigDecimal("2"), 2000L, 66L, 1934L, 2L);
        when(itemSettlementMapper.selectForUpdate(1L, "item-1")).thenReturn(existing);
        when(itemSettlementMapper.selectByOrder(1L, "order-1")).thenReturn(List.of(existing));
        when(settlementMapper.selectForUpdate(1L, "order-1")).thenReturn(new OrderReturnSettlementDO()
                .setOrderId("order-1").setTenantId(1L).setStatus("PARTIAL").setVersion(2L));

        OrderAfterSaleSettlementResult result = service.record(command(
                "after-sale-3", BigDecimal.ONE, 1000L, 34L, 966L));

        assertThat(result.getFullReturn()).isTrue();
        assertThat(result.getRemainingQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getOrderSettlementVersion()).isEqualTo(3L);
        assertThat(existing.getReturnedQuantity()).isEqualByComparingTo("3");
        assertThat(existing.getReversedBenefitAmountMinor()).isEqualTo(100L);
        assertThat(existing.getRefundedNetAmountMinor()).isEqualTo(2900L);
        verify(settlementMapper).updateById(argThat((OrderReturnSettlementDO value) -> "FULL".equals(value.getStatus())
                && value.getReturnedItemCount() == 1 && value.getRefundedNetAmountMinor() == 2900L));
    }

    @Test
    void shouldReplayOnlyTheExactImmutableEffect() {
        OrderReturnEffectDO effect = new OrderReturnEffectDO().setSettlementEffectId("effect-1")
                .setAfterSaleId("after-sale-1").setAfterSaleItemId("after-sale-item-1")
                .setOrderId("order-1").setOrderItemId("item-1").setQuantity(BigDecimal.ONE)
                .setGrossAmountMinor(1000L).setBenefitAmountMinor(33L).setNetAmountMinor(967L)
                .setInventoryOperationId(11L).setInventoryLedgerTransactionId(12L)
                .setPaymentRefundTransactionId(13L).setBenefitReversalBatchId("benefit-batch-1")
                .setOrderSettlementVersion(1L).setItemSettlementVersion(1L).setFullReturn(false);
        when(effectMapper.selectByAfterSale(1L, "after-sale-1")).thenReturn(effect);

        OrderAfterSaleSettlementResult replay = service.record(command(
                "after-sale-1", BigDecimal.ONE, 1000L, 33L, 967L));

        assertThat(replay.getDuplicate()).isTrue();
        assertThat(replay.getSettlementEffectId()).isEqualTo("effect-1");
        verify(itemSettlementMapper, never()).insert(any(OrderItemReturnSettlementDO.class));
        verify(outboxAppender, never()).append(any());

        assertThatThrownBy(() -> service.record(command(
                "after-sale-1", BigDecimal.ONE, 1000L, 32L, 968L)))
                .hasMessage("after-sale settlement replay conflicts with immutable first effect");
    }

    @Test
    void shouldKeepOrderPartialWhenOneOfTwoLinesIsFullyReturned() {
        OrderHeaderDO multiLineOrder = new OrderHeaderDO().setOrderId("order-1").setStatus("COMPLETED")
                .setVersion(7L).setTotalQuantity(new BigDecimal("2"))
                .setPayableAmountMinor(1900L).setDiscountAmountMinor(100L);
        OrderItemDO first = new OrderItemDO().setOrderItemId("item-1").setQuantity(BigDecimal.ONE)
                .setLineAmountMinor(1000L).setDiscountAmountMinor(100L).setNetAmountMinor(900L);
        OrderItemDO second = new OrderItemDO().setOrderItemId("item-2").setQuantity(BigDecimal.ONE)
                .setLineAmountMinor(1000L).setDiscountAmountMinor(0L).setNetAmountMinor(1000L);
        OrderItemReturnSettlementDO firstReturned = new OrderItemReturnSettlementDO()
                .setOrderItemId("item-1").setTenantId(1L).setOrderId("order-1")
                .setOrderedQuantity(BigDecimal.ONE).setReturnedQuantity(BigDecimal.ONE)
                .setReturnedGrossAmountMinor(1000L).setReversedBenefitAmountMinor(100L)
                .setRefundedNetAmountMinor(900L).setVersion(1L);
        when(orderMapper.selectForUpdate(1L, "order-1")).thenReturn(multiLineOrder);
        when(itemMapper.selectByOrder(1L, "order-1")).thenReturn(List.of(first, second));
        when(itemSettlementMapper.selectByOrder(1L, "order-1")).thenReturn(List.of(firstReturned));

        OrderAfterSaleSettlementResult result = service.record(multiLineCommand(
                "after-sale-line-1", "item-1", 1000L, 100L, 900L));

        assertThat(result.getFullReturn()).isFalse();
        verify(settlementMapper).insert(argThat((OrderReturnSettlementDO value) ->
                "PARTIAL".equals(value.getStatus()) && value.getReturnedItemCount() == 1
                        && value.getTotalItemCount() == 2));
    }

    @Test
    void shouldMarkFullOnlyWhenTheLastDifferentLineIsReturned() {
        OrderHeaderDO multiLineOrder = new OrderHeaderDO().setOrderId("order-1").setStatus("COMPLETED")
                .setVersion(7L).setTotalQuantity(new BigDecimal("2"))
                .setPayableAmountMinor(1900L).setDiscountAmountMinor(100L);
        OrderItemDO first = new OrderItemDO().setOrderItemId("item-1").setQuantity(BigDecimal.ONE)
                .setLineAmountMinor(1000L).setDiscountAmountMinor(100L).setNetAmountMinor(900L);
        OrderItemDO second = new OrderItemDO().setOrderItemId("item-2").setQuantity(BigDecimal.ONE)
                .setLineAmountMinor(1000L).setDiscountAmountMinor(0L).setNetAmountMinor(1000L);
        OrderItemReturnSettlementDO firstReturned = new OrderItemReturnSettlementDO()
                .setOrderItemId("item-1").setTenantId(1L).setOrderId("order-1")
                .setOrderedQuantity(BigDecimal.ONE).setReturnedQuantity(BigDecimal.ONE)
                .setReturnedGrossAmountMinor(1000L).setReversedBenefitAmountMinor(100L)
                .setRefundedNetAmountMinor(900L).setVersion(1L);
        OrderItemReturnSettlementDO secondReturned = new OrderItemReturnSettlementDO()
                .setOrderItemId("item-2").setTenantId(1L).setOrderId("order-1")
                .setOrderedQuantity(BigDecimal.ONE).setReturnedQuantity(BigDecimal.ONE)
                .setReturnedGrossAmountMinor(1000L).setReversedBenefitAmountMinor(0L)
                .setRefundedNetAmountMinor(1000L).setVersion(1L);
        when(orderMapper.selectForUpdate(1L, "order-1")).thenReturn(multiLineOrder);
        when(itemMapper.selectByOrder(1L, "order-1")).thenReturn(List.of(first, second));
        when(itemSettlementMapper.selectForUpdate(1L, "item-2")).thenReturn(null);
        when(itemSettlementMapper.selectByOrder(1L, "order-1"))
                .thenReturn(List.of(firstReturned, secondReturned));
        when(settlementMapper.selectForUpdate(1L, "order-1")).thenReturn(new OrderReturnSettlementDO()
                .setOrderId("order-1").setTenantId(1L).setStatus("PARTIAL").setVersion(1L));

        OrderAfterSaleSettlementResult result = service.record(multiLineCommand(
                "after-sale-line-2", "item-2", 1000L, 0L, 1000L));

        assertThat(result.getFullReturn()).isTrue();
        verify(settlementMapper).updateById(argThat((OrderReturnSettlementDO value) ->
                "FULL".equals(value.getStatus()) && value.getReturnedItemCount() == 2
                        && value.getRefundedNetAmountMinor() == 1900L));
    }

    private static OrderHeaderDO order() {
        return new OrderHeaderDO().setOrderId("order-1").setStatus("COMPLETED").setVersion(7L)
                .setTotalQuantity(new BigDecimal("3")).setPayableAmountMinor(2900L)
                .setDiscountAmountMinor(100L);
    }

    private static OrderItemDO item() {
        return new OrderItemDO().setOrderItemId("item-1").setQuantity(new BigDecimal("3"))
                .setLineAmountMinor(3000L).setDiscountAmountMinor(100L).setNetAmountMinor(2900L);
    }

    private static OrderItemReturnSettlementDO itemSettlement(BigDecimal quantity, long gross,
                                                               long benefit, long net, long version) {
        return new OrderItemReturnSettlementDO().setOrderItemId("item-1").setTenantId(1L)
                .setOrderId("order-1").setOrderedQuantity(new BigDecimal("3"))
                .setReturnedQuantity(quantity).setReturnedGrossAmountMinor(gross)
                .setReversedBenefitAmountMinor(benefit).setRefundedNetAmountMinor(net).setVersion(version);
    }

    private static OrderAfterSaleSettlementCommand command(String afterSaleId, BigDecimal quantity,
                                                            long gross, long benefit, long net) {
        return OrderAfterSaleSettlementCommand.builder().afterSaleId(afterSaleId)
                .afterSaleItemId("after-sale-item-1").runId("run-1").orderId("order-1")
                .orderItemId("item-1").quantity(quantity).grossAmountMinor(gross)
                .benefitAmountMinor(benefit).netAmountMinor(net).inventoryOperationId(11L)
                .inventoryLedgerTransactionId(12L).paymentRefundTransactionId(13L)
                .benefitReversalBatchId(benefit == 0 ? null : "benefit-batch-1")
                .correlationId("correlation-1").causationId("causation-1")
                .occurredAt(Instant.parse("2026-07-17T00:00:00Z")).build();
    }

    private static OrderAfterSaleSettlementCommand multiLineCommand(String afterSaleId, String orderItemId,
                                                                     long gross, long benefit, long net) {
        return OrderAfterSaleSettlementCommand.builder().afterSaleId(afterSaleId)
                .afterSaleItemId("after-sale-item-" + orderItemId).runId("run-multi-line")
                .orderId("order-1").orderItemId(orderItemId).quantity(BigDecimal.ONE)
                .grossAmountMinor(gross).benefitAmountMinor(benefit).netAmountMinor(net)
                .inventoryOperationId(11L).inventoryLedgerTransactionId(12L)
                .paymentRefundTransactionId(13L)
                .benefitReversalBatchId(benefit == 0 ? null : "benefit-batch-1")
                .correlationId("correlation-1").causationId("causation-1")
                .occurredAt(Instant.parse("2026-07-17T00:00:00Z")).build();
    }
}
