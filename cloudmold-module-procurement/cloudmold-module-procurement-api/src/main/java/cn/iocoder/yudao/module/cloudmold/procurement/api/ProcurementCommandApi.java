package cn.iocoder.yudao.module.cloudmold.procurement.api;

public interface ProcurementCommandApi {
    default ProcurementResult execute(ProcurementCommand command) {
        throw new IllegalStateException("attested actor Principal is required");
    }

    ProcurementResult execute(ProcurementCommand command, String actorPrincipalId);
}
