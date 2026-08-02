package cn.iocoder.yudao.module.cloudmold.procurement.api;

public interface SourcingCommandApi {
    SourcingResult execute(SourcingCommand command, String actorPrincipalId);
}
