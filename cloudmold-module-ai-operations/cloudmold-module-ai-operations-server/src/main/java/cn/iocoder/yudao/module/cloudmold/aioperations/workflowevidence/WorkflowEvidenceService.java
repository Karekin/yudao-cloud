package cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal.ExternalEvidenceSnapshotMapper;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal.WorkflowEvidenceDigestRow;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal.WorkflowEvidenceOperationMapper;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal.WorkflowEvidenceQueryMapper;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal.WorkflowEvidenceTimelineRow;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal.WorkflowFeedbackMapper;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal.WorkflowObservationMapper;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal.WorkflowProblemMapper;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal.dataobject.ExternalEvidenceSnapshotDO;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal.dataobject.WorkflowEvidenceOperationDO;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal.dataobject.WorkflowFeedbackDO;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal.dataobject.WorkflowObservationDO;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal.dataobject.WorkflowProblemDO;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.request.WorkflowEvidenceDigestQueryRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.request.WorkflowEvidenceExternalSnapshotRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.request.WorkflowEvidenceFeedbackRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.request.WorkflowEvidenceIngestRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.request.WorkflowEvidenceProblemRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.request.WorkflowEvidenceTimelineQueryRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.view.WorkflowEvidenceDigestBucketView;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.view.WorkflowEvidenceDigestView;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.view.WorkflowEvidenceTimelineItemView;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.view.WorkflowEvidenceWriteView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class WorkflowEvidenceService {

    static final int OPERATION_SUCCEEDED = 10;
    private static final Set<String> SOURCE_TYPES = Set.of("USER_BEHAVIOR", "SYSTEM_RUN", "SYSTEM_LOG",
            "BUSINESS_KPI", "DQC", "APPROVAL", "WORK_ORDER", "EXTERNAL_WEB");
    private static final Set<String> SEVERITIES = Set.of("INFO", "LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> DQC_STATUSES = Set.of("PASS", "WARN", "FAIL", "UNKNOWN", "NOT_APPLICABLE");
    private static final Set<String> OBSERVATION_STATUSES = Set.of("CAPTURED", "CONFIRMED", "STALE", "REJECTED");
    private static final Set<String> PROBLEM_STATUSES = Set.of("OPEN", "ACKNOWLEDGED", "MITIGATED", "DISMISSED");
    private static final Set<String> FEEDBACK_STATUSES = Set.of("RECEIVED", "ACKNOWLEDGED", "APPLIED", "REJECTED");
    private static final Set<String> EXTERNAL_STATUSES = Set.of("CAPTURED", "VERIFIED", "STALE", "REJECTED");
    private static final Set<String> FEEDBACK_TYPES = Set.of("USER_FEEDBACK", "APPROVAL_NOTE", "RELEASE_SIGNAL",
            "BUSINESS_REVIEW");
    private static final Set<String> EXTERNAL_SOURCE_CLASSES = Set.of("NEWS", "REGULATION", "MARKETPLACE", "SOCIAL",
            "VENDOR_DOC", "THIRD_PARTY_ANALYSIS");
    private static final Set<String> INTERNAL_REF_SCHEMES = Set.of("cloudmold", "urn", "https", "s3", "oss");
    private static final Pattern SAFE_TOKEN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:/#-]{0,254}$");
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,190}$");
    private static final Pattern SHA256 = Pattern.compile("^[A-Fa-f0-9]{64}$");
    private static final Pattern EMAIL = Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
    private static final Pattern IPV4 = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?\\d[\\d -]{8,}\\d)(?!\\d)");
    private static final Pattern SECRET = Pattern.compile("(?i)(api[_-]?key|secret|password|authorization|bearer\\s+[a-z0-9._-]+|token|sessionid|cookie)");
    private static final Pattern AWS_KEY = Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b");
    private static final List<String> INJECTION_MARKERS = List.of("ignore previous", "system prompt",
            "developer message", "tool call", "browse the web", "<system>", "<assistant>", "follow these instructions");

    private final WorkflowEvidenceOperationMapper operationMapper;
    private final WorkflowObservationMapper observationMapper;
    private final WorkflowProblemMapper problemMapper;
    private final WorkflowFeedbackMapper feedbackMapper;
    private final ExternalEvidenceSnapshotMapper externalEvidenceSnapshotMapper;
    private final WorkflowEvidenceQueryMapper queryMapper;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public WorkflowEvidenceWriteView ingest(WorkflowEvidenceIngestRequest request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Actor actor = requireActor(tenantId);
        validateObservationRequest(request);
        OperationResolution operation = beginOperation(tenantId, actor.subject(), "INGEST_OBSERVATION",
                request.getIdempotencyKey(), request);
        if (operation.duplicateResult() != null) {
            operation.duplicateResult().setDuplicate(true);
            return operation.duplicateResult();
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String observationId = valueOrGenerated(request.getObservationId(), "wfo");
        WorkflowObservationDO observation = new WorkflowObservationDO()
                .setObservationId(observationId)
                .setTenantId(tenantId)
                .setLineageId(request.getLineageId().trim())
                .setWorkflowId(request.getWorkflowId().trim())
                .setWorkflowVersion(request.getWorkflowVersion().trim())
                .setProposalId(request.getProposalId().trim())
                .setSourceType(normalizedEnum(request.getSourceType(), SOURCE_TYPES, "sourceType"))
                .setHeadline(sanitizeContextText(request.getHeadline(), "headline", 512))
                .setDetailText(sanitizeContextText(request.getDetailText(), "detailText", 2000))
                .setEvidenceRef(sanitizeOpaqueRef(request.getEvidenceRef(), "evidenceRef"))
                .setSummarySourceRefsJson(toJson(summarySourceRefs(request.getModelSummary(), request.getSummarySourceRefs())))
                .setModelSummary(sanitizeModelSummary(request.getModelSummary(), "modelSummary"))
                .setMetricsJson(toJson(requireObjectOrNull(request.getMetrics(), "metrics")))
                .setSeverity(normalizedEnum(request.getSeverity(), SEVERITIES, "severity"))
                .setDqcStatus(normalizedEnum(request.getDqcStatus(), DQC_STATUSES, "dqcStatus"))
                .setRecordStatus(normalizedEnum(request.getStatus(), OBSERVATION_STATUSES, "status"))
                .setReleaseEligible(Boolean.TRUE.equals(request.getReleaseEligible()))
                .setCorroboratingSourceTypesJson(toJson(corroboratingTypes(request.getReleaseEligible(),
                        request.getSourceType(), request.getCorroboratingSourceTypes())))
                .setFreshUntil(request.getFreshUntil())
                .setWindowStart(request.getWindowStart())
                .setWindowEnd(request.getWindowEnd())
                .setObservedAt(request.getObservedAt())
                .setActorSubject(actor.subject())
                .setCreatedAt(now);
        observationMapper.insert(observation);

        List<String> snapshotIds = new ArrayList<>();
        if (request.getExternalSnapshots() != null) {
            for (WorkflowEvidenceExternalSnapshotRequest snapshotRequest : request.getExternalSnapshots()) {
                validateExternalSnapshot(snapshotRequest);
                String snapshotId = valueOrGenerated(snapshotRequest.getSnapshotId(), "wes");
                ExternalEvidenceSnapshotDO snapshot = new ExternalEvidenceSnapshotDO()
                        .setSnapshotId(snapshotId)
                        .setTenantId(tenantId)
                        .setLineageId(request.getLineageId().trim())
                        .setWorkflowId(request.getWorkflowId().trim())
                        .setWorkflowVersion(request.getWorkflowVersion().trim())
                        .setProposalId(request.getProposalId().trim())
                        .setSourceType("EXTERNAL_WEB")
                        .setSourceClass(normalizedEnum(snapshotRequest.getSourceClass(), EXTERNAL_SOURCE_CLASSES,
                                "externalSnapshots.sourceClass"))
                        .setUrl(sanitizeExternalUrl(snapshotRequest.getUrl()))
                        .setPublishedAt(snapshotRequest.getPublishedAt())
                        .setFetchedAt(snapshotRequest.getFetchedAt())
                        .setSummaryText(sanitizeContextText(snapshotRequest.getSummary(),
                                "externalSnapshots.summary", 2000))
                        .setContentHashSha256(requireSha256(snapshotRequest.getContentHashSha256(),
                                "externalSnapshots.contentHashSha256"))
                        .setConfidence(normalizedConfidence(snapshotRequest.getConfidence()))
                        .setRegion(sanitizeContextText(snapshotRequest.getRegion(), "externalSnapshots.region", 128))
                        .setApplicability(sanitizeContextText(snapshotRequest.getApplicability(),
                                "externalSnapshots.applicability", 512))
                        .setSeverity(normalizedEnum(snapshotRequest.getSeverity(), SEVERITIES,
                                "externalSnapshots.severity"))
                        .setDqcStatus(normalizedEnum(snapshotRequest.getDqcStatus(), DQC_STATUSES,
                                "externalSnapshots.dqcStatus"))
                        .setRecordStatus(normalizedEnum(snapshotRequest.getStatus(), EXTERNAL_STATUSES,
                                "externalSnapshots.status"))
                        .setFreshUntil(snapshotRequest.getFreshUntil())
                        .setWindowStart(snapshotRequest.getWindowStart())
                        .setWindowEnd(snapshotRequest.getWindowEnd())
                        .setReleaseEligible(false)
                        .setActorSubject(actor.subject())
                        .setCreatedAt(now);
                externalEvidenceSnapshotMapper.insert(snapshot);
                snapshotIds.add(snapshotId);
            }
        }

        WorkflowEvidenceWriteView result = WorkflowEvidenceWriteView.builder()
                .aggregateType("WORKFLOW_OBSERVATION")
                .aggregateId(observationId)
                .workflowId(observation.getWorkflowId())
                .lineageId(observation.getLineageId())
                .workflowVersion(observation.getWorkflowVersion())
                .proposalId(observation.getProposalId())
                .externalSnapshotIds(snapshotIds)
                .actorSubject(actor.subject())
                .storedAt(now)
                .duplicate(false)
                .build();
        markSucceeded(operation.operationId(), tenantId, "WORKFLOW_OBSERVATION", observationId, result, now);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkflowEvidenceWriteView recordProblem(WorkflowEvidenceProblemRequest request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Actor actor = requireActor(tenantId);
        validateProblemRequest(request);
        OperationResolution operation = beginOperation(tenantId, actor.subject(), "RECORD_PROBLEM",
                request.getIdempotencyKey(), request);
        if (operation.duplicateResult() != null) {
            operation.duplicateResult().setDuplicate(true);
            return operation.duplicateResult();
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String problemId = valueOrGenerated(request.getProblemId(), "wfp");
        WorkflowProblemDO row = new WorkflowProblemDO()
                .setProblemId(problemId)
                .setTenantId(tenantId)
                .setLineageId(request.getLineageId().trim())
                .setWorkflowId(request.getWorkflowId().trim())
                .setWorkflowVersion(request.getWorkflowVersion().trim())
                .setProposalId(request.getProposalId().trim())
                .setSourceType(normalizedEnum(request.getSourceType(), SOURCE_TYPES, "sourceType"))
                .setHeadline(sanitizeContextText(request.getHeadline(), "headline", 512))
                .setProblemDetail(sanitizeContextText(request.getProblemDetail(), "problemDetail", 2000))
                .setEvidenceRef(sanitizeOpaqueRef(request.getEvidenceRef(), "evidenceRef"))
                .setSummarySourceRefsJson(toJson(summarySourceRefs(request.getModelSummary(), request.getSummarySourceRefs())))
                .setModelSummary(sanitizeModelSummary(request.getModelSummary(), "modelSummary"))
                .setMetricsJson(toJson(requireObjectOrNull(request.getMetrics(), "metrics")))
                .setSeverity(normalizedEnum(request.getSeverity(), SEVERITIES, "severity"))
                .setDqcStatus(normalizedEnum(request.getDqcStatus(), DQC_STATUSES, "dqcStatus"))
                .setRecordStatus(normalizedEnum(request.getStatus(), PROBLEM_STATUSES, "status"))
                .setReleaseEligible(Boolean.TRUE.equals(request.getReleaseEligible()))
                .setCorroboratingSourceTypesJson(toJson(corroboratingTypes(request.getReleaseEligible(),
                        request.getSourceType(), request.getCorroboratingSourceTypes())))
                .setFreshUntil(request.getFreshUntil())
                .setWindowStart(request.getWindowStart())
                .setWindowEnd(request.getWindowEnd())
                .setObservedAt(request.getObservedAt())
                .setActorSubject(actor.subject())
                .setCreatedAt(now);
        problemMapper.insert(row);
        WorkflowEvidenceWriteView result = WorkflowEvidenceWriteView.builder()
                .aggregateType("WORKFLOW_PROBLEM")
                .aggregateId(problemId)
                .workflowId(row.getWorkflowId())
                .lineageId(row.getLineageId())
                .workflowVersion(row.getWorkflowVersion())
                .proposalId(row.getProposalId())
                .externalSnapshotIds(List.of())
                .actorSubject(actor.subject())
                .storedAt(now)
                .duplicate(false)
                .build();
        markSucceeded(operation.operationId(), tenantId, "WORKFLOW_PROBLEM", problemId, result, now);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkflowEvidenceWriteView recordFeedback(WorkflowEvidenceFeedbackRequest request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Actor actor = requireActor(tenantId);
        validateFeedbackRequest(request);
        OperationResolution operation = beginOperation(tenantId, actor.subject(), "RECORD_FEEDBACK",
                request.getIdempotencyKey(), request);
        if (operation.duplicateResult() != null) {
            operation.duplicateResult().setDuplicate(true);
            return operation.duplicateResult();
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String feedbackId = valueOrGenerated(request.getFeedbackId(), "wff");
        WorkflowFeedbackDO row = new WorkflowFeedbackDO()
                .setFeedbackId(feedbackId)
                .setTenantId(tenantId)
                .setLineageId(request.getLineageId().trim())
                .setWorkflowId(request.getWorkflowId().trim())
                .setWorkflowVersion(request.getWorkflowVersion().trim())
                .setProposalId(request.getProposalId().trim())
                .setSourceType(normalizedEnum(request.getSourceType(), SOURCE_TYPES, "sourceType"))
                .setFeedbackType(normalizedEnum(request.getFeedbackType(), FEEDBACK_TYPES, "feedbackType"))
                .setFeedbackLabel(sanitizeContextText(request.getFeedbackLabel(), "feedbackLabel", 512))
                .setFeedbackText(sanitizeContextText(request.getFeedbackText(), "feedbackText", 2000))
                .setEvidenceRef(sanitizeOpaqueRef(request.getEvidenceRef(), "evidenceRef"))
                .setSummarySourceRefsJson(toJson(summarySourceRefs(request.getModelSummary(), request.getSummarySourceRefs())))
                .setModelSummary(sanitizeModelSummary(request.getModelSummary(), "modelSummary"))
                .setMetricsJson(toJson(requireObjectOrNull(request.getMetrics(), "metrics")))
                .setSeverity(normalizedEnum(request.getSeverity(), SEVERITIES, "severity"))
                .setDqcStatus(normalizedEnum(request.getDqcStatus(), DQC_STATUSES, "dqcStatus"))
                .setRecordStatus(normalizedEnum(request.getStatus(), FEEDBACK_STATUSES, "status"))
                .setReleaseEligible(Boolean.TRUE.equals(request.getReleaseEligible()))
                .setCorroboratingSourceTypesJson(toJson(corroboratingTypes(request.getReleaseEligible(),
                        request.getSourceType(), request.getCorroboratingSourceTypes())))
                .setFreshUntil(request.getFreshUntil())
                .setWindowStart(request.getWindowStart())
                .setWindowEnd(request.getWindowEnd())
                .setObservedAt(request.getObservedAt())
                .setActorSubject(actor.subject())
                .setCreatedAt(now);
        feedbackMapper.insert(row);
        WorkflowEvidenceWriteView result = WorkflowEvidenceWriteView.builder()
                .aggregateType("WORKFLOW_USER_FEEDBACK")
                .aggregateId(feedbackId)
                .workflowId(row.getWorkflowId())
                .lineageId(row.getLineageId())
                .workflowVersion(row.getWorkflowVersion())
                .proposalId(row.getProposalId())
                .externalSnapshotIds(List.of())
                .actorSubject(actor.subject())
                .storedAt(now)
                .duplicate(false)
                .build();
        markSucceeded(operation.operationId(), tenantId, "WORKFLOW_USER_FEEDBACK", feedbackId, result, now);
        return result;
    }

    @Transactional(readOnly = true)
    public PageResult<WorkflowEvidenceTimelineItemView> query(WorkflowEvidenceTimelineQueryRequest request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireActor(tenantId);
        validateWindow(request.getWorkflowId(), request.getWindowStart(), request.getWindowEnd());
        int pageSize = Math.min(request.getPageSize(), 100);
        long offset = (long) (request.getPageNo() - 1) * pageSize;
        Long total = queryMapper.countTimeline(tenantId, request.getWorkflowId().trim(),
                request.getWindowStart(), request.getWindowEnd());
        if (total == null || total == 0) {
            return PageResult.empty();
        }
        List<WorkflowEvidenceTimelineRow> rows = queryMapper.selectTimeline(tenantId, request.getWorkflowId().trim(),
                request.getWindowStart(), request.getWindowEnd(), offset, pageSize);
        List<WorkflowEvidenceTimelineItemView> items = rows.stream().map(this::toTimelineView).toList();
        return new PageResult<>(items, total);
    }

    @Transactional(readOnly = true)
    public WorkflowEvidenceDigestView dailyDigest(WorkflowEvidenceDigestQueryRequest request) {
        return digest("DAILY", request);
    }

    @Transactional(readOnly = true)
    public WorkflowEvidenceDigestView weeklyDigest(WorkflowEvidenceDigestQueryRequest request) {
        return digest("WEEKLY", request);
    }

    private WorkflowEvidenceDigestView digest(String granularity, WorkflowEvidenceDigestQueryRequest request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireActor(tenantId);
        validateWindow(request.getWorkflowId(), request.getWindowStart(), request.getWindowEnd());
        List<WorkflowEvidenceDigestRow> rows = "DAILY".equals(granularity)
                ? queryMapper.selectDailyDigest(tenantId, request.getWorkflowId().trim(),
                request.getWindowStart(), request.getWindowEnd())
                : queryMapper.selectWeeklyDigest(tenantId, request.getWorkflowId().trim(),
                request.getWindowStart(), request.getWindowEnd());
        return WorkflowEvidenceDigestView.builder()
                .workflowId(request.getWorkflowId().trim())
                .granularity(granularity)
                .windowStart(request.getWindowStart())
                .windowEnd(request.getWindowEnd())
                .generatedAt(LocalDateTime.now(ZoneOffset.UTC))
                .buckets(rows.stream().map(this::toDigestBucket).toList())
                .build();
    }

    private OperationResolution beginOperation(Long tenantId, String actorSubject, String operationType,
                                               String idempotencyKey, Object request) {
        requireSafeToken(idempotencyKey, "idempotencyKey", 128);
        String hash = DigestUtil.sha256Hex(tenantId + "\u001f" + operationType + "\u001f" + canonicalJson(request));
        String attemptToken = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        operationMapper.insertOrResolve(tenantId, operationType, idempotencyKey, hash, attemptToken, actorSubject, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve workflow evidence operation");
        WorkflowEvidenceOperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "workflow evidence operation disappeared");
        if (attemptToken.equals(operation.getAttemptToken())) {
            return new OperationResolution(operationId, null);
        }
        require(Objects.equals(hash, operation.getRequestHash()), "idempotency key conflicts with different payload");
        require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                "existing workflow evidence operation is not complete");
        return new OperationResolution(operationId,
                JsonUtils.parseObject(operation.getResultJson(), WorkflowEvidenceWriteView.class));
    }

    private void markSucceeded(Long operationId, Long tenantId, String aggregateType, String aggregateId,
                               WorkflowEvidenceWriteView result, LocalDateTime now) {
        require(operationMapper.markSucceeded(operationId, tenantId, aggregateType, aggregateId,
                        JsonUtils.toJsonString(result), now) == 1,
                "workflow evidence operation completion conflict");
    }

    private void validateObservationRequest(WorkflowEvidenceIngestRequest request) {
        Objects.requireNonNull(request, "request is required");
        validateCommonEnvelope(request.getIdempotencyKey(), request.getLineageId(), request.getWorkflowId(),
                request.getWorkflowVersion(), request.getProposalId(), request.getSourceType(), request.getSeverity(),
                request.getDqcStatus(), request.getWindowStart(), request.getWindowEnd(), request.getFreshUntil());
        requireNonBlank(request.getHeadline(), "headline");
        requireNonBlank(request.getDetailText(), "detailText");
        sanitizeContextText(request.getHeadline(), "headline", 512);
        sanitizeContextText(request.getDetailText(), "detailText", 2000);
        require(request.getObservedAt() != null, "observedAt is required");
        require(!request.getObservedAt().isBefore(request.getWindowStart()), "observedAt must not precede windowStart");
        require(!request.getObservedAt().isAfter(request.getWindowEnd()), "observedAt must not exceed windowEnd");
        normalizedEnum(request.getStatus(), OBSERVATION_STATUSES, "status");
        sanitizeOpaqueRef(request.getEvidenceRef(), "evidenceRef");
        sanitizeModelSummary(request.getModelSummary(), "modelSummary");
        summarySourceRefs(request.getModelSummary(), request.getSummarySourceRefs());
        corroboratingTypes(request.getReleaseEligible(), request.getSourceType(), request.getCorroboratingSourceTypes());
        requireObjectOrNull(request.getMetrics(), "metrics");
        if (request.getExternalSnapshots() != null) {
            require(request.getExternalSnapshots().size() <= 20, "externalSnapshots must not exceed 20 items");
            request.getExternalSnapshots().forEach(this::validateExternalSnapshot);
        }
    }

    private void validateProblemRequest(WorkflowEvidenceProblemRequest request) {
        Objects.requireNonNull(request, "request is required");
        validateCommonEnvelope(request.getIdempotencyKey(), request.getLineageId(), request.getWorkflowId(),
                request.getWorkflowVersion(), request.getProposalId(), request.getSourceType(), request.getSeverity(),
                request.getDqcStatus(), request.getWindowStart(), request.getWindowEnd(), request.getFreshUntil());
        requireNonBlank(request.getHeadline(), "headline");
        requireNonBlank(request.getProblemDetail(), "problemDetail");
        sanitizeContextText(request.getHeadline(), "headline", 512);
        sanitizeContextText(request.getProblemDetail(), "problemDetail", 2000);
        require(request.getObservedAt() != null, "observedAt is required");
        normalizedEnum(request.getStatus(), PROBLEM_STATUSES, "status");
        sanitizeOpaqueRef(request.getEvidenceRef(), "evidenceRef");
        sanitizeModelSummary(request.getModelSummary(), "modelSummary");
        summarySourceRefs(request.getModelSummary(), request.getSummarySourceRefs());
        corroboratingTypes(request.getReleaseEligible(), request.getSourceType(), request.getCorroboratingSourceTypes());
        requireObjectOrNull(request.getMetrics(), "metrics");
    }

    private void validateFeedbackRequest(WorkflowEvidenceFeedbackRequest request) {
        Objects.requireNonNull(request, "request is required");
        validateCommonEnvelope(request.getIdempotencyKey(), request.getLineageId(), request.getWorkflowId(),
                request.getWorkflowVersion(), request.getProposalId(), request.getSourceType(), request.getSeverity(),
                request.getDqcStatus(), request.getWindowStart(), request.getWindowEnd(), request.getFreshUntil());
        normalizedEnum(request.getFeedbackType(), FEEDBACK_TYPES, "feedbackType");
        requireNonBlank(request.getFeedbackLabel(), "feedbackLabel");
        requireNonBlank(request.getFeedbackText(), "feedbackText");
        sanitizeContextText(request.getFeedbackLabel(), "feedbackLabel", 512);
        sanitizeContextText(request.getFeedbackText(), "feedbackText", 2000);
        require(request.getObservedAt() != null, "observedAt is required");
        normalizedEnum(request.getStatus(), FEEDBACK_STATUSES, "status");
        sanitizeOpaqueRef(request.getEvidenceRef(), "evidenceRef");
        sanitizeModelSummary(request.getModelSummary(), "modelSummary");
        summarySourceRefs(request.getModelSummary(), request.getSummarySourceRefs());
        corroboratingTypes(request.getReleaseEligible(), request.getSourceType(), request.getCorroboratingSourceTypes());
        requireObjectOrNull(request.getMetrics(), "metrics");
    }

    private void validateCommonEnvelope(String idempotencyKey, String lineageId, String workflowId,
                                        String workflowVersion, String proposalId, String sourceType,
                                        String severity, String dqcStatus, LocalDateTime windowStart,
                                        LocalDateTime windowEnd, LocalDateTime freshUntil) {
        requireSafeToken(idempotencyKey, "idempotencyKey", 128);
        requireSafeIdentifier(lineageId, "lineageId", 128);
        requireSafeIdentifier(workflowId, "workflowId", 191);
        requireSafeIdentifier(workflowVersion, "workflowVersion", 64);
        requireSafeIdentifier(proposalId, "proposalId", 64);
        normalizedEnum(sourceType, SOURCE_TYPES, "sourceType");
        normalizedEnum(severity, SEVERITIES, "severity");
        normalizedEnum(dqcStatus, DQC_STATUSES, "dqcStatus");
        require(windowStart != null, "windowStart is required");
        require(windowEnd != null, "windowEnd is required");
        require(!windowEnd.isBefore(windowStart), "windowEnd must not precede windowStart");
        if (freshUntil != null) {
            require(!freshUntil.isBefore(windowEnd), "freshUntil must not precede windowEnd");
        }
    }

    private void validateExternalSnapshot(WorkflowEvidenceExternalSnapshotRequest request) {
        Objects.requireNonNull(request, "externalSnapshot is required");
        sanitizeExternalUrl(request.getUrl());
        require(request.getFetchedAt() != null, "externalSnapshots.fetchedAt is required");
        if (request.getPublishedAt() != null) {
            require(!request.getFetchedAt().isBefore(request.getPublishedAt()),
                    "externalSnapshots.fetchedAt must not precede publishedAt");
        }
        requireNonBlank(request.getSummary(), "externalSnapshots.summary");
        require(request.getSummary().trim().length() <= 2000, "externalSnapshots.summary is too long");
        sanitizeContextText(request.getSummary(), "externalSnapshots.summary", 2000);
        requireSha256(request.getContentHashSha256(), "externalSnapshots.contentHashSha256");
        normalizedEnum(request.getSourceClass(), EXTERNAL_SOURCE_CLASSES, "externalSnapshots.sourceClass");
        normalizedEnum(request.getSeverity(), SEVERITIES, "externalSnapshots.severity");
        normalizedEnum(request.getDqcStatus(), DQC_STATUSES, "externalSnapshots.dqcStatus");
        normalizedEnum(request.getStatus(), EXTERNAL_STATUSES, "externalSnapshots.status");
        require(request.getWindowStart() != null, "externalSnapshots.windowStart is required");
        require(request.getWindowEnd() != null, "externalSnapshots.windowEnd is required");
        require(!request.getWindowEnd().isBefore(request.getWindowStart()),
                "externalSnapshots.windowEnd must not precede windowStart");
        if (request.getFreshUntil() != null) {
            require(!request.getFreshUntil().isBefore(request.getWindowEnd()),
                    "externalSnapshots.freshUntil must not precede windowEnd");
        }
        normalizedConfidence(request.getConfidence());
        sanitizeContextText(request.getRegion(), "externalSnapshots.region", 128);
        sanitizeContextText(request.getApplicability(), "externalSnapshots.applicability", 512);
    }

    private WorkflowEvidenceTimelineItemView toTimelineView(WorkflowEvidenceTimelineRow row) {
        return WorkflowEvidenceTimelineItemView.builder()
                .recordType(row.getRecordType())
                .recordId(row.getRecordId())
                .lineageId(row.getLineageId())
                .workflowId(row.getWorkflowId())
                .workflowVersion(row.getWorkflowVersion())
                .proposalId(row.getProposalId())
                .sourceType(row.getSourceType())
                .headline(row.getHeadline())
                .detailText(row.getDetailText())
                .modelSummary(row.getModelSummary())
                .evidenceRef(row.getEvidenceRef())
                .externalUrl(row.getExternalUrl())
                .summarySourceRefs(parseStringList(row.getSummarySourceRefsJson()))
                .corroboratingSourceTypes(parseStringList(row.getCorroboratingSourceTypesJson()))
                .metrics(parseJson(row.getMetricsJson()))
                .severity(row.getSeverity())
                .dqcStatus(row.getDqcStatus())
                .status(row.getRecordStatus())
                .releaseEligible(Boolean.TRUE.equals(row.getReleaseEligible()))
                .freshUntil(row.getFreshUntil())
                .windowStart(row.getWindowStart())
                .windowEnd(row.getWindowEnd())
                .recordedAt(row.getRecordedAt())
                .actorSubject(row.getActorSubject())
                .sourceClass(row.getSourceClass())
                .confidence(row.getConfidence())
                .region(row.getRegion())
                .applicability(row.getApplicability())
                .contentHashSha256(row.getContentHashSha256())
                .build();
    }

    private WorkflowEvidenceDigestBucketView toDigestBucket(WorkflowEvidenceDigestRow row) {
        return WorkflowEvidenceDigestBucketView.builder()
                .bucketStart(row.getBucketStart())
                .bucketEnd(row.getBucketEnd())
                .totalCount(row.getTotalCount())
                .observationCount(row.getObservationCount())
                .problemCount(row.getProblemCount())
                .feedbackCount(row.getFeedbackCount())
                .externalSnapshotCount(row.getExternalSnapshotCount())
                .releaseEligibleCount(row.getReleaseEligibleCount())
                .criticalCount(row.getCriticalCount())
                .dqcFailCount(row.getDqcFailCount())
                .staleCount(row.getStaleCount())
                .openProblemCount(row.getOpenProblemCount())
                .build();
    }

    private Actor requireActor(Long tenantId) {
        LoginUser loginUser = Objects.requireNonNull(SecurityFrameworkUtils.getLoginUser(),
                "Workflow evidence hub requires an authenticated login subject");
        Long userId = Objects.requireNonNull(loginUser.getId(),
                "Workflow evidence hub requires an authenticated login user id");
        Long subjectTenant = loginUser.getVisitTenantId() != null ? loginUser.getVisitTenantId() : loginUser.getTenantId();
        if (subjectTenant != null && !Objects.equals(subjectTenant, tenantId)) {
            throw new IllegalStateException("Workflow evidence hub subject is not bound to the current tenant");
        }
        return new Actor(loginUser.getUserType() + ":" + userId);
    }

    private void validateWindow(String workflowId, LocalDateTime windowStart, LocalDateTime windowEnd) {
        requireSafeIdentifier(workflowId, "workflowId", 191);
        require(windowStart != null, "windowStart is required");
        require(windowEnd != null, "windowEnd is required");
        require(!windowEnd.isBefore(windowStart), "windowEnd must not precede windowStart");
    }

    private List<String> summarySourceRefs(String modelSummary, List<String> refs) {
        if (modelSummary == null || modelSummary.isBlank()) {
            return List.of();
        }
        require(refs != null && !refs.isEmpty(), "summarySourceRefs are required when modelSummary is present");
        require(refs.size() <= 10, "summarySourceRefs must not exceed 10 items");
        List<String> sanitized = new ArrayList<>();
        for (String ref : refs) {
            sanitized.add(sanitizeInternalRef(ref, "summarySourceRefs"));
        }
        return sanitized;
    }

    private List<String> corroboratingTypes(Boolean releaseEligible, String sourceType, List<String> corroboratingTypes) {
        if (!Boolean.TRUE.equals(releaseEligible)) {
            return List.of();
        }
        if (!"EXTERNAL_WEB".equalsIgnoreCase(sourceType)) {
            return sanitizeSourceTypes(corroboratingTypes);
        }
        require(corroboratingTypes != null && !corroboratingTypes.isEmpty(),
                "releaseEligible EXTERNAL_WEB evidence requires corroboratingSourceTypes");
        List<String> sanitized = sanitizeSourceTypes(corroboratingTypes);
        require(sanitized.stream().anyMatch(type -> !"EXTERNAL_WEB".equals(type)),
                "internet evidence cannot be releaseEligible without at least one non-web corroborating source");
        return sanitized;
    }

    private List<String> sanitizeSourceTypes(List<String> sourceTypes) {
        if (sourceTypes == null || sourceTypes.isEmpty()) {
            return List.of();
        }
        require(sourceTypes.size() <= 8, "corroboratingSourceTypes must not exceed 8 items");
        List<String> sanitized = new ArrayList<>();
        for (String sourceType : sourceTypes) {
            sanitized.add(normalizedEnum(sourceType, SOURCE_TYPES, "corroboratingSourceTypes"));
        }
        return sanitized;
    }

    private JsonNode requireObjectOrNull(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return null;
        }
        require(node.isObject(), fieldName + " must be a JSON object");
        rejectUnsafeJsonText(node, fieldName);
        return node;
    }

    private void rejectUnsafeJsonText(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            sanitizeContextText(node.asText(), fieldName, 2000);
            return;
        }
        if (node.isContainerNode()) {
            node.elements().forEachRemaining(value -> rejectUnsafeJsonText(value, fieldName));
        }
    }

    private String sanitizeContextText(String value, String fieldName, int maxLength) {
        if (value == null) {
            return null;
        }
        String sanitized = sanitizeModelSummary(value, fieldName);
        if (sanitized != null) {
            require(sanitized.length() <= maxLength, fieldName + " is too long");
        }
        return sanitized;
    }

    private String sanitizeModelSummary(String summary, String fieldName) {
        if (summary == null) {
            return null;
        }
        String trimmed = summary.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        require(trimmed.length() <= 2000, fieldName + " must not exceed 2000 characters");
        String lower = trimmed.toLowerCase(Locale.ROOT);
        require(!EMAIL.matcher(trimmed).find(), fieldName + " must not contain raw email addresses");
        require(!IPV4.matcher(trimmed).find(), fieldName + " must not contain raw IP addresses");
        require(!PHONE.matcher(trimmed).find(), fieldName + " must not contain raw phone numbers");
        require(!SECRET.matcher(trimmed).find(), fieldName + " must not contain secrets or credentials");
        require(!AWS_KEY.matcher(trimmed).find(), fieldName + " must not contain cloud credentials");
        for (String marker : INJECTION_MARKERS) {
            require(!lower.contains(marker), fieldName + " must not contain webpage or prompt instructions");
        }
        return trimmed;
    }

    private String sanitizeOpaqueRef(String ref, String fieldName) {
        if (ref == null) {
            return null;
        }
        return sanitizeInternalRef(ref, fieldName);
    }

    private String sanitizeInternalRef(String ref, String fieldName) {
        require(ref != null && !ref.isBlank(), fieldName + " is invalid");
        String trimmed = ref.trim();
        require(trimmed.length() <= 255 && SAFE_TOKEN.matcher(trimmed).matches(),
                fieldName + " must be a bounded opaque reference");
        sanitizeContextText(trimmed, fieldName, 255);
        URI uri = URI.create(trimmed);
        String scheme = normalizedScheme(uri.getScheme(), fieldName);
        require(INTERNAL_REF_SCHEMES.contains(scheme), fieldName + " uses a non-allowlisted protocol");
        require(uri.getUserInfo() == null && uri.getRawQuery() == null,
                fieldName + " must already be redacted");
        return trimmed;
    }

    private String sanitizeExternalUrl(String url) {
        require(url != null && !url.isBlank(), "externalSnapshots.url is required");
        String trimmed = url.trim();
        require(trimmed.length() <= 512, "externalSnapshots.url must not exceed 512 characters");
        URI uri = URI.create(trimmed);
        String scheme = normalizedScheme(uri.getScheme(), "externalSnapshots.url");
        require(Set.of("http", "https").contains(scheme), "externalSnapshots.url must use http or https");
        String host = uri.getHost();
        require(host != null && !host.isBlank(), "externalSnapshots.url requires a public host");
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        require(!normalizedHost.equals("localhost") && !normalizedHost.endsWith(".localhost")
                        && !normalizedHost.endsWith(".local") && !normalizedHost.endsWith(".internal")
                        && !normalizedHost.matches("[0-9.]+") && !normalizedHost.contains(":"),
                "externalSnapshots.url must not target local, private, or literal-address hosts");
        require(uri.getUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null,
                "externalSnapshots.url must be redacted and must not include credentials, query parameters, or fragments");
        sanitizeContextText(trimmed, "externalSnapshots.url", 512);
        return trimmed;
    }

    private BigDecimal normalizedConfidence(BigDecimal confidence) {
        require(confidence != null, "externalSnapshots.confidence is required");
        require(confidence.compareTo(BigDecimal.ZERO) >= 0 && confidence.compareTo(BigDecimal.ONE) <= 0,
                "externalSnapshots.confidence must be between 0 and 1");
        return confidence.setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private String requireSha256(String value, String fieldName) {
        require(value != null && SHA256.matcher(value).matches(), fieldName + " must be a SHA-256 hex digest");
        return value.toLowerCase(Locale.ROOT);
    }

    private String normalizedEnum(String value, Set<String> allowed, String fieldName) {
        require(value != null && !value.isBlank(), fieldName + " is required");
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        require(allowed.contains(normalized), fieldName + " is invalid");
        return normalized;
    }

    private String valueOrGenerated(String value, String prefix) {
        if (value == null || value.isBlank()) {
            return prefix + "-" + UUID.randomUUID();
        }
        requireSafeIdentifier(value, "id", 64);
        return value.trim();
    }

    private void requireSafeToken(String value, String fieldName, int maxLength) {
        require(value != null && value.length() <= maxLength && SAFE_TOKEN.matcher(value).matches(),
                fieldName + " must be a bounded token");
    }

    private void requireSafeIdentifier(String value, String fieldName, int maxLength) {
        require(value != null && value.length() <= maxLength && SAFE_IDENTIFIER.matcher(value).matches(),
                fieldName + " must be a bounded identifier");
    }

    private void requireNonBlank(String value, String fieldName) {
        require(value != null && !value.isBlank(), fieldName + " is required");
    }

    private String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String canonicalJson(Object value) {
        try {
            return objectMapper.writeValueAsString(sortJson(objectMapper.valueToTree(value))) + "\n";
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("workflow evidence request cannot be canonicalized", ex);
        }
    }

    private JsonNode sortJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            Map<String, JsonNode> fields = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            iterator.forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach((key, value) -> sorted.set(key, sortJson(value)));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = objectMapper.createArrayNode();
            node.forEach(value -> sorted.add(sortJson(value)));
            return sorted;
        }
        return node;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("workflow evidence JSON serialization failed", ex);
        }
    }

    private JsonNode parseJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("workflow evidence JSON parsing failed", ex);
        }
    }

    private List<String> parseStringList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("workflow evidence list parsing failed", ex);
        }
    }

    private String normalizedScheme(String value, String fieldName) {
        require(value != null && !value.isBlank(), fieldName + " requires a URI scheme");
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private record Actor(String subject) {
    }

    private record OperationResolution(Long operationId, WorkflowEvidenceWriteView duplicateResult) {
    }
}
