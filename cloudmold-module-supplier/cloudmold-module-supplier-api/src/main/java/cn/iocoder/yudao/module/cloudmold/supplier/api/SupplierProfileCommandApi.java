package cn.iocoder.yudao.module.cloudmold.supplier.api;

public interface SupplierProfileCommandApi {
    SupplierProfileResult execute(SupplierProfileCommand command, String actorPrincipalId);
}
