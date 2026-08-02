package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockCountSnapshotApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockCountSnapshotQuery;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockCountSnapshotView;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3BalanceDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryV3BalanceMapper;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantOwnerValidationApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseReferenceValidationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryStockCountSnapshotServiceImpl implements InventoryStockCountSnapshotApi {

    private static final Set<String> STOCK_STATUSES = Set.of("SELLABLE", "NON_SELLABLE");
    private static final Set<String> QUALITY_STATUSES = Set.of("PENDING_QC", "QUALIFIED", "DAMAGED", "REJECTED");

    private final InventoryV3BalanceMapper balanceMapper;
    private final MerchantOwnerValidationApi merchantOwnerValidationApi;
    private final CatalogSkuValidationApi catalogSkuValidationApi;
    private final WarehouseReferenceValidationApi warehouseValidationApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryStockCountSnapshotView requireSnapshot(InventoryStockCountSnapshotQuery rawQuery) {
        Query query = normalize(rawQuery);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        merchantOwnerValidationApi.requireActiveMerchant(query.ownerId());
        catalogSkuValidationApi.requireActiveSku(query.canonicalSkuId());
        warehouseValidationApi.requireActiveLocation(query.warehouseId(), query.locationId());

        balanceMapper.insertOrResolve(UUID.randomUUID().toString(), tenantId, query.ownerType(), query.ownerId(),
                query.canonicalSkuId(), query.warehouseId(), query.locationId(), query.lotId(),
                query.stockStatus(), query.qualityStatus(), query.baseUomCode(), java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        InventoryV3BalanceDO balance = balanceMapper.selectDimensionForUpdate(tenantId, query.ownerType(), query.ownerId(),
                query.canonicalSkuId(), query.warehouseId(), query.locationId(), query.lotId(),
                query.stockStatus(), query.qualityStatus());
        if (balance == null) {
            throw new IllegalArgumentException("inventory stock count snapshot balance not found");
        }
        if (!Objects.equals(balance.getBaseUomCode(), query.baseUomCode())) {
            throw new IllegalArgumentException("inventory stock count snapshot base UOM mismatch");
        }
        BigDecimal onHand = scaled(balance.getOnHandQuantity());
        BigDecimal reserved = scaled(balance.getReservedQuantity());
        BigDecimal inTransit = scaled(balance.getInTransitQuantity());
        return InventoryStockCountSnapshotView.builder()
                .balanceId(balance.getBalanceId())
                .ownerType(balance.getOwnerType())
                .ownerId(balance.getOwnerId())
                .canonicalSkuId(balance.getCanonicalSkuId())
                .warehouseId(balance.getWarehouseId())
                .locationId(balance.getLocationId())
                .lotId(balance.getLotId())
                .stockStatus(balance.getStockStatus())
                .qualityStatus(balance.getQualityStatus())
                .baseUomCode(balance.getBaseUomCode())
                .onHandQuantity(onHand)
                .reservedQuantity(reserved)
                .inTransitQuantity(inTransit)
                .availableQuantity(onHand.subtract(reserved).setScale(6, RoundingMode.HALF_UP))
                .aggregateVersion(balance.getVersion())
                .build();
    }

    private static Query normalize(InventoryStockCountSnapshotQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("inventory stock count snapshot query is required");
        }
        String stockStatus = requireUpper(query.getStockStatus(), "stockStatus");
        String qualityStatus = requireUpper(query.getQualityStatus(), "qualityStatus");
        if (!STOCK_STATUSES.contains(stockStatus)) {
            throw new IllegalArgumentException("unsupported stockStatus");
        }
        if (!QUALITY_STATUSES.contains(qualityStatus)) {
            throw new IllegalArgumentException("unsupported qualityStatus");
        }
        return new Query(
                requireUpper(query.getOwnerType(), "ownerType"),
                requireText(query.getOwnerId(), "ownerId"),
                requireText(query.getCanonicalSkuId(), "canonicalSkuId"),
                requireText(query.getWarehouseId(), "warehouseId"),
                requireText(query.getLocationId(), "locationId"),
                query.getLotId() == null || query.getLotId().isBlank() ? null : query.getLotId().trim(),
                stockStatus,
                qualityStatus,
                requireUpper(query.getBaseUomCode(), "baseUomCode"));
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String requireUpper(String value, String field) {
        return requireText(value, field).toUpperCase(Locale.ROOT);
    }

    private record Query(String ownerType,
                         String ownerId,
                         String canonicalSkuId,
                         String warehouseId,
                         String locationId,
                         String lotId,
                         String stockStatus,
                         String qualityStatus,
                         String baseUomCode) {
    }
}
