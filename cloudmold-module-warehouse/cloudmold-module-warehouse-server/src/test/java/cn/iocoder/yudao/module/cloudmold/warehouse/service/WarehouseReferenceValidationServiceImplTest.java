package cn.iocoder.yudao.module.cloudmold.warehouse.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.WarehouseDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.WarehouseLocationDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.WarehouseLocationMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.CanonicalWarehouseMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class WarehouseReferenceValidationServiceImplTest {

    private final CanonicalWarehouseMapper warehouseMapper = mock(CanonicalWarehouseMapper.class);
    private final WarehouseLocationMapper locationMapper = mock(WarehouseLocationMapper.class);
    private final WarehouseReferenceValidationServiceImpl service =
            new WarehouseReferenceValidationServiceImpl(warehouseMapper, locationMapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void rejectsLocationThatIsNotActiveAndAddressable() {
        when(warehouseMapper.selectCurrent(1L, "warehouse-1")).thenReturn(new WarehouseDO()
                .setWarehouseId("warehouse-1").setTenantId(1L).setStatus("ACTIVE"));
        when(locationMapper.selectCurrent(1L, "location-1")).thenReturn(new WarehouseLocationDO()
                .setLocationId("location-1").setTenantId(1L).setWarehouseId("warehouse-1")
                .setLocationType("LEGACY_UNALLOCATED").setStatus("ACTIVE"));

        assertThatThrownBy(() -> service.requireActiveLocation("warehouse-1", "location-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("location is migration-only and not addressable");
    }
}
