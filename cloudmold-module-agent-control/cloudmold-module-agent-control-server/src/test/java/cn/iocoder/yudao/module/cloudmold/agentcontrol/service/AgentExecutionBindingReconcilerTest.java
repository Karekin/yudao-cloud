package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentExecutionBindingApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.MissionRuntimeApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ExecutionReconcileCandidate;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.MissionResolutionCandidate;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskQueryApi;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class AgentExecutionBindingReconcilerTest {

    private final AgentControlStoreMapper mapper = mock(AgentControlStoreMapper.class);
    private final SkillTaskQueryApi skillTasks = mock(SkillTaskQueryApi.class);
    private final AgentExecutionBindingApi bindings = mock(AgentExecutionBindingApi.class);
    private final MissionRuntimeApi missionRuntime = mock(MissionRuntimeApi.class);
    private final AgentExecutionBindingReconciler reconciler =
            new AgentExecutionBindingReconciler(mapper, skillTasks, bindings, missionRuntime);

    @Test
    void completesBoundExecutionWhenTheSkillTaskHasTerminalSuccess() {
        ExecutionReconcileCandidate candidate = new ExecutionReconcileCandidate().setTenantId(1L)
                .setBindingId("binding-1").setOperatorUserId(7L).setSkillTaskId("task-1")
                .setSkillId("skill.inventory").setRunId("run-1");
        when(mapper.selectExecutionReconcileCandidates(100)).thenReturn(List.of(candidate));
        when(skillTasks.get("task-1")).thenReturn(SkillTaskView.builder().taskId("task-1")
                .status("SUCCEEDED").build());

        reconciler.reconcile();

        verify(bindings).reconcile("binding-1", 7L);
    }

    @Test
    void leavesRunningSkillTasksBoundForTheNextScan() {
        ExecutionReconcileCandidate candidate = new ExecutionReconcileCandidate().setTenantId(1L)
                .setBindingId("binding-1").setOperatorUserId(7L).setSkillTaskId("task-1")
                .setSkillId("skill.inventory").setRunId("run-1");
        when(mapper.selectExecutionReconcileCandidates(100)).thenReturn(List.of(candidate));
        when(skillTasks.get("task-1")).thenReturn(SkillTaskView.builder().taskId("task-1")
                .status("RUNNING").build());

        reconciler.reconcile();

        verifyNoInteractions(bindings);
    }

    @Test
    void resumesCompletedMissionResolutionAfterAProcessRestart() {
        when(mapper.selectExecutionReconcileCandidates(100)).thenReturn(List.of());
        when(mapper.selectMissionResolutionCandidates(100)).thenReturn(List.of(
                new MissionResolutionCandidate().setTenantId(1L).setWorkOrderId("wo-completed")));

        reconciler.reconcile();

        verify(missionRuntime).resolveCompletedWorkOrder("wo-completed");
    }
}
