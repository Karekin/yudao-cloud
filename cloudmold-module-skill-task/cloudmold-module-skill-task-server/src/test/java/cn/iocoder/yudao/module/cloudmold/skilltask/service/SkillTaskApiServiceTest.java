package cn.iocoder.yudao.module.cloudmold.skilltask.service;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.skilltask.SkillTaskProperties;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskRetryCommand;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskSubmitCommand;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalPermitClaims;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalRefCodec;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskMissionLeaseFencePort;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskMissionLeaseFencePort.MissionLeaseFence;
import cn.iocoder.yudao.module.cloudmold.skilltask.approval.SkillTaskApprovalEvidence;
import cn.iocoder.yudao.module.cloudmold.skilltask.approval.SkillTaskApprovalVerifier;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskMapper;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.PermitConsumption;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Task;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.SkillTaskDefinition;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.SkillTaskDefinitionRegistry;
import cn.iocoder.yudao.module.cloudmold.skilltask.service.query.ManagedSkillTaskQueryService;
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
import java.util.Optional;
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
    private final ManagedSkillTaskQueryService managedSkillTaskQueryService = mock(ManagedSkillTaskQueryService.class);
    private final SkillTaskApprovalVerifier approvalVerifier = mock(SkillTaskApprovalVerifier.class);
    private final SkillTaskMissionLeaseFencePort missionLeaseFenceApi = mock(SkillTaskMissionLeaseFencePort.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-18T12:00:00Z"), ZoneOffset.UTC);
    private final SkillTaskApiService service = new SkillTaskApiService(mapper, definitions, json,
            managedSkillTaskQueryService, approvalVerifier, Optional.of(missionLeaseFenceApi), clock);

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
        verify(mapper, never()).insertTask(anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void persistsAFirstSubmissionWithItsStepAndInitialHistory() throws Exception {
        authenticate();
        SkillTaskDefinition definition = readDefinition();
        when(definitions.require("skill.read", "1.0.0")).thenReturn(definition);
        AtomicReference<Task> stored = new AtomicReference<>();
        when(mapper.selectByRequestKey(8L, "skill.read", "request-1")).thenReturn(null);
        when(mapper.insertTask(anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), anyLong(), any(), any(), any(), any())).thenAnswer(invocation -> {
                    String taskId = invocation.getArgument(1);
                    Task created = task(taskId, "request-1", "{}", json.sha256("{}"));
                    created.setRiskLevel(invocation.getArgument(10));
                    created.setApprovalRef(invocation.getArgument(11));
                    stored.set(created);
                    return 1;
                });
        when(mapper.selectByRequestKeyForUpdate(8L, "skill.read", "request-1"))
                .thenAnswer(invocation -> stored.get());
        when(mapper.selectTask(eq(8L), any())).thenAnswer(invocation -> stored.get());

        SkillTaskView result = service.submit(command("request-1", "{}"));

        assertThat(result.getTaskId()).isNotBlank().isEqualTo(result.getRunId());
        assertThat(result.getApprovalSummary()).isNull();
        verify(mapper).insertTask(anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), anyLong(), any(), any(), any(), any());
        verify(mapper).insertStep(8L, result.getTaskId(), "read", 1, "cap.read", "READ", "[]",
                result.getTaskId() + ".read", LocalDateTime.of(2026, 7, 18, 12, 0));
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
    void verifiesR3ApprovalBeforePersistingAnyTask() throws Exception {
        authenticate();
        SkillTaskDefinition definition = readDefinition();
        definition.setRiskLevel("R3");
        when(definitions.require("skill.read", "1.0.0")).thenReturn(definition);
        SkillTaskSubmitCommand command = command("request-r3", "{}");
        command.setRiskLevel("R3");
        command.setApprovalRef("cma1:approval-r3:1784380000:" + "a".repeat(64));
        when(approvalVerifier.verify(any())).thenThrow(new SecurityException("approval scope rejected"));

        assertThatThrownBy(() -> service.submit(command))
                .isInstanceOf(SecurityException.class)
                .hasMessage("approval scope rejected");
        verify(mapper, never()).insertTask(anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void consumesCma3PermitOnceAndRejectsDifferentRequestKeyReplay() throws Exception {
        authenticate();
        SkillTaskDefinition definition = readDefinition();
        definition.setRiskLevel("R3");
        when(definitions.require("skill.read", "1.0.0")).thenReturn(definition);
        SkillTaskSubmitCommand first = command("request-1", "{}");
        first.setRiskLevel("R3");
        SkillTaskSubmitCommand replay = command("request-2", "{}");
        replay.setRiskLevel("R3");
        SkillTaskApprovalEvidence evidence = approvalEvidence("permit-1", "approval-r3-1",
                definition.getDefinitionClosureSha256());
        String approvalRef = validClaimsReference(evidence.claims());
        first.setApprovalRef(approvalRef);
        replay.setApprovalRef(approvalRef);
        when(approvalVerifier.verify(any())).thenReturn(evidence);
        AtomicReference<Task> stored = new AtomicReference<>();
        when(mapper.selectByRequestKey(8L, "skill.read", "request-1")).thenReturn(null);
        when(mapper.insertTask(anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), anyLong(), any(), any(), any(), any())).thenAnswer(invocation -> {
                    String taskId = invocation.getArgument(1);
                    String requestKey = invocation.getArgument(5);
                    Task created = task(taskId, requestKey, "{}", json.sha256("{}"));
                    created.setRiskLevel(invocation.getArgument(10));
                    created.setApprovalRef(invocation.getArgument(11));
                    stored.set(created);
                    return 1;
                });
        when(mapper.selectByRequestKeyForUpdate(eq(8L), eq("skill.read"), any())).thenAnswer(invocation -> stored.get());
        when(mapper.selectTask(eq(8L), any())).thenAnswer(invocation -> stored.get());
        when(mapper.selectPermitConsumption(8L, "permit-1")).thenAnswer(invocation ->
                consumption(evidence, stored.get().getTaskId(), "request-1",
                        definition.getDefinitionClosureSha256(), json.sha256("{}"), "R3"));

        service.submit(first);

        when(mapper.selectByRequestKey(8L, "skill.read", "request-2")).thenReturn(null);
        assertThatThrownBy(() -> service.submit(replay))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different clientRequestKey");
    }

    @Test
    void rejectsOldMissionPermitWhenTakeoverWinsBeforeSubmit() throws Exception {
        authenticate();
        SkillTaskDefinition definition = readDefinition();
        definition.setRiskLevel("R3");
        when(definitions.require("skill.read", "1.0.0")).thenReturn(definition);
        SkillTaskApprovalEvidence evidence = missionApprovalEvidence(
                definition.getDefinitionClosureSha256(), "run-1", "worker-1", 2L, 3L);
        SkillTaskSubmitCommand command = command("request-takeover", "{}");
        command.setRiskLevel("R3");
        command.setRunId("run-1");
        command.setApprovalRef(validClaimsReference(evidence.claims()));
        when(approvalVerifier.verify(any())).thenReturn(evidence);
        org.mockito.Mockito.doThrow(new SecurityException("mission lease fence is stale"))
                .when(missionLeaseFenceApi).validateCurrentLease(any());

        assertThatThrownBy(() -> service.submit(command))
                .isInstanceOf(SecurityException.class)
                .hasMessage("mission lease fence is stale");
        verify(missionLeaseFenceApi).validateCurrentLease(new MissionLeaseFence(
                8L, "wo-r3-1", "run-1", "worker-1", 3L, 2L));
        verify(mapper, never()).insertTask(anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void revalidatesThePersistedMissionFenceBeforeReturningAnIdempotentReplay() throws Exception {
        authenticate();
        SkillTaskDefinition definition = readDefinition();
        definition.setRiskLevel("R3");
        when(definitions.require("skill.read", "1.0.0")).thenReturn(definition);
        SkillTaskApprovalEvidence evidence = missionApprovalEvidence(
                definition.getDefinitionClosureSha256(), "run-1", "worker-1", 2L, 3L);
        String approvalRef = validClaimsReference(evidence.claims());
        Task existing = task("task-existing", "request-1", "{}", json.sha256("{}"));
        existing.setRunId("run-1");
        existing.setRiskLevel("R3");
        existing.setApprovalRef(approvalRef);
        when(mapper.selectByRequestKey(8L, "skill.read", "request-1")).thenReturn(existing);
        org.mockito.Mockito.doThrow(new SecurityException("mission lease fence is stale"))
                .when(missionLeaseFenceApi).validateCurrentLease(any());
        SkillTaskSubmitCommand replay = command("request-1", "{}");
        replay.setRiskLevel("R3");
        replay.setRunId("run-1");
        replay.setApprovalRef(approvalRef);

        assertThatThrownBy(() -> service.submit(replay))
                .isInstanceOf(SecurityException.class)
                .hasMessage("mission lease fence is stale");
        verify(approvalVerifier, never()).verify(any());
    }

    @Test
    void comparesTheCompleteApprovalReferenceOnRequestKeyReplay() throws Exception {
        authenticate();
        SkillTaskDefinition definition = readDefinition();
        definition.setRiskLevel("R3");
        when(definitions.require("skill.read", "1.0.0")).thenReturn(definition);
        Task existing = task("task-existing", "request-1", "{}", json.sha256("{}"));
        existing.setRiskLevel("R3");
        existing.setApprovalRef("cma3:key-1:old:signature");
        when(mapper.selectByRequestKey(8L, "skill.read", "request-1")).thenReturn(existing);
        SkillTaskSubmitCommand replay = command("request-1", "{}");
        replay.setRiskLevel("R3");
        replay.setApprovalRef("cma3:key-1:new:signature");

        assertThatThrownBy(() -> service.submit(replay))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different approvalRef");
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

    @Test
    void terminalProofApiRejectsAnyNonTerminalTask() {
        authenticate();
        Task running = task("task-running", "request-1", "{}", json.sha256("{}"));
        running.setStatus("RUNNING");
        when(mapper.selectTask(8L, "task-running")).thenReturn(running);

        assertThatThrownBy(() -> service.getTerminalProof("task-running"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("terminal proof");
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
                .definitionSha256("d".repeat(64)).definitionClosureSha256("c".repeat(64))
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
        task.setDefinitionSha256("d".repeat(64)); task.setDefinitionClosureSha256("c".repeat(64));
        task.setSubmitterId(42L); task.setSubmitterType(2);
        task.setOperatorId(42L); task.setOperatorType(2); task.setStatus("QUEUED"); task.setCurrentStepCode("read");
        task.setAttemptCount(0); task.setMaxAttempts(3); task.setVersion(0L);
        task.setNextRetryAt(LocalDateTime.of(2026, 7, 18, 12, 0));
        task.setCreatedAt(task.getNextRetryAt()); task.setUpdatedAt(task.getNextRetryAt());
        return task;
    }

    private SkillTaskApprovalEvidence approvalEvidence(String permitId, String approvalId, String definitionClosureSha256) {
        SkillTaskApprovalPermitClaims claims = new SkillTaskApprovalPermitClaims("cma3", "risk-1",
                "cloudmold.agent-control", "cloudmold.skill-task", permitId, "wo-r3-1", approvalId,
                "f".repeat(64), "skill.read", "1.0.0", definitionClosureSha256, json.sha256("{}"), "R3",
                "2:42", 8L, Instant.parse("2026-07-18T12:00:00Z"), Instant.parse("2026-07-18T12:00:00Z"),
                Instant.parse("2026-07-18T12:05:00Z"));
        return new SkillTaskApprovalEvidence(claims, "e".repeat(64), "cma3:hmac");
    }

    private SkillTaskApprovalEvidence missionApprovalEvidence(String definitionClosureSha256,
                                                              String runId, String leaseOwner,
                                                              long fencingToken, long leaseEpoch) {
        SkillTaskApprovalPermitClaims claims = new SkillTaskApprovalPermitClaims("cma3", "risk-1",
                "cloudmold.agent-control", "cloudmold.skill-task", "permit-mission-1", "wo-r3-1",
                "approval-r3-1", "f".repeat(64), "skill.read", "1.0.0",
                definitionClosureSha256, json.sha256("{}"), "R3", "2:42", 8L,
                Instant.parse("2026-07-18T12:00:00Z"), Instant.parse("2026-07-18T12:00:00Z"),
                Instant.parse("2026-07-18T12:05:00Z"), runId, fencingToken, leaseOwner, leaseEpoch);
        return new SkillTaskApprovalEvidence(claims, "e".repeat(64), "cma3:hmac");
    }

    private static PermitConsumption consumption(SkillTaskApprovalEvidence evidence, String taskId,
                                                 String requestKey, String definitionClosureSha256,
                                                 String inputSha256, String riskLevel) {
        PermitConsumption consumed = new PermitConsumption();
        consumed.setTenantId(8L);
        consumed.setPermitId(evidence.permitId());
        consumed.setApprovalId(evidence.approvalId());
        consumed.setWorkOrderId(evidence.workOrderId());
        consumed.setRootRequestIdentity(evidence.rootRequestIdentity());
        consumed.setClientRequestKey(requestKey);
        consumed.setTaskId(taskId);
        consumed.setApprovalRefSha256(evidence.referenceSha256());
        consumed.setDefinitionClosureSha256(definitionClosureSha256);
        consumed.setInputSha256(inputSha256);
        consumed.setRiskLevel(riskLevel);
        return consumed;
    }

    private static String validClaimsReference(SkillTaskApprovalPermitClaims claims) {
        return SkillTaskApprovalRefCodec.issueClaims("0123456789abcdef0123456789abcdef".getBytes(), claims);
    }
}
