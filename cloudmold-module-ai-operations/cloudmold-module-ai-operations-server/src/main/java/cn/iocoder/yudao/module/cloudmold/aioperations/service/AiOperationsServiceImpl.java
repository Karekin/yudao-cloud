package cn.iocoder.yudao.module.cloudmold.aioperations.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.aioperations.api.*;
import cn.iocoder.yudao.module.cloudmold.aioperations.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.aioperations.dal.mysql.*;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AiOperationsServiceImpl implements AiOperationsCommandApi, AiOperationsQueryApi {

    static final int OPERATION_SUCCEEDED = 10;
    private static final String SOURCE_SYSTEM = "cloudmold-ai-operations";
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Pattern EMAIL = Pattern.compile("(?i).+@.+\\..+");
    private static final Pattern IPV4 = Pattern.compile("(?:^|.*[^0-9])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?:[^0-9].*|$)");

    private final AiOperationsOperationMapper operationMapper;
    private final AiApplicationMapper applicationMapper;
    private final AiWorkflowDefinitionMapper workflowDefinitionMapper;
    private final AiWorkflowVersionMapper workflowVersionMapper;
    private final AiWorkflowRunMapper workflowRunMapper;
    private final AiInvocationAttemptMapper invocationAttemptMapper;
    private final AiOutcomeFeedbackMapper outcomeFeedbackMapper;
    private final AiStatusHistoryMapper statusHistoryMapper;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiOperationsCommandResult execute(AiOperationsCommand command) {
        validateEnvelope(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = at(command.getOccurredAt());
        String hash = fingerprint(tenantId, command);
        String attemptToken = UUID.randomUUID().toString();
        operationMapper.insertOrResolve(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                hash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve AI operation");
        AiOperationsOperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "AI operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(hash, operation.getRequestHash()),
                    "idempotency key conflicts with different payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing AI operation is not complete");
            AiOperationsCommandResult replay = JsonUtils.parseObject(operation.getResultJson(),
                    AiOperationsCommandResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        Outcome outcome = switch (command.getOperation()) {
            case REGISTER_APPLICATION -> registerApplication(tenantId, operationId, command);
            case ACTIVATE_APPLICATION, SUSPEND_APPLICATION, RETIRE_APPLICATION ->
                    changeApplication(tenantId, operationId, command, now);
            case PUBLISH_WORKFLOW_VERSION -> publishWorkflowVersion(tenantId, command, now);
            case START_WORKFLOW_RUN -> startRun(tenantId, operationId, command, now);
            case COMPLETE_WORKFLOW_RUN, FAIL_WORKFLOW_RUN, CANCEL_WORKFLOW_RUN ->
                    finishRun(tenantId, operationId, command, now);
            case RECORD_MODEL_INVOCATION -> recordInvocation(tenantId, command, now);
            case RECORD_OUTCOME_FEEDBACK -> recordFeedback(tenantId, command, now);
        };
        appendEvent(tenantId, command, outcome);
        AiOperationsCommandResult result = outcome.result();
        result.setOperationId(operationId);
        require(operationMapper.markSucceeded(operationId, tenantId, outcome.aggregateType(), outcome.aggregateId(),
                JsonUtils.toJsonString(result), now) == 1, "AI operation completion conflict");
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public AiOperationsAggregateView get(String aggregateType, String aggregateId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireSafeRef(aggregateId, "aggregateId", 64);
        return switch (normalized(aggregateType)) {
            case "AI_APPLICATION" -> applicationView(requireNonNull(
                    applicationMapper.selectOneById(tenantId, aggregateId), "application not found"));
            case "AI_WORKFLOW" -> workflowView(requireNonNull(
                    workflowDefinitionMapper.selectOneById(tenantId, aggregateId), "workflow not found"));
            case "AI_WORKFLOW_RUN" -> runView(requireNonNull(
                    workflowRunMapper.selectOneById(tenantId, aggregateId), "workflow run not found"));
            case "AI_MODEL_INVOCATION" -> invocationView(requireNonNull(
                    invocationAttemptMapper.selectOneById(tenantId, aggregateId), "invocation attempt not found"));
            case "AI_OUTCOME_FEEDBACK" -> feedbackView(requireNonNull(
                    outcomeFeedbackMapper.selectOneById(tenantId, aggregateId), "outcome feedback not found"));
            default -> throw new IllegalArgumentException("unsupported aggregateType");
        };
    }

    private Outcome registerApplication(Long tenantId, Long operationId, AiOperationsCommand command) {
        AiOperationsCommand.ApplicationDefinition input = requireNonNull(command.getApplication(),
                "application is required");
        requireSafeCode(input.getApplicationCode(), "applicationCode", 64);
        requireSafeName(input.getName(), "application name", 128);
        require(applicationMapper.selectByCode(tenantId, input.getApplicationCode()) == null,
                "applicationCode already exists");
        String id = valueOrUuid(input.getApplicationId());
        LocalDateTime now = at(command.getOccurredAt());
        AiApplicationDO row = new AiApplicationDO().setApplicationId(id).setTenantId(tenantId)
                .setApplicationCode(input.getApplicationCode()).setName(input.getName().trim()).setStatus("DRAFT")
                .setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        applicationMapper.insert(row);
        appendHistory(tenantId, operationId, command, "ai_application", id, 1L, null, "DRAFT", null);
        return outcome("ai.application.status_changed", "ai_application", id, 1L, "DRAFT",
                applicationPayload(row, null, "DRAFT", command.getOperation()));
    }

    private Outcome changeApplication(Long tenantId, Long operationId, AiOperationsCommand command,
                                      LocalDateTime now) {
        AiOperationsCommand.ApplicationDefinition input = requireNonNull(command.getApplication(),
                "application is required");
        requireSafeRef(input.getApplicationId(), "applicationId", 64);
        AiApplicationDO row = requireNonNull(applicationMapper.selectForUpdate(tenantId, input.getApplicationId()),
                "application not found");
        requireVersion(row.getVersion(), input.getExpectedVersion());
        String next = applicationTransition(row.getStatus(), command.getOperation());
        require(applicationMapper.updateStatusCas(tenantId, row.getApplicationId(), row.getVersion(), next, now) == 1,
                "application version conflict");
        long nextVersion = row.getVersion() + 1;
        appendHistory(tenantId, operationId, command, "ai_application", row.getApplicationId(), nextVersion,
                row.getStatus(), next, null);
        return outcome("ai.application.status_changed", "ai_application", row.getApplicationId(), nextVersion,
                next, applicationPayload(row, row.getStatus(), next, command.getOperation()));
    }

    private Outcome publishWorkflowVersion(Long tenantId, AiOperationsCommand command, LocalDateTime now) {
        AiOperationsCommand.WorkflowVersionDefinition input = requireNonNull(command.getWorkflowVersion(),
                "workflowVersion is required");
        requireSafeRef(input.getApplicationId(), "applicationId", 64);
        requireSafeCode(input.getWorkflowCode(), "workflowCode", 64);
        requireSafeRef(input.getDefinitionRef(), "definitionRef", 256);
        requireSha256(input.getDefinitionSha256(), "definitionSha256");
        require(input.getWorkflowVersion() != null && input.getWorkflowVersion() > 0,
                "workflowVersion must be positive");
        AiApplicationDO application = requireNonNull(
                applicationMapper.selectForUpdate(tenantId, input.getApplicationId()), "application not found");
        require("ACTIVE".equals(application.getStatus()), "application must be ACTIVE");

        String workflowId = valueOrUuid(input.getWorkflowId());
        AiWorkflowDefinitionDO definition = workflowDefinitionMapper.selectForUpdate(tenantId, workflowId);
        if (definition == null) {
            require(input.getExpectedDefinitionVersion() == null || input.getExpectedDefinitionVersion() == 0,
                    "expectedDefinitionVersion must be absent or zero for first version");
            require(input.getWorkflowVersion() == 1L, "first workflowVersion must be 1");
            require(workflowDefinitionMapper.selectByCode(tenantId, input.getApplicationId(),
                    input.getWorkflowCode()) == null, "workflowCode already exists");
            definition = new AiWorkflowDefinitionDO().setWorkflowId(workflowId).setTenantId(tenantId)
                    .setApplicationId(input.getApplicationId()).setWorkflowCode(input.getWorkflowCode())
                    .setCurrentVersion(1L).setCreatedAt(now).setUpdatedAt(now);
            workflowDefinitionMapper.insert(definition);
        } else {
            require(Objects.equals(definition.getApplicationId(), input.getApplicationId()),
                    "workflow application mismatch");
            require(Objects.equals(definition.getWorkflowCode(), input.getWorkflowCode()),
                    "workflowCode is immutable");
            requireVersion(definition.getCurrentVersion(), input.getExpectedDefinitionVersion());
            require(input.getWorkflowVersion().equals(definition.getCurrentVersion() + 1),
                    "workflowVersion must be the next version");
            require(workflowDefinitionMapper.publishVersionCas(tenantId, workflowId,
                    definition.getCurrentVersion(), now) == 1, "workflow definition version conflict");
        }
        require(workflowVersionMapper.selectByVersion(tenantId, workflowId, input.getWorkflowVersion()) == null,
                "workflow version already exists");
        String versionId = valueOrUuid(input.getWorkflowVersionId());
        AiWorkflowVersionDO version = new AiWorkflowVersionDO().setWorkflowVersionId(versionId)
                .setTenantId(tenantId).setWorkflowId(workflowId).setApplicationId(input.getApplicationId())
                .setWorkflowVersion(input.getWorkflowVersion()).setDefinitionRef(input.getDefinitionRef())
                .setDefinitionSha256(input.getDefinitionSha256().toLowerCase(Locale.ROOT))
                .setPublishedAt(now).setCreatedAt(now);
        workflowVersionMapper.insert(version);
        return outcome("ai.workflow.version.published", "ai_workflow", workflowId,
                input.getWorkflowVersion(), "PUBLISHED", workflowPayload(definition, version, command.getOperation()));
    }

    private Outcome startRun(Long tenantId, Long operationId, AiOperationsCommand command, LocalDateTime now) {
        AiOperationsCommand.WorkflowRunDefinition input = requireNonNull(command.getWorkflowRun(),
                "workflowRun is required");
        requireSafeRef(input.getRunKey(), "runKey", 128);
        requireSafeRef(input.getApplicationId(), "applicationId", 64);
        requireSafeRef(input.getWorkflowId(), "workflowId", 64);
        requireSafeCode(input.getTriggerType(), "triggerType", 32);
        if (input.getBusinessRef() != null) requireSafeRef(input.getBusinessRef(), "businessRef", 256);
        require(input.getWorkflowVersion() != null && input.getWorkflowVersion() > 0,
                "workflowVersion must be positive");
        require(input.getExpectedInvocationCount() != null && input.getExpectedInvocationCount() >= 0,
                "expectedInvocationCount must be non-negative");
        require(workflowRunMapper.selectByRunKey(tenantId, input.getRunKey()) == null,
                "runKey already exists");
        AiApplicationDO application = requireNonNull(applicationMapper.selectForUpdate(tenantId,
                input.getApplicationId()), "application not found");
        require("ACTIVE".equals(application.getStatus()), "application must be ACTIVE");
        AiWorkflowDefinitionDO workflow = requireNonNull(workflowDefinitionMapper.selectForUpdate(tenantId,
                input.getWorkflowId()), "workflow not found");
        require(Objects.equals(workflow.getApplicationId(), input.getApplicationId()),
                "workflow application mismatch");
        AiWorkflowVersionDO version = requireNonNull(workflowVersionMapper.selectByVersion(tenantId,
                input.getWorkflowId(), input.getWorkflowVersion()), "workflow version not found");
        String id = valueOrUuid(input.getRunId());
        AiWorkflowRunDO row = new AiWorkflowRunDO().setRunId(id).setTenantId(tenantId)
                .setRunKey(input.getRunKey()).setApplicationId(input.getApplicationId())
                .setWorkflowId(input.getWorkflowId()).setWorkflowVersionId(version.getWorkflowVersionId())
                .setWorkflowVersion(input.getWorkflowVersion()).setTriggerType(normalized(input.getTriggerType()))
                .setBusinessRef(input.getBusinessRef()).setStatus("RUNNING")
                .setExpectedInvocationCount(input.getExpectedInvocationCount()).setStartedAt(now)
                .setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        workflowRunMapper.insert(row);
        appendHistory(tenantId, operationId, command, "ai_workflow_run", id, 1L, null, "RUNNING", null);
        return outcome("ai.workflow.run.status_changed", "ai_workflow_run", id, 1L, "RUNNING",
                runPayload(row, null, "RUNNING", command.getOperation()));
    }

    private Outcome finishRun(Long tenantId, Long operationId, AiOperationsCommand command, LocalDateTime now) {
        AiOperationsCommand.WorkflowRunDefinition input = requireNonNull(command.getWorkflowRun(),
                "workflowRun is required");
        requireSafeRef(input.getRunId(), "runId", 64);
        AiWorkflowRunDO row = requireNonNull(workflowRunMapper.selectForUpdate(tenantId, input.getRunId()),
                "workflow run not found");
        requireVersion(row.getVersion(), input.getExpectedVersion());
        require("RUNNING".equals(row.getStatus()), "workflow run is terminal");
        int recorded = invocationAttemptMapper.countByRun(tenantId, row.getRunId());
        require(recorded == row.getExpectedInvocationCount(),
                "recorded invocation count does not match expectedInvocationCount");
        String next = switch (command.getOperation()) {
            case COMPLETE_WORKFLOW_RUN -> "SUCCEEDED";
            case FAIL_WORKFLOW_RUN -> "FAILED";
            case CANCEL_WORKFLOW_RUN -> "CANCELLED";
            default -> throw new IllegalArgumentException("invalid workflow run operation");
        };
        String errorCode = input.getErrorCode();
        if ("SUCCEEDED".equals(next)) {
            require(errorCode == null, "errorCode is not valid for a successful run");
        } else {
            requireSafeCode(errorCode, "errorCode", 64);
        }
        require(!now.isBefore(row.getStartedAt()), "finishedAt must not precede startedAt");
        require(workflowRunMapper.updateStatusCas(tenantId, row.getRunId(), row.getVersion(), next,
                now, errorCode, now) == 1, "workflow run version conflict");
        long nextVersion = row.getVersion() + 1;
        row.setFinishedAt(now).setErrorCode(errorCode);
        appendHistory(tenantId, operationId, command, "ai_workflow_run", row.getRunId(), nextVersion,
                row.getStatus(), next, errorCode);
        return outcome("ai.workflow.run.status_changed", "ai_workflow_run", row.getRunId(), nextVersion,
                next, runPayload(row, row.getStatus(), next, command.getOperation()));
    }

    private Outcome recordInvocation(Long tenantId, AiOperationsCommand command, LocalDateTime now) {
        AiOperationsCommand.InvocationAttemptDefinition input = requireNonNull(command.getInvocationAttempt(),
                "invocationAttempt is required");
        requireSafeRef(input.getAttemptKey(), "attemptKey", 128);
        requireSafeRef(input.getRunId(), "runId", 64);
        requireSafeRef(input.getStepRef(), "stepRef", 128);
        requireSafeCode(input.getProviderCode(), "providerCode", 64);
        requireSafeCode(input.getModelCode(), "modelCode", 128);
        if (input.getProviderRequestRef() != null) {
            requireSafeRef(input.getProviderRequestRef(), "providerRequestRef", 256);
        }
        require(input.getAttemptNo() != null && input.getAttemptNo() > 0, "attemptNo must be positive");
        String attemptOutcome = normalized(input.getOutcome());
        require(Set.of("SUCCEEDED", "FAILED", "CANCELLED").contains(attemptOutcome), "invalid invocation outcome");
        validateTokens(input);
        require(input.getLatencyMillis() != null && input.getLatencyMillis() >= 0,
                "latencyMillis must be non-negative");
        validateCost(input);
        if ("SUCCEEDED".equals(attemptOutcome)) {
            require(input.getErrorCode() == null, "errorCode is not valid for a successful invocation");
        } else {
            requireSafeCode(input.getErrorCode(), "errorCode", 64);
        }
        require(invocationAttemptMapper.selectByAttemptKey(tenantId, input.getAttemptKey()) == null,
                "attemptKey already exists");
        AiWorkflowRunDO run = requireNonNull(workflowRunMapper.selectForUpdate(tenantId, input.getRunId()),
                "workflow run not found");
        require("RUNNING".equals(run.getStatus()), "invocation cannot be appended to a terminal run");
        int recorded = invocationAttemptMapper.countByRun(tenantId, run.getRunId());
        require(run.getExpectedInvocationCount() != null && recorded < run.getExpectedInvocationCount(),
                "invocation would exceed expectedInvocationCount");
        String id = valueOrUuid(input.getAttemptId());
        AiInvocationAttemptDO row = new AiInvocationAttemptDO().setAttemptId(id).setTenantId(tenantId)
                .setAttemptKey(input.getAttemptKey()).setRunId(run.getRunId())
                .setApplicationId(run.getApplicationId()).setWorkflowId(run.getWorkflowId())
                .setStepRef(input.getStepRef()).setAttemptNo(input.getAttemptNo())
                .setProviderCode(normalized(input.getProviderCode())).setModelCode(input.getModelCode())
                .setProviderRequestRef(input.getProviderRequestRef()).setOutcome(attemptOutcome)
                .setInputTokens(input.getInputTokens()).setCachedInputTokens(input.getCachedInputTokens())
                .setOutputTokens(input.getOutputTokens()).setTotalTokens(input.getTotalTokens())
                .setLatencyMillis(input.getLatencyMillis()).setCostAmountMinor(input.getCostAmountMinor())
                .setCurrencyCode(input.getCurrencyCode() == null ? null : currency(input.getCurrencyCode()))
                .setPricingVersionRef(input.getPricingVersionRef()).setErrorCode(input.getErrorCode())
                .setOccurredAt(now).setCreatedAt(now);
        invocationAttemptMapper.insert(row);
        return outcome("ai.model.invocation.recorded", "ai_model_invocation", id, 1L, "RECORDED",
                invocationPayload(row));
    }

    private Outcome recordFeedback(Long tenantId, AiOperationsCommand command, LocalDateTime now) {
        AiOperationsCommand.OutcomeFeedbackDefinition input = requireNonNull(command.getOutcomeFeedback(),
                "outcomeFeedback is required");
        requireSafeRef(input.getFeedbackKey(), "feedbackKey", 128);
        requireSafeRef(input.getRunId(), "runId", 64);
        String type = normalized(input.getFeedbackType());
        String result = normalized(input.getOutcomeCode());
        String evaluator = normalized(input.getEvaluatorType());
        require(Set.of("QUALITY", "CORRECTNESS", "BUSINESS_OUTCOME", "SAFETY").contains(type),
                "invalid feedbackType");
        require(Set.of("POSITIVE", "NEGATIVE", "NEUTRAL", "UNKNOWN").contains(result),
                "invalid outcomeCode");
        require(Set.of("HUMAN", "AUTOMATED", "BUSINESS_SYSTEM").contains(evaluator),
                "invalid evaluatorType");
        if (input.getEvidenceRef() != null) requireSafeRef(input.getEvidenceRef(), "evidenceRef", 256);
        require(outcomeFeedbackMapper.selectByFeedbackKey(tenantId, input.getFeedbackKey()) == null,
                "feedbackKey already exists");
        AiWorkflowRunDO run = requireNonNull(workflowRunMapper.selectForUpdate(tenantId, input.getRunId()),
                "workflow run not found");
        require(!"RUNNING".equals(run.getStatus()), "outcome feedback requires a terminal workflow run");
        String id = valueOrUuid(input.getFeedbackId());
        AiOutcomeFeedbackDO row = new AiOutcomeFeedbackDO().setFeedbackId(id).setTenantId(tenantId)
                .setFeedbackKey(input.getFeedbackKey()).setRunId(input.getRunId()).setFeedbackType(type)
                .setOutcomeCode(result).setEvaluatorType(evaluator).setEvidenceRef(input.getEvidenceRef())
                .setOccurredAt(now).setCreatedAt(now);
        outcomeFeedbackMapper.insert(row);
        return outcome("ai.outcome.feedback.recorded", "ai_outcome_feedback", id, 1L, "RECORDED",
                feedbackPayload(row));
    }

    private void appendHistory(Long tenantId, Long operationId, AiOperationsCommand command, String aggregateType,
                               String aggregateId, Long aggregateVersion, String previous, String current,
                               String errorCode) {
        statusHistoryMapper.insert(new AiStatusHistoryDO().setHistoryId(UUID.randomUUID().toString())
                .setTenantId(tenantId).setAggregateType(aggregateType).setAggregateId(aggregateId)
                .setAggregateVersion(aggregateVersion).setOperationId(operationId)
                .setOperationType(command.getOperation().name()).setPreviousStatus(previous)
                .setCurrentStatus(current).setErrorCode(errorCode).setOccurredAt(at(command.getOccurredAt()))
                .setCreatedAt(at(command.getOccurredAt())));
    }

    private void appendEvent(Long tenantId, AiOperationsCommand command, Outcome outcome) {
        outboxAppender.append(AppendDomainEventCommand.builder().eventType(outcome.eventType()).schemaVersion(1)
                .sourceSystem(SOURCE_SYSTEM).tenantId(tenantId).aggregateType(outcome.aggregateType())
                .aggregateId(outcome.aggregateId()).aggregateVersion(outcome.aggregateVersion())
                .eventSequence((short) 1).occurredAt(command.getOccurredAt())
                .correlationId(command.getCorrelationId()).causationId(command.getCausationId())
                .idempotencyKey(command.getIdempotencyKey() + ":event").payload(outcome.payload())
                .headers(Map.of("operation", command.getOperation().name())).destination("lakehouse").build());
    }

    static String fingerprint(Long tenantId, AiOperationsCommand command) {
        return DigestUtil.sha256Hex(tenantId + "\u001f" + JsonUtils.toJsonString(command));
    }

    private static void validateEnvelope(AiOperationsCommand command) {
        require(command != null && command.getOperation() != null, "operation is required");
        requireSafeRef(command.getIdempotencyKey(), "idempotencyKey", 128);
        requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) requireUuid(command.getCausationId(), "causationId");
        require(command.getOccurredAt() != null, "occurredAt is required");
    }

    private static void validateTokens(AiOperationsCommand.InvocationAttemptDefinition input) {
        require(input.getInputTokens() != null && input.getInputTokens() >= 0, "inputTokens must be non-negative");
        require(input.getCachedInputTokens() != null && input.getCachedInputTokens() >= 0,
                "cachedInputTokens must be non-negative");
        require(input.getOutputTokens() != null && input.getOutputTokens() >= 0,
                "outputTokens must be non-negative");
        require(input.getTotalTokens() != null && input.getTotalTokens() >= 0,
                "totalTokens must be non-negative");
        require(input.getCachedInputTokens() <= input.getInputTokens(),
                "cachedInputTokens must not exceed inputTokens");
        require(input.getTotalTokens().equals(Math.addExact(input.getInputTokens(), input.getOutputTokens())),
                "totalTokens must equal inputTokens plus outputTokens");
    }

    private static void validateCost(AiOperationsCommand.InvocationAttemptDefinition input) {
        boolean amount = input.getCostAmountMinor() != null;
        boolean currency = input.getCurrencyCode() != null;
        boolean pricing = input.getPricingVersionRef() != null;
        require(amount == currency && currency == pricing,
                "costAmountMinor, currencyCode and pricingVersionRef must be provided together");
        if (amount) {
            require(input.getCostAmountMinor() >= 0, "costAmountMinor must be non-negative");
            currency(input.getCurrencyCode());
            requireSafeRef(input.getPricingVersionRef(), "pricingVersionRef", 128);
        }
    }

    private static String applicationTransition(String status, AiOperationsOperation operation) {
        return switch (operation) {
            case ACTIVATE_APPLICATION -> transition(status, Set.of("DRAFT", "SUSPENDED"), "ACTIVE", "application");
            case SUSPEND_APPLICATION -> transition(status, Set.of("ACTIVE"), "SUSPENDED", "application");
            case RETIRE_APPLICATION -> transition(status, Set.of("DRAFT", "ACTIVE", "SUSPENDED"), "RETIRED", "application");
            default -> throw new IllegalArgumentException("invalid application operation");
        };
    }

    private static String transition(String current, Set<String> allowed, String next, String name) {
        require(allowed.contains(current), "illegal " + name + " transition from " + current + " to " + next);
        return next;
    }

    private static Map<String, Object> applicationPayload(AiApplicationDO row, String previous, String current,
                                                           AiOperationsOperation operation) {
        Map<String, Object> p = payload();
        put(p, "application_id", row.getApplicationId()); put(p, "application_code", row.getApplicationCode());
        put(p, "name", row.getName()); put(p, "previous_status", previous); put(p, "current_status", current);
        put(p, "operation", operation.name()); return p;
    }

    private static Map<String, Object> workflowPayload(AiWorkflowDefinitionDO definition, AiWorkflowVersionDO version,
                                                        AiOperationsOperation operation) {
        Map<String, Object> p = payload();
        put(p, "workflow_id", definition.getWorkflowId()); put(p, "workflow_code", definition.getWorkflowCode());
        put(p, "application_id", definition.getApplicationId()); put(p, "workflow_version", version.getWorkflowVersion());
        put(p, "definition_sha256", version.getDefinitionSha256()); put(p, "published_at", instant(version.getPublishedAt()));
        put(p, "operation", operation.name()); return p;
    }

    private static Map<String, Object> runPayload(AiWorkflowRunDO row, String previous, String current,
                                                   AiOperationsOperation operation) {
        Map<String, Object> p = payload();
        put(p, "run_id", row.getRunId()); put(p, "application_id", row.getApplicationId());
        put(p, "workflow_id", row.getWorkflowId()); put(p, "workflow_version", row.getWorkflowVersion());
        put(p, "trigger_type", row.getTriggerType()); put(p, "previous_status", previous);
        put(p, "current_status", current); put(p, "expected_invocation_count", row.getExpectedInvocationCount());
        put(p, "started_at", instant(row.getStartedAt())); put(p, "finished_at", instant(row.getFinishedAt()));
        put(p, "error_code", row.getErrorCode()); put(p, "operation", operation.name()); return p;
    }

    private static Map<String, Object> invocationPayload(AiInvocationAttemptDO row) {
        Map<String, Object> p = payload();
        put(p, "attempt_id", row.getAttemptId()); put(p, "run_id", row.getRunId());
        put(p, "application_id", row.getApplicationId()); put(p, "workflow_id", row.getWorkflowId());
        put(p, "provider_code", row.getProviderCode()); put(p, "model_code", row.getModelCode());
        put(p, "attempt_no", row.getAttemptNo()); put(p, "outcome", row.getOutcome());
        put(p, "input_tokens", row.getInputTokens()); put(p, "cached_input_tokens", row.getCachedInputTokens());
        put(p, "output_tokens", row.getOutputTokens()); put(p, "total_tokens", row.getTotalTokens());
        put(p, "latency_millis", row.getLatencyMillis()); put(p, "cost_amount_minor", row.getCostAmountMinor());
        put(p, "currency_code", row.getCurrencyCode()); put(p, "pricing_version_ref", row.getPricingVersionRef());
        put(p, "error_code", row.getErrorCode()); put(p, "occurred_at", instant(row.getOccurredAt())); return p;
    }

    private static Map<String, Object> feedbackPayload(AiOutcomeFeedbackDO row) {
        Map<String, Object> p = payload();
        put(p, "feedback_id", row.getFeedbackId()); put(p, "run_id", row.getRunId());
        put(p, "feedback_type", row.getFeedbackType()); put(p, "outcome_code", row.getOutcomeCode());
        put(p, "evaluator_type", row.getEvaluatorType()); put(p, "occurred_at", instant(row.getOccurredAt())); return p;
    }

    private static AiOperationsAggregateView applicationView(AiApplicationDO row) {
        return view("ai_application", row.getApplicationId(), row.getApplicationCode(), row.getStatus(),
                row.getVersion(), Map.of("name", row.getName()));
    }

    private static AiOperationsAggregateView workflowView(AiWorkflowDefinitionDO row) {
        return view("ai_workflow", row.getWorkflowId(), row.getWorkflowCode(), "PUBLISHED",
                row.getCurrentVersion(), Map.of("application_id", row.getApplicationId()));
    }

    private static AiOperationsAggregateView runView(AiWorkflowRunDO row) {
        Map<String, Object> a = payload(); put(a, "application_id", row.getApplicationId());
        put(a, "workflow_id", row.getWorkflowId()); put(a, "workflow_version", row.getWorkflowVersion());
        put(a, "expected_invocation_count", row.getExpectedInvocationCount());
        return view("ai_workflow_run", row.getRunId(), row.getRunKey(), row.getStatus(), row.getVersion(), a);
    }

    private static AiOperationsAggregateView invocationView(AiInvocationAttemptDO row) {
        Map<String, Object> a = invocationPayload(row); a.remove("attempt_id");
        return view("ai_model_invocation", row.getAttemptId(), row.getAttemptKey(), "RECORDED", 1L, a);
    }

    private static AiOperationsAggregateView feedbackView(AiOutcomeFeedbackDO row) {
        Map<String, Object> a = feedbackPayload(row); a.remove("feedback_id");
        return view("ai_outcome_feedback", row.getFeedbackId(), row.getFeedbackKey(), "RECORDED", 1L, a);
    }

    private static AiOperationsAggregateView view(String type, String id, String code, String status,
                                                   Long version, Map<String, Object> attributes) {
        return AiOperationsAggregateView.builder().aggregateType(type).aggregateId(id).businessCode(code)
                .status(status).version(version).attributes(attributes).build();
    }

    private static Outcome outcome(String eventType, String aggregateType, String aggregateId, Long version,
                                   String status, Map<String, Object> payload) {
        return new Outcome(eventType, aggregateType, aggregateId, version, payload,
                AiOperationsCommandResult.builder().aggregateType(aggregateType).aggregateId(aggregateId)
                        .aggregateVersion(version).status(status).duplicate(false).build());
    }

    private static void requireVersion(Long actual, Long expected) {
        require(expected != null, "expectedVersion is required");
        require(Objects.equals(actual, expected), "aggregate version conflict");
    }

    private static String currency(String value) {
        require(value != null && value.matches("[A-Za-z]{3}"), "currencyCode must be ISO-4217 alpha-3");
        return value.toUpperCase(Locale.ROOT);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String valueOrUuid(String value) {
        if (value == null || value.isBlank()) return UUID.randomUUID().toString();
        requireSafeRef(value, "id", 64); return value.trim();
    }

    private static LocalDateTime at(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static Map<String, Object> payload() {
        return new LinkedHashMap<>();
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }

    private static void requireSafeName(String value, String name, int maxLength) {
        require(value != null && !value.isBlank() && value.trim().length() <= maxLength, name + " is invalid");
        String lower = value.toLowerCase(Locale.ROOT);
        require(!EMAIL.matcher(value).matches() && !IPV4.matcher(value).matches()
                        && !lower.contains("api_key") && !lower.contains("apikey")
                        && !lower.contains("authorization:") && !lower.contains("bearer ")
                        && !lower.contains("password=") && !lower.contains("stacktrace"),
                name + " contains forbidden sensitive material");
    }

    private static void requireSafeCode(String value, String name, int maxLength) {
        require(value != null && value.length() <= maxLength && SAFE_CODE.matcher(value).matches(),
                name + " must be a bounded token");
    }

    private static void requireSafeRef(String value, String name, int maxLength) {
        require(value != null && value.length() <= maxLength && SAFE_REF.matcher(value).matches(),
                name + " must be an opaque bounded reference");
    }

    private static void requireSha256(String value, String name) {
        require(value != null && value.matches("[A-Fa-f0-9]{64}"), name + " must be a SHA-256 hex digest");
    }

    private static void requireUuid(String value, String name) {
        require(value != null && value.length() == 36, name + " is required");
        try { UUID.fromString(value); } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(name + " must be a UUID");
        }
    }

    private static <T> T requireNonNull(T value, String message) {
        require(value != null, message); return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private record Outcome(String eventType, String aggregateType, String aggregateId, Long aggregateVersion,
                           Map<String, Object> payload, AiOperationsCommandResult result) {
    }
}
