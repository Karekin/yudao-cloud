package cn.iocoder.yudao.module.cloudmold.skilltask.service;

import cn.iocoder.yudao.module.cloudmold.skilltask.SkillTaskProperties;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskMapper;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Step;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Task;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillTaskCheckpointServiceTest {

    private final SkillTaskMapper mapper = mock(SkillTaskMapper.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-18T12:00:00Z"), ZoneOffset.UTC);
    private final SkillTaskJson json = new SkillTaskJson(new ObjectMapper(), new SkillTaskProperties());
    private final SkillTaskCheckpointService service =
            new SkillTaskCheckpointService(mapper, new SkillTaskProperties(), json, clock);

    @Test
    void persistsStepResultAndNextCheckpointInOneTransition() {
        Task task = runningTask();
        Step first = step("first", 1);
        Step second = step("second", 2);
        when(mapper.selectTaskForUpdate(8L, "task-1")).thenReturn(task);
        when(mapper.markStepSucceeded(eq(8L), eq("task-1"), eq("first"), eq("{\"ok\":true}"), eq("hash"),
                any(LocalDateTime.class)))
                .thenReturn(1);
        when(mapper.selectSteps(8L, "task-1")).thenReturn(List.of(first, second));
        when(mapper.advance(any(), any(), any(), any(), any(), any())).thenReturn(1);

        String next = service.checkpointSuccess(task, first, "worker-1", "{\"ok\":true}", "hash");

        assertThat(next).isEqualTo("second");
        verify(mapper).advance(any(), any(), any(), eq("second"), any(), any());
        verify(mapper).insertHistory(8L, "task-1", 2L, "RUNNING", "RUNNING", "second",
                "STEP_SUCCEEDED", "first", 42L, 2,
                java.time.LocalDateTime.of(2026, 7, 18, 12, 0));
    }

    @Test
    void persistsWaitingCheckpointAndReleasesTheParentLease() {
        Task task = runningTask();
        Step wait = step("first", 1);
        when(mapper.selectTaskForUpdate(8L, "task-1")).thenReturn(task);
        when(mapper.markStepWaiting(8L, "task-1", "first", null, null,
                LocalDateTime.of(2026, 7, 18, 12, 0)))
                .thenReturn(1);
        when(mapper.waitTask(8L, "task-1", "worker-1", LocalDateTime.of(2026, 7, 18, 12, 0, 3),
                LocalDateTime.of(2026, 7, 18, 12, 0))).thenReturn(1);

        service.checkpointWaiting(task, wait, "worker-1", Duration.ofSeconds(3),
                "childTaskId=child-1; status=RUNNING", null, null);

        verify(mapper).waitTask(8L, "task-1", "worker-1", LocalDateTime.of(2026, 7, 18, 12, 0, 3),
                LocalDateTime.of(2026, 7, 18, 12, 0));
        verify(mapper).insertHistory(8L, "task-1", 2L, "RUNNING", "WAITING", "first",
                "CHILD_TASK_WAITING", "childTaskId=child-1; status=RUNNING", 42L, 2,
                LocalDateTime.of(2026, 7, 18, 12, 0));
    }

    @Test
    void freezesOneTaskLevelTerminalHashWhenTheLastStepSucceeds() {
        Task task = runningTask();
        Step last = step("first", 1);
        last.setStepKind("CAPABILITY"); last.setCapabilityId("cap.read"); last.setOperationType("READ");
        last.setRequestSha256("c".repeat(64)); last.setResultSha256("d".repeat(64));
        when(mapper.selectTaskForUpdate(8L, "task-1")).thenReturn(task);
        when(mapper.markStepSucceeded(eq(8L), eq("task-1"), eq("first"), eq("{\"ok\":true}"),
                eq("d".repeat(64)), any(LocalDateTime.class))).thenReturn(1);
        when(mapper.selectSteps(8L, "task-1")).thenReturn(List.of(last));
        when(mapper.selectChildren(8L, "task-1")).thenReturn(List.of());
        when(mapper.complete(eq(8L), eq("task-1"), eq("worker-1"), argThat(value -> value.matches("[0-9a-f]{64}")),
                any(LocalDateTime.class))).thenReturn(1);

        assertThat(service.checkpointSuccess(task, last, "worker-1", "{\"ok\":true}", "d".repeat(64)))
                .isNull();
        verify(mapper).complete(eq(8L), eq("task-1"), eq("worker-1"), argThat(value -> value.matches("[0-9a-f]{64}")),
                eq(LocalDateTime.of(2026, 7, 18, 12, 0)));
    }

    private static Task runningTask() {
        Task task = new Task();
        task.setTenantId(8L); task.setTaskId("task-1"); task.setStatus("RUNNING");
        task.setOperatorId(42L); task.setOperatorType(2);
        task.setCurrentStepCode("first"); task.setLeaseOwner("worker-1"); task.setVersion(1L);
        task.setRunId("run-1"); task.setSkillId("skill.test"); task.setSkillVersion("1.0.0");
        task.setInputSha256("a".repeat(64)); task.setRiskLevel("R1");
        task.setDefinitionClosureSha256("b".repeat(64));
        return task;
    }

    private static Step step(String code, int order) {
        Step step = new Step();
        step.setTenantId(8L); step.setTaskId("task-1"); step.setStepCode(code); step.setStepOrder(order);
        return step;
    }
}
