package cn.iocoder.yudao.module.cloudmold.warehouse.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseReferenceValidationApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class WarehouseReferenceValidationServiceImpl implements WarehouseReferenceValidationApi {

    private final CanonicalWarehouseMapper warehouseMapper;
    private final WarehouseLocationMapper locationMapper;

    @Override
    public void requireActiveWarehouse(String warehouseId) {
        requireText(warehouseId, "warehouseId");
        WarehouseDO warehouse = warehouseMapper.selectCurrent(TenantContextHolder.getRequiredTenantId(), warehouseId);
        require(warehouse != null && "ACTIVE".equals(warehouse.getStatus()), "warehouse is not ACTIVE");
    }

    @Override
    public void requireActiveLocation(String warehouseId, String locationId) {
        requireActiveWarehouse(warehouseId);
        requireText(locationId, "locationId");
        WarehouseLocationDO location = locationMapper.selectCurrent(TenantContextHolder.getRequiredTenantId(), locationId);
        require(location != null && "ACTIVE".equals(location.getStatus()), "location is not ACTIVE");
        require(Objects.equals(warehouseId, location.getWarehouseId()), "location does not belong to warehouse");
        require(!"LEGACY_UNALLOCATED".equals(location.getLocationType()),
                "location is migration-only and not addressable");
    }

    private static void requireText(String value, String field) {
        require(value != null && !value.isBlank(), field + " is required");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
