package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

public interface MissionRuntimeApi {
    AgentControlResult startStockoutMission(StockoutMissionCommand command, Long supervisorUserId);
    AgentRunLeaseView claim(AgentRunClaimCommand command, Long actorUserId);
    AgentControlResult checkpoint(AgentCheckpointCommand command, Long actorUserId);
    AgentControlResult waitFor(AgentWaitCommand command, Long actorUserId);
    AgentControlResult matchEvent(MissionEventCommand command);
    AgentControlResult fireTimer(String timerId);
    AgentControlResult resolveCompletedWorkOrder(String workOrderId);
}
