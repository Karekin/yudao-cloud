package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentExecutionBindingCommand;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.MissionRuntimeApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.*;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.*;
import org.junit.jupiter.api.*;

import java.time.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentExecutionBindingServiceTest {
    private final AgentControlStoreMapper mapper = mock(AgentControlStoreMapper.class);
    private final SkillTaskQueryApi skillTasks = mock(SkillTaskQueryApi.class);
    private final MissionRuntimeApi missionRuntime = mock(MissionRuntimeApi.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-19T04:00:00Z"), ZoneOffset.UTC);
    private final AgentExecutionBindingService service =
            new AgentExecutionBindingService(mapper, skillTasks, missionRuntime, clock);

    @BeforeEach void tenant() { TenantContextHolder.setTenantId(17L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test
    void refusesToBindAClientTaskWhoseFrozenInputDoesNotMatch() {
        WorkOrder workOrder = executableWorkOrder();
        when(mapper.selectWorkOrderForUpdate(17L, "wo-1")).thenReturn(workOrder);
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), eq(42L), eq("buyer"), any())).thenReturn(new ActorRoleGrant());
        SkillTaskView task = taskView(); task.setInputSha256("f".repeat(64));
        when(skillTasks.get("task-1")).thenReturn(task);

        assertThatThrownBy(() -> service.bind(bindingCommand(), 42L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("server-frozen");
        verify(mapper, never()).insertExecutionBinding(any());
    }

    @Test
    void onlyAnExactTerminalProofCompletesExecutableWork() {
        WorkOrder workOrder = executableWorkOrder();
        ExecutionBinding binding = new ExecutionBinding().setBindingId("binding-1").setTenantId(17L)
                .setWorkOrderId("wo-1").setSkillTaskId("task-1").setSkillId("skill.procure")
                .setSkillVersion("1.0.0").setSkillDefinitionClosureSha256("d".repeat(64))
                .setInputSha256("a".repeat(64)).setStatus("BOUND").setVersion(1L);
        when(mapper.selectExecutionBindingForUpdate(17L, "binding-1")).thenReturn(binding);
        when(mapper.selectWorkOrderForUpdate(17L, "wo-1")).thenReturn(workOrder);
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), eq(42L), eq("buyer"), any())).thenReturn(new ActorRoleGrant());
        SkillTaskTerminalProofView proof = SkillTaskTerminalProofView.builder().tenantId(17L).taskId("task-1")
                .runId("run-1")
                .skillId("skill.procure").skillVersion("1.0.0").definitionClosureSha256("d".repeat(64))
                .inputSha256("a".repeat(64)).status("SUCCEEDED").terminalResultSha256("b".repeat(64)).build();
        when(skillTasks.getTerminalProof("task-1")).thenReturn(proof);
        when(mapper.acceptExecutionBinding(eq(17L), eq("binding-1"), eq(1L), eq("b".repeat(64)), any())).thenReturn(1);
        when(mapper.insertBusinessResult(any())).thenReturn(1);
        when(mapper.transitionWorkOrder(eq(17L), eq("wo-1"), eq(7L), eq("IN_PROGRESS"), eq("COMPLETED"),
                eq(42L), isNull(), any())).thenReturn(1);
        when(mapper.insertAuditEvent(any())).thenReturn(1);

        assertThat(service.reconcile("binding-1", 42L).getStatus()).isEqualTo("EXECUTION_SUCCEEDED");
        verify(mapper).insertBusinessResult(argThat(result -> result.getEvidenceRef()
                .equals("skilltask:task-1:sha256:" + "b".repeat(64))));
        verify(mapper).transitionWorkOrder(eq(17L), eq("wo-1"), eq(7L), eq("IN_PROGRESS"),
                eq("COMPLETED"), eq(42L), isNull(), any());
        verify(missionRuntime).resolveCompletedWorkOrder("wo-1");
    }

    @Test
    void replacesANeedsReviewExecutionWithTheNextImmutableGeneration() {
        WorkOrder workOrder = executableWorkOrder();
        ExecutionBinding previous = new ExecutionBinding().setBindingId("binding-1").setTenantId(17L)
                .setWorkOrderId("wo-1").setExecutionGeneration(1).setSkillTaskId("task-1")
                .setSkillId("skill.procure").setSkillVersion("1.0.0")
                .setSkillDefinitionClosureSha256("d".repeat(64)).setInputSha256("a".repeat(64))
                .setStatus("BOUND").setVersion(2L);
        when(mapper.selectWorkOrderForUpdate(17L, "wo-1")).thenReturn(workOrder);
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), eq(42L), eq("buyer"), any()))
                .thenReturn(new ActorRoleGrant());
        when(mapper.selectLatestExecutionBindingForUpdate(17L, "wo-1")).thenReturn(previous);
        SkillTaskView failed = taskView(); failed.setStatus("NEEDS_REVIEW");
        SkillTaskView replacement = taskView(); replacement.setTaskId("task-2");
        when(skillTasks.get("task-1")).thenReturn(failed);
        when(skillTasks.get("task-2")).thenReturn(replacement);
        when(mapper.supersedeExecutionBinding(eq(17L), eq("binding-1"), eq(2L), any())).thenReturn(1);
        when(mapper.insertExecutionBinding(any())).thenReturn(1);
        when(mapper.insertAuditEvent(any())).thenReturn(1);

        AgentExecutionBindingCommand command = AgentExecutionBindingCommand.builder().bindingId("binding-2")
                .workOrderId("wo-1").workOrderExpectedVersion(7L).executionGeneration(2)
                .skillTaskId("task-2").supersededBindingId("binding-1")
                .supersedeReasonCode("PREVIOUS_TASK_NEEDS_REVIEW").build();

        assertThat(service.bind(command, 42L).getStatus()).isEqualTo("BOUND");
        verify(mapper).supersedeExecutionBinding(eq(17L), eq("binding-1"), eq(2L), any());
        verify(mapper).insertExecutionBinding(argThat(value -> value.getExecutionGeneration() == 2
                && value.getSkillTaskId().equals("task-2")));
    }

    @Test
    void takeoverAllowsRebindingWhenTheLatestBoundTaskBelongsToTheOldRun() {
        WorkOrder workOrder = executableWorkOrder().setActiveRunId("run-2").setVersion(9L);
        ExecutionBinding previous = new ExecutionBinding().setBindingId("binding-1").setTenantId(17L)
                .setWorkOrderId("wo-1").setExecutionGeneration(1).setSkillTaskId("task-1")
                .setSkillId("skill.procure").setSkillVersion("1.0.0")
                .setSkillDefinitionClosureSha256("d".repeat(64)).setInputSha256("a".repeat(64))
                .setStatus("BOUND").setVersion(2L);
        when(mapper.selectWorkOrderForUpdate(17L, "wo-1")).thenReturn(workOrder);
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), eq(42L), eq("buyer"), any()))
                .thenReturn(new ActorRoleGrant());
        when(mapper.selectLatestExecutionBindingForUpdate(17L, "wo-1")).thenReturn(previous);
        SkillTaskView oldTask = SkillTaskView.builder().taskId("task-1").runId("run-1")
                .skillId("skill.procure").skillVersion("1.0.0").definitionClosureSha256("d".repeat(64))
                .inputSha256("a".repeat(64)).riskLevel("R3").status("RUNNING").build();
        SkillTaskView replacement = SkillTaskView.builder().taskId("task-2").runId("run-2")
                .skillId("skill.procure").skillVersion("1.0.0").definitionClosureSha256("d".repeat(64))
                .inputSha256("a".repeat(64)).riskLevel("R3").status("RUNNING").build();
        when(skillTasks.get("task-1")).thenReturn(oldTask);
        when(skillTasks.get("task-2")).thenReturn(replacement);
        when(mapper.supersedeExecutionBinding(eq(17L), eq("binding-1"), eq(2L), any())).thenReturn(1);
        when(mapper.insertExecutionBinding(any())).thenReturn(1);
        when(mapper.insertAuditEvent(any())).thenReturn(1);

        AgentExecutionBindingCommand command = AgentExecutionBindingCommand.builder().bindingId("binding-2")
                .workOrderId("wo-1").workOrderExpectedVersion(9L).executionGeneration(2)
                .skillTaskId("task-2").supersededBindingId("binding-1")
                .supersedeReasonCode("PREVIOUS_RUN_EXPIRED").build();

        assertThat(service.bind(command, 42L).getStatus()).isEqualTo("BOUND");
        verify(mapper).supersedeExecutionBinding(eq(17L), eq("binding-1"), eq(2L), any());
        verify(mapper).insertExecutionBinding(argThat(value -> value.getExecutionGeneration() == 2
                && value.getSkillTaskId().equals("task-2")));
    }

    @Test
    void takeoverRejectsLateTerminalProofFromTheOldRun() {
        WorkOrder workOrder = executableWorkOrder().setActiveRunId("run-2").setVersion(9L);
        ExecutionBinding binding = new ExecutionBinding().setBindingId("binding-1").setTenantId(17L)
                .setWorkOrderId("wo-1").setSkillTaskId("task-1").setSkillId("skill.procure")
                .setSkillVersion("1.0.0").setSkillDefinitionClosureSha256("d".repeat(64))
                .setInputSha256("a".repeat(64)).setStatus("BOUND").setVersion(1L);
        when(mapper.selectExecutionBindingForUpdate(17L, "binding-1")).thenReturn(binding);
        when(mapper.selectWorkOrderForUpdate(17L, "wo-1")).thenReturn(workOrder);
        when(mapper.selectEffectiveActorRoleGrant(eq(17L), eq(42L), eq("buyer"), any())).thenReturn(new ActorRoleGrant());
        SkillTaskTerminalProofView proof = SkillTaskTerminalProofView.builder().tenantId(17L).taskId("task-1")
                .runId("run-1").skillId("skill.procure").skillVersion("1.0.0")
                .definitionClosureSha256("d".repeat(64)).inputSha256("a".repeat(64))
                .status("SUCCEEDED").terminalResultSha256("b".repeat(64)).build();
        when(skillTasks.getTerminalProof("task-1")).thenReturn(proof);

        assertThatThrownBy(() -> service.reconcile("binding-1", 42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stale Agent run");
        verify(mapper, never()).acceptExecutionBinding(anyLong(), anyString(), anyLong(), anyString(), any());
        verify(mapper, never()).insertBusinessResult(any());
    }

    private static AgentExecutionBindingCommand bindingCommand() {
        return AgentExecutionBindingCommand.builder().bindingId("binding-1").workOrderId("wo-1")
                .workOrderExpectedVersion(7L).executionGeneration(1).skillTaskId("task-1").build();
    }
    private static WorkOrder executableWorkOrder() {
        return new WorkOrder().setTenantId(17L).setWorkOrderId("wo-1").setRoleCode("buyer")
                .setActionCode("buyer.execute-replenishment").setStatus("IN_PROGRESS").setAssigneeUserId(42L)
                .setExecutionRequired(true).setSkillId("skill.procure").setSkillVersion("1.0.0")
                .setSkillDefinitionClosureSha256("d".repeat(64)).setExecutionInputSha256("a".repeat(64))
                .setRiskLevel("R3").setMissionId("mission-1").setGoalId("goal-1")
                .setActiveRunId("run-1").setVersion(7L);
    }
    private static SkillTaskView taskView() {
        return SkillTaskView.builder().taskId("task-1").runId("run-1").skillId("skill.procure").skillVersion("1.0.0")
                .definitionClosureSha256("d".repeat(64)).inputSha256("a".repeat(64)).riskLevel("R3")
                .status("RUNNING").build();
    }
}
