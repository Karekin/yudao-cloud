package cn.iocoder.yudao.module.cloudmold.aftersale.service;

import cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.mysql.*;
import cn.iocoder.yudao.module.cloudmold.order.api.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AfterSaleBenefitReversalServiceTest {
    private final AfterSaleBenefitReversalMapper reversalMapper = mock(AfterSaleBenefitReversalMapper.class);
    private final AfterSaleBenefitFundingReversalMapper fundingMapper =
            mock(AfterSaleBenefitFundingReversalMapper.class);
    private final OrderAfterSaleQueryApi orderQueryApi = mock(OrderAfterSaleQueryApi.class);
    private final AfterSaleEventService eventService = mock(AfterSaleEventService.class);
    private final AfterSaleBenefitReversalService service = new AfterSaleBenefitReversalService(
            reversalMapper, fundingMapper, orderQueryApi, eventService);

    @Test
    void shouldRecordExactAllocationAndEveryFundingShare() {
        AfterSaleResolutionSagaDO saga = saga();
        when(reversalMapper.selectByAfterSale(1L, "after-sale-1")).thenReturn(List.of());
        when(orderQueryApi.requireEligible("order-1", "order-item-1")).thenReturn(order(null));
        when(reversalMapper.insert(any(AfterSaleBenefitReversalDO.class))).thenReturn(1);
        when(fundingMapper.insert(any(AfterSaleBenefitFundingReversalDO.class))).thenReturn(1);

        AfterSaleBenefitReversalResult result = service.record(saga,
                LocalDateTime.of(2026, 7, 16, 12, 0));

        assertThat(result.reversalCount()).isEqualTo(1);
        assertThat(result.fundingReversalCount()).isEqualTo(2);
        assertThat(result.amountMinor()).isEqualTo(3800L);
        ArgumentCaptor<AfterSaleBenefitFundingReversalDO> captor =
                ArgumentCaptor.forClass(AfterSaleBenefitFundingReversalDO.class);
        verify(fundingMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(AfterSaleBenefitFundingReversalDO::getAmountMinor)
                .containsExactlyInAnyOrder(2000L, 1800L);
        assertThat(captor.getAllValues()).extracting(AfterSaleBenefitFundingReversalDO::getFunderType)
                .containsExactlyInAnyOrder("PLATFORM", "MERCHANT");
        verify(eventService).appendBenefitReversal(same(saga), any(), anyList(), any());
    }

    @Test
    void shouldFailClosedWhenPromotionEntitlementReturnIsRequired() {
        when(reversalMapper.selectByAfterSale(1L, "after-sale-1")).thenReturn(List.of());
        when(orderQueryApi.requireEligible("order-1", "order-item-1"))
                .thenReturn(order("entitlement-1"));

        assertThatThrownBy(() -> service.record(saga(), LocalDateTime.now()))
                .hasMessage("coupon entitlement reversal requires the Promotion return adapter");
        verify(reversalMapper, never()).insert(any(AfterSaleBenefitReversalDO.class));
        verifyNoInteractions(fundingMapper, eventService);
    }

    @Test
    void shouldResolveCommittedEffectsWithoutWritingDuplicates() {
        AfterSaleBenefitReversalDO reversal = new AfterSaleBenefitReversalDO()
                .setReversalBatchId("batch-1").setAmountMinor(3800L);
        when(reversalMapper.selectByAfterSale(1L, "after-sale-1")).thenReturn(List.of(reversal));
        when(fundingMapper.selectByAfterSale(1L, "after-sale-1")).thenReturn(List.of(
                new AfterSaleBenefitFundingReversalDO().setAmountMinor(2000L),
                new AfterSaleBenefitFundingReversalDO().setAmountMinor(1800L)));

        AfterSaleBenefitReversalResult result = service.record(saga(), LocalDateTime.now());

        assertThat(result.batchId()).isEqualTo("batch-1");
        assertThat(result.amountMinor()).isEqualTo(3800L);
        verifyNoInteractions(orderQueryApi, eventService);
        verify(reversalMapper, never()).insert(any(AfterSaleBenefitReversalDO.class));
        verify(fundingMapper, never()).insert(any(AfterSaleBenefitFundingReversalDO.class));
    }

    private static AfterSaleResolutionSagaDO saga() {
        return new AfterSaleResolutionSagaDO().setTenantId(1L).setSagaId("saga-1")
                .setAfterSaleId("after-sale-1").setAfterSaleItemId("after-sale-item-1")
                .setRunId("run-1").setOrderId("order-1").setOrderItemId("order-item-1")
                .setGrossAmountMinor(39800L).setBenefitAmountMinor(3800L).setNetAmountMinor(36000L)
                .setBenefitReversalOccurredAt(LocalDateTime.of(2026, 7, 16, 11, 0))
                .setCorrelationId("70000000-0000-4000-8000-000000000001");
    }

    private static OrderAfterSaleView order(String entitlementId) {
        OrderBenefitFundingView platform = OrderBenefitFundingView.builder()
                .benefitFundingId("funding-platform").funderType("PLATFORM")
                .funderId("cloudmold-platform").amountMinor(2000L).currencyCode("CNY").build();
        OrderBenefitFundingView merchant = OrderBenefitFundingView.builder()
                .benefitFundingId("funding-merchant").funderType("MERCHANT")
                .funderId("merchant-1").amountMinor(1800L).currencyCode("CNY").build();
        OrderBenefitAllocationView allocation = OrderBenefitAllocationView.builder()
                .benefitAllocationId("allocation-1").orderItemId("order-item-1")
                .amountMinor(3800L).currencyCode("CNY").funding(List.of(platform, merchant)).build();
        OrderBenefitApplicationView application = OrderBenefitApplicationView.builder()
                .benefitApplicationId("application-1").benefitType("PROMOTION")
                .benefitSourceType("CONTROLLED_PROMOTION").benefitSourceId("promotion-1")
                .benefitSourceVersion(1L).entitlementId(entitlementId).amountMinor(3800L)
                .currencyCode("CNY").allocations(List.of(allocation)).build();
        return OrderAfterSaleView.builder().orderId("order-1").orderItemId("order-item-1")
                .lineAmountMinor(39800L).discountAmountMinor(3800L).netAmountMinor(36000L)
                .benefitApplications(List.of(application)).build();
    }
}
