package cn.iocoder.yudao.module.cloudmold.crossborder.api;

public interface CrossBorderCommandApi {
    CrossBorderResult execute(CrossBorderCommand command, String actorPrincipalId);
}
