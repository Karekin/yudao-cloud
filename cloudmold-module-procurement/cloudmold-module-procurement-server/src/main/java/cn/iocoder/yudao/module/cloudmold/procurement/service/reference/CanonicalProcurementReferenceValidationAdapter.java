package cn.iocoder.yudao.module.cloudmold.procurement.service.reference;

import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionApi;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierProfileView;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierSourcingQueryApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseReferenceValidationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CanonicalProcurementReferenceValidationAdapter implements ProcurementReferenceValidationPort {
    private final SupplierSourcingQueryApi supplierSourcingQueryApi;
    private final CatalogSkuProjectionApi catalogSkuProjectionApi;
    private final WarehouseReferenceValidationApi warehouseReferenceValidationApi;

    @Override
    public void requireActiveSupplier(String supplierId) {
        SupplierProfileView supplier = supplierSourcingQueryApi.requireSupplier(supplierId);
        require("ACTIVE".equals(supplier.getStatus()), "supplier is not ACTIVE");
        require("ADMITTED".equals(supplier.getAdmissionStatus()), "supplier is not ADMITTED");
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
