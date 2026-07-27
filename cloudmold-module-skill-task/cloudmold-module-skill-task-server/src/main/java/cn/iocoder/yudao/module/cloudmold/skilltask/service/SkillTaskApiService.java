package cn.iocoder.yudao.module.cloudmold.skilltask.service;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskCommandApi;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskQueryApi;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskRetryCommand;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskStepView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskSubmitCommand;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskTerminalProofView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskView;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskDetailView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskRunPageRequest;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskRunView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalRefCodec;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalPermitClaims;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskMissionLeaseFencePort;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskMissionLeaseFencePort.MissionLeaseFence;
import cn.iocoder.yudao.module.cloudmold.skilltask.approval.SkillTaskApprovalContext;
import cn.iocoder.yudao.module.cloudmold.skilltask.approval.SkillTaskApprovalEvidence;
import cn.iocoder.yudao.module.cloudmold.skilltask.approval.SkillTaskApprovalVerifier;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskMapper;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.PermitConsumption;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Step;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Task;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.SkillTaskDefinition;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.SkillTaskDefinitionRegistry;
import cn.iocoder.yudao.module.cloudmold.skilltask.service.query.ManagedSkillTaskQueryService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class SkillTaskApiService implements SkillTaskCommandApi, SkillTaskQueryApi {

    private static final int APPROVAL_REF_MAX_LENGTH = 2048;

    private final SkillTaskMapper mapper;
    private final SkillTaskDefinitionRegistry definitions;
    private final SkillTaskJson json;
    private final ManagedSkillTaskQueryService managedSkillTaskQueryService;
    private final SkillTaskApprovalVerifier approvalVerifier;
    private final SkillTaskMissionLeaseFencePort missionLeaseFenceApi;
    private final Clock clock;

    @Autowired
    public SkillTaskApiService(SkillTaskMapper mapper, SkillTaskDefinitionRegistry definitions,
                               SkillTaskJson json, ManagedSkillTaskQueryService managedSkillTaskQueryService,
                               SkillTaskApprovalVerifier approvalVerifier,
                               Optional<SkillTaskMissionLeaseFencePort> missionLeaseFenceApi, Clock clock) {
        this.mapper = mapper;
        this.definitions = definitions;
        this.json = json;
        this.managedSkillTaskQueryService = managedSkillTaskQueryService;
        this.approvalVerifier = approvalVerifier;
        this.missionLeaseFenceApi = missionLeaseFenceApi.orElse(null);
        this.clock = clock;
    }

    public SkillTaskApiService(SkillTaskMapper mapper, SkillTaskDefinitionRegistry definitions,
                               SkillTaskJson json, ManagedSkillTaskQueryService managedSkillTaskQueryService,
                               SkillTaskApprovalVerifier approvalVerifier, Clock clock) {
        this(mapper, definitions, json, managedSkillTaskQueryService, approvalVerifier, Optional.empty(), clock);
    }

    @Override
    @Transactional
    public SkillTaskView submit(SkillTaskSubmitCommand command) {
        Objects.requireNonNull(command, "command");
        String skillId = text(command.getSkillId(), "skillId", 191);
        String skillVersion = text(command.getSkillVersion(), "skillVersion", 64);
        String requestedRunId = optionalRunId(command.getRunId());
        String requestKey = text(command.getClientRequestKey(), "clientRequestKey", 191);
        SkillTaskDefinition definition = definitions.require(skillId, skillVersion);
        String riskLevel = text(command.getRiskLevel(), "riskLevel", 2).toUpperCase(Locale.ROOT);
        if (!definition.getRiskLevel().equals(riskLevel)) {
            throw new IllegalArgumentException("riskLevel does not match the registered Skill definition");
        }
        String approvalRef = optionalText(command.getApprovalRef(), "approvalRef", APPROVAL_REF_MAX_LENGTH);
        if (!"R1".equals(riskLevel) && approvalRef == null) {
            throw new SecurityException(riskLevel + " Skill submission requires approvalRef");
        }
        ObjectNode input = json.parseObject(command.getInputJson(), "inputJson");
        String inputJson = json.canonical(input);
        String inputSha256 = json.sha256(inputJson);
        long tenantId = TenantContextHolder.getRequiredTenantId();
        LoginUser operator = requireOperator();

        Task existing = mapper.selectByRequestKey(tenantId, skillId, requestKey);
        if (existing != null) {
            verifyReplay(existing, skillVersion, requestedRunId, inputSha256, riskLevel, approvalRef);
            verifyDefinitionProof(existing, definition);
            validatePersistedMissionFence(existing, requestedRunId);
            return toView(existing);
        }
        SkillTaskApprovalEvidence approval = verifyApproval(tenantId, operator, skillId, skillVersion,
                inputSha256, definition.getDefinitionClosureSha256(), riskLevel, approvalRef);
        validateMissionFence(approval == null ? null : approval.claims(), requestedRunId);

        String taskId = UUID.randomUUID().toString().replace("-", "");
        String runId = requestedRunId == null ? taskId : requestedRunId;
        LocalDateTime now = now();
        SkillTaskDefinition.Step first = definition.getSteps().get(0);
        mapper.insertTask(tenantId, taskId, runId, skillId, skillVersion, requestKey,
                inputJson, inputSha256, definition.getDefinitionSha256(), definition.getDefinitionClosureSha256(),
                riskLevel, approvalRef, operator.getId(), operator.getUserType(), first.getStepCode(),
                definition.getMaxAttempts(), now);

        Task stored = requireByRequestKeyForUpdate(tenantId, skillId, requestKey);
        verifyReplay(stored, skillVersion, requestedRunId, inputSha256, riskLevel, approvalRef);
        if (!taskId.equals(stored.getTaskId())) {
            verifyDefinitionProof(stored, definition);
            return toView(stored);
        }
        consumeRootPermit(tenantId, requestKey, stored.getTaskId(), inputSha256, riskLevel, definition, approval, now);
        insertSteps(tenantId, taskId, definition, now);
        mapper.insertHistory(tenantId, taskId, 0L, null, "QUEUED", first.getStepCode(),
                "TASK_SUBMITTED", submittedMessage(approval), operator.getId(), operator.getUserType(), now);
        return toView(requireTask(tenantId, taskId));
    }

    @Override
    @Transactional
    public SkillTaskView retry(SkillTaskRetryCommand command) {
        Objects.requireNonNull(command, "command");
        long tenantId = TenantContextHolder.getRequiredTenantId();
        LoginUser retryOperator = requireOperator();
        String taskId = text(command.getTaskId(), "taskId", 64);
        if (command.getExpectedVersion() == null || command.getExpectedVersion() < 0) {
            throw new IllegalArgumentException("expectedVersion is required");
        }
        String reason = text(command.getReason(), "reason", 2_000);
        Task task = mapper.selectTaskForUpdate(tenantId, taskId);
        if (task == null) {
            throw new IllegalArgumentException("Skill task does not exist: " + taskId);
        }
        if (!"NEEDS_REVIEW".equals(task.getStatus())) {
            throw new IllegalStateException("Only NEEDS_REVIEW tasks can be retried");
        }
        if (!command.getExpectedVersion().equals(task.getVersion())) {
            throw new IllegalStateException("Skill task version changed; reload before retrying");
        }
        String approvalRef = optionalText(command.getApprovalRef(), "approvalRef", APPROVAL_REF_MAX_LENGTH);
        String effectiveApprovalRef = approvalRef == null ? task.getApprovalRef() : approvalRef;
        if (!"R1".equals(task.getRiskLevel()) && effectiveApprovalRef == null) {
            throw new SecurityException(task.getRiskLevel() + " Skill retry requires approvalRef");
        }
        SkillTaskApprovalEvidence approval = verifyApproval(tenantId, retryOperator,
                approvalScopeSkillId(task), approvalScopeSkillVersion(task), approvalScopeInputSha256(task),
                approvalScopeDefinitionClosureSha256(task), approvalScopeRiskLevel(task), effectiveApprovalRef);
        LocalDateTime now = now();
        resumeDescendants(task, effectiveApprovalRef, retryOperator, now);
        mapper.resetFailedSteps(tenantId, taskId, now);
        int updated = mapper.retry(tenantId, taskId, command.getExpectedVersion(), approvalRef,
                retryOperator.getId(), retryOperator.getUserType(), now);
        if (updated != 1) {
            throw new IllegalStateException("Skill task retry lost an optimistic-lock race");
        }
        mapper.insertHistory(tenantId, taskId, task.getVersion() + 1, "NEEDS_REVIEW", "QUEUED",
                task.getCurrentStepCode(), "MANUAL_RETRY", truncate(retryMessage(reason, approval), 2_000),
                retryOperator.getId(), retryOperator.getUserType(), now);
        return toView(requireTask(tenantId, taskId));
    }

    @Override
    public SkillTaskView get(String taskId) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        return toView(requireTask(tenantId, text(taskId, "taskId", 64)));
    }

    @Override
    public SkillTaskView getByRequestKey(String skillId, String clientRequestKey) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        return toView(requireByRequestKey(tenantId, text(skillId, "skillId", 191),
                text(clientRequestKey, "clientRequestKey", 191)));
    }

    @Override
    public List<SkillTaskStepView> listSteps(String taskId) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        String normalizedTaskId = text(taskId, "taskId", 64);
        requireTask(tenantId, normalizedTaskId);
        return mapper.selectSteps(tenantId, normalizedTaskId).stream().map(this::toStepView).toList();
    }

    @Override
    public SkillTaskTerminalProofView getTerminalProof(String taskId) {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        Task task = requireTask(tenantId, text(taskId, "taskId", 64));
        if (!"SUCCEEDED".equals(task.getStatus()) || task.getTerminalResultSha256() == null
                || task.getDefinitionClosureSha256() == null) {
            throw new IllegalStateException("Skill task does not have an acceptable terminal proof");
        }
        return SkillTaskTerminalProofView.builder().tenantId(task.getTenantId()).taskId(task.getTaskId())
                .runId(task.getRunId()).skillId(task.getSkillId()).skillVersion(task.getSkillVersion())
                .definitionSha256(task.getDefinitionSha256())
                .definitionClosureSha256(task.getDefinitionClosureSha256()).inputSha256(task.getInputSha256())
                .riskLevel(task.getRiskLevel()).status(task.getStatus())
                .terminalResultSha256(task.getTerminalResultSha256()).taskVersion(task.getVersion())
                .completedAt(toInstant(task.getCompletedAt())).build();
    }

    @Override
    public List<ManagedSkillTaskWorkflowView> listManagedWorkflows() {
        return managedSkillTaskQueryService.listManagedWorkflows();
    }

    @Override
    public PageResult<ManagedSkillTaskRunView> pageManagedRuns(ManagedSkillTaskRunPageRequest request) {
        Objects.requireNonNull(request, "request");
        return managedSkillTaskQueryService.getManagedRunPage(request);
    }

    @Override
    public ManagedSkillTaskDetailView getManagedRun(String taskId) {
        return managedSkillTaskQueryService.getManagedRun(taskId);
    }

    /**
     * Persist one deterministic child task from a claimed SUBMIT_CHILD step. The parent approval scope is inherited,
     * so every descendant WRITE is still authorized by the single audited R3 approval rather than by an invented
     * child approval.
     */
    @Transactional
    public Task submitChild(Task parent, Step parentStep, ObjectNode input, String requestedRunId) {
        if (!"SUBMIT_CHILD".equals(parentStep.getStepKind())) {
            throw new IllegalArgumentException("Only SUBMIT_CHILD steps may create child tasks");
        }
        SkillTaskDefinition definition = definitions.require(parentStep.getChildSkillId(),
                parentStep.getChildSkillVersion());
        String inputJson = json.canonical(input);
        String inputSha256 = json.sha256(inputJson);
        String requestKey = parent.getTaskId() + ":" + parentStep.getStepCode();
        String expectedRunId = requestedRunId == null ? defaultChildRunId(parent, parentStep)
                : requiredRunId(requestedRunId);
        Task existing = mapper.selectByRequestKey(parent.getTenantId(), definition.getSkillId(), requestKey);
        if (existing != null) {
            verifyChildReplay(existing, parent, parentStep, expectedRunId, inputSha256);
            bindChild(parent, parentStep, existing.getTaskId());
            return existing;
        }

        String taskId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = now();
        SkillTaskDefinition.Step first = definition.getSteps().get(0);
        mapper.insertChildTask(parent.getTenantId(), taskId, expectedRunId, definition.getSkillId(),
                definition.getSkillVersion(), requestKey, inputJson, inputSha256, definition.getDefinitionSha256(),
                definition.getDefinitionClosureSha256(), definition.getRiskLevel(), parent.getApprovalRef(),
                approvalScopeSkillId(parent), approvalScopeSkillVersion(parent),
                approvalScopeDefinitionClosureSha256(parent), approvalScopeInputSha256(parent),
                approvalScopeRiskLevel(parent), parent.getTaskId(), parentStep.getStepCode(),
                parent.getOperatorId(), parent.getOperatorType(), first.getStepCode(), definition.getMaxAttempts(),
                now);
        Task stored = requireByRequestKeyForUpdate(parent.getTenantId(), definition.getSkillId(), requestKey);
        verifyChildReplay(stored, parent, parentStep, expectedRunId, inputSha256);
        if (taskId.equals(stored.getTaskId())) {
            insertSteps(parent.getTenantId(), taskId, definition, now);
            mapper.insertHistory(parent.getTenantId(), taskId, 0L, null, "QUEUED", first.getStepCode(),
                    "CHILD_TASK_SUBMITTED", "Parent task=" + parent.getTaskId() + "; parentStep="
                            + parentStep.getStepCode(), parent.getOperatorId(), parent.getOperatorType(), now);
        }
        bindChild(parent, parentStep, stored.getTaskId());
        return requireTask(parent.getTenantId(), stored.getTaskId());
    }

    private void verifyReplay(Task task, String skillVersion, String requestedRunId,
                              String inputSha256, String riskLevel,
                              String approvalRef) {
        if (!skillVersion.equals(task.getSkillVersion()) || !inputSha256.equals(task.getInputSha256())
                || !riskLevel.equals(task.getRiskLevel())) {
            throw new IllegalStateException("clientRequestKey was already used with different Skill input");
        }
        if (requestedRunId != null && !requestedRunId.equals(task.getRunId())) {
            throw new IllegalStateException("clientRequestKey was already used with a different runId");
        }
        if (!Objects.equals(approvalRef, task.getApprovalRef())) {
            throw new IllegalStateException("clientRequestKey was already used with a different approvalRef");
        }
    }

    private void validatePersistedMissionFence(Task task, String requestedRunId) {
        String approvalRef = task.getApprovalRef();
        if (approvalRef == null || !approvalRef.startsWith(SkillTaskApprovalRefCodec.CLAIMS_VERSION + ":")) {
            return;
        }
        validateMissionFence(SkillTaskApprovalRefCodec.parseClaims(approvalRef).claims(), requestedRunId);
    }

    private void validateMissionFence(SkillTaskApprovalPermitClaims claims, String requestedRunId) {
        if (claims == null || !SkillTaskApprovalRefCodec.CLAIMS_VERSION.equals(claims.version())) {
            return;
        }
        if (missionLeaseFenceApi == null) {
            throw new SecurityException("cma3 mission lease validation is unavailable");
        }
        if (claims.missionBound() && !Objects.equals(requestedRunId, claims.missionRunId())) {
            throw new SecurityException("Skill Task runId does not match the mission lease fence");
        }
        missionLeaseFenceApi.validateCurrentLease(new MissionLeaseFence(
                claims.tenantId(), claims.workOrderId(), claims.missionRunId(), claims.leaseOwner(),
                claims.leaseEpoch() == null ? 0L : claims.leaseEpoch(),
                claims.fencingToken() == null ? 0L : claims.fencingToken()));
    }

    private void verifyDefinitionProof(Task task, SkillTaskDefinition definition) {
        if (task.getDefinitionSha256() == null || task.getDefinitionClosureSha256() == null) {
            throw new IllegalStateException("Persisted Skill task predates immutable definition proof");
        }
        if (!task.getDefinitionSha256().equals(definition.getDefinitionSha256())
                || !task.getDefinitionClosureSha256().equals(definition.getDefinitionClosureSha256())) {
            throw new IllegalStateException("Persisted Skill task definition proof drifted");
        }
    }

    private void consumeRootPermit(long tenantId, String requestKey, String taskId, String inputSha256,
                                   String riskLevel, SkillTaskDefinition definition,
                                   SkillTaskApprovalEvidence approval, LocalDateTime now) {
        if (approval == null) {
            return;
        }
        if (definition.getDefinitionSha256() == null || definition.getDefinitionClosureSha256() == null) {
            throw new IllegalStateException("Skill Task registry did not produce immutable definition proof");
        }
        if (!approval.definitionClosureSha256().equals(definition.getDefinitionClosureSha256())) {
            throw new SecurityException("approvalRef definition closure does not match the registered Skill");
        }
        mapper.insertPermitConsumption(tenantId, approval.permitId(), approval.approvalId(), approval.workOrderId(),
                approval.rootRequestIdentity(), requestKey, taskId, approval.referenceSha256(),
                definition.getDefinitionClosureSha256(), inputSha256, riskLevel, now);
        PermitConsumption consumption = mapper.selectPermitConsumption(tenantId, approval.permitId());
        if (consumption == null) {
            throw new IllegalStateException("Skill Task permit consumption did not become visible");
        }
        if (!Objects.equals(requestKey, consumption.getClientRequestKey())) {
            throw new IllegalStateException("approvalRef was already consumed by a different clientRequestKey");
        }
        if (!Objects.equals(approval.permitId(), consumption.getPermitId())
                || !Objects.equals(approval.approvalId(), consumption.getApprovalId())
                || !Objects.equals(approval.workOrderId(), consumption.getWorkOrderId())
                || !Objects.equals(approval.rootRequestIdentity(), consumption.getRootRequestIdentity())
                || !Objects.equals(taskId, consumption.getTaskId())
                || !Objects.equals(approval.referenceSha256(), consumption.getApprovalRefSha256())
                || !Objects.equals(definition.getDefinitionClosureSha256(),
                        consumption.getDefinitionClosureSha256())
                || !Objects.equals(inputSha256, consumption.getInputSha256())
                || !Objects.equals(riskLevel, consumption.getRiskLevel())) {
            throw new IllegalStateException("approvalRef permit consumption drifted after first consumption");
        }
    }

    private void verifyChildReplay(Task child, Task parent, Step parentStep, String expectedRunId,
                                   String inputSha256) {
        if (!parentStep.getChildSkillVersion().equals(child.getSkillVersion())
                || !expectedRunId.equals(child.getRunId())
                || !inputSha256.equals(child.getInputSha256())
                || !parent.getTaskId().equals(child.getParentTaskId())
                || !parentStep.getStepCode().equals(child.getParentStepCode())) {
            throw new IllegalStateException("Persisted child task conflicts with its deterministic parent step");
        }
    }

    private void bindChild(Task parent, Step parentStep, String childTaskId) {
        if (mapper.bindChildTask(parent.getTenantId(), parent.getTaskId(), parentStep.getStepCode(), childTaskId,
                now()) != 1) {
            throw new IllegalStateException("Parent step could not bind its persisted child task");
        }
    }

    private void insertSteps(long tenantId, String taskId, SkillTaskDefinition definition, LocalDateTime now) {
        for (SkillTaskDefinition.Step step : definition.getSteps()) {
            String idempotencyKey = taskId + ":" + step.getStepCode();
            if (step.getStepKind() == null || "CAPABILITY".equals(step.getStepKind())) {
                mapper.insertStep(tenantId, taskId, step.getStepCode(), step.getStepOrder(), step.getCapabilityId(),
                        step.getOperationType(), json.canonical(step.getArguments()), idempotencyKey, now);
            } else if ("WAIT_CAPABILITY".equals(step.getStepKind())) {
                mapper.insertWaitCapabilityStep(tenantId, taskId, step.getStepCode(), step.getStepOrder(),
                        step.getCapabilityId(), json.canonical(step.getArguments()), step.getPollIntervalSeconds(),
                        json.canonical(step.getWaitSuccess()), step.getWaitFailure() == null ? null
                                : json.canonical(step.getWaitFailure()), idempotencyKey, now);
            } else {
                mapper.insertOrchestrationStep(tenantId, taskId, step.getStepCode(), step.getStepOrder(),
                        step.getStepKind(), json.canonical(step.getArguments()), step.getChildSkillId(),
                        step.getChildSkillVersion(), step.getChildRunId(), step.getPollIntervalSeconds(),
                        idempotencyKey, now);
            }
        }
    }

    private void resumeDescendants(Task parent, String approvalRef, LoginUser operator, LocalDateTime now) {
        for (Task child : mapper.selectChildren(parent.getTenantId(), parent.getTaskId())) {
            resumeDescendants(child, approvalRef, operator, now);
            if (!"NEEDS_REVIEW".equals(child.getStatus())) {
                continue;
            }
            mapper.resetFailedSteps(child.getTenantId(), child.getTaskId(), now);
            if (mapper.retry(child.getTenantId(), child.getTaskId(), child.getVersion(), approvalRef,
                    operator.getId(), operator.getUserType(), now) != 1) {
                throw new IllegalStateException("Child task retry lost an optimistic-lock race: " + child.getTaskId());
            }
            mapper.insertHistory(child.getTenantId(), child.getTaskId(), child.getVersion() + 1,
                    "NEEDS_REVIEW", "QUEUED", child.getCurrentStepCode(), "PARENT_MANUAL_RETRY",
                    "Approval renewed by parent task=" + parent.getTaskId(), operator.getId(),
                    operator.getUserType(), now);
        }
    }

    private Task requireTask(long tenantId, String taskId) {
        Task task = mapper.selectTask(tenantId, taskId);
        if (task == null) {
            throw new IllegalArgumentException("Skill task does not exist: " + taskId);
        }
        return task;
    }

    private Task requireByRequestKey(long tenantId, String skillId, String requestKey) {
        Task task = mapper.selectByRequestKey(tenantId, skillId, requestKey);
        if (task == null) {
            throw new IllegalStateException("Skill task insert did not become visible");
        }
        return task;
    }

    private Task requireByRequestKeyForUpdate(long tenantId, String skillId, String requestKey) {
        Task task = mapper.selectByRequestKeyForUpdate(tenantId, skillId, requestKey);
        if (task == null) {
            throw new IllegalStateException("Skill task insert did not become visible");
        }
        return task;
    }

    private static LoginUser requireOperator() {
        LoginUser operator = SecurityFrameworkUtils.getLoginUser();
        if (operator == null || operator.getId() == null || operator.getId() <= 0
                || operator.getUserType() == null || operator.getUserType() <= 0) {
            throw new SecurityException("Authenticated RPC operator is required");
        }
        return operator;
    }

    private SkillTaskApprovalEvidence verifyApproval(long tenantId, LoginUser operator, String skillId,
                                                     String skillVersion, String inputSha256,
                                                     String definitionClosureSha256, String riskLevel,
                                                     String approvalRef) {
        if ("R1".equals(riskLevel)) {
            return null;
        }
        return approvalVerifier.verify(new SkillTaskApprovalContext(tenantId, operator.getId(),
                operator.getUserType(), skillId, skillVersion, definitionClosureSha256,
                inputSha256, riskLevel, approvalRef));
    }

    private static String approvalScopeSkillId(Task task) {
        return task.getApprovalScopeSkillId() == null ? task.getSkillId() : task.getApprovalScopeSkillId();
    }

    private static String approvalScopeSkillVersion(Task task) {
        return task.getApprovalScopeSkillVersion() == null ? task.getSkillVersion()
                : task.getApprovalScopeSkillVersion();
    }

    private static String approvalScopeInputSha256(Task task) {
        return task.getApprovalScopeInputSha256() == null ? task.getInputSha256()
                : task.getApprovalScopeInputSha256();
    }

    private static String approvalScopeDefinitionClosureSha256(Task task) {
        return task.getApprovalScopeDefinitionClosureSha256() == null ? task.getDefinitionClosureSha256()
                : task.getApprovalScopeDefinitionClosureSha256();
    }

    private static String approvalScopeRiskLevel(Task task) {
        return task.getApprovalScopeRiskLevel() == null ? task.getRiskLevel() : task.getApprovalScopeRiskLevel();
    }

    private static String submittedMessage(SkillTaskApprovalEvidence approval) {
        return approval == null ? "Skill task accepted"
                : "Skill task accepted; approval=" + approval.approvalId()
                + "; approvalSha256=" + approval.referenceSha256()
                + "; approvalExpiresAt=" + approval.expiresAt();
    }

    private static String retryMessage(String reason, SkillTaskApprovalEvidence approval) {
        return approval == null ? reason : reason + "; approval=" + approval.approvalId()
                + "; approvalSha256=" + approval.referenceSha256()
                + "; approvalExpiresAt=" + approval.expiresAt();
    }

    private SkillTaskView toView(Task task) {
        return SkillTaskView.builder()
                .taskId(task.getTaskId()).runId(task.getRunId()).skillId(task.getSkillId())
                .skillVersion(task.getSkillVersion()).clientRequestKey(task.getClientRequestKey())
                .inputSha256(task.getInputSha256()).definitionSha256(task.getDefinitionSha256())
                .definitionClosureSha256(task.getDefinitionClosureSha256())
                .terminalResultSha256(task.getTerminalResultSha256())
                .riskLevel(task.getRiskLevel())
                .approvalSummary(SkillTaskApprovalRefCodec.summarize(task.getApprovalRef()))
                .submitterId(task.getSubmitterId()).submitterType(task.getSubmitterType())
                .operatorId(task.getOperatorId()).operatorType(task.getOperatorType()).status(task.getStatus())
                .currentStepCode(task.getCurrentStepCode()).attemptCount(task.getAttemptCount())
                .maxAttempts(task.getMaxAttempts()).lastErrorCode(task.getLastErrorCode())
                .lastErrorMessage(task.getLastErrorMessage()).version(task.getVersion())
                .nextRetryAt(toInstant(task.getNextRetryAt())).startedAt(toInstant(task.getStartedAt()))
                .completedAt(toInstant(task.getCompletedAt())).createdAt(toInstant(task.getCreatedAt()))
                .updatedAt(toInstant(task.getUpdatedAt())).build();
    }

    private SkillTaskStepView toStepView(Step step) {
        return SkillTaskStepView.builder()
                .taskId(step.getTaskId()).stepCode(step.getStepCode()).stepOrder(step.getStepOrder())
                .capabilityId(step.getCapabilityId()).operationType(step.getOperationType()).status(step.getStatus())
                .idempotencyKey(step.getIdempotencyKey()).attemptCount(step.getAttemptCount())
                .requestSha256(step.getRequestSha256()).resultSha256(step.getResultSha256())
                .resultJson(step.getResultJson()).lastErrorCode(step.getLastErrorCode())
                .lastErrorMessage(step.getLastErrorMessage()).startedAt(toInstant(step.getStartedAt()))
                .completedAt(toInstant(step.getCompletedAt())).createdAt(toInstant(step.getCreatedAt()))
                .updatedAt(toInstant(step.getUpdatedAt())).build();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private Instant toInstant(LocalDateTime value) {
        ZoneId zone = clock.getZone();
        return value == null ? null : value.atZone(zone).toInstant();
    }

    private static String text(String value, String field, int maxLength) {
        String result = optionalText(value, field, maxLength);
        if (result == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return result;
    }

    private static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String result = value.trim();
        if (result.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return result;
    }

    private static String optionalRunId(String value) {
        String runId = optionalText(value, "runId", 64);
        if (runId != null && !runId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")) {
            throw new IllegalArgumentException("runId contains unsupported characters");
        }
        return runId;
    }

    private static String requiredRunId(String value) {
        String runId = optionalRunId(value);
        if (runId == null) {
            throw new IllegalArgumentException("childRunId is required when its template is configured");
        }
        return runId;
    }

    private String defaultChildRunId(Task parent, Step step) {
        String suffix = json.sha256(parent.getTaskId() + ":" + step.getStepCode()).substring(0, 12);
        String prefix = parent.getRunId().length() <= 50 ? parent.getRunId() : parent.getRunId().substring(0, 50);
        return prefix + ":" + suffix;
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
