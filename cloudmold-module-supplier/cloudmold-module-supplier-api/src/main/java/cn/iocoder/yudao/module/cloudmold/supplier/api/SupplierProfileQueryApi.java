package cn.iocoder.yudao.module.cloudmold.supplier.api;

/** Stable Supplier-owned read contract for supplier master data and procurement eligibility. */
public interface SupplierProfileQueryApi {
    SupplierProfileView requireSupplier(String supplierId);

    SupplierProfileView requireProcurementEligibleSupplier(String supplierId);
}
