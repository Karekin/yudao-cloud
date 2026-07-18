package cn.iocoder.yudao.module.cloudmold.warehouse.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.*;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class WarehouseSourceMappingQueryServiceImpl implements WarehouseSourceMappingQueryApi {

    private final WarehouseSourceMappingMapper mapper;
    private final CanonicalWarehouseMapper warehouseMapper;
    private final WarehouseZoneMapper zoneMapper;
    private final WarehouseLocationMapper locationMapper;

    @Override
    public WarehouseSourceMappingView resolveActive(WarehouseSourceReference source, Instant effectiveAt) {
        require(source != null, "source is required");
        requireText(source.getSourceSystem(), "sourceSystem");
        requireText(source.getSourceType(), "sourceType");
        requireText(source.getSourceId(), "sourceId");
        require(effectiveAt != null, "effectiveAt is required");
        String sourceSystem = source.getSourceSystem().trim().toUpperCase(Locale.ROOT);
        String sourceType = source.getSourceType().trim().toUpperCase(Locale.ROOT);
        String sourceId = source.getSourceId().trim();
        List<WarehouseSourceMappingDO> matches = mapper.selectEffective(TenantContextHolder.getRequiredTenantId(),
                sourceSystem, sourceType, sourceId,
                LocalDateTime.ofInstant(effectiveAt, ZoneOffset.UTC));
        requireState(matches != null && matches.size() == 1,
                matches == null || matches.isEmpty() ? "source mapping does not exist" : "source mapping is ambiguous");
        WarehouseSourceMappingDO row = matches.get(0);
        return WarehouseSourceMappingView.builder().mappingId(row.getMappingId())
                .sourceSystem(row.getSourceSystem()).sourceType(row.getSourceType()).sourceId(row.getSourceId())
                .canonicalType(row.getCanonicalType()).canonicalId(row.getCanonicalId())
                .warehouseId(row.getWarehouseId()).zoneId(row.getZoneId())
                .locationId(row.getLocationId()).validFrom(toInstant(row.getValidFrom()))
                .validTo(toInstant(row.getValidTo())).version(row.getVersion()).build();
    }

    @Override
    public WarehouseNetworkView resolveReadyNetwork(WarehouseSourceReference source, Instant effectiveAt) {
        WarehouseSourceMappingView mapping = resolveActive(source, effectiveAt);
        requireState("WAREHOUSE".equals(mapping.getCanonicalType())
                        && mapping.getWarehouseId() != null,
                "source does not resolve to a canonical Warehouse");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        WarehouseDO warehouse = warehouseMapper.selectCurrent(tenantId, mapping.getWarehouseId());
        requireState(warehouse != null && "ACTIVE".equals(warehouse.getStatus()),
                "canonical Warehouse is not ACTIVE");
        List<WarehouseZoneDO> zones = zoneMapper.selectActiveByWarehouse(tenantId, warehouse.getWarehouseId());
        List<WarehouseLocationDO> locations = locationMapper.selectActiveByWarehouse(
                tenantId, warehouse.getWarehouseId());
        requireState(zones != null && zones.size() == 1,
                "canonical Warehouse must have exactly one ACTIVE Zone");
        requireState(locations != null && locations.size() == 1,
                "canonical Warehouse must have exactly one ACTIVE Location");
        WarehouseZoneDO zone = zones.get(0);
        WarehouseLocationDO location = locations.get(0);
        requireState(zone.getZoneId().equals(location.getZoneId()),
                "canonical Location does not belong to the resolved Zone");
        return WarehouseNetworkView.builder().mappingId(mapping.getMappingId())
                .warehouseId(warehouse.getWarehouseId()).warehouseStatus(warehouse.getStatus())
                .zoneId(zone.getZoneId()).zoneStatus(zone.getStatus())
                .locationId(location.getLocationId()).locationStatus(location.getStatus()).build();
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static void requireText(String value, String field) {
        require(value != null && !value.isBlank(), field + " is required");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
    private static void requireState(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
