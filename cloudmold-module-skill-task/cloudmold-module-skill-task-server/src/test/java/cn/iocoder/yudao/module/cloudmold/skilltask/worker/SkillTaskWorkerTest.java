package cn.iocoder.yudao.module.cloudmold.skilltask.worker;

import cn.iocoder.yudao.module.cloudmold.executor.CloudMoldCapabilityExecutor;
import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcCallContext;
import cn.iocoder.yudao.module.cloudmold.skilltask.SkillTaskProperties;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalPermitClaims;
import cn.iocoder.yudao.module.cloudmold.skilltask.approval.SkillTaskApprovalEvidence;
import cn.iocoder.yudao.module.cloudmold.skilltask.approval.SkillTaskApprovalVerifier;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskMapper;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Candidate;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Step;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Task;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.SkillTaskTemplateResolver;
import cn.iocoder.yudao.module.cloudmold.skilltask.service.SkillTaskCheckpointService;
import cn.iocoder.yudao.module.cloudmold.skilltask.service.SkillTaskJson;
import cn.iocoder.yudao.module.cloudmold.skilltask.service.SkillTaskApiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SkillTaskWorkerTest {

    private final SkillTaskMapper mapper = mock(SkillTaskMapper.class);
    private final SkillTaskCheckpointService checkpoints = mock(SkillTaskCheckpointService.class);
    private final CloudMoldCapabilityExecutor executor = mock(CloudMoldCapabilityExecutor.class);
    private final SkillTaskApprovalVerifier approvalVerifier = mock(SkillTaskApprovalVerifier.class);
    private final SkillTaskApiService taskService = mock(SkillTaskApiService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SkillTaskProperties properties = new SkillTaskProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-18T12:00:00Z"), ZoneOffset.UTC);
    private final SkillTaskJson json = new SkillTaskJson(objectMapper, properties);
    private final SkillTaskWorker worker = new SkillTaskWorker(mapper, checkpoints,
            new SkillTaskTemplateResolver(objectMapper), json, executor, approvalVerifier, taskService,
            properties, clock);

    @BeforeEach
    void executeNonStaleOperations() {
        when(checkpoints.executeWithMissionFence(any(), any())).thenAnswer(invocation -> {
            Supplier<?> operation = invocation.getArgument(1);
            return operation.get();
        });
    }

    @AfterEach
    void clearTenant() {
        cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.clear();
    }

    @Test
    void resumesExpiredRunningTaskWithThePersistedStepIdempotencyKey() {
        Candidate candidate = new Candidate();
        candidate.setTenantId(8L); candidate.setTaskId("task-1"); candidate.setStatus("RUNNING");
        candidate.setVersion(3L); candidate.setAttemptCount(1); candidate.setMaxAttempts(3);
        Task task = task();
        Step step = step();
        when(mapper.selectDue(LocalDateTime.of(2026, 7, 18, 12, 0), 20)).thenReturn(List.of(candidate));
        when(checkpoints.claim(eq(candidate), anyString())).thenReturn(task);
        when(mapper.selectStep(8L, "task-1", "write")).thenReturn(step);
        when(mapper.selectSteps(8L, "task-1")).thenReturn(List.of(step));
        when(approvalVerifier.verify(any())).thenReturn(new SkillTaskApprovalEvidence(
                new SkillTaskApprovalPermitClaims("cma3", "risk-1", "cloudmold.agent-control",
                        "cloudmold.skill-task", "permit-1", "wo-r3-1", "approval-1", "f".repeat(64),
                        "skill.write", "1.0.0", "c".repeat(64), "a".repeat(64), "R3", "2:42", 8L,
                        Instant.parse("2026-07-18T12:00:00Z"), Instant.parse("2026-07-18T12:00:00Z"),
                        Instant.parse("2026-07-18T13:00:00Z")),
                "a".repeat(64), "test"));
        when(executor.execute(eq("cap.write"), any(), any(), anyBoolean())).thenReturn(TextNode.valueOf("ok"));
        when(checkpoints.checkpointSuccess(eq(task), eq(step), anyString(), eq("\"ok\""), anyString()))
                .thenReturn(null);

        worker.poll();

        ArgumentCaptor<JsonNode> arguments = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<CloudMoldRpcCallContext> context = ArgumentCaptor.forClass(CloudMoldRpcCallContext.class);
        verify(executor).execute(eq("cap.write"), arguments.capture(), context.capture(), eq(true));
        verify(approvalVerifier).verify(any());
        assertThat(arguments.getValue().get(0).asText()).isEqualTo("task-1:write");
        assertThat(context.getValue()).isEqualTo(new CloudMoldRpcCallContext(8L, 42L, 2,
                "skill.write", "task-1"));
        verify(checkpoints).prepareStep(eq(task), eq(step), anyString(), eq("[\"task-1:write\"]"), anyString());
    }

    @Test
    void releasesParentLeaseWhileItsPersistedChildIsStillRunning() {
        Candidate candidate = new Candidate();
        candidate.setTenantId(8L); candidate.setTaskId("parent-1"); candidate.setStatus("WAITING");
        candidate.setVersion(4L); candidate.setAttemptCount(1); candidate.setMaxAttempts(3);
        Task parent = task();
        parent.setTaskId("parent-1"); parent.setRunId("parent-run"); parent.setStatus("RUNNING");
        parent.setCurrentStepCode("wait-child"); parent.setInputJson("{\"childTaskId\":\"child-1\"}");
        Step wait = step();
        wait.setTaskId("parent-1"); wait.setStepCode("wait-child"); wait.setStepKind("WAIT_CHILD");
        wait.setOperationType("ORCHESTRATE"); wait.setCapabilityId(null);
        wait.setArgumentTemplateJson("[\"$input.childTaskId\"]"); wait.setPollIntervalSeconds(3);
        Task child = new Task();
        child.setTenantId(8L); child.setTaskId("child-1"); child.setParentTaskId("parent-1");
        child.setStatus("RUNNING");
        when(mapper.selectDue(LocalDateTime.of(2026, 7, 18, 12, 0), 20)).thenReturn(List.of(candidate));
        when(checkpoints.claim(eq(candidate), anyString())).thenReturn(parent);
        when(mapper.selectStep(8L, "parent-1", "wait-child")).thenReturn(wait);
        when(mapper.selectSteps(8L, "parent-1")).thenReturn(List.of(wait));
        when(mapper.selectTask(8L, "child-1")).thenReturn(child);

        worker.poll();

        verify(checkpoints).prepareStep(eq(parent), eq(wait), anyString(), eq("[\"child-1\"]"), anyString());
        verify(checkpoints).checkpointWaiting(eq(parent), eq(wait), anyString(), eq(java.time.Duration.ofSeconds(3)),
                eq("childTaskId=child-1; status=RUNNING"), eq(null), eq(null));
        verifyNoInteractions(executor, taskService);
    }

    @Test
    void checkpointsAndReleasesLeaseWhileDomainSagaIsNotTerminal() throws Exception {
        Candidate candidate = new Candidate();
        candidate.setTenantId(8L); candidate.setTaskId("task-poll"); candidate.setStatus("WAITING");
        candidate.setVersion(4L); candidate.setAttemptCount(3); candidate.setMaxAttempts(3);
        Task task = task();
        task.setTaskId("task-poll"); task.setStatus("RUNNING"); task.setCurrentStepCode("wait-saga");
        task.setInputJson("{\"afterSaleId\":\"after-1\"}");
        Step wait = step();
        wait.setTaskId("task-poll"); wait.setStepCode("wait-saga"); wait.setStepKind("WAIT_CAPABILITY");
        wait.setOperationType("READ"); wait.setCapabilityId("cap.after-sale.get");
        wait.setArgumentTemplateJson("[\"$input.afterSaleId\"]"); wait.setPollIntervalSeconds(2);
        wait.setWaitSuccessJson("{\"/caseStatus\":\"COMPLETED\",\"/refundStatus\":\"SUCCEEDED\"}");
        wait.setWaitFailureJson("{\"/resolutionSagaStatus\":[\"FAILED\",\"NEEDS_REVIEW\"]}");
        JsonNode pending = objectMapper.readTree(
                "{\"caseStatus\":\"RESOLUTION_PENDING\",\"resolutionSagaStatus\":\"RUNNING\"}");
        when(mapper.selectDue(LocalDateTime.of(2026, 7, 18, 12, 0), 20)).thenReturn(List.of(candidate));
        when(checkpoints.claim(eq(candidate), anyString())).thenReturn(task);
        when(mapper.selectStep(8L, "task-poll", "wait-saga")).thenReturn(wait);
        when(mapper.selectSteps(8L, "task-poll")).thenReturn(List.of(wait));
        when(executor.execute(eq("cap.after-sale.get"), any(), any(), eq(false))).thenReturn(pending);

        worker.poll();

        verify(checkpoints).checkpointWaiting(eq(task), eq(wait), anyString(),
                eq(java.time.Duration.ofSeconds(2)), eq("capabilityId=cap.after-sale.get; terminal=false"),
                eq("{\"caseStatus\":\"RESOLUTION_PENDING\",\"resolutionSagaStatus\":\"RUNNING\"}"), anyString());
        verifyNoInteractions(taskService);
    }

    @Test
    void acceptsNumericallyEqualWaitConditionsAcrossJsonIntegerWidths() {
        Candidate candidate = new Candidate();
        candidate.setTenantId(8L); candidate.setTaskId("task-poll"); candidate.setStatus("WAITING");
        candidate.setVersion(4L); candidate.setAttemptCount(3); candidate.setMaxAttempts(3);
        Task task = task();
        task.setTaskId("task-poll"); task.setStatus("RUNNING"); task.setCurrentStepCode("wait-case");
        task.setInputJson("{\"alertId\":\"alert-1\"}");
        Step wait = step();
        wait.setTaskId("task-poll"); wait.setStepCode("wait-case"); wait.setStepKind("WAIT_CAPABILITY");
        wait.setOperationType("READ"); wait.setCapabilityId("cap.operations-alert.get");
        wait.setArgumentTemplateJson("[\"$input.alertId\"]"); wait.setPollIntervalSeconds(2);
        wait.setWaitSuccessJson("{\"/status\":\"RESOLVED\",\"/aggregateVersion\":4}");
        wait.setWaitFailureJson("{\"/status\":[\"INVALID\"]}");
        JsonNode resolved = objectMapper.createObjectNode()
                .put("status", "RESOLVED")
                .put("aggregateVersion", 4L);
        when(mapper.selectDue(LocalDateTime.of(2026, 7, 18, 12, 0), 20)).thenReturn(List.of(candidate));
        when(checkpoints.claim(eq(candidate), anyString())).thenReturn(task);
        when(mapper.selectStep(8L, "task-poll", "wait-case")).thenReturn(wait);
        when(mapper.selectSteps(8L, "task-poll")).thenReturn(List.of(wait));
        when(executor.execute(eq("cap.operations-alert.get"), any(), any(), eq(false))).thenReturn(resolved);
        when(checkpoints.checkpointSuccess(eq(task), eq(wait), anyString(),
                eq("{\"aggregateVersion\":4,\"status\":\"RESOLVED\"}"), anyString())).thenReturn(null);

        worker.poll();

        verify(checkpoints).checkpointSuccess(eq(task), eq(wait), anyString(),
                eq("{\"aggregateVersion\":4,\"status\":\"RESOLVED\"}"), anyString());
        verify(checkpoints, never()).checkpointWaiting(any(), any(), anyString(), any(),
                anyString(), any(), any());
    }

    @Test
    void doesNotInvokeTheActuatorWhenTakeoverWinsBeforeExecution() {
        Candidate candidate = new Candidate();
        candidate.setTenantId(8L); candidate.setTaskId("task-1"); candidate.setStatus("RUNNING");
        candidate.setVersion(3L); candidate.setAttemptCount(1); candidate.setMaxAttempts(3);
        Task task = task();
        Step step = step();
        when(mapper.selectDue(LocalDateTime.of(2026, 7, 18, 12, 0), 20)).thenReturn(List.of(candidate));
        when(checkpoints.claim(eq(candidate), anyString())).thenReturn(task);
        when(mapper.selectStep(8L, "task-1", "write")).thenReturn(step);
        doThrow(new SkillTaskCheckpointService.LeaseLostException("mission lease fence is stale"))
                .when(checkpoints).executeWithMissionFence(eq(task), any());

        worker.poll();

        verify(checkpoints).prepareStep(eq(task), eq(step), anyString(), anyString(), anyString());
        verify(executor, never()).execute(anyString(), any(), any(), anyBoolean());
        verify(checkpoints, never()).checkpointSuccess(any(), any(), anyString(), anyString(), anyString());
    }

    private static Task task() {
        Task task = new Task();
        task.setTenantId(8L); task.setTaskId("task-1"); task.setRunId("task-1");
        task.setSkillId("skill.write"); task.setSkillVersion("1.0.0"); task.setInputJson("{}");
        task.setInputSha256("a".repeat(64));
        task.setRiskLevel("R2"); task.setApprovalRef("approval-1");
        task.setOperatorId(42L); task.setOperatorType(2); task.setStatus("RUNNING");
        task.setCurrentStepCode("write"); task.setAttemptCount(2); task.setMaxAttempts(3);
        return task;
    }

    private static Step step() {
        Step step = new Step();
        step.setTenantId(8L); step.setTaskId("task-1"); step.setStepCode("write"); step.setStepOrder(1);
        step.setStepKind("CAPABILITY");
        step.setCapabilityId("cap.write"); step.setOperationType("WRITE");
        step.setArgumentTemplateJson("[\"$task.stepIdempotencyKey\"]");
        step.setIdempotencyKey("task-1:write"); step.setStatus("RUNNING");
        return step;
    }
}
