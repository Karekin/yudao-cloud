package cn.iocoder.yudao.module.cloudmold.aftersale.service;

import cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.mysql.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AfterSaleResolutionCheckpointServiceTest {

    @Test
    void shouldCompleteCaseReleaseActiveItemAndReachVersionContracts() {
        AfterSaleResolutionSagaMapper sagaMapper = mock(AfterSaleResolutionSagaMapper.class);
        AfterSaleCaseMapper caseMapper = mock(AfterSaleCaseMapper.class);
        AfterSaleItemMapper itemMapper = mock(AfterSaleItemMapper.class);
        AfterSaleEventService eventService = mock(AfterSaleEventService.class);
        AfterSaleResolutionCheckpointService service = new AfterSaleResolutionCheckpointService(
                sagaMapper, caseMapper, itemMapper, eventService);
        AfterSaleResolutionSagaDO saga = new AfterSaleResolutionSagaDO().setTenantId(1L).setSagaId("saga-1")
                .setAfterSaleId("after-sale-1").setLeaseOwner("worker-1").setStatus("ORDER_RETURNED")
                .setActiveStep("COMPLETE").setVersion(9L).setInventoryLedgerTransactionId(101L)
                .setPaymentRefundTransactionId(201L).setOrderRefundOperationId(301L).setOrderReturnOperationId(401L)
                .setOrderSettlementEffectId("settlement-effect-1").setOrderSettlementVersion(1L)
                .setOrderReturnFull(true)
                .setBenefitAmountMinor(0L).setBenefitReversalStatus("NOT_REQUIRED")
                .setBenefitReversalAmountMinor(0L);
        AfterSaleCaseDO sale = new AfterSaleCaseDO().setTenantId(1L).setAfterSaleId("after-sale-1")
                .setResolutionSagaId("saga-1").setStatus("RESOLUTION_PENDING").setRefundStatus("SUCCEEDED")
                .setVersion(3L);
        AfterSaleItemDO item = new AfterSaleItemDO().setTenantId(1L).setAfterSaleId("after-sale-1")
                .setAfterSaleItemId("after-sale-item-1").setQuantity(BigDecimal.ONE).setActiveGuard(1);
        when(sagaMapper.selectForUpdate(1L, "saga-1")).thenReturn(saga);
        when(caseMapper.selectForUpdate(1L, "after-sale-1")).thenReturn(sale);
        when(itemMapper.selectByAfterSale(1L, "after-sale-1")).thenReturn(List.of(item));
        when(caseMapper.complete(eq(1L), eq("after-sale-1"), eq(3L), any())).thenReturn(1);
        when(itemMapper.releaseActiveGuard(1L, "after-sale-1")).thenReturn(1);
        when(sagaMapper.updateById(saga)).thenReturn(1);
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 2, 0);

        service.markCompleted(1L, "saga-1", "worker-1", now);

        assertThat(sale.getStatus()).isEqualTo("COMPLETED");
        assertThat(sale.getVersion()).isEqualTo(4L);
        assertThat(saga.getStatus()).isEqualTo("COMPLETED");
        assertThat(saga.getVersion()).isEqualTo(10L);
        assertThat(saga.getActiveStep()).isEqualTo("NONE");
        verify(itemMapper).releaseActiveGuard(1L, "after-sale-1");
        verify(eventService).appendCase(isNull(), same(sale), same(item), eq("RESOLUTION_PENDING"), any(), eq(now));
        verify(eventService).appendSaga(same(saga), eq("ORDER_RETURNED"), eq(now));
    }
}
