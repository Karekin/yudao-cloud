package cn.iocoder.yudao.module.cloudmold.crossborder.api.bonded;

public interface BondedCustomsCommandApi {
    BondedCustomsResult execute(BondedCustomsCommand command, String actorPrincipalId);
}
