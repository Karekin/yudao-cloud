package cn.iocoder.yudao.module.cloudmold.agentcontrol.api.workflow;

/**
 * Governed read contract for the fixed stockout-mission lifecycle.
 */
public interface MissionLifecycleWorkflowQueryApi {

    MissionLifecycleWorkflowView inspect(String missionId);
}
