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
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.view.WorkflowRegistryGovernanceStatusView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class WorkflowRegistryGovernanceService {

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9_.:-]{1,191}$");
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern WINDOW = Pattern.compile("^(\\d+)([mhd])$");
    private static final Duration VALIDATION_REQUEST_TTL = Duration.ofHours(24);
    private static final Set<String> RISKS_REQUIRING_APPROVAL = Set.of("E2", "E3");
    private static final Set<String> RETIREMENT_ACTIONS = Set.of(
            "KILL_SWITCH_CANDIDATE", "ROLLBACK_ACTIVE", "ENABLE_KILL_SWITCH", "DISABLE_KILL_SWITCH");
    private static final Set<String> APPROVAL_DECISIONS = Set.of("APPROVE", "REJECT");

    private final WorkflowRegistryPointerMapper pointerMapper;
    private final WorkflowRegistryVersionMapper versionMapper;
    private final WorkflowRegistryEvaluationMapper evaluationMapper;
    private final WorkflowRegistryApprovalMapper approvalMapper;
    private final WorkflowRegistryReleaseMapper releaseMapper;
    private final WorkflowRegistryValidationRequestMapper validationRequestMapper;
    private final WorkflowEvaluationAttestationVerifier evaluationAttestationVerifier;
    private final ObjectMapper objectMapper;

    public WorkflowRegistryGovernanceService(WorkflowRegistryPointerMapper pointerMapper,
                                             WorkflowRegistryVersionMapper versionMapper,
                                             WorkflowRegistryEvaluationMapper evaluationMapper,
                                             WorkflowRegistryApprovalMapper approvalMapper,
                                             WorkflowRegistryReleaseMapper releaseMapper,
                                             WorkflowRegistryValidationRequestMapper validationRequestMapper,
                                             WorkflowEvaluationAttestationVerifier evaluationAttestationVerifier,
                                             ObjectMapper objectMapper) {
        this.pointerMapper = pointerMapper;
        this.versionMapper = versionMapper;
        this.evaluationMapper = evaluationMapper;
        this.approvalMapper = approvalMapper;
        this.releaseMapper = releaseMapper;
        this.validationRequestMapper = validationRequestMapper;
        this.evaluationAttestationVerifier = evaluationAttestationVerifier;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkflowRegistryGovernanceStatusView startValidation(WorkflowRegistryValidationStartRequest request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Actor actor = requireActor(tenantId);
        validateValidationStartRequest(request);
        WorkflowRegistryValidationRequestDO duplicate = validationRequestMapper.selectByIdempotencyKey(
                tenantId, request.getIdempotencyKey());
        if (duplicate != null) {
            ensureDuplicateTarget(request.getWorkflowId(), request.getCandidateVersionId(), duplicate.getSkillId(),
                    duplicate.getRegistryVersionId(), "validation request");
            return validationStatus(duplicate, true, null);
        }

        WorkflowRegistryPointerDO pointer = requirePointerForCandidate(tenantId, request.getWorkflowId(),
                request.getCandidateVersionId(), request.getExpectedPointerVersion());
        WorkflowRegistryVersionDO candidate = requireVersion(tenantId, pointer.getCandidateVersionId(), "candidate");
        WorkflowRegistryVersionDO stable = requireVersion(tenantId, pointer.getStableVersionId(), "stable");
        transitionVersion(tenantId, candidate, "SUBMITTED", "VALIDATING");
        LocalDateTime now = LocalDateTime.now();
        WorkflowRegistryValidationRequestDO validation = new WorkflowRegistryValidationRequestDO()
                .setValidationRequestId("wrq-" + UUID.randomUUID())
                .setTenantId(tenantId)
                .setSkillId(request.getWorkflowId())
                .setRegistryVersionId(candidate.getRegistryVersionId())
                .setPointerVersion(pointer.getPointerVersion())
                .setChallenge(UUID.randomUUID().toString())
                .setRequestStatus("REQUESTED")
                .setRequestedBySubject(actor.label())
                .setIdempotencyKey(request.getIdempotencyKey())
                .setExpiresAt(now.plus(VALIDATION_REQUEST_TTL))
                .setCreatedAt(now);
        validationRequestMapper.insert(validation);
        WorkflowRegistryReleaseDO release = appendRelease(pointer, candidate, stable, "VALIDATING",
                "VALIDATION_REQUESTED", actor.label(), request.getIdempotencyKey(), false,
                objectMapper.createArrayNode(), objectMapper.createObjectNode(), now);
        return validationStatus(validation, false, release.getReleaseId());
    }

    @Transactional
    public WorkflowRegistryGovernanceStatusView recordEvaluation(WorkflowRegistryEvaluationRequest request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Actor actor = requireActor(tenantId);
        validateEvaluationRequest(request);
        WorkflowRegistryEvaluationDO duplicate = evaluationMapper.selectByIdempotencyKey(tenantId, request.getIdempotencyKey());
        if (duplicate != null) {
            ensureDuplicateTarget(request.getWorkflowId(), request.getCandidateVersionId(), duplicate.getSkillId(),
                    duplicate.getRegistryVersionId(), "evaluation");
            return responseForSkill(request.getWorkflowId(), true, duplicate.getEvaluationId(), null, null);
        }

        WorkflowRegistryPointerDO pointer = requirePointerForCandidate(tenantId, request.getWorkflowId(),
                request.getCandidateVersionId(), request.getExpectedPointerVersion());
        WorkflowRegistryVersionDO candidate = requireVersion(tenantId, pointer.getCandidateVersionId(), "candidate");
        WorkflowRegistryVersionDO stable = requireVersion(tenantId, pointer.getStableVersionId(), "stable");
        if (!"VALIDATING".equals(candidate.getRegistryStatus())) {
            throw new IllegalArgumentException("candidate must be in VALIDATING state before evaluation");
        }
        if (Objects.equals(candidate.getProposedBy(), actor.label())) {
            throw new IllegalArgumentException("evaluator must be independent from the proposer");
        }
        deriveServerEvaluationBounds(request, candidate);
        WorkflowRegistryValidationRequestDO validation = validationRequestMapper.selectForUpdate(
                tenantId, request.getValidationRequestId());
        if (validation == null
                || !Objects.equals(validation.getSkillId(), request.getWorkflowId())
                || !Objects.equals(validation.getRegistryVersionId(), request.getCandidateVersionId())
                || !Objects.equals(validation.getPointerVersion(), request.getExpectedPointerVersion())) {
            throw new IllegalArgumentException("evaluation must reference the authoritative validation request for this candidate");
        }
        if (!"REQUESTED".equals(validation.getRequestStatus())
                || validation.getExpiresAt() == null || validation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("validation request is expired or already consumed");
        }
        if (Objects.equals(validation.getRequestedBySubject(), actor.label())) {
            throw new IllegalArgumentException("evaluator must be independent from the validation requestor");
        }
        evaluationAttestationVerifier.verifyFresh(tenantId, validation, request);

        LocalDateTime now = LocalDateTime.now();
        String evaluationStatus = evaluationStatus(candidate.getRiskLevel(), request);
        WorkflowRegistryEvaluationDO evaluation = new WorkflowRegistryEvaluationDO()
                .setEvaluationId("wre-" + UUID.randomUUID())
                .setTenantId(tenantId)
                .setSkillId(request.getWorkflowId())
                .setRegistryVersionId(candidate.getRegistryVersionId())
                .setValidationRequestId(validation.getValidationRequestId())
                .setStableVersionId(stable.getRegistryVersionId())
                .setPointerVersion(pointer.getPointerVersion())
                .setRiskLevel(candidate.getRiskLevel())
                .setEvaluationStatus(evaluationStatus)
                .setProposedBySubject(candidate.getProposedBy())
                .setEvaluatorSubject(actor.label())
                .setEvaluatorRunId(request.getEvaluatorRunId())
                .setDatasetSha256(request.getDatasetSha256())
                .setSampleCount(request.getSampleCount())
                .setObservationStartedAt(request.getObservationStartedAt())
                .setObservationEndedAt(request.getObservationEndedAt())
                .setReplayPassed(request.isReplayPassed())
                .setShadowPassed(request.isShadowPassed())
                .setCanaryPassed(request.isCanaryPassed())
                .setGuardrailsPassed(request.isGuardrailsPassed())
                .setSamplePassed(request.isSamplePassed())
                .setDqcPassed(request.isDqcPassed())
                .setObservationWindowPassed(request.isObservationWindowPassed())
                .setEvidenceRefsJson(json(request.getEvidenceRefs(), "evidence_refs"))
                .setMetricsJson(json(request.getMetrics(), "metrics"))
                .setFailureSamplesJson(json(request.getFailureSamples(), "failure_samples"))
                .setValidatorAttestationJson(json(request.getValidatorAttestation(), "validator_attestation"))
                .setIdempotencyKey(request.getIdempotencyKey())
                .setCreatedAt(now);
        evaluationMapper.insert(evaluation);
        if (validationRequestMapper.markCompleted(tenantId, validation.getValidationRequestId(), now) != 1) {
            throw new IllegalStateException("validation request changed concurrently");
        }

        if (allPreCanaryGatesPassed(request)) {
            appendRelease(pointer, candidate, stable, "VALIDATING", "EVALUATION_RECORDED", actor.label(),
                    request.getIdempotencyKey(), false, request.getEvidenceRefs(), request.getMetrics(), now);
            appendRelease(pointer, candidate, stable, "VALIDATED", "EVALUATION_VALIDATED", actor.label(),
                    request.getIdempotencyKey(), false, request.getEvidenceRefs(), request.getMetrics(), now);
            appendRelease(pointer, candidate, stable, "SHADOW", "SHADOW_PASSED", actor.label(),
                    request.getIdempotencyKey(), false, request.getEvidenceRefs(), request.getMetrics(), now);
            appendRelease(pointer, candidate, stable, "CANDIDATE", "CANDIDATE_READY", actor.label(),
                    request.getIdempotencyKey(), RISKS_REQUIRING_APPROVAL.contains(candidate.getRiskLevel()),
                    request.getEvidenceRefs(), request.getMetrics(), now);
        }

        if (!request.isCanaryPassed() && allPreCanaryGatesPassed(request)) {
            transitionVersion(tenantId, candidate, "VALIDATING", "REJECTED");
            int cleared = pointerMapper.clearCandidateCas(tenantId, request.getWorkflowId(), request.getCandidateVersionId(),
                    request.getExpectedPointerVersion(), actor.label(), now);
            if (cleared != 1) {
                throw new IllegalStateException("candidate pointer changed concurrently");
            }
            appendRelease(pointer, candidate, stable, "ROLLED_BACK", "CANARY_FAILED", actor.label(),
                    request.getIdempotencyKey(), false, request.getEvidenceRefs(), request.getMetrics(), now);
            return buildStatus(request.getWorkflowId(), stable.getRegistryVersionId(), null,
                    request.getExpectedPointerVersion() + 1, "ROLLED_BACK", evaluation.getEvaluationId(), null, null,
                    false);
        }

        if (!allGatesPassed(request)) {
            transitionVersion(tenantId, candidate, "VALIDATING", "REJECTED");
            int cleared = pointerMapper.clearCandidateCas(tenantId, request.getWorkflowId(), request.getCandidateVersionId(),
                    request.getExpectedPointerVersion(), actor.label(), now);
            if (cleared != 1) {
                throw new IllegalStateException("candidate pointer changed concurrently");
            }
            appendRelease(pointer, candidate, stable, "REJECTED", "EVALUATION_FAILED", actor.label(),
                    request.getIdempotencyKey(), false, request.getEvidenceRefs(), request.getMetrics(), now);
            return buildStatus(request.getWorkflowId(), stable.getRegistryVersionId(), null,
                    request.getExpectedPointerVersion() + 1, "REJECTED", evaluation.getEvaluationId(), null, null,
                    false);
        }

        appendRelease(pointer, candidate, stable, "CANARY", "CANARY_PASSED", actor.label(),
                request.getIdempotencyKey(), RISKS_REQUIRING_APPROVAL.contains(candidate.getRiskLevel()),
                request.getEvidenceRefs(), request.getMetrics(), now);
        if (RISKS_REQUIRING_APPROVAL.contains(candidate.getRiskLevel())) {
            transitionVersion(tenantId, candidate, "VALIDATING", "READY_FOR_REVIEW");
            return buildStatus(request.getWorkflowId(), stable.getRegistryVersionId(), candidate.getRegistryVersionId(),
                    pointer.getPointerVersion(), "CANARY", evaluation.getEvaluationId(), null, null, false);
        }

        int promoted = pointerMapper.promoteCandidateToStableCas(tenantId, request.getWorkflowId(),
                request.getCandidateVersionId(), request.getExpectedPointerVersion(), actor.label(), now);
        if (promoted != 1) {
            throw new IllegalStateException("candidate pointer changed concurrently");
        }
        transitionVersion(tenantId, candidate, "VALIDATING", "ACTIVE");
        transitionVersion(tenantId, stable, "ACTIVE", "RETIRED");
        WorkflowRegistryReleaseDO activeRelease = appendRelease(pointer, candidate, stable, "ACTIVE", "AUTO_PROMOTED",
                actor.label(), request.getIdempotencyKey(), false, request.getEvidenceRefs(), request.getMetrics(), now);
        return buildStatus(request.getWorkflowId(), candidate.getRegistryVersionId(), null,
                request.getExpectedPointerVersion() + 1, "ACTIVE", evaluation.getEvaluationId(), null,
                activeRelease.getReleaseId(), false);
    }

    @Transactional
    public WorkflowRegistryGovernanceStatusView recordApproval(WorkflowRegistryApprovalRequest request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Actor actor = requireActor(tenantId);
        validateApprovalRequest(request);
        WorkflowRegistryApprovalDO duplicate = approvalMapper.selectByIdempotencyKey(tenantId, request.getIdempotencyKey());
        if (duplicate != null) {
            ensureDuplicateTarget(request.getWorkflowId(), request.getCandidateVersionId(), duplicate.getSkillId(),
                    duplicate.getRegistryVersionId(), "approval");
            return responseForSkill(request.getWorkflowId(), true, null, duplicate.getApprovalId(), null);
        }

        WorkflowRegistryPointerDO pointer = requirePointerForCandidate(tenantId, request.getWorkflowId(),
                request.getCandidateVersionId(), request.getExpectedPointerVersion());
        WorkflowRegistryVersionDO candidate = requireVersion(tenantId, pointer.getCandidateVersionId(), "candidate");
        if (!RISKS_REQUIRING_APPROVAL.contains(candidate.getRiskLevel())) {
            throw new IllegalArgumentException("only E2 and E3 candidates require explicit approval");
        }
        WorkflowRegistryVersionDO stable = requireVersion(tenantId, pointer.getStableVersionId(), "stable");
        if (!"READY_FOR_REVIEW".equals(candidate.getRegistryStatus())) {
            throw new IllegalArgumentException("candidate must be READY_FOR_REVIEW before approval");
        }
        WorkflowRegistryEvaluationDO evaluation = requireEvaluation(tenantId, request.getEvaluationId());
        if (!Objects.equals(evaluation.getRegistryVersionId(), candidate.getRegistryVersionId())
                || !Objects.equals(evaluation.getSkillId(), request.getWorkflowId())) {
            throw new IllegalArgumentException("approval must reference the current candidate evaluation");
        }
        if (!"CANARY".equals(evaluation.getEvaluationStatus())) {
            throw new IllegalArgumentException("approval requires a passed canary evaluation");
        }
        if (Objects.equals(candidate.getProposedBy(), actor.label())
                || Objects.equals(evaluation.getEvaluatorSubject(), actor.label())) {
            throw new IllegalArgumentException("approver must be independent from proposer and evaluator");
        }

        LocalDateTime now = LocalDateTime.now();
        WorkflowRegistryApprovalDO approval = new WorkflowRegistryApprovalDO()
                .setApprovalId("wra-" + UUID.randomUUID())
                .setTenantId(tenantId)
                .setSkillId(request.getWorkflowId())
                .setRegistryVersionId(candidate.getRegistryVersionId())
                .setEvaluationId(evaluation.getEvaluationId())
                .setRiskLevel(candidate.getRiskLevel())
                .setApprovalDecision(normalizeDecision(request.getDecision()))
                .setProposedBySubject(candidate.getProposedBy())
                .setEvaluatorSubject(evaluation.getEvaluatorSubject())
                .setApproverSubject(actor.label())
                .setRationale(normalizeReason(request.getRationale()))
                .setEvidenceRefsJson(json(request.getEvidenceRefs(), "evidence_refs"))
                .setMetricsJson(json(request.getMetrics(), "metrics"))
                .setIdempotencyKey(request.getIdempotencyKey())
                .setCreatedAt(now);
        approvalMapper.insert(approval);

        if ("REJECT".equals(approval.getApprovalDecision())) {
            transitionVersion(tenantId, candidate, "READY_FOR_REVIEW", "REJECTED");
            int cleared = pointerMapper.clearCandidateCas(tenantId, request.getWorkflowId(), request.getCandidateVersionId(),
                    request.getExpectedPointerVersion(), actor.label(), now);
            if (cleared != 1) {
                throw new IllegalStateException("candidate pointer changed concurrently");
            }
            WorkflowRegistryReleaseDO release = appendRelease(pointer, candidate, stable, "REJECTED", "APPROVAL_REJECTED",
                    actor.label(), request.getIdempotencyKey(), true, request.getEvidenceRefs(), request.getMetrics(), now);
            return buildStatus(request.getWorkflowId(), stable.getRegistryVersionId(), null,
                    request.getExpectedPointerVersion() + 1, "REJECTED", null, approval.getApprovalId(),
                    release.getReleaseId(), false);
        }

        int promoted = pointerMapper.promoteCandidateToStableCas(tenantId, request.getWorkflowId(),
                request.getCandidateVersionId(), request.getExpectedPointerVersion(), actor.label(), now);
        if (promoted != 1) {
            throw new IllegalStateException("candidate pointer changed concurrently");
        }
        transitionVersion(tenantId, candidate, "READY_FOR_REVIEW", "ACTIVE");
        transitionVersion(tenantId, stable, "ACTIVE", "RETIRED");
        WorkflowRegistryReleaseDO release = appendRelease(pointer, candidate, stable, "ACTIVE", "APPROVAL_APPROVED",
                actor.label(), request.getIdempotencyKey(), true, request.getEvidenceRefs(), request.getMetrics(), now);
        return buildStatus(request.getWorkflowId(), candidate.getRegistryVersionId(), null,
                request.getExpectedPointerVersion() + 1, "ACTIVE", null, approval.getApprovalId(),
                release.getReleaseId(), false);
    }

    @Transactional
    public WorkflowRegistryGovernanceStatusView retire(WorkflowRegistryRetirementRequest request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Actor actor = requireActor(tenantId);
        validateRetirementRequest(request);

        String action = request.getAction().trim().toUpperCase();
        String primaryReason = switch (action) {
            case "KILL_SWITCH_CANDIDATE" -> "KILL_SWITCH";
            case "ROLLBACK_ACTIVE" -> "ACTIVE_GUARDRAIL_FAILED";
            case "ENABLE_KILL_SWITCH" -> "KILL_SWITCH_ARMED";
            case "DISABLE_KILL_SWITCH" -> "KILL_SWITCH_DISARMED";
            default -> throw new IllegalArgumentException("unsupported retirement action");
        };
        WorkflowRegistryReleaseDO duplicate = releaseMapper.selectBySourceKeyAndReason(
                tenantId, request.getIdempotencyKey(), primaryReason);
        if (duplicate != null) {
            ensureDuplicateTarget(request.getWorkflowId(), request.getTargetVersionId(), duplicate.getSkillId(),
                    duplicate.getRegistryVersionId(), "retirement");
            return responseForSkill(request.getWorkflowId(), true, null, null, duplicate.getReleaseId());
        }

        WorkflowRegistryPointerDO pointer = requirePointer(tenantId, request.getWorkflowId());
        LocalDateTime now = LocalDateTime.now();
        if ("ENABLE_KILL_SWITCH".equals(action) || "DISABLE_KILL_SWITCH".equals(action)) {
            if (!Objects.equals(pointer.getPointerVersion(), request.getExpectedPointerVersion())
                    || !Objects.equals(pointer.getStableVersionId(), request.getTargetVersionId())) {
                throw new IllegalArgumentException("kill switch action must target the current stable version with a fresh CAS version");
            }
            boolean enable = "ENABLE_KILL_SWITCH".equals(action);
            int updated = enable
                    ? pointerMapper.armKillSwitchCas(tenantId, request.getWorkflowId(), request.getTargetVersionId(),
                    request.getExpectedPointerVersion(), actor.label(), now)
                    : pointerMapper.disarmKillSwitchCas(tenantId, request.getWorkflowId(), request.getTargetVersionId(),
                    request.getExpectedPointerVersion(), actor.label(), now);
            if (updated != 1) {
                throw new IllegalStateException("kill switch state changed concurrently");
            }
            WorkflowRegistryVersionDO stable = requireVersion(tenantId, pointer.getStableVersionId(), "stable");
            WorkflowRegistryReleaseDO release = appendRelease(pointer, stable, stable, enable ? "BLOCKED" : "ACTIVE",
                    primaryReason, actor.label(), request.getIdempotencyKey(), false,
                    request.getEvidenceRefs(), request.getMetrics(), now);
            return buildStatus(request.getWorkflowId(), stable.getRegistryVersionId(), pointer.getCandidateVersionId(),
                    request.getExpectedPointerVersion() + 1, enable ? "BLOCKED" : "ACTIVE", null, null,
                    release.getReleaseId(), false, enable);
        }
        if ("KILL_SWITCH_CANDIDATE".equals(action)) {
            if (!Objects.equals(pointer.getPointerVersion(), request.getExpectedPointerVersion())
                    || !Objects.equals(pointer.getCandidateVersionId(), request.getTargetVersionId())) {
                throw new IllegalArgumentException("kill switch must target the current candidate with a fresh CAS version");
            }
            WorkflowRegistryVersionDO stable = requireVersion(tenantId, pointer.getStableVersionId(), "stable");
            WorkflowRegistryVersionDO candidate = requireVersion(tenantId, pointer.getCandidateVersionId(), "candidate");
            transitionVersion(tenantId, candidate, candidate.getRegistryStatus(), "RETIRED");
            int cleared = pointerMapper.clearCandidateCas(tenantId, request.getWorkflowId(), request.getTargetVersionId(),
                    request.getExpectedPointerVersion(), actor.label(), now);
            if (cleared != 1) {
                throw new IllegalStateException("candidate pointer changed concurrently");
            }
            WorkflowRegistryReleaseDO release = appendRelease(pointer, candidate, stable, "RETIRED", "KILL_SWITCH",
                    actor.label(), request.getIdempotencyKey(), false, request.getEvidenceRefs(), request.getMetrics(), now);
            return buildStatus(request.getWorkflowId(), stable.getRegistryVersionId(), null,
                    request.getExpectedPointerVersion() + 1, "RETIRED", null, null, release.getReleaseId(), false);
        }

        if (!Objects.equals(pointer.getPointerVersion(), request.getExpectedPointerVersion())
                || !Objects.equals(pointer.getStableVersionId(), request.getTargetVersionId())
                || pointer.getCandidateVersionId() != null) {
            throw new IllegalArgumentException("active rollback requires the current stable version, no candidate, and a fresh CAS version");
        }
        WorkflowRegistryVersionDO currentStable = requireVersion(tenantId, pointer.getStableVersionId(), "stable");
        if (currentStable.getParentRegistryVersionId() == null) {
            throw new IllegalArgumentException("active rollback requires a previous stable version");
        }
        WorkflowRegistryVersionDO previousStable = requireVersion(tenantId, currentStable.getParentRegistryVersionId(),
                "previous stable");
        int rolledBack = pointerMapper.rollbackStableCas(tenantId, request.getWorkflowId(), request.getTargetVersionId(),
                previousStable.getRegistryVersionId(), request.getExpectedPointerVersion(), actor.label(), now);
        if (rolledBack != 1) {
            throw new IllegalStateException("stable pointer changed concurrently");
        }
        transitionVersion(tenantId, currentStable, "ACTIVE", "RETIRED");
        transitionVersion(tenantId, previousStable, "RETIRED", "ACTIVE");
        WorkflowRegistryReleaseDO release = appendRelease(pointer, currentStable, previousStable, "ROLLED_BACK",
                "ACTIVE_GUARDRAIL_FAILED", actor.label(), request.getIdempotencyKey(), false,
                request.getEvidenceRefs(), request.getMetrics(), now);
        appendRelease(pointer, previousStable, currentStable, "ACTIVE", "ROLLBACK_RESTORED", actor.label(),
                request.getIdempotencyKey(), false, request.getEvidenceRefs(), request.getMetrics(), now);
        return buildStatus(request.getWorkflowId(), previousStable.getRegistryVersionId(), null,
                request.getExpectedPointerVersion() + 1, "ROLLED_BACK", null, null, release.getReleaseId(), false);
    }

    public WorkflowRegistryGovernanceStatusView getStatus(String skillId) {
        requireSafeId(skillId, "workflow_id");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireActor(tenantId);
        return responseForSkill(skillId, false, null, null, null);
    }

    public List<WorkflowRegistryGovernanceStatusView> listStatuses(int limit) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireActor(tenantId);
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("limit must be between 1 and 200");
        }
        return pointerMapper.selectByTenant(tenantId, limit).stream()
                .map(pointer -> responseForSkill(pointer.getSkillId(), false, null, null, null))
                .toList();
    }

    private WorkflowRegistryGovernanceStatusView validationStatus(WorkflowRegistryValidationRequestDO validation,
                                                                  boolean duplicate, String releaseId) {
        WorkflowRegistryPointerDO pointer = pointerMapper.selectBySkillId(validation.getTenantId(), validation.getSkillId());
        return WorkflowRegistryGovernanceStatusView.builder()
                .workflowId(validation.getSkillId())
                .stableVersionId(pointer == null ? null : pointer.getStableVersionId())
                .candidateVersionId(validation.getRegistryVersionId())
                .pointerVersion(validation.getPointerVersion())
                .killSwitchEnabled(pointer != null && Boolean.TRUE.equals(pointer.getKillSwitchEnabled()))
                .currentStatus("VALIDATING")
                .validationRequestId(validation.getValidationRequestId())
                .validationChallenge(validation.getChallenge())
                .releaseId(releaseId)
                .duplicate(duplicate)
                .build();
    }

    private WorkflowRegistryGovernanceStatusView responseForSkill(String workflowId, boolean duplicate,
                                                                  String evaluationId, String approvalId,
                                                                  String releaseId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        WorkflowRegistryPointerDO pointer = pointerMapper.selectBySkillId(tenantId, workflowId);
        WorkflowRegistryReleaseDO latestRelease = releaseMapper.selectLatestBySkill(tenantId, workflowId);
        WorkflowRegistryEvaluationDO latestEvaluation = evaluationMapper.selectLatestBySkill(tenantId, workflowId);
        WorkflowRegistryApprovalDO latestApproval = approvalMapper.selectLatestBySkill(tenantId, workflowId);
        WorkflowRegistryValidationRequestDO latestValidation = validationRequestMapper.selectLatestBySkill(tenantId, workflowId);
        if (pointer == null) {
            return WorkflowRegistryGovernanceStatusView.builder()
                    .workflowId(workflowId)
                    .pointerVersion(0L)
                    .currentStatus(latestRelease == null ? null : latestRelease.getTargetStatus())
                    .validationRequestId(latestValidation == null ? null : latestValidation.getValidationRequestId())
                    .validationChallenge(latestValidation == null ? null : latestValidation.getChallenge())
                    .evaluationId(evaluationId != null ? evaluationId : latestEvaluation == null ? null : latestEvaluation.getEvaluationId())
                    .approvalId(approvalId != null ? approvalId : latestApproval == null ? null : latestApproval.getApprovalId())
                    .releaseId(releaseId != null ? releaseId : latestRelease == null ? null : latestRelease.getReleaseId())
                    .duplicate(duplicate)
                    .build();
        }
        return WorkflowRegistryGovernanceStatusView.builder()
                .workflowId(workflowId)
                .stableVersionId(pointer.getStableVersionId())
                .candidateVersionId(pointer.getCandidateVersionId())
                .pointerVersion(pointer.getPointerVersion())
                .killSwitchEnabled(Boolean.TRUE.equals(pointer.getKillSwitchEnabled()))
                .currentStatus(latestRelease == null ? null : latestRelease.getTargetStatus())
                .validationRequestId(latestValidation == null ? null : latestValidation.getValidationRequestId())
                .validationChallenge(latestValidation == null ? null : latestValidation.getChallenge())
                .evaluationId(evaluationId != null ? evaluationId
                        : latestEvaluation == null ? null : latestEvaluation.getEvaluationId())
                .approvalId(approvalId != null ? approvalId
                        : latestApproval == null ? null : latestApproval.getApprovalId())
                .releaseId(releaseId != null ? releaseId : latestRelease == null ? null : latestRelease.getReleaseId())
                .duplicate(duplicate)
                .build();
    }

    private WorkflowRegistryGovernanceStatusView buildStatus(String workflowId, String stableVersionId,
                                                             String candidateVersionId, Long pointerVersion,
                                                             String currentStatus, String evaluationId,
                                                             String approvalId, String releaseId,
                                                             boolean duplicate) {
        return buildStatus(workflowId, stableVersionId, candidateVersionId, pointerVersion, currentStatus,
                evaluationId, approvalId, releaseId, duplicate, false);
    }

    private WorkflowRegistryGovernanceStatusView buildStatus(String workflowId, String stableVersionId,
                                                             String candidateVersionId, Long pointerVersion,
                                                             String currentStatus, String evaluationId,
                                                             String approvalId, String releaseId,
                                                             boolean duplicate, boolean killSwitchEnabled) {
        return WorkflowRegistryGovernanceStatusView.builder()
                .workflowId(workflowId)
                .stableVersionId(stableVersionId)
                .candidateVersionId(candidateVersionId)
                .pointerVersion(pointerVersion)
                .killSwitchEnabled(killSwitchEnabled)
                .currentStatus(currentStatus)
                .evaluationId(evaluationId)
                .approvalId(approvalId)
                .releaseId(releaseId)
                .duplicate(duplicate)
                .build();
    }

    private WorkflowRegistryReleaseDO appendRelease(WorkflowRegistryPointerDO pointer,
                                                    WorkflowRegistryVersionDO version,
                                                    WorkflowRegistryVersionDO previousStable,
                                                    String targetStatus,
                                                    String releaseReason,
                                                    String actor,
                                                    String idempotencyKey,
                                                    boolean approvalRequired,
                                                    JsonNode evidenceRefs,
                                                    JsonNode metrics,
                                                    LocalDateTime createdAt) {
        WorkflowRegistryReleaseDO release = new WorkflowRegistryReleaseDO()
                .setReleaseId("wrr-" + UUID.randomUUID())
                .setTenantId(TenantContextHolder.getRequiredTenantId())
                .setSkillId(pointer.getSkillId())
                .setRegistryVersionId(version.getRegistryVersionId())
                .setPreviousStableVersionId(previousStable == null ? null : previousStable.getRegistryVersionId())
                .setTargetStatus(targetStatus)
                .setReleaseReason(releaseReason)
                .setActorSubject(actor)
                .setApprovalRequired(approvalRequired)
                .setKillSwitchArmed(releaseReason.startsWith("KILL_SWITCH"))
                .setSourceIdempotencyKey(idempotencyKey)
                .setEvidenceRefsJson(json(evidenceRefs, "evidence_refs"))
                .setMetricsJson(json(metrics, "metrics"))
                .setCreatedAt(createdAt);
        releaseMapper.insert(release);
        return release;
    }

    private WorkflowRegistryPointerDO requirePointerForCandidate(Long tenantId, String workflowId,
                                                                 String candidateVersionId,
                                                                 Long expectedPointerVersion) {
        WorkflowRegistryPointerDO pointer = requirePointer(tenantId, workflowId);
        if (!Objects.equals(pointer.getPointerVersion(), expectedPointerVersion)
                || !Objects.equals(pointer.getCandidateVersionId(), candidateVersionId)) {
            throw new IllegalArgumentException("candidate pointer is stale or does not match the active candidate");
        }
        return pointer;
    }

    private WorkflowRegistryPointerDO requirePointer(Long tenantId, String workflowId) {
        WorkflowRegistryPointerDO pointer = pointerMapper.selectForUpdate(tenantId, workflowId);
        if (pointer == null) {
            throw new IllegalArgumentException("workflow registry pointer does not exist");
        }
        return pointer;
    }

    private WorkflowRegistryVersionDO requireVersion(Long tenantId, String versionId, String label) {
        WorkflowRegistryVersionDO version = versionMapper.selectByRegistryVersionId(tenantId, versionId);
        if (version == null) {
            throw new IllegalArgumentException(label + " version does not exist");
        }
        return version;
    }

    private WorkflowRegistryEvaluationDO requireEvaluation(Long tenantId, String evaluationId) {
        WorkflowRegistryEvaluationDO evaluation = evaluationMapper.selectByEvaluationId(tenantId, evaluationId);
        if (evaluation == null) {
            throw new IllegalArgumentException("evaluation does not exist");
        }
        return evaluation;
    }

    private void validateEvaluationRequest(WorkflowRegistryEvaluationRequest request) {
        Objects.requireNonNull(request, "request is required");
        requireSafeId(request.getWorkflowId(), "workflow_id");
        requireSafeId(request.getCandidateVersionId(), "candidate_version_id");
        requireSafeId(request.getIdempotencyKey(), "idempotency_key");
        requireSafeId(request.getValidationRequestId(), "validation_request_id");
        requireSafeId(request.getEvaluatorRunId(), "evaluator_run_id");
        if (request.getDatasetSha256() == null || !SHA256.matcher(request.getDatasetSha256()).matches()) {
            throw new IllegalArgumentException("dataset_sha256 must be a lowercase SHA-256 digest");
        }
        if (request.getSampleCount() < 1) {
            throw new IllegalArgumentException("sample_count must be positive");
        }
        if (request.getObservationStartedAt() == null || request.getObservationEndedAt() == null
                || !request.getObservationEndedAt().isAfter(request.getObservationStartedAt())) {
            throw new IllegalArgumentException("observation window must have a positive duration");
        }
        if (request.getExpectedPointerVersion() == null || request.getExpectedPointerVersion() < 0) {
            throw new IllegalArgumentException("expected_pointer_version must be non-negative");
        }
        validateEvidencePayload(request.getEvidenceRefs(), request.getMetrics());
        requireJson(request.getFailureSamples(), "failure_samples");
        requireJson(request.getValidatorAttestation(), "validator_attestation");
        if (!request.getFailureSamples().isArray() || request.getFailureSamples().size() > 100) {
            throw new IllegalArgumentException("failure_samples must be an array with at most 100 items");
        }
    }

    private void deriveServerEvaluationBounds(WorkflowRegistryEvaluationRequest request,
                                              WorkflowRegistryVersionDO candidate) {
        JsonNode proposal;
        try {
            proposal = objectMapper.readTree(candidate.getProposalJson());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("candidate proposal metadata cannot be read", exception);
        }
        int minimumSample = proposal.path("minimum_sample").asInt(-1);
        if (minimumSample < 1) {
            throw new IllegalStateException("candidate proposal has no valid minimum_sample");
        }
        request.setSamplePassed(request.getSampleCount() >= minimumSample);
        String requiredWindow = proposal.path("observation_window").asText();
        var matcher = WINDOW.matcher(requiredWindow);
        if (!matcher.matches()) {
            throw new IllegalStateException("candidate proposal has no machine-readable observation_window");
        }
        long units = Long.parseLong(matcher.group(1));
        long requiredSeconds = switch (matcher.group(2)) {
            case "m" -> Duration.ofMinutes(units).getSeconds();
            case "h" -> Duration.ofHours(units).getSeconds();
            case "d" -> Duration.ofDays(units).getSeconds();
            default -> throw new IllegalStateException("unsupported observation_window unit");
        };
        long actualSeconds = Duration.between(request.getObservationStartedAt(),
                request.getObservationEndedAt()).getSeconds();
        request.setObservationWindowPassed(actualSeconds >= requiredSeconds);
    }

    private void validateValidationStartRequest(WorkflowRegistryValidationStartRequest request) {
        Objects.requireNonNull(request, "request is required");
        requireSafeId(request.getWorkflowId(), "workflow_id");
        requireSafeId(request.getCandidateVersionId(), "candidate_version_id");
        requireSafeId(request.getIdempotencyKey(), "idempotency_key");
        if (request.getExpectedPointerVersion() == null || request.getExpectedPointerVersion() < 0) {
            throw new IllegalArgumentException("expected_pointer_version must be non-negative");
        }
    }

    private void transitionVersion(Long tenantId, WorkflowRegistryVersionDO version,
                                   String expectedStatus, String targetStatus) {
        int updated = versionMapper.updateStatus(tenantId, version.getRegistryVersionId(), expectedStatus, targetStatus);
        if (updated != 1) {
            throw new IllegalStateException("workflow version state changed concurrently");
        }
        version.setRegistryStatus(targetStatus);
    }

    private void validateApprovalRequest(WorkflowRegistryApprovalRequest request) {
        Objects.requireNonNull(request, "request is required");
        requireSafeId(request.getWorkflowId(), "workflow_id");
        requireSafeId(request.getCandidateVersionId(), "candidate_version_id");
        requireSafeId(request.getEvaluationId(), "evaluation_id");
        requireSafeId(request.getIdempotencyKey(), "idempotency_key");
        if (request.getExpectedPointerVersion() == null || request.getExpectedPointerVersion() < 0) {
            throw new IllegalArgumentException("expected_pointer_version must be non-negative");
        }
        if (!APPROVAL_DECISIONS.contains(normalizeDecision(request.getDecision()))) {
            throw new IllegalArgumentException("decision must be APPROVE or REJECT");
        }
        validateEvidencePayload(request.getEvidenceRefs(), request.getMetrics());
    }

    private void validateRetirementRequest(WorkflowRegistryRetirementRequest request) {
        Objects.requireNonNull(request, "request is required");
        requireSafeId(request.getWorkflowId(), "workflow_id");
        requireSafeId(request.getTargetVersionId(), "target_version_id");
        requireSafeId(request.getIdempotencyKey(), "idempotency_key");
        if (request.getExpectedPointerVersion() == null || request.getExpectedPointerVersion() < 0) {
            throw new IllegalArgumentException("expected_pointer_version must be non-negative");
        }
        String action = request.getAction() == null ? "" : request.getAction().trim().toUpperCase();
        if (!RETIREMENT_ACTIONS.contains(action)) {
            throw new IllegalArgumentException("action must be KILL_SWITCH_CANDIDATE, ROLLBACK_ACTIVE, "
                    + "ENABLE_KILL_SWITCH, or DISABLE_KILL_SWITCH");
        }
        validateEvidencePayload(request.getEvidenceRefs(), request.getMetrics());
    }

    private void validateEvidencePayload(JsonNode evidenceRefs, JsonNode metrics) {
        requireJson(evidenceRefs, "evidence_refs");
        requireJson(metrics, "metrics");
        if (!evidenceRefs.isArray() || evidenceRefs.size() > 100) {
            throw new IllegalArgumentException("evidence_refs must be an array with at most 100 items");
        }
        if (!metrics.isObject()) {
            throw new IllegalArgumentException("metrics must be an object");
        }
    }

    private void requireSafeId(String value, String field) {
        if (value == null || !SAFE_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " contains unsupported characters");
        }
    }

    private void requireJson(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private String json(JsonNode node, String field) {
        requireJson(node, field);
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(field + " cannot be serialized", e);
        }
    }

    private String normalizeDecision(String decision) {
        return decision == null ? "" : decision.trim().toUpperCase();
    }

    private String normalizeReason(String reason) {
        return reason == null ? null : reason.trim();
    }

    private String evaluationStatus(String riskLevel, WorkflowRegistryEvaluationRequest request) {
        if (!allGatesPassed(request)) {
            return request.isCanaryPassed() ? "REJECTED" : allPreCanaryGatesPassed(request) ? "ROLLED_BACK" : "REJECTED";
        }
        return RISKS_REQUIRING_APPROVAL.contains(riskLevel) ? "CANARY" : "ACTIVE";
    }

    private boolean allPreCanaryGatesPassed(WorkflowRegistryEvaluationRequest request) {
        return request.isReplayPassed()
                && request.isShadowPassed()
                && request.isGuardrailsPassed()
                && request.isSamplePassed()
                && request.isDqcPassed()
                && request.isObservationWindowPassed();
    }

    private boolean allGatesPassed(WorkflowRegistryEvaluationRequest request) {
        return allPreCanaryGatesPassed(request) && request.isCanaryPassed();
    }

    private void ensureDuplicateTarget(String expectedSkillId, String expectedVersionId,
                                       String actualSkillId, String actualVersionId, String objectType) {
        if (!Objects.equals(expectedSkillId, actualSkillId) || !Objects.equals(expectedVersionId, actualVersionId)) {
            throw new IllegalArgumentException(objectType + " idempotency_key already belongs to another workflow candidate");
        }
    }

    private Actor requireActor(Long tenantId) {
        LoginUser user = Objects.requireNonNull(SecurityFrameworkUtils.getLoginUser(),
                "Workflow governance requires an authenticated login subject");
        Long userId = Objects.requireNonNull(user.getId(), "Workflow governance requires a login user id");
        Long subjectTenant = user.getVisitTenantId() != null ? user.getVisitTenantId() : user.getTenantId();
        if (subjectTenant != null && !Objects.equals(subjectTenant, tenantId)) {
            throw new IllegalStateException("Workflow governance subject is not bound to the current tenant");
        }
        return new Actor(String.valueOf(user.getUserType()) + ":" + userId, String.valueOf(userId));
    }

    private record Actor(String label, String userId) {
    }
}
