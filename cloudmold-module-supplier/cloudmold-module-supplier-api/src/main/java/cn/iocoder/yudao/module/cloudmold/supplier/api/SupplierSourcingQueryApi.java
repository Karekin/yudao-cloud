package cn.iocoder.yudao.module.cloudmold.supplier.api;

public interface SupplierSourcingQueryApi {
    SupplierProfileView requireSupplier(String supplierId);

    SupplierSourcingDecisionView requireDecision(String sourcingCaseId);
}
