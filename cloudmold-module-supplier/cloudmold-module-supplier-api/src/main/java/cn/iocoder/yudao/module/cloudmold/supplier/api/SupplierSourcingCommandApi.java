package cn.iocoder.yudao.module.cloudmold.supplier.api;

public interface SupplierSourcingCommandApi {
    default SupplierSourcingResult execute(SupplierSourcingCommand command) {
        throw new IllegalStateException("attested actor Principal is required");
    }

    SupplierSourcingResult execute(SupplierSourcingCommand command, String actorPrincipalId);
}
