package cn.iocoder.yudao.module.cloudmold.warehouse.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseSourceReference;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.WarehouseSourceMappingDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WarehouseSourceMappingQueryServiceImplTest {

    private final WarehouseSourceMappingMapper mapper = mock(WarehouseSourceMappingMapper.class);
    private final CanonicalWarehouseMapper warehouseMapper = mock(CanonicalWarehouseMapper.class);
    private final WarehouseZoneMapper zoneMapper = mock(WarehouseZoneMapper.class);
    private final WarehouseLocationMapper locationMapper = mock(WarehouseLocationMapper.class);
    private final WarehouseSourceMappingQueryServiceImpl service =
            new WarehouseSourceMappingQueryServiceImpl(
                    mapper, warehouseMapper, zoneMapper, locationMapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void resolvesOneEffectiveSourceMappingByQualifiedIdentity() {
        when(mapper.selectEffective(eq(1L), eq("WMS"), eq("WAREHOUSE"), eq("10"), any()))
                .thenReturn(List.of(new WarehouseSourceMappingDO().setMappingId("mapping-1")
                        .setTenantId(1L).setSourceSystem("WMS").setSourceType("WAREHOUSE").setSourceId("10")
                        .setCanonicalType("WAREHOUSE").setCanonicalId("warehouse-1")
                        .setWarehouseId("warehouse-1").setStatus("ACTIVE")
                        .setVersion(1L)));

        var result = service.resolveActive(new WarehouseSourceReference("WMS", "WAREHOUSE", "10"),
                Instant.parse("2026-07-15T10:00:00Z"));

        assertThat(result.getWarehouseId()).isEqualTo("warehouse-1");
        assertThat(result.getCanonicalType()).isEqualTo("WAREHOUSE");
        assertThat(result.getCanonicalId()).isEqualTo("warehouse-1");
        assertThat(result.getSourceSystem()).isEqualTo("WMS");
    }

    @Test
    void normalizesQualifiedSourceIdentityAndKeepsTenantBoundary() {
        when(mapper.selectEffective(eq(1L), eq("ERP"), eq("WAREHOUSE"), eq("42"), any()))
                .thenReturn(List.of(new WarehouseSourceMappingDO().setMappingId("mapping-2")
                        .setTenantId(1L).setSourceSystem("ERP").setSourceType("WAREHOUSE").setSourceId("42")
                        .setCanonicalType("WAREHOUSE").setCanonicalId("warehouse-2")
                        .setWarehouseId("warehouse-2").setStatus("ACTIVE").setVersion(1L)));

        var result = service.resolveActive(new WarehouseSourceReference(" erp ", " warehouse ", " 42 "),
                Instant.parse("2026-07-15T10:00:00Z"));

        assertThat(result.getWarehouseId()).isEqualTo("warehouse-2");
        verify(mapper).selectEffective(eq(1L), eq("ERP"), eq("WAREHOUSE"), eq("42"), any());
    }

    @Test
    void failsClosedWhenEffectiveSourceMappingIsAmbiguous() {
        when(mapper.selectEffective(anyLong(), anyString(), anyString(), anyString(), any()))
                .thenReturn(List.of(new WarehouseSourceMappingDO(), new WarehouseSourceMappingDO()));

        assertThatThrownBy(() -> service.resolveActive(
                new WarehouseSourceReference("WMS", "WAREHOUSE", "10"), Instant.now()))
                .isInstanceOf(IllegalStateException.class).hasMessage("source mapping is ambiguous");
    }

    @Test
    void resolvesOneReadyWarehouseNetworkWithoutCreatingMasterData() {
        when(mapper.selectEffective(eq(1L), eq("ERP"), eq("WAREHOUSE"), eq("3"), any()))
                .thenReturn(List.of(new WarehouseSourceMappingDO().setMappingId("mapping-3")
                        .setSourceSystem("ERP").setSourceType("WAREHOUSE").setSourceId("3")
                        .setCanonicalType("WAREHOUSE").setCanonicalId("warehouse-3")
                        .setWarehouseId("warehouse-3").setVersion(1L)));
        when(warehouseMapper.selectCurrent(1L, "warehouse-3"))
                .thenReturn(new WarehouseDO().setWarehouseId("warehouse-3").setStatus("ACTIVE"));
        when(zoneMapper.selectActiveByWarehouse(1L, "warehouse-3"))
                .thenReturn(List.of(new WarehouseZoneDO().setZoneId("zone-3").setStatus("ACTIVE")));
        when(locationMapper.selectActiveByWarehouse(1L, "warehouse-3"))
                .thenReturn(List.of(new WarehouseLocationDO().setLocationId("location-3")
                        .setZoneId("zone-3").setStatus("ACTIVE")));

        var result = service.resolveReadyNetwork(
                new WarehouseSourceReference("ERP", "WAREHOUSE", "3"), Instant.now());

        assertThat(result.getWarehouseId()).isEqualTo("warehouse-3");
        assertThat(result.getZoneId()).isEqualTo("zone-3");
        assertThat(result.getLocationId()).isEqualTo("location-3");
    }
}
