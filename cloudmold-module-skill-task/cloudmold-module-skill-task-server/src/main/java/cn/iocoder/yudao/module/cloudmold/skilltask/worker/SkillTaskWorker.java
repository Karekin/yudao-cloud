package cn.iocoder.yudao.module.cloudmold.skilltask.worker;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.cloudmold.executor.CloudMoldCapabilityExecutor;
import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcCallContext;
import cn.iocoder.yudao.module.cloudmold.skilltask.SkillTaskProperties;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskMapper;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Candidate;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Step;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Task;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.SkillTaskTemplateResolver;
import cn.iocoder.yudao.module.cloudmold.skilltask.service.SkillTaskCheckpointService;
import cn.iocoder.yudao.module.cloudmold.skilltask.service.SkillTaskCheckpointService.LeaseLostException;
import cn.iocoder.yudao.module.cloudmold.skilltask.service.SkillTaskJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.dubbo.rpc.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "cloudmold.skill-task", name = "worker-enabled", havingValue = "true",
        matchIfMissing = true)
public class SkillTaskWorker {

    private static final Logger log = LoggerFactory.getLogger(SkillTaskWorker.class);

    private final SkillTaskMapper mapper;
    private final SkillTaskCheckpointService checkpoints;
    private final SkillTaskTemplateResolver templates;
    private final SkillTaskJson json;
    private final CloudMoldCapabilityExecutor capabilityExecutor;
    private final SkillTaskProperties properties;
    private final Clock clock;
    private final String leaseOwner = ownerId();

    public SkillTaskWorker(SkillTaskMapper mapper, SkillTaskCheckpointService checkpoints,
                           SkillTaskTemplateResolver templates, SkillTaskJson json,
                           CloudMoldCapabilityExecutor capabilityExecutor, SkillTaskProperties properties,
                           Clock clock) {
        this.mapper = mapper;
        this.checkpoints = checkpoints;
        this.templates = templates;
        this.json = json;
        this.capabilityExecutor = capabilityExecutor;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${cloudmold.skill-task.worker-delay:1s}")
    public void poll() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Candidate> candidates = mapper.selectDue(now, properties.getBatchSize());
        for (Candidate candidate : candidates) {
            try {
                Task claimed = checkpoints.claim(candidate, leaseOwner);
                if (claimed != null) {
                    TenantUtils.execute(claimed.getTenantId(), () -> process(claimed));
                }
            } catch (RuntimeException ex) {
                log.error("Skill task poll failed for tenant={} task={}", candidate.getTenantId(),
                        candidate.getTaskId(), ex);
            }
        }
    }

    private void process(Task claimed) {
        Task task = claimed;
        while ("RUNNING".equals(task.getStatus()) && task.getCurrentStepCode() != null) {
            Step step = mapper.selectStep(task.getTenantId(), task.getTaskId(), task.getCurrentStepCode());
            if (step == null) {
                throw new IllegalStateException("Current Skill task step is missing: " + task.getCurrentStepCode());
            }
            try {
                ArrayNode arguments = resolveArguments(task, step);
                String requestJson = json.canonical(arguments);
                checkpoints.prepareStep(task, step, leaseOwner, requestJson, json.sha256(requestJson));
                boolean writeApproved = writeApproved(task, step);
                JsonNode result = capabilityExecutor.execute(step.getCapabilityId(), arguments,
                        new CloudMoldRpcCallContext(task.getTenantId(), task.getOperatorId(), task.getOperatorType(),
                                task.getSkillId(), task.getRunId()), writeApproved);
                String resultJson = json.canonical(result);
                String nextStep = checkpoints.checkpointSuccess(task, step, leaseOwner,
                        resultJson, json.sha256(resultJson));
                if (nextStep == null) {
                    log.info("Skill task succeeded tenant={} task={} skill={}", task.getTenantId(),
                            task.getTaskId(), task.getSkillId());
                    return;
                }
                task = mapper.selectTask(task.getTenantId(), task.getTaskId());
            } catch (LeaseLostException ex) {
                log.warn("Stopped stale Skill task worker tenant={} task={}: {}", task.getTenantId(),
                        task.getTaskId(), ex.getMessage());
                return;
            } catch (RuntimeException ex) {
                checkpointFailure(task, step, ex);
                return;
            }
        }
    }

    private ArrayNode resolveArguments(Task task, Step current) {
        JsonNode input = json.parse(task.getInputJson(), "persisted inputJson");
        Map<String, JsonNode> results = new LinkedHashMap<>();
        for (Step step : mapper.selectSteps(task.getTenantId(), task.getTaskId())) {
            if ("SUCCEEDED".equals(step.getStatus()) && step.getResultJson() != null) {
                results.put(step.getStepCode(), json.parse(step.getResultJson(), "persisted resultJson"));
            }
        }
        JsonNode template = json.parse(current.getArgumentTemplateJson(), "persisted argumentTemplateJson");
        return templates.resolve(template, input, results, task.getTaskId(), current.getIdempotencyKey());
    }

    private boolean writeApproved(Task task, Step step) {
        if (!"WRITE".equals(step.getOperationType())) {
            return false;
        }
        if ("R1".equals(task.getRiskLevel()) || task.getApprovalRef() == null || task.getApprovalRef().isBlank()) {
            throw new SecurityException("Persisted WRITE step has no R2/R3 approval evidence");
        }
        return true;
    }

    private void checkpointFailure(Task task, Step step, RuntimeException failure) {
        boolean permanent = isPermanent(failure);
        boolean attemptsExhausted = task.getAttemptCount() >= task.getMaxAttempts();
        boolean needsReview = permanent || attemptsExhausted;
        String errorCode = permanent ? "PERMANENT_EXECUTION_FAILURE"
                : attemptsExhausted ? "RETRY_EXHAUSTED" : "TRANSIENT_EXECUTION_FAILURE";
        String message = truncate(rootMessage(failure), properties.getMaxErrorMessageLength());
        LocalDateTime nextRetryAt = LocalDateTime.now(clock).plus(backoff(task.getAttemptCount()));
        try {
            checkpoints.checkpointFailure(task, step, leaseOwner, needsReview, nextRetryAt, errorCode, message);
        } catch (LeaseLostException ex) {
            log.warn("Failure checkpoint lost lease tenant={} task={}: {}", task.getTenantId(), task.getTaskId(),
                    ex.getMessage());
            return;
        }
        log.warn("Skill task {} tenant={} task={} step={} error={}", needsReview ? "needs review" : "will retry",
                task.getTenantId(), task.getTaskId(), step.getStepCode(), message);
    }

    private Duration backoff(int attemptCount) {
        long seconds = 1L << Math.min(Math.max(attemptCount - 1, 0), 20);
        return Duration.ofSeconds(Math.min(seconds, properties.getMaxBackoff().toSeconds()));
    }

    private static boolean isPermanent(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SecurityException || current instanceof IllegalArgumentException) {
                return true;
            }
            if (current instanceof RpcException rpc && rpc.getCode() == RpcException.FORBIDDEN_EXCEPTION) {
                return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return current.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static String ownerId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            host = "unknown-host";
        }
        return host + ":" + ManagementFactory.getRuntimeMXBean().getName() + ":"
                + UUID.randomUUID().toString().substring(0, 8);
    }
}
