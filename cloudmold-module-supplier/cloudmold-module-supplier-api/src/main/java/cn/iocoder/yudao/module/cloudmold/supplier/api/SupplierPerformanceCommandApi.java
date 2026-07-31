package cn.iocoder.yudao.module.cloudmold.supplier.api;

public interface SupplierPerformanceCommandApi {
    SupplierPerformanceResult execute(SupplierPerformanceCommand command, String actorPrincipalId);
}
