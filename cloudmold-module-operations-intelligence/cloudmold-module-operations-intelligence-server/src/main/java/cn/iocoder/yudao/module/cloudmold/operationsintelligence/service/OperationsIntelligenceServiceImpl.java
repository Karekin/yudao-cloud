package cn.iocoder.yudao.module.cloudmold.operationsintelligence.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.api.*;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.dal.dataobject.OperationsIntelligenceRecords.*;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.dal.mysql.OperationsIntelligenceStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class OperationsIntelligenceServiceImpl
        implements OperationsIntelligenceCommandApi, OperationsIntelligenceQueryApi {

    static final int OPERATION_SUCCEEDED = 10;
    static final String SOURCE_SYSTEM = "cloudmold-operations-intelligence";
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern EVIDENCE_REF = Pattern.compile(
            "(?:sha256:[0-9a-f]{64}|restricted:[A-Za-z0-9_-]{16,128})");
    private static final Set<String> SUBJECT_TYPES = Set.of(
            "PRINCIPAL", "ORDER", "TICKET", "MERCHANT", "CONTENT", "TASK", "DATASET", "EXTERNAL_SUBJECT");
    private static final Set<String> MODEL_OUTCOMES = Set.of("SUCCEEDED", "FAILED", "PARTIAL");
    private static final Set<String> CLUE_DECISIONS = Set.of("ACCEPT", "REJECT");
    private static final Set<String> ALERT_SOURCE_TYPES = Set.of("OBSERVATION", "CLUE", "METADATA_TASK", "METRIC");
    private static final Set<String> ALERT_SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> TERMINAL_ALERT_STATUSES = Set.of("RESOLVED", "INVALID", "CLOSED_NO_ACTION");

    private final OperationsIntelligenceStoreMapper mapper;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OperationsIntelligenceResult execute(OperationsIntelligenceCommand command) {
        validateEnvelope(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve operations-intelligence operation");
        Operation operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "operations-intelligence operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with different payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing operations-intelligence operation is incomplete");
            OperationsIntelligenceResult replay = JsonUtils.parseObject(
                    operation.getResultJson(), OperationsIntelligenceResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        Outcome outcome = switch (command.getOperation()) {
            case RECORD_OBSERVATION -> recordObservation(tenantId, command, now);
            case RECORD_MODEL_RESULT -> recordModelResult(tenantId, command, now);
            case RECORD_CLUE -> recordClue(tenantId, command, now);
            case REVIEW_CLUE -> reviewClue(tenantId, command, now);
            case OPEN_ALERT -> openAlert(tenantId, operationId, command, now);
            case NOTICE_ALERT, CLAIM_ALERT, RESOLVE_ALERT, INVALIDATE_ALERT, CLOSE_ALERT_NO_ACTION ->
                    transitionAlert(tenantId, operationId, command, now);
        };
        appendEvent(tenantId, command, outcome);
        OperationsIntelligenceResult result = OperationsIntelligenceResult.builder()
                .operationId(operationId).duplicate(false).aggregateType(outcome.aggregateType())
                .aggregateId(outcome.aggregateId()).aggregateVersion(outcome.version())
                .status(outcome.status()).build();
        require(mapper.markOperationSucceeded(operationId, tenantId, outcome.aggregateType(), outcome.aggregateId(),
                JsonUtils.toJsonString(result), now) == 1, "operations-intelligence operation completion conflict");
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public OperationsIntelligenceResult getObservation(String observationId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireRef(observationId, "observationId", 128);
        Observation row = requireNonNull(mapper.selectObservation(tenantId, observationId), "observation not found");
        return view("intelligence_observation", row.getObservationId(), 1L, "RECORDED");
    }

    @Override
    @Transactional(readOnly = true)
    public OperationsIntelligenceResult getClue(String clueId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireRef(clueId, "clueId", 128);
        Clue row = requireNonNull(mapper.selectClue(tenantId, clueId), "clue not found");
        return view("intelligence_clue", row.getClueId(), row.getVersion(), row.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public OperationsIntelligenceResult getAlert(String alertId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireRef(alertId, "alertId", 128);
        Alert row = requireNonNull(mapper.selectAlert(tenantId, alertId), "alert not found");
        return view("operations_alert", row.getAlertId(), row.getVersion(), row.getStatus());
    }

    private Outcome recordObservation(Long tenantId, OperationsIntelligenceCommand command, LocalDateTime now) {
        OperationsIntelligenceCommand.ObservationDefinition input = requireNonNull(
                command.getObservation(), "observation is required");
        requireCode(input.getSourceSystem(), "sourceSystem");
        requireRef(input.getSourceEventId(), "sourceEventId", 256);
        requireCode(input.getObservationType(), "observationType");
        require(SUBJECT_TYPES.contains(input.getSubjectType()), "unsupported subjectType");
        requireRef(input.getSubjectRef(), "subjectRef", 256);
        requireEvidence(input.getEvidenceRef(), "evidenceRef");
        requireSha256(input.getContentSha256(), "contentSha256");
        require(input.getObservedAt() != null && !input.getObservedAt().isAfter(command.getOccurredAt()),
                "observedAt must not follow occurredAt");
        require(mapper.selectObservationBySource(tenantId, input.getSourceSystem(), input.getSourceEventId()) == null,
                "source observation already exists");
        String id = valueOrUuid(input.getObservationId());
        Observation row = new Observation().setObservationId(id).setTenantId(tenantId)
                .setSourceSystem(input.getSourceSystem()).setSourceEventId(input.getSourceEventId())
                .setObservationType(input.getObservationType()).setSubjectType(input.getSubjectType())
                .setSubjectRef(input.getSubjectRef()).setEvidenceRef(input.getEvidenceRef())
                .setContentSha256(input.getContentSha256()).setObservedAt(at(input.getObservedAt())).setCreatedAt(now);
        require(mapper.insertObservation(row) == 1, "failed to persist observation");
        Map<String, Object> payload = payload("observation_id", id, "source_system", row.getSourceSystem(),
                "source_event_id", row.getSourceEventId(), "observation_type", row.getObservationType(),
                "subject_type", row.getSubjectType(), "subject_ref", row.getSubjectRef(),
                "evidence_ref", row.getEvidenceRef(), "content_sha256", row.getContentSha256(),
                "observed_at", input.getObservedAt().toString());
        return new Outcome("operations_intelligence.observation.recorded", "intelligence_observation", id,
                1L, "RECORDED", payload);
    }

    private Outcome recordModelResult(Long tenantId, OperationsIntelligenceCommand command, LocalDateTime now) {
        OperationsIntelligenceCommand.ModelResultDefinition input = requireNonNull(
                command.getModelResult(), "modelResult is required");
        requireRef(input.getObservationId(), "observationId", 128);
        requireNonNull(mapper.selectObservation(tenantId, input.getObservationId()), "observation not found");
        requireRef(input.getInvocationAttemptRef(), "invocationAttemptRef", 128);
        requireRef(input.getModelVersionRef(), "modelVersionRef", 128);
        require(MODEL_OUTCOMES.contains(input.getOutcomeCode()), "unsupported model outcomeCode");
        require(input.getRetryNo() != null && input.getRetryNo() >= 0 && input.getRetryNo() <= 100,
                "retryNo must be between 0 and 100");
        if (input.getScoreBasisPoints() != null) {
            require(input.getScoreBasisPoints() >= 0 && input.getScoreBasisPoints() <= 10_000,
                    "scoreBasisPoints must be between 0 and 10000");
        }
        requireEvidence(input.getEvidenceRef(), "evidenceRef");
        requireSha256(input.getResultSha256(), "resultSha256");
        String id = valueOrUuid(input.getModelResultId());
        ModelResult row = new ModelResult().setModelResultId(id).setTenantId(tenantId)
                .setObservationId(input.getObservationId()).setInvocationAttemptRef(input.getInvocationAttemptRef())
                .setModelVersionRef(input.getModelVersionRef()).setOutcomeCode(input.getOutcomeCode())
                .setScoreBasisPoints(input.getScoreBasisPoints()).setRetryNo(input.getRetryNo())
                .setEvidenceRef(input.getEvidenceRef()).setResultSha256(input.getResultSha256())
                .setOccurredAt(at(command.getOccurredAt())).setCreatedAt(now);
        require(mapper.insertModelResult(row) == 1, "failed to persist model result observation");
        Map<String, Object> payload = payload("model_result_id", id, "observation_id", row.getObservationId(),
                "invocation_attempt_ref", row.getInvocationAttemptRef(), "model_version_ref", row.getModelVersionRef(),
                "outcome_code", row.getOutcomeCode(), "score_basis_points", row.getScoreBasisPoints(),
                "retry_no", row.getRetryNo(), "evidence_ref", row.getEvidenceRef(),
                "result_sha256", row.getResultSha256());
        return new Outcome("operations_intelligence.model_result.recorded", "intelligence_model_result", id,
                1L, "RECORDED", payload);
    }

    private Outcome recordClue(Long tenantId, OperationsIntelligenceCommand command, LocalDateTime now) {
        OperationsIntelligenceCommand.ClueDefinition input = requireNonNull(command.getClue(), "clue is required");
        requireRef(input.getObservationId(), "observationId", 128);
        requireNonNull(mapper.selectObservation(tenantId, input.getObservationId()), "observation not found");
        if (input.getModelResultId() != null) {
            ModelResult result = requireNonNull(mapper.selectModelResult(tenantId, input.getModelResultId()),
                    "model result not found");
            require(Objects.equals(result.getObservationId(), input.getObservationId()),
                    "model result belongs to a different observation");
        }
        requireCode(input.getClueType(), "clueType");
        requireCode(input.getSourceCode(), "sourceCode");
        require(input.getSourcePublishedAt() != null && !input.getSourcePublishedAt().isAfter(command.getOccurredAt()),
                "sourcePublishedAt must not follow occurredAt");
        requireEvidence(input.getEvidenceRef(), "evidenceRef");
        requireSha256(input.getEvidenceSha256(), "evidenceSha256");
        String id = valueOrUuid(input.getClueId());
        Clue row = new Clue().setClueId(id).setTenantId(tenantId).setObservationId(input.getObservationId())
                .setModelResultId(input.getModelResultId()).setClueType(input.getClueType())
                .setSourceCode(input.getSourceCode()).setSourcePublishedAt(at(input.getSourcePublishedAt()))
                .setEvidenceRef(input.getEvidenceRef()).setEvidenceSha256(input.getEvidenceSha256())
                .setStatus("OBSERVED").setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertClue(row) == 1, "failed to persist intelligence clue");
        Map<String, Object> payload = payload("clue_id", id, "observation_id", row.getObservationId(),
                "model_result_id", row.getModelResultId(), "clue_type", row.getClueType(),
                "source_code", row.getSourceCode(), "source_published_at", input.getSourcePublishedAt().toString(),
                "evidence_ref", row.getEvidenceRef(), "evidence_sha256", row.getEvidenceSha256(),
                "current_status", row.getStatus());
        return new Outcome("operations_intelligence.clue.recorded", "intelligence_clue", id,
                1L, row.getStatus(), payload);
    }

    private Outcome reviewClue(Long tenantId, OperationsIntelligenceCommand command, LocalDateTime now) {
        OperationsIntelligenceCommand.ClueDefinition input = requireNonNull(command.getClue(), "clue is required");
        requireRef(input.getClueId(), "clueId", 128);
        require(input.getExpectedVersion() != null && input.getExpectedVersion() == 1L,
                "only clue version 1 can be reviewed");
        requireRef(input.getReviewerPrincipalId(), "reviewerPrincipalId", 128);
        require(CLUE_DECISIONS.contains(input.getReviewDecision()), "reviewDecision must be ACCEPT or REJECT");
        requireCode(input.getReasonCode(), "reasonCode");
        Clue row = requireNonNull(mapper.selectClueForUpdate(tenantId, input.getClueId()), "clue not found");
        require("OBSERVED".equals(row.getStatus()) && Objects.equals(row.getVersion(), input.getExpectedVersion()),
                "clue is already reviewed or version conflicts");
        String after = "ACCEPT".equals(input.getReviewDecision()) ? "ACCEPTED" : "REJECTED";
        require(mapper.reviewClue(tenantId, row.getClueId(), row.getVersion(), after, now) == 1,
                "clue review conflict");
        ClueReview review = new ClueReview().setReviewId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setClueId(row.getClueId()).setDecision(input.getReviewDecision()).setReasonCode(input.getReasonCode())
                .setReviewerPrincipalId(input.getReviewerPrincipalId()).setOccurredAt(at(command.getOccurredAt()))
                .setCreatedAt(now);
        require(mapper.insertClueReview(review) == 1, "failed to persist immutable clue review");
        Map<String, Object> payload = payload("clue_id", row.getClueId(), "review_id", review.getReviewId(),
                "previous_status", row.getStatus(), "current_status", after, "decision", review.getDecision(),
                "reason_code", review.getReasonCode(), "reviewer_principal_id", review.getReviewerPrincipalId());
        return new Outcome("operations_intelligence.clue.reviewed", "intelligence_clue", row.getClueId(),
                2L, after, payload);
    }

    private Outcome openAlert(Long tenantId, Long operationId, OperationsIntelligenceCommand command,
                              LocalDateTime now) {
        OperationsIntelligenceCommand.AlertDefinition input = requireNonNull(command.getAlert(), "alert is required");
        requireCode(input.getAlertCode(), "alertCode");
        require(ALERT_SOURCE_TYPES.contains(input.getSourceType()), "unsupported alert sourceType");
        requireRef(input.getSourceRef(), "sourceRef", 128);
        validateAlertSource(tenantId, input.getSourceType(), input.getSourceRef());
        require(ALERT_SEVERITIES.contains(input.getSeverity()), "unsupported alert severity");
        requireCode(input.getCategory(), "category");
        requireCode(input.getSubcategory(), "subcategory");
        requireEvidence(input.getEvidenceRef(), "evidenceRef");
        requireSha256(input.getTitleSha256(), "titleSha256");
        requireRef(input.getActorPrincipalId(), "actorPrincipalId", 128);
        String id = valueOrUuid(input.getAlertId());
        Alert row = new Alert().setAlertId(id).setTenantId(tenantId).setAlertCode(input.getAlertCode())
                .setSourceType(input.getSourceType()).setSourceRef(input.getSourceRef())
                .setSeverity(input.getSeverity()).setCategory(input.getCategory()).setSubcategory(input.getSubcategory())
                .setEvidenceRef(input.getEvidenceRef()).setTitleSha256(input.getTitleSha256()).setStatus("OPEN")
                .setCurrentActorPrincipalId(input.getActorPrincipalId()).setVersion(1L).setOpenedAt(now)
                .setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertAlert(row) == 1, "failed to persist operations alert");
        insertAlertHistory(tenantId, operationId, command, row, null, "OPEN", input.getActorPrincipalId(), null, now);
        return alertOutcome(command, row, null, "OPEN", input.getActorPrincipalId(), null);
    }

    private Outcome transitionAlert(Long tenantId, Long operationId, OperationsIntelligenceCommand command,
                                    LocalDateTime now) {
        OperationsIntelligenceCommand.AlertDefinition input = requireNonNull(command.getAlert(), "alert is required");
        requireRef(input.getAlertId(), "alertId", 128);
        require(input.getExpectedVersion() != null && input.getExpectedVersion() > 0
                        && input.getExpectedVersion() < Short.MAX_VALUE,
                "expectedVersion must be a positive bounded version");
        requireRef(input.getActorPrincipalId(), "actorPrincipalId", 128);
        requireCode(input.getReasonCode(), "reasonCode");
        Alert row = requireNonNull(mapper.selectAlertForUpdate(tenantId, input.getAlertId()), "alert not found");
        require(Objects.equals(row.getVersion(), input.getExpectedVersion()), "alert version conflict");
        String before = row.getStatus();
        String after = alertTransition(before, command.getOperation());
        LocalDateTime terminalAt = TERMINAL_ALERT_STATUSES.contains(after) ? now : null;
        require(mapper.transitionAlert(tenantId, row.getAlertId(), row.getVersion(), before, after,
                input.getActorPrincipalId(), terminalAt, now) == 1, "alert state transition conflict");
        row.setStatus(after).setVersion(row.getVersion() + 1).setCurrentActorPrincipalId(input.getActorPrincipalId())
                .setTerminalAt(terminalAt).setUpdatedAt(now);
        insertAlertHistory(tenantId, operationId, command, row, before, after, input.getActorPrincipalId(),
                input.getReasonCode(), now);
        return alertOutcome(command, row, before, after, input.getActorPrincipalId(), input.getReasonCode());
    }

    private void validateAlertSource(Long tenantId, String sourceType, String sourceRef) {
        if ("OBSERVATION".equals(sourceType)) {
            requireNonNull(mapper.selectObservation(tenantId, sourceRef), "alert observation source not found");
        } else if ("CLUE".equals(sourceType)) {
            Clue clue = requireNonNull(mapper.selectClue(tenantId, sourceRef), "alert clue source not found");
            require("ACCEPTED".equals(clue.getStatus()), "only a human-accepted clue can open an alert");
        }
    }

    private static String alertTransition(String before, OperationsIntelligenceOperation operation) {
        return switch (operation) {
            case NOTICE_ALERT -> transition(before, Set.of("OPEN"), "NOTIFIED");
            case CLAIM_ALERT -> transition(before, Set.of("OPEN", "NOTIFIED"), "CLAIMED");
            case RESOLVE_ALERT -> transition(before, Set.of("CLAIMED"), "RESOLVED");
            case INVALIDATE_ALERT -> transition(before, Set.of("OPEN", "NOTIFIED", "CLAIMED"), "INVALID");
            case CLOSE_ALERT_NO_ACTION -> transition(before, Set.of("OPEN", "NOTIFIED"), "CLOSED_NO_ACTION");
            default -> throw new IllegalArgumentException("operation is not an alert transition");
        };
    }

    private static String transition(String before, Set<String> allowedBefore, String after) {
        require(allowedBefore.contains(before), "alert cannot transition from " + before + " to " + after);
        return after;
    }

    private void insertAlertHistory(Long tenantId, Long operationId, OperationsIntelligenceCommand command,
                                    Alert row, String before, String after, String actor, String reason,
                                    LocalDateTime now) {
        require(mapper.insertAlertHistory(new AlertHistory().setTenantId(tenantId).setAlertId(row.getAlertId())
                .setAlertVersion(row.getVersion()).setPreviousStatus(before).setCurrentStatus(after)
                .setActorPrincipalId(actor).setReasonCode(reason).setOperationId(operationId)
                .setOccurredAt(at(command.getOccurredAt())).setCreatedAt(now)) == 1,
                "failed to persist immutable alert history");
    }

    private Outcome alertOutcome(OperationsIntelligenceCommand command, Alert row, String before, String after,
                                 String actor, String reason) {
        Map<String, Object> payload = payload("alert_id", row.getAlertId(), "alert_code", row.getAlertCode(),
                "source_type", row.getSourceType(), "source_ref", row.getSourceRef(), "severity", row.getSeverity(),
                "category", row.getCategory(), "subcategory", row.getSubcategory(),
                "evidence_ref", row.getEvidenceRef(), "title_sha256", row.getTitleSha256(),
                "previous_status", before, "current_status", after, "actor_principal_id", actor,
                "reason_code", reason, "operation", command.getOperation().name());
        return new Outcome("operations_intelligence.alert.status_changed", "operations_alert", row.getAlertId(),
                row.getVersion(), after, payload);
    }

    private void appendEvent(Long tenantId, OperationsIntelligenceCommand command, Outcome outcome) {
        outboxAppender.append(AppendDomainEventCommand.builder().eventType(outcome.eventType()).schemaVersion(1)
                .sourceSystem(SOURCE_SYSTEM).tenantId(tenantId).aggregateType(outcome.aggregateType())
                .aggregateId(outcome.aggregateId()).aggregateVersion(outcome.version())
                .eventSequence(outcome.version().shortValue()).occurredAt(command.getOccurredAt())
                .traceId(command.getRunId()).correlationId(command.getCorrelationId())
                .causationId(command.getCausationId())
                .idempotencyKey(outcome.aggregateType() + ":" + outcome.aggregateId() + ":event:" + outcome.version())
                .payload(outcome.payload()).headers(Map.of("pii_safe", true, "automatic_enforcement", false,
                        "raw_content_stored", false)).destination("lakehouse").build());
    }

    private static OperationsIntelligenceResult view(String type, String id, Long version, String status) {
        return OperationsIntelligenceResult.builder().duplicate(false).aggregateType(type).aggregateId(id)
                .aggregateVersion(version).status(status).build();
    }

    private static void validateEnvelope(OperationsIntelligenceCommand command) {
        require(command != null, "operations-intelligence command is required");
        require(command.getOperation() != null, "operation is required");
        requireRef(command.getIdempotencyKey(), "idempotencyKey", 128);
        if (command.getRunId() != null) requireRef(command.getRunId(), "runId", 128);
        if (command.getCorrelationId() != null) requireRef(command.getCorrelationId(), "correlationId", 128);
        if (command.getCausationId() != null) requireRef(command.getCausationId(), "causationId", 128);
        require(command.getOccurredAt() != null, "occurredAt is required");
    }

    private static void requireCode(String value, String field) {
        require(value != null && SAFE_CODE.matcher(value).matches(), field + " must be an uppercase code");
    }

    private static void requireRef(String value, String field, int maximumLength) {
        require(value != null && value.length() <= maximumLength && SAFE_REF.matcher(value).matches(),
                field + " must be a safe opaque reference");
    }

    private static void requireEvidence(String value, String field) {
        require(value != null && EVIDENCE_REF.matcher(value).matches(),
                field + " must be a restricted or sha256 evidence reference");
    }

    private static void requireSha256(String value, String field) {
        require(value != null && SHA256.matcher(value).matches(), field + " must be lowercase SHA-256");
    }

    private static String valueOrUuid(String value) {
        if (value == null) return UUID.randomUUID().toString();
        requireRef(value, "aggregate id", 128);
        return value;
    }

    private static LocalDateTime at(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static <T> T requireNonNull(T value, String message) {
        require(value != null, message);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static Map<String, Object> payload(Object... values) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            payload.put((String) values[index], values[index + 1]);
        }
        return payload;
    }

    private record Outcome(String eventType, String aggregateType, String aggregateId, Long version,
                           String status, Map<String, Object> payload) {}
}
