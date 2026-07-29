package cn.iocoder.yudao.module.cloudmold.agentcontrol.api.workflow;

/**
 * Narrow write contract for the fixed stockout-mission lifecycle.
 */
public interface MissionLifecycleWorkflowCommandApi {

    MissionLifecycleWorkflowCommandResult startStockoutMission(MissionLifecycleWorkflowCommand command);
}
