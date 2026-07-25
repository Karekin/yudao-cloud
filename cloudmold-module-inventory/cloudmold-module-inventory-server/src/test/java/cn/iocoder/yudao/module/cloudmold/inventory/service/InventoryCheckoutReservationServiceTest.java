package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryCheckoutReservationCommand;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3Command;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3CommandApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3CommandResult;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3BalanceDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryV3BalanceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryCheckoutReservationServiceTest {

    private final InventoryV3BalanceMapper balanceMapper = mock(InventoryV3BalanceMapper.class);
    private final InventoryV3CommandApi commandApi = mock(InventoryV3CommandApi.class);
    private final InventoryCheckoutReservationService service =
            new InventoryCheckoutReservationService(balanceMapper, commandApi);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(balanceMapper.selectReservationCandidate(eq(1L), eq("sku-1"), eq(BigDecimal.ONE), any()))
                .thenReturn(new InventoryV3BalanceDO()
                        .setOwnerType("MERCHANT").setOwnerId("owner-1").setCanonicalSkuId("sku-1")
                        .setWarehouseId("warehouse-1").setLocationId("location-1")
                        .setStockStatus("SELLABLE").setQualityStatus("QUALIFIED").setBaseUomCode("PIECE"));
        when(commandApi.execute(any())).thenReturn(InventoryV3CommandResult.builder()
                .reservationId("reservation-1").allocationId("allocation-1").aggregateVersion(2L).build());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldMapMaximumLengthIdempotencyKeyToStableUuidSourceEventId() {
        String idempotencyKey = "x".repeat(128);
        InventoryCheckoutReservationCommand request = InventoryCheckoutReservationCommand.builder()
                .idempotencyKey(idempotencyKey).canonicalSkuId("sku-1").quantity(BigDecimal.ONE)
                .orderId("order-1").orderItemId("item-1").orderNo("CMO-1")
                .correlationId("correlation-1").causationId("causation-1")
                .occurredAt(Instant.parse("2026-07-25T00:00:00Z")).build();

        service.reserve(request);
        service.reserve(request);

        String expectedSourceEventId = InventoryCheckoutReservationService.sourceEventId(idempotencyKey);
        assertThat(expectedSourceEventId).hasSize(36);
        verify(commandApi, times(2)).execute(argThat((InventoryV3Command command) ->
                idempotencyKey.equals(command.getIdempotencyKey())
                        && expectedSourceEventId.equals(command.getSourceEventId())));
    }
}
