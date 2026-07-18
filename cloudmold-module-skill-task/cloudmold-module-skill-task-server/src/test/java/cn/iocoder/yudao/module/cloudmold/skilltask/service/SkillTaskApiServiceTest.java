package cn.iocoder.yudao.module.cloudmold.skilltask.service;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.skilltask.SkillTaskProperties;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskRetryCommand;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskSubmitCommand;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskView;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskMapper;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Task;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.SkillTaskDefinition;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.SkillTaskDefinitionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillTaskApiServiceTest {

    private final SkillTaskMapper mapper = mock(SkillTaskMapper.class);
    private final SkillTaskDefinitionRegistry definitions = mock(SkillTaskDefinitionRegistry.class);
    private final SkillTaskJson json = new SkillTaskJson(new ObjectMapper(), new SkillTaskProperties());
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-18T12:00:00Z"), ZoneOffset.UTC);
    private final SkillTaskApiService service = new SkillTaskApiService(mapper, definitions, json, clock);

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsExistingTaskForAnIdenticalRequestReplay() throws Exception {
        authenticate();
        SkillTaskDefinition definition = readDefinition();
        when(definitions.require("skill.read", "1.0.0")).thenReturn(definition);
        Task existing = task("task-existing", "request-1", "{}", json.sha256("{}"));
        when(mapper.selectByRequestKey(8L, "skill.read", "request-1")).thenReturn(existing);

        SkillTaskView result = service.submit(command("request-1", "{}"));

        assertThat(result.getTaskId()).isEqualTo("task-existing");
        verify(mapper, never()).insertTask(anyLong(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void persistsAFirstSubmissionWithItsStepAndInitialHistory() throws Exception {
        authenticate();
        SkillTaskDefinition definition = readDefinition();
        when(definitions.require("skill.read", "1.0.0")).thenReturn(definition);
        AtomicReference<Task> stored = new AtomicReference<>();
        when(mapper.selectByRequestKey(8L, "skill.read", "request-1")).thenReturn(null);
        when(mapper.insertTask(anyLong(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), anyLong(), any(), any(), any(), any())).thenAnswer(invocation -> {
                    String taskId = invocation.getArgument(1);
                    stored.set(task(taskId, "request-1", "{}", json.sha256("{}")));
                    return 1;
                });
        when(mapper.selectByRequestKeyForUpdate(8L, "skill.read", "request-1"))
                .thenAnswer(invocation -> stored.get());
        when(mapper.selectTask(eq(8L), any())).thenAnswer(invocation -> stored.get());

        SkillTaskView result = service.submit(command("request-1", "{}"));

        assertThat(result.getTaskId()).isNotBlank().isEqualTo(result.getRunId());
        verify(mapper).insertTask(anyLong(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), anyLong(), any(), any(), any(), any());
        verify(mapper).insertStep(8L, result.getTaskId(), "read", 1, "cap.read", "READ", "[]",
                result.getTaskId() + ":read", LocalDateTime.of(2026, 7, 18, 12, 0));
        verify(mapper).insertHistory(8L, result.getTaskId(), 0L, null, "QUEUED", "read",
                "TASK_SUBMITTED", "Skill task accepted", 42L, 2,
                LocalDateTime.of(2026, 7, 18, 12, 0));
    }

    @Test
    void rejectsRequestKeyReplayWithDifferentCanonicalInput() throws Exception {
        authenticate();
        when(definitions.require("skill.read", "1.0.0")).thenReturn(readDefinition());
        Task existing = task("task-existing", "request-1", "{}", json.sha256("{}"));
        when(mapper.selectByRequestKey(8L, "skill.read", "request-1")).thenReturn(existing);

        assertThatThrownBy(() -> service.submit(command("request-1", "{\"sku\":\"SKU-2\"}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different Skill input");
    }

    @Test
    void rejectsR2SubmissionWithoutApprovalEvidence() throws Exception {
        authenticate();
        SkillTaskDefinition definition = readDefinition();
        definition.setRiskLevel("R2");
        when(definitions.require("skill.read", "1.0.0")).thenReturn(definition);
        SkillTaskSubmitCommand command = command("request-1", "{}");
        command.setRiskLevel("R2");

        assertThatThrownBy(() -> service.submit(command))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("approvalRef");
    }

    @Test
    void manualRetryTransfersExecutionActorAndAuditsRetryingOperator() {
        authenticate(99L, 3);
        Task failed = task("task-failed", "request-1", "{}", json.sha256("{}"));
        failed.setStatus("NEEDS_REVIEW");
        failed.setVersion(5L);
        Task queued = task("task-failed", "request-1", "{}", json.sha256("{}"));
        queued.setOperatorId(99L);
        queued.setOperatorType(3);
        queued.setVersion(6L);
        when(mapper.selectTaskForUpdate(8L, "task-failed")).thenReturn(failed);
        when(mapper.retry(8L, "task-failed", 5L, null, 99L, 3,
                LocalDateTime.of(2026, 7, 18, 12, 0))).thenReturn(1);
        when(mapper.selectTask(8L, "task-failed")).thenReturn(queued);

        SkillTaskView result = service.retry(SkillTaskRetryCommand.builder()
                .taskId("task-failed").expectedVersion(5L).reason("operator approved retry").build());

        assertThat(result.getOperatorId()).isEqualTo(99L);
        assertThat(result.getSubmitterId()).isEqualTo(42L);
        verify(mapper).insertHistory(8L, "task-failed", 6L, "NEEDS_REVIEW", "QUEUED", "read",
                "MANUAL_RETRY", "operator approved retry", 99L, 3,
                LocalDateTime.of(2026, 7, 18, 12, 0));
    }

    private void authenticate() {
        authenticate(42L, 2);
    }

    private void authenticate(long operatorId, int operatorType) {
        TenantContextHolder.setTenantId(8L);
        LoginUser user = new LoginUser();
        user.setId(operatorId);
        user.setUserType(operatorType);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private SkillTaskDefinition readDefinition() throws Exception {
        return SkillTaskDefinition.builder().schemaVersion(SkillTaskDefinitionRegistry.SCHEMA_VERSION)
                .skillId("skill.read").skillVersion("1.0.0").riskLevel("R1").maxAttempts(3)
                .steps(List.of(SkillTaskDefinition.Step.builder().stepCode("read").stepOrder(1)
                        .capabilityId("cap.read").operationType("READ").approvalRequired(false)
                        .arguments(new ObjectMapper().readTree("[]")).build())).build();
    }

    private SkillTaskSubmitCommand command(String requestKey, String input) {
        return SkillTaskSubmitCommand.builder().skillId("skill.read").skillVersion("1.0.0")
                .clientRequestKey(requestKey).inputJson(input).riskLevel("R1").build();
    }

    private static Task task(String taskId, String requestKey, String input, String hash) {
        Task task = new Task();
        task.setTenantId(8L); task.setTaskId(taskId); task.setRunId(taskId);
        task.setSkillId("skill.read"); task.setSkillVersion("1.0.0"); task.setClientRequestKey(requestKey);
        task.setInputJson(input); task.setInputSha256(hash); task.setRiskLevel("R1");
        task.setSubmitterId(42L); task.setSubmitterType(2);
        task.setOperatorId(42L); task.setOperatorType(2); task.setStatus("QUEUED"); task.setCurrentStepCode("read");
        task.setAttemptCount(0); task.setMaxAttempts(3); task.setVersion(0L);
        task.setNextRetryAt(LocalDateTime.of(2026, 7, 18, 12, 0));
        task.setCreatedAt(task.getNextRetryAt()); task.setUpdatedAt(task.getNextRetryAt());
        return task;
    }
}
