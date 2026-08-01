package cn.iocoder.yudao.module.cloudmold.skilltask.worker;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.cloudmold.executor.CloudMoldCapabilityExecutor;
import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcCallContext;
import cn.iocoder.yudao.module.cloudmold.skilltask.SkillTaskProperties;
import cn.iocoder.yudao.module.cloudmold.skilltask.approval.SkillTaskApprovalContext;
import cn.iocoder.yudao.module.cloudmold.skilltask.approval.SkillTaskApprovalVerifier;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskMapper;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Candidate;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Step;
import cn.iocoder.yudao.module.cloudmold.skilltask.dal.SkillTaskRecords.Task;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.SkillTaskTemplateResolver;
import cn.iocoder.yudao.module.cloudmold.skilltask.service.SkillTaskCheckpointService;
import cn.iocoder.yudao.module.cloudmold.skilltask.service.SkillTaskCheckpointService.LeaseLostException;
import cn.iocoder.yudao.module.cloudmold.skilltask.service.SkillTaskJson;
import cn.iocoder.yudao.module.cloudmold.skilltask.service.SkillTaskApiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
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
import java.util.Iterator;
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
    private final SkillTaskApprovalVerifier approvalVerifier;
    private final SkillTaskApiService taskService;
    private final SkillTaskProperties properties;
    private final Clock clock;
    private final String leaseOwner = ownerId();

    public SkillTaskWorker(SkillTaskMapper mapper, SkillTaskCheckpointService checkpoints,
                           SkillTaskTemplateResolver templates, SkillTaskJson json,
                           CloudMoldCapabilityExecutor capabilityExecutor,
                           SkillTaskApprovalVerifier approvalVerifier, SkillTaskApiService taskService,
                           SkillTaskProperties properties, Clock clock) {
        this.mapper = mapper;
        this.checkpoints = checkpoints;
        this.templates = templates;
        this.json = json;
        this.capabilityExecutor = capabilityExecutor;
        this.approvalVerifier = approvalVerifier;
        this.taskService = taskService;
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
                JsonNode request = resolveTemplate(task, step, step.getArgumentTemplateJson());
                String requestJson = json.canonical(request);
                checkpoints.prepareStep(task, step, leaseOwner, requestJson, json.sha256(requestJson));
                if ("WAIT_CHILD".equals(step.getStepKind()) && waitForChild(task, step, request)) {
                    return;
                }
                Task executionTask = task;
                JsonNode result = checkpoints.executeWithMissionFence(
                        executionTask, () -> executeStep(executionTask, step, request));
                requireSuccessfulWriteOutcome(step, result);
                String resultJson = json.canonical(result);
                if ("WAIT_CAPABILITY".equals(step.getStepKind())
                        && waitForCapability(task, step, result, resultJson)) {
                    return;
                }
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

    private JsonNode executeStep(Task task, Step step, JsonNode request) {
        return switch (step.getStepKind() == null ? "CAPABILITY" : step.getStepKind()) {
            case "CAPABILITY" -> capabilityExecutor.execute(step.getCapabilityId(), requireArray(request),
                    new CloudMoldRpcCallContext(task.getTenantId(), task.getOperatorId(), task.getOperatorType(),
                            task.getSkillId(), task.getRunId()), writeApproved(task, step));
            case "SUBMIT_CHILD" -> submitChild(task, step, requireObject(request));
            case "WAIT_CHILD" -> childResult(task, requireChild(task, childTaskId(request)), true);
            case "WAIT_CAPABILITY" -> capabilityExecutor.execute(step.getCapabilityId(), requireArray(request),
                    new CloudMoldRpcCallContext(task.getTenantId(), task.getOperatorId(), task.getOperatorType(),
                            task.getSkillId(), task.getRunId()), false);
            default -> throw new IllegalArgumentException("Unsupported persisted step kind: " + step.getStepKind());
        };
    }

    private JsonNode submitChild(Task task, Step step, ObjectNode childInput) {
        verifyApprovalScope(task);
        String childRunId = null;
        if (step.getChildRunIdTemplate() != null) {
            JsonNode resolved = resolveTemplate(task, step,
                    json.canonical(TextNode.valueOf(step.getChildRunIdTemplate())));
            if (!resolved.isTextual() || resolved.asText().isBlank()) {
                throw new IllegalArgumentException("Resolved child_run_id must be a non-blank string");
            }
            childRunId = resolved.asText();
        }
        Task child = taskService.submitChild(task, step, childInput, childRunId);
        return childResult(task, child, false);
    }

    private boolean waitForChild(Task task, Step step, JsonNode request) {
        Task child = requireChild(task, childTaskId(request));
        if ("SUCCEEDED".equals(child.getStatus())) {
            return false;
        }
        if ("NEEDS_REVIEW".equals(child.getStatus())) {
            String message = "Child task " + child.getTaskId() + " requires review"
                    + (child.getLastErrorMessage() == null ? "" : ": " + child.getLastErrorMessage());
            checkpoints.checkpointFailure(task, step, leaseOwner, true, null,
                    "CHILD_NEEDS_REVIEW", truncate(message, properties.getMaxErrorMessageLength()));
            return true;
        }
        if (!List.of("QUEUED", "RUNNING", "WAITING").contains(child.getStatus())) {
            throw new IllegalStateException("Child task has unsupported status: " + child.getStatus());
        }
        checkpoints.checkpointWaiting(task, step, leaseOwner,
                Duration.ofSeconds(step.getPollIntervalSeconds() == null ? 2 : step.getPollIntervalSeconds()),
                "childTaskId=" + child.getTaskId() + "; status=" + child.getStatus(), null, null);
        return true;
    }

    private boolean waitForCapability(Task task, Step step, JsonNode result, String resultJson) {
        JsonNode success = json.parse(step.getWaitSuccessJson(), "persisted waitSuccessJson");
        if (matchesAll(result, success)) {
            return false;
        }
        if (step.getWaitFailureJson() != null) {
            JsonNode failure = json.parse(step.getWaitFailureJson(), "persisted waitFailureJson");
            if (matchesAny(result, failure)) {
                String message = "Polled capability entered a configured failure state: " + resultJson;
                checkpoints.checkpointFailure(task, step, leaseOwner, true, null,
                        "POLLED_CAPABILITY_FAILED", truncate(message, properties.getMaxErrorMessageLength()));
                return true;
            }
        }
        checkpoints.checkpointWaiting(task, step, leaseOwner,
                Duration.ofSeconds(step.getPollIntervalSeconds() == null ? 2 : step.getPollIntervalSeconds()),
                "capabilityId=" + step.getCapabilityId() + "; terminal=false", resultJson,
                json.sha256(resultJson));
        return true;
    }

    private static boolean matchesAll(JsonNode result, JsonNode conditions) {
        Iterator<Map.Entry<String, JsonNode>> fields = conditions.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> condition = fields.next();
            if (!scalarEquals(condition.getValue(), result.at(condition.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesAny(JsonNode result, JsonNode conditions) {
        Iterator<Map.Entry<String, JsonNode>> fields = conditions.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> condition = fields.next();
            JsonNode actual = result.at(condition.getKey());
            for (JsonNode expected : condition.getValue()) {
                if (scalarEquals(expected, actual)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean scalarEquals(JsonNode expected, JsonNode actual) {
        if (expected.isNumber() && actual.isNumber()) {
            return expected.decimalValue().compareTo(actual.decimalValue()) == 0;
        }
        return expected.equals(actual);
    }

    private Task requireChild(Task parent, String childTaskId) {
        Task child = mapper.selectTask(parent.getTenantId(), childTaskId);
        if (child == null || !parent.getTaskId().equals(child.getParentTaskId())) {
            throw new SecurityException("WAIT_CHILD may only observe a direct persisted child task");
        }
        return child;
    }

    private static String childTaskId(JsonNode request) {
        ArrayNode arguments = requireArray(request);
        if (arguments.size() != 1 || !arguments.get(0).isTextual() || arguments.get(0).asText().isBlank()) {
            throw new IllegalArgumentException("WAIT_CHILD requires one non-blank child task ID");
        }
        return arguments.get(0).asText();
    }

    private ObjectNode childResult(Task parent, Task child, boolean includeOutputs) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        result.put("parentTaskId", parent.getTaskId());
        result.put("childTaskId", child.getTaskId());
        result.put("childRunId", child.getRunId());
        result.put("status", child.getStatus());
        if (includeOutputs && "SUCCEEDED".equals(child.getStatus())) {
            ObjectNode outputs = result.putObject("outputs");
            for (Step childStep : mapper.selectSteps(child.getTenantId(), child.getTaskId())) {
                if ("SUCCEEDED".equals(childStep.getStatus()) && childStep.getResultJson() != null) {
                    outputs.set(childStep.getStepCode(),
                            json.parse(childStep.getResultJson(), "persisted child step resultJson"));
                }
            }
        }
        return result;
    }

    private JsonNode resolveTemplate(Task task, Step current, String templateJson) {
        JsonNode input = json.parse(task.getInputJson(), "persisted inputJson");
        Map<String, JsonNode> results = new LinkedHashMap<>();
        for (Step step : mapper.selectSteps(task.getTenantId(), task.getTaskId())) {
            if ("SUCCEEDED".equals(step.getStatus()) && step.getResultJson() != null) {
                results.put(step.getStepCode(), json.parse(step.getResultJson(), "persisted resultJson"));
            }
        }
        JsonNode template = json.parse(templateJson, "persisted step template");
        return templates.resolveValue(template, input, results, task.getTaskId(), task.getRunId(),
                current.getIdempotencyKey(), task.getApprovalRef());
    }

    private boolean writeApproved(Task task, Step step) {
        if (!"WRITE".equals(step.getOperationType())) {
            return false;
        }
        if ("R1".equals(task.getRiskLevel()) || task.getApprovalRef() == null || task.getApprovalRef().isBlank()) {
            throw new SecurityException("Persisted WRITE step has no R2/R3 approval evidence");
        }
        verifyApprovalScope(task);
        return true;
    }

    private static void requireSuccessfulWriteOutcome(Step step, JsonNode result) {
        if (!"CAPABILITY".equals(step.getStepKind())
                || !"WRITE".equals(step.getOperationType())
                || result == null || !result.isObject()
                || !result.has("success") || result.path("success").asBoolean(true)) {
            return;
        }
        String failureCode = result.path("failureCode").asText("BUSINESS_WRITE_FAILED");
        String failureMessage = result.path("failureMessage").asText(failureCode);
        if ("INVALID_ARGUMENT".equals(failureCode)) {
            throw new IllegalArgumentException(failureMessage);
        }
        throw new IllegalStateException(failureCode + ": " + failureMessage);
    }

    private void verifyApprovalScope(Task task) {
        String skillId = task.getApprovalScopeSkillId() == null ? task.getSkillId()
                : task.getApprovalScopeSkillId();
        String skillVersion = task.getApprovalScopeSkillVersion() == null ? task.getSkillVersion()
                : task.getApprovalScopeSkillVersion();
        String inputSha256 = task.getApprovalScopeInputSha256() == null ? task.getInputSha256()
                : task.getApprovalScopeInputSha256();
        String definitionClosureSha256 = task.getApprovalScopeDefinitionClosureSha256() == null
                ? task.getDefinitionClosureSha256() : task.getApprovalScopeDefinitionClosureSha256();
        String riskLevel = task.getApprovalScopeRiskLevel() == null ? task.getRiskLevel()
                : task.getApprovalScopeRiskLevel();
        approvalVerifier.verify(new SkillTaskApprovalContext(task.getTenantId(), task.getOperatorId(),
                task.getOperatorType(), skillId, skillVersion, definitionClosureSha256,
                inputSha256, riskLevel, task.getApprovalRef()));
    }

    private static ArrayNode requireArray(JsonNode value) {
        if (!value.isArray()) {
            throw new IllegalArgumentException("Resolved step arguments are not an array");
        }
        return (ArrayNode) value;
    }

    private static ObjectNode requireObject(JsonNode value) {
        if (!value.isObject()) {
            throw new IllegalArgumentException("Resolved child input is not an object");
        }
        return (ObjectNode) value;
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
