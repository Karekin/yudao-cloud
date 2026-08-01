package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.WorkflowRegistryApprovalMapper;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.WorkflowRegistryEvaluationMapper;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.WorkflowRegistryPointerMapper;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.WorkflowRegistryReleaseMapper;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.WorkflowRegistryVersionMapper;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.WorkflowRegistryValidationRequestMapper;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.dataobject.WorkflowRegistryApprovalDO;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.dataobject.WorkflowRegistryEvaluationDO;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.dataobject.WorkflowRegistryPointerDO;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.dataobject.WorkflowRegistryReleaseDO;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.dataobject.WorkflowRegistryVersionDO;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.dataobject.WorkflowRegistryValidationRequestDO;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.request.WorkflowRegistryApprovalRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.request.WorkflowRegistryEvaluationRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.request.WorkflowRegistryRetirementRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.request.WorkflowRegistryValidationStartRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowRegistryGovernanceServiceTest {

    private final WorkflowRegistryPointerMapper pointerMapper = mock(WorkflowRegistryPointerMapper.class);
    private final WorkflowRegistryVersionMapper versionMapper = mock(WorkflowRegistryVersionMapper.class);
    private final WorkflowRegistryEvaluationMapper evaluationMapper = mock(WorkflowRegistryEvaluationMapper.class);
    private final WorkflowRegistryApprovalMapper approvalMapper = mock(WorkflowRegistryApprovalMapper.class);
    private final WorkflowRegistryReleaseMapper releaseMapper = mock(WorkflowRegistryReleaseMapper.class);
    private final WorkflowRegistryValidationRequestMapper validationRequestMapper =
            mock(WorkflowRegistryValidationRequestMapper.class);
    private final WorkflowEvaluationAttestationVerifier evaluationAttestationVerifier =
            mock(WorkflowEvaluationAttestationVerifier.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkflowRegistryGovernanceService service =
            new WorkflowRegistryGovernanceService(pointerMapper, versionMapper, evaluationMapper,
                    approvalMapper, releaseMapper, validationRequestMapper, evaluationAttestationVerifier, objectMapper);

    private MockedStatic<SecurityFrameworkUtils> security;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(162L);
        security = mockStatic(SecurityFrameworkUtils.class);
        security.when(SecurityFrameworkUtils::getLoginUser).thenReturn(loginUser(162L, 101L));
        when(versionMapper.updateStatus(any(), any(), any(), any())).thenReturn(1);
        when(validationRequestMapper.markCompleted(eq(162L), any(), any())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        security.close();
    }

    @Test
    void autoPromotesE1AfterReplayShadowCanaryAndGuardrailsPass() {
        WorkflowRegistryEvaluationRequest request = evaluationRequest("E1");
        WorkflowRegistryPointerDO pointer = pointer("stable-1", "candidate-1", 3L);
        WorkflowRegistryVersionDO stable = version("stable-1", null, "E0", "2:88");
        WorkflowRegistryVersionDO candidate = version("candidate-1", "stable-1", "E1", "2:88")
                .setRegistryStatus("VALIDATING");
        when(evaluationMapper.selectByIdempotencyKey(162L, request.getIdempotencyKey())).thenReturn(null);
        stubValidation(request);
        when(pointerMapper.selectForUpdate(162L, skillId())).thenReturn(pointer);
        when(versionMapper.selectByRegistryVersionId(162L, "stable-1")).thenReturn(stable);
        when(versionMapper.selectByRegistryVersionId(162L, "candidate-1")).thenReturn(candidate);
        when(pointerMapper.promoteCandidateToStableCas(eq(162L), eq(skillId()), eq("candidate-1"), eq(3L),
                eq("2:101"), any()))
                .thenReturn(1);

        var result = service.recordEvaluation(request);

        assertThat(result.currentStatus()).isEqualTo("ACTIVE");
        assertThat(result.stableVersionId()).isEqualTo("candidate-1");
        assertThat(result.candidateVersionId()).isNull();
        assertThat(result.pointerVersion()).isEqualTo(4L);
        verify(pointerMapper).promoteCandidateToStableCas(eq(162L), eq(skillId()), eq("candidate-1"), eq(3L),
                eq("2:101"), any());
        verify(releaseMapper, times(6)).insert(any(WorkflowRegistryReleaseDO.class));
    }

    @Test
    void canaryFailureClearsCandidateKeepsStableAndRecordsRollback() {
        WorkflowRegistryEvaluationRequest request = evaluationRequest("E1");
        request.setCanaryPassed(false);
        WorkflowRegistryPointerDO pointer = pointer("stable-1", "candidate-1", 3L);
        WorkflowRegistryVersionDO stable = version("stable-1", null, "E0", "2:88");
        WorkflowRegistryVersionDO candidate = version("candidate-1", "stable-1", "E1", "2:88")
                .setRegistryStatus("VALIDATING");
        when(evaluationMapper.selectByIdempotencyKey(162L, request.getIdempotencyKey())).thenReturn(null);
        stubValidation(request);
        when(pointerMapper.selectForUpdate(162L, skillId())).thenReturn(pointer);
        when(versionMapper.selectByRegistryVersionId(162L, "stable-1")).thenReturn(stable);
        when(versionMapper.selectByRegistryVersionId(162L, "candidate-1")).thenReturn(candidate);
        when(pointerMapper.clearCandidateCas(eq(162L), eq(skillId()), eq("candidate-1"), eq(3L),
                eq("2:101"), any()))
                .thenReturn(1);

        var result = service.recordEvaluation(request);

        assertThat(result.currentStatus()).isEqualTo("ROLLED_BACK");
        assertThat(result.stableVersionId()).isEqualTo("stable-1");
        assertThat(result.candidateVersionId()).isNull();
        verify(pointerMapper).clearCandidateCas(eq(162L), eq(skillId()), eq("candidate-1"), eq(3L),
                eq("2:101"), any());
        ArgumentCaptor<WorkflowRegistryReleaseDO> releases = ArgumentCaptor.forClass(WorkflowRegistryReleaseDO.class);
        verify(releaseMapper, times(5)).insert(releases.capture());
        assertThat(releases.getAllValues().get(4).getTargetStatus()).isEqualTo("ROLLED_BACK");
        assertThat(releases.getAllValues().get(4).getReleaseReason()).isEqualTo("CANARY_FAILED");
    }

    @Test
    void e2ApprovalRequiresIndependentApproverAndPromotesCandidate() {
        WorkflowRegistryEvaluationRequest evaluationRequest = evaluationRequest("E2");
        WorkflowRegistryPointerDO pointer = pointer("stable-1", "candidate-1", 3L);
        WorkflowRegistryVersionDO stable = version("stable-1", null, "E0", "2:88");
        WorkflowRegistryVersionDO candidate = version("candidate-1", "stable-1", "E2", "2:88")
                .setRegistryStatus("VALIDATING");
        when(evaluationMapper.selectByIdempotencyKey(162L, evaluationRequest.getIdempotencyKey())).thenReturn(null);
        stubValidation(evaluationRequest);
        when(pointerMapper.selectForUpdate(162L, skillId())).thenReturn(pointer);
        when(versionMapper.selectByRegistryVersionId(162L, "stable-1")).thenReturn(stable);
        when(versionMapper.selectByRegistryVersionId(162L, "candidate-1")).thenReturn(candidate);

        var canary = service.recordEvaluation(evaluationRequest);
        assertThat(canary.currentStatus()).isEqualTo("CANARY");
        verify(pointerMapper, never()).promoteCandidateToStableCas(eq(162L), eq(skillId()), eq("candidate-1"), eq(3L), eq("2:101"), any());

        WorkflowRegistryEvaluationDO storedEvaluation = new WorkflowRegistryEvaluationDO()
                .setEvaluationId(canary.evaluationId())
                .setTenantId(162L)
                .setSkillId(skillId())
                .setRegistryVersionId("candidate-1")
                .setEvaluationStatus("CANARY")
                .setEvaluatorSubject("2:101");
        WorkflowRegistryApprovalRequest approvalRequest = approvalRequest(canary.evaluationId());
        when(approvalMapper.selectByIdempotencyKey(162L, approvalRequest.getIdempotencyKey())).thenReturn(null);
        when(pointerMapper.selectForUpdate(162L, skillId())).thenReturn(pointer);
        when(evaluationMapper.selectByEvaluationId(162L, canary.evaluationId())).thenReturn(storedEvaluation);
        when(pointerMapper.promoteCandidateToStableCas(eq(162L), eq(skillId()), eq("candidate-1"), eq(3L),
                eq("2:202"), any()))
                .thenReturn(1);
        security.when(SecurityFrameworkUtils::getLoginUser).thenReturn(loginUser(162L, 202L));

        var result = service.recordApproval(approvalRequest);

        assertThat(result.currentStatus()).isEqualTo("ACTIVE");
        assertThat(result.stableVersionId()).isEqualTo("candidate-1");
        verify(pointerMapper).promoteCandidateToStableCas(eq(162L), eq(skillId()), eq("candidate-1"), eq(3L),
                eq("2:202"), any());
    }

    @Test
    void approverCannotAlsoBeEvaluatorOrProposer() {
        WorkflowRegistryApprovalRequest request = approvalRequest("wre-1");
        WorkflowRegistryPointerDO pointer = pointer("stable-1", "candidate-1", 3L);
        WorkflowRegistryVersionDO stable = version("stable-1", null, "E0", "2:88");
        WorkflowRegistryVersionDO candidate = version("candidate-1", "stable-1", "E2", "2:101")
                .setRegistryStatus("READY_FOR_REVIEW");
        WorkflowRegistryEvaluationDO evaluation = new WorkflowRegistryEvaluationDO()
                .setEvaluationId("wre-1")
                .setTenantId(162L)
                .setSkillId(skillId())
                .setRegistryVersionId("candidate-1")
                .setEvaluationStatus("CANARY")
                .setEvaluatorSubject("2:101");
        when(approvalMapper.selectByIdempotencyKey(162L, request.getIdempotencyKey())).thenReturn(null);
        when(pointerMapper.selectForUpdate(162L, skillId())).thenReturn(pointer);
        when(versionMapper.selectByRegistryVersionId(162L, "stable-1")).thenReturn(stable);
        when(versionMapper.selectByRegistryVersionId(162L, "candidate-1")).thenReturn(candidate);
        when(evaluationMapper.selectByEvaluationId(162L, "wre-1")).thenReturn(evaluation);

        assertThatThrownBy(() -> service.recordApproval(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("independent");
    }

    @Test
    void activeRollbackRestoresPreviousStable() {
        WorkflowRegistryRetirementRequest request = retirementRequest("ROLLBACK_ACTIVE");
        WorkflowRegistryPointerDO pointer = pointer("stable-2", null, 4L);
        WorkflowRegistryVersionDO currentStable = version("stable-2", "stable-1", "E1", "2:88")
                .setRegistryStatus("ACTIVE");
        WorkflowRegistryVersionDO previousStable = version("stable-1", null, "E0", "2:77")
                .setRegistryStatus("RETIRED");
        when(pointerMapper.selectForUpdate(162L, skillId())).thenReturn(pointer);
        when(versionMapper.selectByRegistryVersionId(162L, "stable-2")).thenReturn(currentStable);
        when(versionMapper.selectByRegistryVersionId(162L, "stable-1")).thenReturn(previousStable);
        when(pointerMapper.rollbackStableCas(eq(162L), eq(skillId()), eq("stable-2"), eq("stable-1"), eq(4L),
                eq("2:101"), any()))
                .thenReturn(1);

        var result = service.retire(request);

        assertThat(result.currentStatus()).isEqualTo("ROLLED_BACK");
        assertThat(result.stableVersionId()).isEqualTo("stable-1");
        verify(pointerMapper).rollbackStableCas(eq(162L), eq(skillId()), eq("stable-2"), eq("stable-1"), eq(4L),
                eq("2:101"), any());
        verify(releaseMapper, times(2)).insert(any(WorkflowRegistryReleaseDO.class));
    }

    @Test
    void armsPersistentKillSwitchAndReplaysIdempotently() {
        WorkflowRegistryRetirementRequest request = retirementRequest("ENABLE_KILL_SWITCH");
        WorkflowRegistryPointerDO pointer = pointer("stable-2", null, 4L).setKillSwitchEnabled(false);
        WorkflowRegistryVersionDO stable = version("stable-2", "stable-1", "E1", "2:88")
                .setRegistryStatus("ACTIVE");
        when(releaseMapper.selectBySourceKeyAndReason(162L, "retire-key", "KILL_SWITCH_ARMED"))
                .thenReturn(null);
        when(pointerMapper.selectForUpdate(162L, skillId())).thenReturn(pointer);
        when(versionMapper.selectByRegistryVersionId(162L, "stable-2")).thenReturn(stable);
        when(pointerMapper.armKillSwitchCas(eq(162L), eq(skillId()), eq("stable-2"), eq(4L),
                eq("2:101"), any())).thenReturn(1);

        var result = service.retire(request);

        assertThat(result.currentStatus()).isEqualTo("BLOCKED");
        assertThat(result.killSwitchEnabled()).isTrue();
        verify(pointerMapper).armKillSwitchCas(eq(162L), eq(skillId()), eq("stable-2"), eq(4L),
                eq("2:101"), any());

        WorkflowRegistryReleaseDO prior = new WorkflowRegistryReleaseDO()
                .setReleaseId("wrr-existing")
                .setTenantId(162L)
                .setSkillId(skillId())
                .setRegistryVersionId("stable-2")
                .setReleaseReason("KILL_SWITCH_ARMED");
        when(releaseMapper.selectBySourceKeyAndReason(162L, "retire-key", "KILL_SWITCH_ARMED"))
                .thenReturn(prior);
        when(pointerMapper.selectBySkillId(162L, skillId())).thenReturn(pointer.setKillSwitchEnabled(true));
        when(releaseMapper.selectLatestBySkill(162L, skillId())).thenReturn(prior);

        assertThat(service.retire(request).duplicate()).isTrue();
    }

    @Test
    void proposerMayRequestValidationButCannotRecordItsOwnVerdict() {
        WorkflowRegistryValidationStartRequest request = new WorkflowRegistryValidationStartRequest();
        request.setWorkflowId(skillId());
        request.setCandidateVersionId("candidate-1");
        request.setExpectedPointerVersion(3L);
        request.setIdempotencyKey("validation-start-1");
        WorkflowRegistryPointerDO pointer = pointer("stable-1", "candidate-1", 3L);
        WorkflowRegistryVersionDO stable = version("stable-1", null, "E0", "2:77").setRegistryStatus("ACTIVE");
        WorkflowRegistryVersionDO candidate = version("candidate-1", "stable-1", "E1", "2:101");
        when(validationRequestMapper.selectByIdempotencyKey(162L, "validation-start-1")).thenReturn(null);
        when(pointerMapper.selectForUpdate(162L, skillId())).thenReturn(pointer);
        when(versionMapper.selectByRegistryVersionId(162L, "stable-1")).thenReturn(stable);
        when(versionMapper.selectByRegistryVersionId(162L, "candidate-1")).thenReturn(candidate);

        var started = service.startValidation(request);

        assertThat(started.currentStatus()).isEqualTo("VALIDATING");
        assertThat(started.validationRequestId()).startsWith("wrq-");
        assertThat(candidate.getRegistryStatus()).isEqualTo("VALIDATING");
        assertThatThrownBy(() -> service.recordEvaluation(evaluationRequest("E1")))
                .hasMessageContaining("independent");
    }

    @Test
    void listsTenantScopedGovernancePointersWithinBoundedLimit() {
        WorkflowRegistryPointerDO pointer = pointer("stable-1", "candidate-1", 3L);
        when(pointerMapper.selectByTenant(162L, 100)).thenReturn(List.of(pointer));
        when(pointerMapper.selectBySkillId(162L, skillId())).thenReturn(pointer);

        assertThat(service.listStatuses(100)).singleElement().satisfies(status -> {
            assertThat(status.workflowId()).isEqualTo(skillId());
            assertThat(status.stableVersionId()).isEqualTo("stable-1");
            assertThat(status.candidateVersionId()).isEqualTo("candidate-1");
        });
        assertThatThrownBy(() -> service.listStatuses(201)).hasMessageContaining("between 1 and 200");
    }

    private WorkflowRegistryEvaluationRequest evaluationRequest(String riskLevel) {
        WorkflowRegistryEvaluationRequest request = new WorkflowRegistryEvaluationRequest();
        request.setWorkflowId(skillId());
        request.setCandidateVersionId("candidate-1");
        request.setExpectedPointerVersion(3L);
        request.setIdempotencyKey("eval-key-" + riskLevel);
        request.setValidationRequestId("wrq-1");
        request.setEvaluatorRunId("evaluator-run-" + riskLevel);
        request.setDatasetSha256("d".repeat(64));
        request.setSampleCount(100);
        request.setObservationStartedAt(LocalDateTime.of(2026, 7, 25, 0, 0));
        request.setObservationEndedAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        request.setReplayPassed(true);
        request.setShadowPassed(true);
        request.setCanaryPassed(true);
        request.setGuardrailsPassed(true);
        request.setSamplePassed(true);
        request.setDqcPassed(true);
        request.setObservationWindowPassed(true);
        request.setEvidenceRefs(objectMapper.createArrayNode().add("evidence://sha256/123"));
        ObjectNode metrics = objectMapper.createObjectNode();
        metrics.put("replay_success_count", 12);
        request.setMetrics(metrics);
        request.setFailureSamples(objectMapper.createArrayNode());
        request.setValidatorAttestation(objectMapper.createObjectNode().put("signature", "attested"));
        return request;
    }

    private void stubValidation(WorkflowRegistryEvaluationRequest request) {
        WorkflowRegistryValidationRequestDO validation = new WorkflowRegistryValidationRequestDO()
                .setValidationRequestId(request.getValidationRequestId())
                .setTenantId(162L)
                .setSkillId(skillId())
                .setRegistryVersionId("candidate-1")
                .setPointerVersion(3L)
                .setChallenge("challenge")
                .setRequestStatus("REQUESTED")
                .setRequestedBySubject("2:999")
                .setExpiresAt(LocalDateTime.now().plusHours(1));
        when(validationRequestMapper.selectForUpdate(162L, request.getValidationRequestId())).thenReturn(validation);
    }

    private WorkflowRegistryApprovalRequest approvalRequest(String evaluationId) {
        WorkflowRegistryApprovalRequest request = new WorkflowRegistryApprovalRequest();
        request.setWorkflowId(skillId());
        request.setCandidateVersionId("candidate-1");
        request.setEvaluationId(evaluationId);
        request.setExpectedPointerVersion(3L);
        request.setIdempotencyKey("approval-key");
        request.setDecision("APPROVE");
        request.setRationale("independent-review");
        request.setEvidenceRefs(objectMapper.createArrayNode().add("evidence://sha256/abc"));
        request.setMetrics(objectMapper.createObjectNode().put("approval_confidence", 0.99));
        return request;
    }

    private WorkflowRegistryRetirementRequest retirementRequest(String action) {
        WorkflowRegistryRetirementRequest request = new WorkflowRegistryRetirementRequest();
        request.setWorkflowId(skillId());
        request.setTargetVersionId("stable-2");
        request.setAction(action);
        request.setExpectedPointerVersion(4L);
        request.setIdempotencyKey("retire-key");
        request.setReason("guardrail-failure");
        request.setEvidenceRefs(objectMapper.createArrayNode().add("evidence://sha256/rollback"));
        request.setMetrics(objectMapper.createObjectNode().put("error_rate", 0.42));
        return request;
    }

    private WorkflowRegistryPointerDO pointer(String stableId, String candidateId, long pointerVersion) {
        return new WorkflowRegistryPointerDO().setTenantId(162L).setSkillId(skillId())
                .setStableVersionId(stableId).setCandidateVersionId(candidateId)
                .setPointerVersion(pointerVersion)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
    }

    private WorkflowRegistryVersionDO version(String versionId, String parentVersionId,
                                              String riskLevel, String proposedBy) {
        return new WorkflowRegistryVersionDO()
                .setRegistryVersionId(versionId)
                .setTenantId(162L)
                .setSkillId(skillId())
                .setParentRegistryVersionId(parentVersionId)
                .setRiskLevel(riskLevel)
                .setProposedBy(proposedBy)
                .setProposalJson("{\"minimum_sample\":100,\"observation_window\":\"7d\"}")
                .setRegistryStatus("SUBMITTED");
    }

    private LoginUser loginUser(Long tenantId, Long userId) {
        LoginUser user = new LoginUser();
        user.setId(userId);
        user.setUserType(2);
        user.setTenantId(tenantId);
        user.setVisitTenantId(tenantId);
        return user;
    }

    private String skillId() {
        return "skill.cloudmold.inventory.stockout-diagnosis.v1";
    }
}
