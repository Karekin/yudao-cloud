package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockoutDiagnosisQuery;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockoutDiagnosisQueryApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockoutDiagnosisResult;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockoutDiagnosisResult.SizeStockFact;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryStockoutDiagnosisMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryStockoutDiagnosisService implements InventoryStockoutDiagnosisQueryApi {

    private static final BigDecimal DEFAULT_LOW_STOCK_THRESHOLD = new BigDecimal("5.000000");
    private static final BigDecimal MAX_LOW_STOCK_THRESHOLD = new BigDecimal("1000000.000000");
    private static final BigDecimal ZERO = new BigDecimal("0.000000");

    private final InventoryStockoutDiagnosisMapper mapper;

    @Override
    public InventoryStockoutDiagnosisResult diagnose(InventoryStockoutDiagnosisQuery query) {
        require(query != null, "query is required");
        String canonicalSpuId = requireRef(query.getCanonicalSpuId(), "canonicalSpuId");
        String warehouseId = optionalRef(query.getWarehouseId(), "warehouseId");
        BigDecimal threshold = query.getLowStockThreshold() == null
                ? DEFAULT_LOW_STOCK_THRESHOLD : query.getLowStockThreshold();
        require(threshold.compareTo(ZERO) >= 0 && threshold.compareTo(MAX_LOW_STOCK_THRESHOLD) <= 0,
                "lowStockThreshold must be between 0 and 1000000");
        threshold = threshold.setScale(6);

        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String identity = mapper.selectActiveSpuIdentity(tenantId, canonicalSpuId);
        require(identity != null, "active canonical SPU does not exist");
        List<SizeStockFact> facts = mapper.selectSizeStockFacts(tenantId, canonicalSpuId, warehouseId);
        require(facts != null && !facts.isEmpty(), "active canonical SPU has no active SKU matrix");
        int stockoutCount = 0;
        int lowStockCount = 0;
        for (SizeStockFact fact : facts) {
            normalizeQuantities(fact);
            if (fact.getAllocatableQuantity().compareTo(ZERO) == 0
                    && fact.getInTransitQuantity().compareTo(ZERO) == 0) {
                fact.setSeverity("P1_STOCKOUT");
                stockoutCount++;
            } else if (fact.getAllocatableQuantity().compareTo(threshold) <= 0) {
                fact.setSeverity("P2_LOW_STOCK");
                lowStockCount++;
            } else {
                fact.setSeverity("HEALTHY");
            }
        }
        String[] identityParts = identity.split("\\|", -1);
        return InventoryStockoutDiagnosisResult.builder().canonicalSpuId(canonicalSpuId)
                .spuCode(identityParts[0]).styleCode(identityParts[1]).warehouseId(warehouseId)
                .lowStockThreshold(threshold).outcomeCode(stockoutCount > 0 ? "STOCKOUT_DETECTED"
                        : lowStockCount > 0 ? "LOW_STOCK_DETECTED" : "INVENTORY_HEALTHY")
                .skuCount(facts.size()).stockoutCount(stockoutCount).lowStockCount(lowStockCount)
                .sizeStockFacts(List.copyOf(facts)).build();
    }

    private static void normalizeQuantities(SizeStockFact fact) {
        fact.setOnHandQuantity(scale(fact.getOnHandQuantity()));
        fact.setReservedQuantity(scale(fact.getReservedQuantity()));
        fact.setInTransitQuantity(scale(fact.getInTransitQuantity()));
        fact.setAllocatableQuantity(scale(fact.getAllocatableQuantity()));
        if (fact.getMaxInventoryVersion() == null) fact.setMaxInventoryVersion(0L);
    }

    private static BigDecimal scale(BigDecimal value) {
        return (value == null ? ZERO : value).setScale(6);
    }

    private static String optionalRef(String value, String field) {
        return value == null || value.isBlank() ? null : requireRef(value, field);
    }

    private static String requireRef(String value, String field) {
        require(value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}"), "invalid " + field);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

}
