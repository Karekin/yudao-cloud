package cn.iocoder.yudao.module.cloudmold.inventory.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.inventory.controller.admin.vo.InventoryV3BalancePageReqVO;
import cn.iocoder.yudao.module.cloudmold.inventory.controller.admin.vo.InventoryV3LedgerPageReqVO;
import cn.iocoder.yudao.module.cloudmold.inventory.controller.admin.vo.InventoryV3ReservationPageReqVO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryV3BalancePageMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryV3LedgerPageMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryV3ReservationPageMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryV3QueryServiceTest {

    private final InventoryV3BalancePageMapper balancePageMapper = mock(InventoryV3BalancePageMapper.class);
    private final InventoryV3ReservationPageMapper reservationPageMapper = mock(InventoryV3ReservationPageMapper.class);
    private final InventoryV3LedgerPageMapper ledgerPageMapper = mock(InventoryV3LedgerPageMapper.class);
    private final InventoryV3QueryService service = new InventoryV3QueryService(balancePageMapper,
            reservationPageMapper, ledgerPageMapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(9L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldNormalizeBalanceFiltersPropagateTenantAndComputeOffset() {
        InventoryV3BalancePageReqVO request = new InventoryV3BalancePageReqVO();
        request.setPageNo(3);
        request.setPageSize(15);
        request.setSkuCode("  SKU-1  ");
        request.setWarehouseCode("  WH-01 ");
        request.setLocationCode("   ");
        request.setLotCode(" LOT-01 ");
        request.setOwnerType(" merchant ");
        request.setOwnerId(" owner-1 ");
        request.setStockStatus(" sellable ");
        request.setQualityStatus(" qualified ");
        request.setOnlyNonZero(Boolean.TRUE);
        InventoryV3BalancePageItem item = new InventoryV3BalancePageItem();
        item.setBalanceId("balance-1");
        when(balancePageMapper.countBalancePage(9L, "SKU-1", "WH-01", null, "LOT-01", "MERCHANT", "owner-1",
                "SELLABLE", "QUALIFIED", Boolean.TRUE)).thenReturn(16L);
        when(balancePageMapper.selectBalancePage(9L, "SKU-1", "WH-01", null, "LOT-01", "MERCHANT", "owner-1",
                "SELLABLE", "QUALIFIED", Boolean.TRUE, 30L, 15)).thenReturn(List.of(item));

        PageResult<InventoryV3BalancePageItem> result = service.getBalancePage(request);

        assertThat(result.getTotal()).isEqualTo(16L);
        assertThat(result.getList()).containsExactly(item);
        verify(balancePageMapper).selectBalancePage(9L, "SKU-1", "WH-01", null, "LOT-01", "MERCHANT", "owner-1",
                "SELLABLE", "QUALIFIED", Boolean.TRUE, 30L, 15);
    }

    @Test
    void shouldShortCircuitBalancePageWhenNoRowsMatch() {
        InventoryV3BalancePageReqVO request = new InventoryV3BalancePageReqVO();
        when(balancePageMapper.countBalancePage(9L, null, null, null, null, null, null, null, null, null))
                .thenReturn(0L);

        PageResult<InventoryV3BalancePageItem> result = service.getBalancePage(request);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getList()).isEmpty();
        verify(balancePageMapper, never()).selectBalancePage(anyLong(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), anyLong(), anyInt());
    }

    @Test
    void shouldNormalizeReservationFiltersPropagateTenantAndComputeOffset() {
        InventoryV3ReservationPageReqVO request = new InventoryV3ReservationPageReqVO();
        request.setPageNo(2);
        request.setPageSize(20);
        request.setReservationId("  res-1 ");
        request.setBusinessType(" trade_order ");
        request.setBusinessId(" order-1 ");
        request.setBusinessItemId("   ");
        request.setStatus(10);
        request.setSkuCode(" SKU-1 ");
        request.setWarehouseCode(" WH-01 ");
        request.setLocationCode(" LOC-01 ");
        request.setLotCode("   ");
        InventoryV3ReservationPageItem item = new InventoryV3ReservationPageItem();
        item.setReservationId("res-1");
        when(reservationPageMapper.countReservationPage(9L, "res-1", "TRADE_ORDER", "order-1", null, 10,
                "SKU-1", "WH-01", "LOC-01", null)).thenReturn(21L);
        when(reservationPageMapper.selectReservationPage(9L, "res-1", "TRADE_ORDER", "order-1", null, 10,
                "SKU-1", "WH-01", "LOC-01", null, 20L, 20)).thenReturn(List.of(item));

        PageResult<InventoryV3ReservationPageItem> result = service.getReservationPage(request);

        assertThat(result.getTotal()).isEqualTo(21L);
        assertThat(result.getList()).containsExactly(item);
        verify(reservationPageMapper).selectReservationPage(9L, "res-1", "TRADE_ORDER", "order-1", null, 10,
                "SKU-1", "WH-01", "LOC-01", null, 20L, 20);
    }

    @Test
    void shouldShortCircuitReservationPageWhenNoRowsMatch() {
        InventoryV3ReservationPageReqVO request = new InventoryV3ReservationPageReqVO();
        when(reservationPageMapper.countReservationPage(9L, null, null, null, null, null, null, null, null, null))
                .thenReturn(0L);

        PageResult<InventoryV3ReservationPageItem> result = service.getReservationPage(request);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getList()).isEmpty();
        verify(reservationPageMapper, never()).selectReservationPage(anyLong(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), anyLong(), anyInt());
    }

    @Test
    void shouldNormalizeLedgerFiltersPropagateTenantAndComputeOffset() {
        InventoryV3LedgerPageReqVO request = new InventoryV3LedgerPageReqVO();
        request.setPageNo(4);
        request.setPageSize(10);
        request.setMovementGroupId(" mg-1 ");
        request.setCommandType(" reserve ");
        request.setBusinessType(" trade_order ");
        request.setBusinessId(" order-1 ");
        request.setBusinessItemId(" item-1 ");
        request.setBusinessNo(" SO-1 ");
        request.setSkuCode(" SKU-1 ");
        request.setWarehouseCode(" WH-01 ");
        request.setLocationCode(" LOC-01 ");
        request.setLotCode(" LOT-01 ");
        request.setEntryRole(" single ");
        request.setOccurredTimeFrom(LocalDateTime.of(2026, 7, 18, 0, 0));
        request.setOccurredTimeTo(LocalDateTime.of(2026, 7, 19, 0, 0));
        InventoryV3LedgerPageItem item = new InventoryV3LedgerPageItem();
        item.setLedgerEntryId(1L);
        when(ledgerPageMapper.countLedgerPage(9L, "mg-1", "RESERVE", "TRADE_ORDER", "order-1", "item-1",
                "SO-1", "SKU-1", "WH-01", "LOC-01", "LOT-01", "SINGLE",
                request.getOccurredTimeFrom(), request.getOccurredTimeTo())).thenReturn(31L);
        when(ledgerPageMapper.selectLedgerPage(9L, "mg-1", "RESERVE", "TRADE_ORDER", "order-1", "item-1",
                "SO-1", "SKU-1", "WH-01", "LOC-01", "LOT-01", "SINGLE",
                request.getOccurredTimeFrom(), request.getOccurredTimeTo(), 30L, 10)).thenReturn(List.of(item));

        PageResult<InventoryV3LedgerPageItem> result = service.getLedgerPage(request);

        assertThat(result.getTotal()).isEqualTo(31L);
        assertThat(result.getList()).containsExactly(item);
        verify(ledgerPageMapper).selectLedgerPage(9L, "mg-1", "RESERVE", "TRADE_ORDER", "order-1", "item-1",
                "SO-1", "SKU-1", "WH-01", "LOC-01", "LOT-01", "SINGLE",
                request.getOccurredTimeFrom(), request.getOccurredTimeTo(), 30L, 10);
    }

    @Test
    void shouldShortCircuitLedgerPageWhenNoRowsMatch() {
        InventoryV3LedgerPageReqVO request = new InventoryV3LedgerPageReqVO();
        when(ledgerPageMapper.countLedgerPage(9L, null, null, null, null, null, null, null, null, null, null,
                null, null, null)).thenReturn(0L);

        PageResult<InventoryV3LedgerPageItem> result = service.getLedgerPage(request);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getList()).isEmpty();
        verify(ledgerPageMapper, never()).selectLedgerPage(anyLong(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), anyLong(), anyInt());
    }
}
