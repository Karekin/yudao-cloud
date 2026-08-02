package cn.iocoder.yudao.module.cloudmold.procurement.api;

public interface ProcurementCommandApi {
    ProcurementResult execute(ProcurementCommand command, String actorPrincipalId);
}
