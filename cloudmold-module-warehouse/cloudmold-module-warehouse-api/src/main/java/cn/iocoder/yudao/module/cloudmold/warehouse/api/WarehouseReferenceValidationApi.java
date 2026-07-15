package cn.iocoder.yudao.module.cloudmold.warehouse.api;

public interface WarehouseReferenceValidationApi {
    void requireActiveWarehouse(String warehouseId);
    void requireActiveLocation(String warehouseId, String locationId);
}
