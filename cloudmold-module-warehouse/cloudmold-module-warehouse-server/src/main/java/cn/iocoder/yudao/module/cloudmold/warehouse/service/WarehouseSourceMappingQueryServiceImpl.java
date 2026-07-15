package cn.iocoder.yudao.module.cloudmold.warehouse.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.*;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.WarehouseSourceMappingDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.WarehouseSourceMappingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class WarehouseSourceMappingQueryServiceImpl implements WarehouseSourceMappingQueryApi {

    private final WarehouseSourceMappingMapper mapper;

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
