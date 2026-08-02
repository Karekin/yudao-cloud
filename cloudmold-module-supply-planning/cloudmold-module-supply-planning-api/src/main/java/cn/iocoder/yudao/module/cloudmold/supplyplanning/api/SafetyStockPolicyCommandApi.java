package cn.iocoder.yudao.module.cloudmold.supplyplanning.api;

public interface SafetyStockPolicyCommandApi {
    SafetyStockPolicyResult execute(SafetyStockPolicyCommand command, String actorPrincipalId);
}
