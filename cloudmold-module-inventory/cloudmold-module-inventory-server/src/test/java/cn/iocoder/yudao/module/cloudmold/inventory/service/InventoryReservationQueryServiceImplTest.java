package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryReservationView;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryReservationDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryCancellationReservationDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryReservationMapper;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryReservationQueryServiceImplTest {

    private final InventoryReservationMapper mapper = mock(InventoryReservationMapper.class);
    private final InventoryReservationQueryServiceImpl service = new InventoryReservationQueryServiceImpl(mapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldProveReleasedReservationBelongsToExactOrderItem() {
        when(mapper.selectHint(1L, "reservation-1")).thenReturn(reservation(30));

        InventoryReservationView result = service.requireReleased("reservation-1", "TRADE_ORDER",
                "order-1", "item-1");

        assertThat(result.getStatus()).isEqualTo("RELEASED");
        assertThat(result.getQuantity()).isEqualByComparingTo("2");
    }

    @Test
    void shouldRejectActiveReservation() {
        when(mapper.selectHint(1L, "reservation-1")).thenReturn(reservation(10));

        assertThatThrownBy(() -> service.requireReleased("reservation-1", "TRADE_ORDER", "order-1", "item-1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("inventory reservation is not released");
    }

    @Test
    void shouldReturnExactReleaseCommandSnapshotForCancellationSaga() {
        when(mapper.selectCancellationView(1L, "reservation-1"))
                .thenReturn(new InventoryCancellationReservationDO().setReservationId("reservation-1")
                        .setBusinessType("TRADE_ORDER").setBusinessId("order-1").setBusinessItemId("item-1")
                        .setQuantity(new BigDecimal("2.000000")).setStatus(10).setVersion(1L)
                        .setOwnerId("owner-1").setCanonicalSkuId("sku-1").setWarehouseId("warehouse-1")
                        .setStockStatus("SELLABLE").setQualityStatus("QUALIFIED").setUomCode("PIECE"));

        InventoryReservationView result = service.requireForCancellation("reservation-1", "TRADE_ORDER",
                "order-1", "item-1");

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getOwnerId()).isEqualTo("owner-1");
        assertThat(result.getWarehouseId()).isEqualTo("warehouse-1");
        assertThat(result.getUomCode()).isEqualTo("PIECE");
    }

    private static InventoryReservationDO reservation(int status) {
        return new InventoryReservationDO().setReservationId("reservation-1").setTenantId(1L)
                .setBusinessType("TRADE_ORDER").setBusinessId("order-1").setBusinessItemId("item-1")
                .setQuantity(new BigDecimal("2.000000")).setStatus(status).setVersion(2L);
    }
}
