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
    private final OrderAfterSaleQueryServiceImpl service = new OrderAfterSaleQueryServiceImpl(
            orderMapper, itemMapper, applicationMapper, allocationMapper, fundingMapper);

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
                .setPaymentId("payment-1").setFulfillmentId("fulfillment-1").setShipmentId("shipment-1"));
        when(itemMapper.selectByOrder(1L, "order-1")).thenReturn(List.of(new OrderItemDO()
                .setOrderItemId("item-1").setCanonicalSkuId("sku-1").setQuantity(BigDecimal.ONE)
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
    void shouldRejectGrossAmountAsCashRefundBasisForDiscountedOrder() {
        when(orderMapper.selectForUpdate(1L, "order-1")).thenReturn(new OrderHeaderDO()
                .setOrderId("order-1").setStatus("COMPLETED").setPaymentId("payment-1")
                .setFulfillmentId("fulfillment-1").setShipmentId("shipment-1")
                .setCurrencyCode("CNY").setPayableAmountMinor(39800L));
        when(itemMapper.selectByOrder(1L, "order-1")).thenReturn(List.of(new OrderItemDO()
                .setOrderItemId("item-1").setQuantity(BigDecimal.ONE).setLineAmountMinor(39800L)
                .setDiscountAmountMinor(3800L).setNetAmountMinor(36000L)));

        assertThatThrownBy(() -> service.requireEligible("order-1", "item-1"))
                .hasMessage("after-sale first slice requires full order net amount on one line");
    }
}
