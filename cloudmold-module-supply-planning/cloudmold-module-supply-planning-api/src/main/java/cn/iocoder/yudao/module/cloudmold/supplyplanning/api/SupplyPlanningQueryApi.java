package cn.iocoder.yudao.module.cloudmold.supplyplanning.api;

import java.util.List;

public interface SupplyPlanningQueryApi {
    List<ReplenishmentExecutionProposalView> listReadyReplenishmentExecutionProposals(int limit);

    ReplenishmentExecutionProposalView requireReadyReplenishmentExecutionProposal(String proposalId);

    ReplenishmentExecutionView requireReplenishmentExecution(String recommendationId);

    ReplenishmentBusinessStageView requireReplenishmentBusinessStage(String recommendationId);
}
