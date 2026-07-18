package cn.iocoder.yudao.module.cloudmold.skilltask.worker;

import cn.iocoder.yudao.module.cloudmold.executor.CloudMoldCapabilityExecutor;
import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcCallContext;
import cn.iocoder.yudao.module.cloudmold.skilltask.SkillTaskProperties;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskMapper;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Candidate;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Step;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Task;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.SkillTaskTemplateResolver;
import cn.iocoder.yudao.module.cloudmold.skilltask.service.SkillTaskCheckpointService;
import cn.iocoder.yudao.module.cloudmold.skilltask.service.SkillTaskJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillTaskWorkerTest {

    private final SkillTaskMapper mapper = mock(SkillTaskMapper.class);
    private final SkillTaskCheckpointService checkpoints = mock(SkillTaskCheckpointService.class);
    private final CloudMoldCapabilityExecutor executor = mock(CloudMoldCapabilityExecutor.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SkillTaskProperties properties = new SkillTaskProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-18T12:00:00Z"), ZoneOffset.UTC);
    private final SkillTaskJson json = new SkillTaskJson(objectMapper, properties);
    private final SkillTaskWorker worker = new SkillTaskWorker(mapper, checkpoints,
            new SkillTaskTemplateResolver(objectMapper), json, executor, properties, clock);

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
        when(executor.execute(eq("cap.write"), any(), any(), anyBoolean())).thenReturn(TextNode.valueOf("ok"));
        when(checkpoints.checkpointSuccess(eq(task), eq(step), anyString(), eq("\"ok\""), anyString()))
                .thenReturn(null);

        worker.poll();

        ArgumentCaptor<JsonNode> arguments = ArgumentCaptor.forClass(JsonNode.class);
        ArgumentCaptor<CloudMoldRpcCallContext> context = ArgumentCaptor.forClass(CloudMoldRpcCallContext.class);
        verify(executor).execute(eq("cap.write"), arguments.capture(), context.capture(), eq(true));
        assertThat(arguments.getValue().get(0).asText()).isEqualTo("task-1:write");
        assertThat(context.getValue()).isEqualTo(new CloudMoldRpcCallContext(8L, 42L, 2,
                "skill.write", "task-1"));
        verify(checkpoints).prepareStep(eq(task), eq(step), anyString(), eq("[\"task-1:write\"]"), anyString());
    }

    private static Task task() {
        Task task = new Task();
        task.setTenantId(8L); task.setTaskId("task-1"); task.setRunId("task-1");
        task.setSkillId("skill.write"); task.setSkillVersion("1.0.0"); task.setInputJson("{}");
        task.setRiskLevel("R2"); task.setApprovalRef("approval-1");
        task.setOperatorId(42L); task.setOperatorType(2); task.setStatus("RUNNING");
        task.setCurrentStepCode("write"); task.setAttemptCount(2); task.setMaxAttempts(3);
        return task;
    }

    private static Step step() {
        Step step = new Step();
        step.setTenantId(8L); step.setTaskId("task-1"); step.setStepCode("write"); step.setStepOrder(1);
        step.setCapabilityId("cap.write"); step.setOperationType("WRITE");
        step.setArgumentTemplateJson("[\"$task.stepIdempotencyKey\"]");
        step.setIdempotencyKey("task-1:write"); step.setStatus("RUNNING");
        return step;
    }
}
