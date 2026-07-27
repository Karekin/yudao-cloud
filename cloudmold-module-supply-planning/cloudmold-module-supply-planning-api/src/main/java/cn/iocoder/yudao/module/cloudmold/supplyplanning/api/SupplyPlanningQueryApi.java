package cn.iocoder.yudao.module.cloudmold.supplyplanning.api;

public interface SupplyPlanningQueryApi {
    ReplenishmentExecutionView requireReplenishmentExecution(String recommendationId);

    ReplenishmentBusinessStageView requireReplenishmentBusinessStage(String recommendationId);
}
