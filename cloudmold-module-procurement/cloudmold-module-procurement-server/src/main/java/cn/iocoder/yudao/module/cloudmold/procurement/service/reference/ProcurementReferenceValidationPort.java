package cn.iocoder.yudao.module.cloudmold.procurement.service.reference;

public interface ProcurementReferenceValidationPort {
    void requireActiveSupplier(String supplierId);

    void requireActiveSku(String canonicalSkuId);

    void requireActiveWarehouse(String canonicalWarehouseId);
}
