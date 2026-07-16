package cn.iocoder.yudao.module.cloudmold.aftersale.service;

import cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.mysql.*;
import cn.iocoder.yudao.module.cloudmold.order.api.*;
import cn.iocoder.yudao.module.cloudmold.promotion.api.*;
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
    private final PromotionCommandApi promotionCommandApi = mock(PromotionCommandApi.class);
    private final AfterSaleEventService eventService = mock(AfterSaleEventService.class);
    private final AfterSaleBenefitReversalService service = new AfterSaleBenefitReversalService(
            reversalMapper, fundingMapper, orderQueryApi, promotionCommandApi, eventService);

    @Test
    void shouldRecordExactAllocationAndEveryFundingShare() {
        AfterSaleResolutionSagaDO saga = saga();
        when(reversalMapper.selectByAfterSale(1L, "after-sale-1")).thenReturn(List.of());
        when(orderQueryApi.requireEligible("order-1", "order-item-1", java.math.BigDecimal.ONE))
                .thenReturn(order(null));
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
    void shouldReturnPromotionEntitlementBeforeRecordingReversal() {
        when(reversalMapper.selectByAfterSale(1L, "after-sale-1")).thenReturn(List.of());
        when(orderQueryApi.requireEligible("order-1", "order-item-1", java.math.BigDecimal.ONE))
                .thenReturn(order("entitlement-1"));
        when(promotionCommandApi.execute(any())).thenReturn(PromotionCommandResult.builder()
                .aggregateId("entitlement-1").aggregateVersion(2L).status("RETURNED").build());
        when(reversalMapper.insert(any(AfterSaleBenefitReversalDO.class))).thenReturn(1);
        when(fundingMapper.insert(any(AfterSaleBenefitFundingReversalDO.class))).thenReturn(1);

        service.record(saga(), LocalDateTime.now());

        verify(promotionCommandApi).execute(argThat(command ->
                command.getOperation() == PromotionOperation.RETURN_COUPON_ENTITLEMENT
                        && command.getIdempotencyKey().equals(
                        "after-sale:after-sale-1:benefit:application-1:return-entitlement")
                        && command.getCouponEntitlement().getEntitlementId().equals("entitlement-1")
                        && command.getCouponEntitlement().getOrderRef().equals("run-1")
                        && command.getCouponEntitlement().getExpectedVersion() == 1L));
        verify(reversalMapper).insert(argThat((AfterSaleBenefitReversalDO row) ->
                "entitlement-1".equals(row.getEntitlementId())
                && "RETURNED".equals(row.getEntitlementEffectStatus())));
    }

    @Test
    void shouldRetainEntitlementUntilItsCumulativeBenefitIsFullyReversed() {
        AfterSaleResolutionSagaDO saga = saga().setGrossAmountMinor(10000L)
                .setBenefitAmountMinor(1000L).setNetAmountMinor(9000L);
        OrderAfterSaleView partial = order("entitlement-1");
        partial.setLineAmountMinor(10000L);
        partial.setDiscountAmountMinor(1000L);
        partial.setNetAmountMinor(9000L);
        OrderBenefitApplicationView application = partial.getBenefitApplications().get(0);
        application.setReturnAmountMinor(1000L);
        OrderBenefitAllocationView allocation = application.getAllocations().get(0);
        allocation.setReturnAmountMinor(1000L);
        allocation.getFunding().get(0).setReturnAmountMinor(500L);
        allocation.getFunding().get(1).setReturnAmountMinor(500L);
        when(reversalMapper.selectByAfterSale(1L, "after-sale-1")).thenReturn(List.of());
        when(reversalMapper.sumByApplication(1L, "order-1", "application-1")).thenReturn(0L);
        when(orderQueryApi.requireEligible("order-1", "order-item-1", java.math.BigDecimal.ONE))
                .thenReturn(partial);
        when(reversalMapper.insert(any(AfterSaleBenefitReversalDO.class))).thenReturn(1);
        when(fundingMapper.insert(any(AfterSaleBenefitFundingReversalDO.class))).thenReturn(1);

        AfterSaleBenefitReversalResult result = service.record(saga, LocalDateTime.now());

        assertThat(result.amountMinor()).isEqualTo(1000L);
        verifyNoInteractions(promotionCommandApi);
        verify(reversalMapper).insert(argThat((AfterSaleBenefitReversalDO row) ->
                "RETAINED_PARTIAL".equals(row.getEntitlementEffectStatus())
                        && row.getAmountMinor() == 1000L));
    }

    @Test
    void shouldReturnEveryDistinctStackedEntitlementAndConserveTheBatch() {
        when(reversalMapper.selectByAfterSale(1L, "after-sale-1")).thenReturn(List.of());
        when(orderQueryApi.requireEligible("order-1", "order-item-1", java.math.BigDecimal.ONE))
                .thenReturn(stackedEntitlementOrder());
        when(promotionCommandApi.execute(any())).thenAnswer(invocation -> {
            PromotionCommand command = invocation.getArgument(0);
            return PromotionCommandResult.builder()
                    .aggregateId(command.getCouponEntitlement().getEntitlementId())
                    .aggregateVersion(5L).status("RETURNED").build();
        });
        when(reversalMapper.insert(any(AfterSaleBenefitReversalDO.class))).thenReturn(1);
        when(fundingMapper.insert(any(AfterSaleBenefitFundingReversalDO.class))).thenReturn(1);

        AfterSaleBenefitReversalResult result = service.record(saga(), LocalDateTime.now());

        assertThat(result.reversalCount()).isEqualTo(2);
        assertThat(result.fundingReversalCount()).isEqualTo(2);
        assertThat(result.amountMinor()).isEqualTo(3800L);
        ArgumentCaptor<PromotionCommand> commandCaptor = ArgumentCaptor.forClass(PromotionCommand.class);
        verify(promotionCommandApi, times(2)).execute(commandCaptor.capture());
        assertThat(commandCaptor.getAllValues())
                .extracting(command -> command.getCouponEntitlement().getEntitlementId())
                .containsExactlyInAnyOrder("entitlement-platform", "entitlement-merchant");
        assertThat(commandCaptor.getAllValues())
                .extracting(command -> command.getCouponEntitlement().getExpectedVersion()).containsOnly(4L);
        ArgumentCaptor<AfterSaleBenefitReversalDO> reversalCaptor =
                ArgumentCaptor.forClass(AfterSaleBenefitReversalDO.class);
        verify(reversalMapper, times(2)).insert(reversalCaptor.capture());
        assertThat(reversalCaptor.getAllValues()).extracting(AfterSaleBenefitReversalDO::getAmountMinor)
                .containsExactlyInAnyOrder(2000L, 1800L);
        assertThat(reversalCaptor.getAllValues()).extracting(AfterSaleBenefitReversalDO::getReversalBatchId)
                .containsOnly(result.batchId());
    }

    @Test
    void shouldFailClosedWhenEntitlementSourceSnapshotIsNotExact() {
        when(reversalMapper.selectByAfterSale(1L, "after-sale-1")).thenReturn(List.of());
        OrderAfterSaleView order = order("entitlement-1");
        order.getBenefitApplications().get(0).setBenefitSourceId("template-1");
        when(orderQueryApi.requireEligible("order-1", "order-item-1", java.math.BigDecimal.ONE)).thenReturn(order);

        assertThatThrownBy(() -> service.record(saga(), LocalDateTime.now()))
                .hasMessage("entitlement benefit source snapshot is invalid");
        verifyNoInteractions(promotionCommandApi, fundingMapper, eventService);
        verify(reversalMapper, never()).insert(any(AfterSaleBenefitReversalDO.class));
    }

    @Test
    void shouldResolveCommittedEffectsWithoutWritingDuplicates() {
        AfterSaleBenefitReversalDO reversal = new AfterSaleBenefitReversalDO()
                .setReversalBatchId("batch-1").setAmountMinor(3800L)
                .setEntitlementEffectStatus("NOT_REQUIRED");
        when(reversalMapper.selectByAfterSale(1L, "after-sale-1")).thenReturn(List.of(reversal));
        when(fundingMapper.selectByAfterSale(1L, "after-sale-1")).thenReturn(List.of(
                new AfterSaleBenefitFundingReversalDO().setAmountMinor(2000L),
                new AfterSaleBenefitFundingReversalDO().setAmountMinor(1800L)));

        AfterSaleBenefitReversalResult result = service.record(saga(), LocalDateTime.now());

        assertThat(result.batchId()).isEqualTo("batch-1");
        assertThat(result.amountMinor()).isEqualTo(3800L);
        verifyNoInteractions(orderQueryApi, promotionCommandApi, eventService);
        verify(reversalMapper, never()).insert(any(AfterSaleBenefitReversalDO.class));
        verify(fundingMapper, never()).insert(any(AfterSaleBenefitFundingReversalDO.class));
    }

    private static AfterSaleResolutionSagaDO saga() {
        return new AfterSaleResolutionSagaDO().setTenantId(1L).setSagaId("saga-1")
                .setAfterSaleId("after-sale-1").setAfterSaleItemId("after-sale-item-1")
                .setRunId("run-1").setOrderId("order-1").setOrderItemId("order-item-1")
                .setQuantity(java.math.BigDecimal.ONE)
                .setGrossAmountMinor(39800L).setBenefitAmountMinor(3800L).setNetAmountMinor(36000L)
                .setBenefitReversalOccurredAt(LocalDateTime.of(2026, 7, 16, 11, 0))
                .setCorrelationId("70000000-0000-4000-8000-000000000001");
    }

    private static OrderAfterSaleView order(String entitlementId) {
        OrderBenefitFundingView platform = OrderBenefitFundingView.builder()
                .benefitFundingId("funding-platform").funderType("PLATFORM")
                .funderId("cloudmold-platform").amountMinor(2000L).returnAmountMinor(2000L)
                .currencyCode("CNY").build();
        OrderBenefitFundingView merchant = OrderBenefitFundingView.builder()
                .benefitFundingId("funding-merchant").funderType("MERCHANT")
                .funderId("merchant-1").amountMinor(1800L).returnAmountMinor(1800L)
                .currencyCode("CNY").build();
        OrderBenefitAllocationView allocation = OrderBenefitAllocationView.builder()
                .benefitAllocationId("allocation-1").orderItemId("order-item-1")
                .amountMinor(3800L).returnAmountMinor(3800L).currencyCode("CNY")
                .funding(List.of(platform, merchant)).build();
        OrderBenefitApplicationView application = OrderBenefitApplicationView.builder()
                .benefitApplicationId("application-1").benefitType("PROMOTION")
                .benefitSourceType(entitlementId == null ? "CONTROLLED_PROMOTION" : "COUPON_ENTITLEMENT")
                .benefitSourceId(entitlementId == null ? "promotion-1" : entitlementId)
                .benefitSourceVersion(1L).entitlementId(entitlementId).amountMinor(3800L)
                .returnAmountMinor(3800L)
                .currencyCode("CNY").allocations(List.of(allocation)).build();
        return OrderAfterSaleView.builder().orderId("order-1").orderItemId("order-item-1")
                .lineAmountMinor(39800L).discountAmountMinor(3800L).netAmountMinor(36000L)
                .benefitApplications(List.of(application)).build();
    }

    private static OrderAfterSaleView stackedEntitlementOrder() {
        OrderBenefitApplicationView platform = entitlementApplication(
                "application-platform", "allocation-platform", "funding-platform",
                "entitlement-platform", "PLATFORM", "cloudmold", 2000L);
        OrderBenefitApplicationView merchant = entitlementApplication(
                "application-merchant", "allocation-merchant", "funding-merchant",
                "entitlement-merchant", "MERCHANT", "merchant-1", 1800L);
        return OrderAfterSaleView.builder().orderId("order-1").orderItemId("order-item-1")
                .lineAmountMinor(39800L).discountAmountMinor(3800L).netAmountMinor(36000L)
                .benefitApplications(List.of(platform, merchant)).build();
    }

    private static OrderBenefitApplicationView entitlementApplication(
            String applicationId, String allocationId, String fundingId, String entitlementId,
            String funderType, String funderId, long amountMinor) {
        OrderBenefitFundingView funding = OrderBenefitFundingView.builder()
                .benefitFundingId(fundingId).funderType(funderType).funderId(funderId)
                .amountMinor(amountMinor).returnAmountMinor(amountMinor).currencyCode("CNY").build();
        OrderBenefitAllocationView allocation = OrderBenefitAllocationView.builder()
                .benefitAllocationId(allocationId).orderItemId("order-item-1")
                .amountMinor(amountMinor).returnAmountMinor(amountMinor).currencyCode("CNY")
                .funding(List.of(funding)).build();
        return OrderBenefitApplicationView.builder().benefitApplicationId(applicationId)
                .benefitType("COUPON").benefitSourceType("COUPON_ENTITLEMENT")
                .benefitSourceId(entitlementId).benefitSourceVersion(4L).entitlementId(entitlementId)
                .amountMinor(amountMinor).returnAmountMinor(amountMinor).currencyCode("CNY")
                .allocations(List.of(allocation)).build();
    }
}
