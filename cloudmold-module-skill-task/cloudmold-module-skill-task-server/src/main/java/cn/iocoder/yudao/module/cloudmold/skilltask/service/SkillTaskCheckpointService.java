package cn.iocoder.yudao.module.cloudmold.skilltask.service;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.cloudmold.skilltask.SkillTaskProperties;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskMapper;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Candidate;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Step;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SkillTaskCheckpointService {

    private final SkillTaskMapper mapper;
    private final SkillTaskProperties properties;
    private final Clock clock;

    public SkillTaskCheckpointService(SkillTaskMapper mapper, SkillTaskProperties properties, Clock clock) {
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Task claim(Candidate candidate, String leaseOwner) {
        LocalDateTime now = now();
        int updated = mapper.claim(candidate.getTenantId(), candidate.getTaskId(), candidate.getVersion(), leaseOwner,
                now.plus(properties.getLeaseDuration()), now);
        if (updated != 1) {
            return null;
        }
        return TenantUtils.execute(candidate.getTenantId(), () -> {
            Task task = requireTask(candidate.getTenantId(), candidate.getTaskId());
            mapper.insertHistory(task.getTenantId(), task.getTaskId(), task.getVersion(), candidate.getStatus(), "RUNNING",
                    task.getCurrentStepCode(), "TASK_CLAIMED", leaseOwner,
                    task.getOperatorId(), task.getOperatorType(), now);
            return task;
        });
    }

    @Transactional
    public void prepareStep(Task claimed, Step step, String leaseOwner, String requestJson, String requestSha256) {
        Task task = lockOwnedTask(claimed.getTenantId(), claimed.getTaskId(), leaseOwner);
        if (!step.getStepCode().equals(task.getCurrentStepCode())) {
            throw new LeaseLostException("Task current step changed before invocation");
        }
        LocalDateTime now = now();
        if (mapper.renewLease(task.getTenantId(), task.getTaskId(), leaseOwner,
                now.plus(properties.getLeaseDuration()), now) != 1) {
            throw new LeaseLostException("Task lease could not be renewed");
        }
        if (mapper.markStepRunning(task.getTenantId(), task.getTaskId(), step.getStepCode(),
                requestJson, requestSha256, now) != 1) {
            throw new LeaseLostException("Task step could not enter RUNNING");
        }
    }

    @Transactional
    public String checkpointSuccess(Task claimed, Step step, String leaseOwner,
                                    String resultJson, String resultSha256) {
        Task task = lockOwnedTask(claimed.getTenantId(), claimed.getTaskId(), leaseOwner);
        if (!step.getStepCode().equals(task.getCurrentStepCode())) {
            throw new LeaseLostException("Task current step changed before success checkpoint");
        }
        LocalDateTime now = now();
        if (mapper.markStepSucceeded(task.getTenantId(), task.getTaskId(), step.getStepCode(),
                resultJson, resultSha256, now) != 1) {
            throw new LeaseLostException("Task step success checkpoint was rejected");
        }
        List<Step> steps = mapper.selectSteps(task.getTenantId(), task.getTaskId());
        String nextStepCode = steps.stream()
                .filter(candidate -> candidate.getStepOrder() > step.getStepOrder())
                .map(Step::getStepCode).findFirst().orElse(null);
        if (nextStepCode == null) {
            if (mapper.complete(task.getTenantId(), task.getTaskId(), leaseOwner, now) != 1) {
                throw new LeaseLostException("Task completion checkpoint was rejected");
            }
            mapper.insertHistory(task.getTenantId(), task.getTaskId(), task.getVersion() + 1,
                    "RUNNING", "SUCCEEDED", null, "TASK_SUCCEEDED", step.getStepCode(),
                    task.getOperatorId(), task.getOperatorType(), now);
        } else {
            if (mapper.advance(task.getTenantId(), task.getTaskId(), leaseOwner, nextStepCode,
                    now.plus(properties.getLeaseDuration()), now) != 1) {
                throw new LeaseLostException("Task step advance checkpoint was rejected");
            }
            mapper.insertHistory(task.getTenantId(), task.getTaskId(), task.getVersion() + 1,
                    "RUNNING", "RUNNING", nextStepCode, "STEP_SUCCEEDED", step.getStepCode(),
                    task.getOperatorId(), task.getOperatorType(), now);
        }
        return nextStepCode;
    }

    @Transactional
    public void checkpointFailure(Task claimed, Step step, String leaseOwner, boolean needsReview,
                                  LocalDateTime nextRetryAt, String errorCode, String errorMessage) {
        Task task = lockOwnedTask(claimed.getTenantId(), claimed.getTaskId(), leaseOwner);
        String taskStatus = needsReview ? "NEEDS_REVIEW" : "QUEUED";
        String stepStatus = needsReview ? "NEEDS_REVIEW" : "FAILED_RETRYABLE";
        LocalDateTime now = now();
        if (mapper.markStepFailed(task.getTenantId(), task.getTaskId(), step.getStepCode(), stepStatus,
                errorCode, errorMessage, now) != 1) {
            throw new LeaseLostException("Task step failure checkpoint was rejected");
        }
        if (mapper.failTask(task.getTenantId(), task.getTaskId(), leaseOwner, taskStatus,
                needsReview ? null : nextRetryAt, errorCode, errorMessage, now) != 1) {
            throw new LeaseLostException("Task failure checkpoint was rejected");
        }
        mapper.insertHistory(task.getTenantId(), task.getTaskId(), task.getVersion() + 1,
                "RUNNING", taskStatus, task.getCurrentStepCode(), errorCode, errorMessage,
                task.getOperatorId(), task.getOperatorType(), now);
    }

    private Task lockOwnedTask(long tenantId, String taskId, String leaseOwner) {
        Task task = requireTask(tenantId, taskId);
        if (!"RUNNING".equals(task.getStatus()) || !leaseOwner.equals(task.getLeaseOwner())) {
            throw new LeaseLostException("Task lease is no longer owned by this worker");
        }
        return task;
    }

    private Task requireTask(long tenantId, String taskId) {
        Task task = mapper.selectTaskForUpdate(tenantId, taskId);
        if (task == null) {
            throw new LeaseLostException("Skill task disappeared: " + taskId);
        }
        return task;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public static final class LeaseLostException extends IllegalStateException {
        public LeaseLostException(String message) {
            super(message);
        }
    }
}
