package cn.iocoder.yudao.module.cloudmold.warehouse.api.supplierreturn;

public interface SupplierReturnCommandApi {
    SupplierReturnResult execute(SupplierReturnCommand command, String actorPrincipalId);
}
