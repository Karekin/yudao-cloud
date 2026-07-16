package cn.iocoder.yudao.module.cloudmold.order.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderAfterSaleView;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.*;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderAfterSaleQueryServiceImplTest {
    private final OrderHeaderMapper orderMapper = mock(OrderHeaderMapper.class);
    private final OrderItemMapper itemMapper = mock(OrderItemMapper.class);
    private final OrderBenefitApplicationMapper applicationMapper = mock(OrderBenefitApplicationMapper.class);
    private final OrderBenefitAllocationMapper allocationMapper = mock(OrderBenefitAllocationMapper.class);
    private final OrderBenefitFundingMapper fundingMapper = mock(OrderBenefitFundingMapper.class);
    private final OrderItemReturnSettlementMapper itemReturnSettlementMapper =
            mock(OrderItemReturnSettlementMapper.class);
    private final OrderAfterSaleQueryServiceImpl service = new OrderAfterSaleQueryServiceImpl(
            orderMapper, itemMapper, applicationMapper, allocationMapper, fundingMapper,
            itemReturnSettlementMapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldExposeExactGrossBenefitNetAndImmutableBenefitGraph() {
        when(orderMapper.selectForUpdate(1L, "order-1")).thenReturn(new OrderHeaderDO()
                .setOrderId("order-1").setOrderNo("CMO1").setBuyerId("buyer-1").setStatus("COMPLETED")
                .setVersion(5L).setPayableAmountMinor(36000L).setCurrencyCode("CNY")
                .setShippingAmountMinor(0L)
                .setPaymentId("payment-1").setFulfillmentId("fulfillment-1").setShipmentId("shipment-1"));
        when(itemMapper.selectByOrder(1L, "order-1")).thenReturn(List.of(new OrderItemDO()
                .setOrderItemId("item-1").setCanonicalSkuId("sku-1").setQuantity(BigDecimal.ONE)
                .setUnitPriceMinor(39800L)
                .setLineAmountMinor(39800L).setDiscountAmountMinor(3800L).setNetAmountMinor(36000L)
                .setReservationId("reservation-1").setListingId("listing-1").setListingOfferId("offer-1")));
        when(applicationMapper.selectByOrder(1L, "order-1")).thenReturn(List.of(
                new OrderBenefitApplicationDO().setBenefitApplicationId("application-1")
                        .setApplicationKey("application-key").setBenefitType("PROMOTION")
                        .setBenefitSourceType("CONTROLLED_PROMOTION").setBenefitSourceId("promotion-1")
                        .setBenefitSourceVersion(1L).setAmountMinor(3800L).setCurrencyCode("CNY")
                        .setCalculationDigest("a".repeat(64)).setVersion(1L)));
        when(allocationMapper.selectByOrder(1L, "order-1")).thenReturn(List.of(
                new OrderBenefitAllocationDO().setBenefitAllocationId("allocation-1")
                        .setBenefitApplicationId("application-1").setOrderItemId("item-1")
                        .setLineKey("line-1").setAmountMinor(3800L).setCurrencyCode("CNY")));
        when(fundingMapper.selectByOrder(1L, "order-1")).thenReturn(List.of(
                new OrderBenefitFundingDO().setBenefitFundingId("funding-1")
                        .setBenefitAllocationId("allocation-1").setFunderType("PLATFORM")
                        .setFunderId("platform-1").setAmountMinor(3800L).setCurrencyCode("CNY")));

        OrderAfterSaleView result = service.requireEligible("order-1", "item-1");

        assertThat(result.getLineAmountMinor()).isEqualTo(39800L);
        assertThat(result.getDiscountAmountMinor()).isEqualTo(3800L);
        assertThat(result.getNetAmountMinor()).isEqualTo(36000L);
        assertThat(result.getBenefitApplications()).hasSize(1);
        assertThat(result.getBenefitApplications().get(0).getAllocations().get(0).getFunding()).hasSize(1);
    }

    @Test
    void shouldProrateCumulativeBenefitAndPreserveFinalResidual() {
        when(orderMapper.selectForUpdate(1L, "order-1")).thenReturn(new OrderHeaderDO()
                .setOrderId("order-1").setStatus("COMPLETED").setPaymentId("payment-1")
                .setFulfillmentId("fulfillment-1").setShipmentId("shipment-1")
                .setCurrencyCode("CNY").setShippingAmountMinor(0L).setPayableAmountMinor(2900L));
        when(itemMapper.selectByOrder(1L, "order-1")).thenReturn(List.of(new OrderItemDO()
                .setOrderItemId("item-1").setCanonicalSkuId("sku-1").setQuantity(new BigDecimal("3"))
                .setUnitPriceMinor(1000L).setLineAmountMinor(3000L)
                .setDiscountAmountMinor(100L).setNetAmountMinor(2900L)
                .setReservationId("reservation-1").setListingId("listing-1")
                .setListingOfferId("offer-1")));
        when(itemReturnSettlementMapper.selectTenant(1L, "item-1")).thenReturn(
                new OrderItemReturnSettlementDO().setOrderItemId("item-1")
                        .setReturnedQuantity(BigDecimal.ONE));
        when(applicationMapper.selectByOrder(1L, "order-1")).thenReturn(List.of(
                new OrderBenefitApplicationDO().setBenefitApplicationId("application-1")
                        .setBenefitType("PROMOTION").setBenefitSourceType("CONTROLLED_PROMOTION")
                        .setBenefitSourceId("promotion-1").setBenefitSourceVersion(1L)
                        .setAmountMinor(100L).setCurrencyCode("CNY").setVersion(1L)));
        when(allocationMapper.selectByOrder(1L, "order-1")).thenReturn(List.of(
                new OrderBenefitAllocationDO().setBenefitAllocationId("allocation-1")
                        .setBenefitApplicationId("application-1").setOrderItemId("item-1")
                        .setAmountMinor(100L).setCurrencyCode("CNY")));
        when(fundingMapper.selectByOrder(1L, "order-1")).thenReturn(List.of(
                new OrderBenefitFundingDO().setBenefitFundingId("funding-1")
                        .setBenefitAllocationId("allocation-1").setFundingKey("funding-1")
                        .setFunderType("PLATFORM").setFunderId("platform-1")
                        .setAmountMinor(100L).setCurrencyCode("CNY")));

        OrderAfterSaleView result = service.requireEligible("order-1", "item-1", BigDecimal.ONE);

        assertThat(result.getPreviouslyReturnedQuantity()).isEqualByComparingTo("1");
        assertThat(result.getRemainingReturnableQuantity()).isEqualByComparingTo("2");
        assertThat(result.getLineAmountMinor()).isEqualTo(1000L);
        assertThat(result.getDiscountAmountMinor()).isEqualTo(33L);
        assertThat(result.getNetAmountMinor()).isEqualTo(967L);
        assertThat(result.getBenefitApplications().get(0).getReturnAmountMinor()).isEqualTo(33L);
        assertThat(OrderAfterSaleQueryServiceImpl.proportionalDelta(100L,
                new BigDecimal("2"), BigDecimal.ONE, new BigDecimal("3"))).isEqualTo(34L);
    }

    @Test
    void shouldRejectQuantityBeyondRemainingReturnableQuantity() {
        when(orderMapper.selectForUpdate(1L, "order-1")).thenReturn(new OrderHeaderDO()
                .setOrderId("order-1").setStatus("COMPLETED").setPaymentId("payment-1")
                .setFulfillmentId("fulfillment-1").setShipmentId("shipment-1")
                .setCurrencyCode("CNY").setShippingAmountMinor(0L));
        when(itemMapper.selectByOrder(1L, "order-1")).thenReturn(List.of(new OrderItemDO()
                .setOrderItemId("item-1").setQuantity(new BigDecimal("2"))
                .setUnitPriceMinor(1000L).setLineAmountMinor(2000L)
                .setDiscountAmountMinor(0L).setNetAmountMinor(2000L)));
        when(itemReturnSettlementMapper.selectTenant(1L, "item-1")).thenReturn(
                new OrderItemReturnSettlementDO().setReturnedQuantity(BigDecimal.ONE));

        assertThatThrownBy(() -> service.requireEligible("order-1", "item-1", new BigDecimal("2")))
                .hasMessage("requestedQuantity exceeds remaining returnable quantity");
    }
}
