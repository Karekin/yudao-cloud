package cn.iocoder.yudao.module.cloudmold.skilltask.service;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskCommandApi;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskQueryApi;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskRetryCommand;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskStepView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskSubmitCommand;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskView;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskMapper;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Step;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Task;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.SkillTaskDefinition;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.SkillTaskDefinitionRegistry;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class SkillTaskApiService implements SkillTaskCommandApi, SkillTaskQueryApi {

    private final SkillTaskMapper mapper;
    private final SkillTaskDefinitionRegistry definitions;
    private final SkillTaskJson json;
    private final Clock clock;

    public SkillTaskApiService(SkillTaskMapper mapper, SkillTaskDefinitionRegistry definitions,
                               SkillTaskJson json, Clock clock) {
        this.mapper = mapper;
        this.definitions = definitions;
        this.json = json;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SkillTaskView submit(SkillTaskSubmitCommand command) {
        Objects.requireNonNull(command, "command");
        String skillId = text(command.getSkillId(), "skillId", 191);
        String skillVersion = text(command.getSkillVersion(), "skillVersion", 64);
        String requestKey = text(command.getClientRequestKey(), "clientRequestKey", 191);
        SkillTaskDefinition definition = definitions.require(skillId, skillVersion);
        String riskLevel = text(command.getRiskLevel(), "riskLevel", 2).toUpperCase(Locale.ROOT);
        if (!definition.getRiskLevel().equals(riskLevel)) {
            throw new IllegalArgumentException("riskLevel does not match the registered Skill definition");
        }
        String approvalRef = optionalText(command.getApprovalRef(), "approvalRef", 191);
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
            verifyReplay(existing, skillVersion, inputSha256, riskLevel, approvalRef);
            return toView(existing);
        }

        String taskId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = now();
        SkillTaskDefinition.Step first = definition.getSteps().get(0);
        mapper.insertTask(tenantId, taskId, taskId, skillId, skillVersion, requestKey,
                inputJson, inputSha256, riskLevel, approvalRef, operator.getId(), operator.getUserType(),
                first.getStepCode(), definition.getMaxAttempts(), now);

        Task stored = requireByRequestKeyForUpdate(tenantId, skillId, requestKey);
        verifyReplay(stored, skillVersion, inputSha256, riskLevel, approvalRef);
        if (!taskId.equals(stored.getTaskId())) {
            return toView(stored);
        }
        for (SkillTaskDefinition.Step step : definition.getSteps()) {
            mapper.insertStep(tenantId, taskId, step.getStepCode(), step.getStepOrder(), step.getCapabilityId(),
                    step.getOperationType(), json.canonical(step.getArguments()), taskId + ":" + step.getStepCode(), now);
        }
        mapper.insertHistory(tenantId, taskId, 0L, null, "QUEUED", first.getStepCode(),
                "TASK_SUBMITTED", "Skill task accepted", operator.getId(), operator.getUserType(), now);
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
        String approvalRef = optionalText(command.getApprovalRef(), "approvalRef", 191);
        if (!"R1".equals(task.getRiskLevel()) && approvalRef == null && task.getApprovalRef() == null) {
            throw new SecurityException(task.getRiskLevel() + " Skill retry requires approvalRef");
        }
        LocalDateTime now = now();
        mapper.resetFailedSteps(tenantId, taskId, now);
        int updated = mapper.retry(tenantId, taskId, command.getExpectedVersion(), approvalRef,
                retryOperator.getId(), retryOperator.getUserType(), now);
        if (updated != 1) {
            throw new IllegalStateException("Skill task retry lost an optimistic-lock race");
        }
        mapper.insertHistory(tenantId, taskId, task.getVersion() + 1, "NEEDS_REVIEW", "QUEUED",
                task.getCurrentStepCode(), "MANUAL_RETRY", reason,
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

    private void verifyReplay(Task task, String skillVersion, String inputSha256, String riskLevel,
                              String approvalRef) {
        if (!skillVersion.equals(task.getSkillVersion()) || !inputSha256.equals(task.getInputSha256())
                || !riskLevel.equals(task.getRiskLevel())) {
            throw new IllegalStateException("clientRequestKey was already used with different Skill input");
        }
        if (approvalRef != null && task.getApprovalRef() != null && !approvalRef.equals(task.getApprovalRef())) {
            throw new IllegalStateException("clientRequestKey was already used with a different approvalRef");
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

    private SkillTaskView toView(Task task) {
        return SkillTaskView.builder()
                .taskId(task.getTaskId()).runId(task.getRunId()).skillId(task.getSkillId())
                .skillVersion(task.getSkillVersion()).clientRequestKey(task.getClientRequestKey())
                .inputSha256(task.getInputSha256()).riskLevel(task.getRiskLevel()).approvalRef(task.getApprovalRef())
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
}
