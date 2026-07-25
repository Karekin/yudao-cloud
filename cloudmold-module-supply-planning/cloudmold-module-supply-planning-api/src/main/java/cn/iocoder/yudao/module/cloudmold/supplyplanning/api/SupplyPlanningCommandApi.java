package cn.iocoder.yudao.module.cloudmold.supplyplanning.api;

public interface SupplyPlanningCommandApi {
    default SupplyPlanningResult execute(SupplyPlanningCommand command) {
        throw new IllegalStateException("attested actor Principal is required");
    }

    SupplyPlanningResult execute(SupplyPlanningCommand command, String actorPrincipalId);
}
