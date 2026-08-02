package cn.iocoder.yudao.module.cloudmold.procurement.service.reference;

import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionApi;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierProfileQueryApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseReferenceValidationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CanonicalProcurementReferenceValidationAdapter implements ProcurementReferenceValidationPort {
    private final SupplierProfileQueryApi supplierProfileQueryApi;
    private final CatalogSkuProjectionApi catalogSkuProjectionApi;
    private final WarehouseReferenceValidationApi warehouseReferenceValidationApi;

    @Override
    public void requireActiveSupplier(String supplierId) {
        supplierProfileQueryApi.requireProcurementEligibleSupplier(supplierId);
    }

    @Override
    public void requireActiveSku(String canonicalSkuId) {
        require(catalogSkuProjectionApi.getActiveSku(canonicalSkuId) != null, "catalog SKU is not ACTIVE");
    }

    @Override
    public void requireActiveWarehouse(String canonicalWarehouseId) {
        warehouseReferenceValidationApi.requireActiveWarehouse(canonicalWarehouseId);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
