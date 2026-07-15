package cn.iocoder.yudao.module.cloudmold.risk.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.risk.api.*;
import cn.iocoder.yudao.module.cloudmold.risk.dal.dataobject.RiskRecords.*;
import cn.iocoder.yudao.module.cloudmold.risk.dal.mysql.RiskStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RiskCommandServiceImpl implements RiskCommandApi, RiskQueryApi {
    static final int OPERATION_SUCCEEDED = 10;
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_-]{1,63}");
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{1,127}");
    private static final Pattern HMAC_TOKEN = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SAFE_REF = Pattern.compile("(?:sha256:[0-9a-f]{64}|restricted:[A-Za-z0-9_-]{16,128})");
    private static final Pattern NUMERIC_THRESHOLD = Pattern.compile("-?[0-9]{1,18}(?:\\.[0-9]{1,6})?");
    private static final Pattern EXPLANATION = Pattern.compile("[A-Za-z0-9 _.,:()<>=%+\\-/]{4,256}");
    private static final Set<String> SIGNAL_SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> MEDIUM_TYPES = Set.of("PHONE", "DEVICE", "IP", "ADDRESS", "PAYMENT_ACCOUNT");
    private static final Set<String> OPERATORS = Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE");
    private static final Set<String> CLUSTER_STATUSES = Set.of("OPEN", "UNDER_REVIEW", "CONFIRMED", "DISMISSED", "CLOSED");
    private static final Set<String> RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> MEMBER_TYPES = Set.of("PRINCIPAL", "RELATION");
    private static final Set<String> DECISION_TYPES = Set.of("DISMISS", "MONITOR", "ESCALATE", "CONFIRM_RISK");
    private static final Set<String> FEEDBACK_TYPES = Set.of("CONFIRMED", "CORRECTED", "NOT_ACTIONABLE", "NEEDS_REVIEW");

    private final RiskStoreMapper mapper;
    private final RiskEventService eventService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RiskView execute(RiskCommand command) {
        validateCommon(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String requestHash = DigestUtil.sha256Hex(tenantId + "|" + JsonUtils.toJsonString(command));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Instant occurredAt = command.getOccurredAt() == null ? now.toInstant(ZoneOffset.UTC) : command.getOccurredAt();
        require(!occurredAt.isAfter(Instant.now().plusSeconds(300)), "occurredAt cannot be materially in the future");
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve risk operation");
        Operation operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "risk operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(operation.getRequestHash(), requestHash),
                    "idempotency key conflicts with different risk payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing risk operation is not complete");
            RiskView replay = JsonUtils.parseObject(operation.getResultJson(), RiskView.class);
            replay.setDuplicate(true);
            return replay;
        }

        RiskView result = switch (command.getOperation()) {
            case CREATE_POLICY -> createPolicy(tenantId, operationId, command, now);
            case PUBLISH_POLICY_VERSION -> publishPolicyVersion(tenantId, operationId, command, occurredAt, now);
            case DETECT_SIGNAL -> detectSignal(tenantId, operationId, command, occurredAt, now);
            case OBSERVE_RELATIONSHIP -> observeRelationship(tenantId, operationId, command, occurredAt, now);
            case CREATE_CLUSTER -> createCluster(tenantId, operationId, command, occurredAt, now);
            case ADD_CLUSTER_MEMBER -> addClusterMember(tenantId, operationId, command, occurredAt, now);
            case CHANGE_CLUSTER_STATUS -> changeClusterStatus(tenantId, operationId, command, occurredAt, now);
            case OPEN_REVIEW -> openReview(tenantId, operationId, command, occurredAt, now);
            case START_REVIEW, CLOSE_REVIEW -> transitionReview(tenantId, operationId, command, occurredAt, now);
            case DECIDE_REVIEW -> decideReview(tenantId, operationId, command, occurredAt, now);
            case RECORD_FEEDBACK -> recordFeedback(tenantId, operationId, command, occurredAt, now);
        };
        String aggregateId = firstNonNull(result.getFeedbackId(), result.getDecisionId(), result.getCaseId(),
                result.getClusterId(), result.getRelationId(), result.getSignalId(), result.getPolicyId());
        require(mapper.markOperationSucceeded(operationId, tenantId, aggregateId, JsonUtils.toJsonString(result), now) == 1,
                "risk operation completion conflict");
        return result;
    }

    @Override
    public RiskView getPolicy(String policyId) {
        requireId(policyId, "policyId");
        Policy policy = mapper.selectPolicy(TenantContextHolder.getRequiredTenantId(), policyId);
        require(policy != null, "risk policy does not exist");
        return policyView(null, policy, false);
    }

    @Override
    public RiskView getCluster(String clusterId) {
        requireId(clusterId, "clusterId");
        Cluster cluster = mapper.selectCluster(TenantContextHolder.getRequiredTenantId(), clusterId);
        require(cluster != null, "risk cluster does not exist");
        return clusterView(null, cluster, false);
    }

    @Override
    public RiskView getReviewCase(String caseId) {
        requireId(caseId, "caseId");
        ReviewCase review = mapper.selectReviewCase(TenantContextHolder.getRequiredTenantId(), caseId);
        require(review != null, "risk review case does not exist");
        return reviewView(null, review, false);
    }

    private RiskView createPolicy(Long tenantId, Long operationId, RiskCommand command, LocalDateTime now) {
        requireCode(command.getPolicyCode(), "policyCode");
        require(command.getPolicyName() != null && command.getPolicyName().length() >= 3
                && command.getPolicyName().length() <= 128, "policyName must be between 3 and 128 characters");
        Policy policy = new Policy().setPolicyId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setPolicyCode(command.getPolicyCode()).setPolicyName(command.getPolicyName()).setStatus("DRAFT")
                .setCurrentVersion(0L).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertPolicy(policy) == 1, "failed to create risk policy");
        return policyView(operationId, policy, false);
    }

    private RiskView publishPolicyVersion(Long tenantId, Long operationId, RiskCommand command, Instant occurredAt,
                                          LocalDateTime now) {
        requireId(command.getPolicyId(), "policyId");
        requireExpectedVersion(command);
        requireId(command.getApprovedByPrincipalId(), "approvedByPrincipalId");
        require(command.getEffectiveFrom() != null, "effectiveFrom is required");
        require(command.getRules() != null && !command.getRules().isEmpty() && command.getRules().size() <= 100,
                "rules must contain between 1 and 100 explainable rules");
        Set<String> ruleCodes = new HashSet<>();
        for (RiskCommand.RuleDefinition rule : command.getRules()) {
            validateRule(rule);
            require(ruleCodes.add(rule.getRuleCode()), "ruleCode must be unique within a policy version");
        }
        Policy policy = mapper.selectPolicyForUpdate(tenantId, command.getPolicyId());
        require(policy != null, "risk policy does not exist");
        require(Objects.equals(policy.getCurrentVersion(), command.getExpectedVersion()), "policy version conflict");
        String previousStatus = policy.getStatus();
        Long nextVersion = policy.getCurrentVersion() + 1;
        String rulesSha256 = DigestUtil.sha256Hex(JsonUtils.toJsonString(command.getRules()));
        PolicyVersion version = new PolicyVersion().setPolicyVersionId(UUID.randomUUID().toString())
                .setTenantId(tenantId).setPolicyId(policy.getPolicyId()).setPolicyVersion(nextVersion)
                .setRulesSha256(rulesSha256).setApprovedByPrincipalId(command.getApprovedByPrincipalId())
                .setEffectiveFrom(LocalDateTime.ofInstant(command.getEffectiveFrom(), ZoneOffset.UTC)).setPublishedAt(now);
        require(mapper.insertPolicyVersion(version) == 1, "failed to persist immutable policy version");
        int sequence = 0;
        for (RiskCommand.RuleDefinition rule : command.getRules()) {
            PolicyRule record = new PolicyRule().setRuleId(UUID.randomUUID().toString()).setTenantId(tenantId)
                    .setPolicyVersionId(version.getPolicyVersionId()).setPolicyId(policy.getPolicyId())
                    .setPolicyVersion(nextVersion).setRuleSequence(++sequence).setRuleCode(rule.getRuleCode())
                    .setSignalType(rule.getSignalType()).setOperatorCode(rule.getOperatorCode())
                    .setThresholdValue(rule.getThresholdValue()).setOutcomeCode(rule.getOutcomeCode())
                    .setExplanationTemplate(rule.getExplanationTemplate()).setCreatedAt(now);
            require(mapper.insertPolicyRule(record) == 1, "failed to persist immutable policy rule");
        }
        require(mapper.publishPolicy(tenantId, policy.getPolicyId(), policy.getCurrentVersion(), now) == 1,
                "policy publish conflict");
        policy.setStatus("PUBLISHED").setCurrentVersion(nextVersion).setUpdatedAt(now);
        eventService.appendPolicy(operationId, policy, version, previousStatus, command, occurredAt, now);
        return policyView(operationId, policy, false);
    }

    private RiskView detectSignal(Long tenantId, Long operationId, RiskCommand command, Instant occurredAt,
                                  LocalDateTime now) {
        requireId(command.getSubjectPrincipalId(), "subjectPrincipalId");
        requireCode(command.getSignalType(), "signalType");
        require(SIGNAL_SEVERITIES.contains(command.getSeverity()), "unsupported severity");
        requireSafeRef(command.getEvidenceRef(), "evidenceRef");
        requireId(command.getPolicyId(), "policyId");
        Policy policy = mapper.selectPolicyForUpdate(tenantId, command.getPolicyId());
        require(policy != null && "PUBLISHED".equals(policy.getStatus()) && policy.getCurrentVersion() > 0,
                "signal requires a published tenant-scoped policy");
        String signalId = command.getSignalId() == null ? UUID.randomUUID().toString() : command.getSignalId();
        requireId(signalId, "signalId");
        Signal signal = new Signal().setSignalId(signalId).setTenantId(tenantId)
                .setSubjectPrincipalId(command.getSubjectPrincipalId()).setPolicyId(policy.getPolicyId())
                .setPolicyVersion(policy.getCurrentVersion()).setSignalType(command.getSignalType())
                .setSeverity(command.getSeverity()).setEvidenceRef(command.getEvidenceRef())
                .setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)).setCreatedAt(now);
        require(mapper.insertSignal(signal) == 1, "failed to persist risk signal");
        eventService.appendSignal(signal, command, occurredAt);
        return new RiskView().setOperationId(operationId).setDuplicate(false).setSignalId(signalId)
                .setPolicyId(policy.getPolicyId()).setPolicyVersion(policy.getCurrentVersion());
    }

    private RiskView observeRelationship(Long tenantId, Long operationId, RiskCommand command, Instant occurredAt,
                                         LocalDateTime now) {
        requireId(command.getSubjectPrincipalId(), "subjectPrincipalId");
        requireId(command.getRelatedPrincipalId(), "relatedPrincipalId");
        require(!command.getSubjectPrincipalId().equals(command.getRelatedPrincipalId()),
                "risk relationship self-loop is forbidden");
        require(MEDIUM_TYPES.contains(command.getMediumType()), "unsupported mediumType");
        require(command.getMediumToken() != null && HMAC_TOKEN.matcher(command.getMediumToken()).matches(),
                "mediumToken must be a lowercase keyed-HMAC SHA-256 token; raw PII is forbidden");
        require(command.getKeyVersion() != null && command.getKeyVersion() > 0, "keyVersion must be positive");
        require(command.getFirstSeenAt() != null && command.getLastSeenAt() != null
                && !command.getLastSeenAt().isBefore(command.getFirstSeenAt()),
                "relationship observation time window is invalid");
        require(command.getConfidenceBasisPoints() != null && command.getConfidenceBasisPoints() >= 0
                && command.getConfidenceBasisPoints() <= 10_000,
                "confidenceBasisPoints must be between 0 and 10000");
        String left = command.getSubjectPrincipalId().compareTo(command.getRelatedPrincipalId()) <= 0
                ? command.getSubjectPrincipalId() : command.getRelatedPrincipalId();
        String right = left.equals(command.getSubjectPrincipalId())
                ? command.getRelatedPrincipalId() : command.getSubjectPrincipalId();
        require(mapper.selectRelationByKey(tenantId, left, right, command.getMediumType(), command.getMediumToken(),
                command.getKeyVersion()) == null, "risk relationship already exists");
        Relation relation = new Relation().setRelationId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setSubjectPrincipalId(left).setRelatedPrincipalId(right).setMediumType(command.getMediumType())
                .setMediumToken(command.getMediumToken()).setKeyVersion(command.getKeyVersion())
                .setFirstSeenAt(LocalDateTime.ofInstant(command.getFirstSeenAt(), ZoneOffset.UTC))
                .setLastSeenAt(LocalDateTime.ofInstant(command.getLastSeenAt(), ZoneOffset.UTC))
                .setConfidenceBasisPoints(command.getConfidenceBasisPoints()).setCreatedAt(now);
        require(mapper.insertRelation(relation) == 1, "failed to persist risk relationship");
        eventService.appendRelationship(relation, command, occurredAt);
        return new RiskView().setOperationId(operationId).setDuplicate(false).setRelationId(relation.getRelationId());
    }

    private RiskView createCluster(Long tenantId, Long operationId, RiskCommand command, Instant occurredAt,
                                   LocalDateTime now) {
        requireCode(command.getClusterCode(), "clusterCode");
        require(RISK_LEVELS.contains(command.getRiskLevel()), "unsupported riskLevel");
        Cluster cluster = new Cluster().setClusterId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setClusterCode(command.getClusterCode()).setStatus("OPEN").setRiskLevel(command.getRiskLevel())
                .setMemberCount(0).setEdgeCount(0).setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertCluster(cluster) == 1, "failed to create risk cluster");
        eventService.appendCluster(operationId, cluster, null, command, occurredAt, now);
        return clusterView(operationId, cluster, false);
    }

    private RiskView addClusterMember(Long tenantId, Long operationId, RiskCommand command, Instant occurredAt,
                                      LocalDateTime now) {
        Cluster cluster = requireMutableCluster(tenantId, command);
        require(Set.of("OPEN", "UNDER_REVIEW").contains(cluster.getStatus()),
                "terminal risk cluster cannot accept members");
        require(MEMBER_TYPES.contains(command.getMemberType()), "memberType must be PRINCIPAL or RELATION");
        requireId(command.getMemberRef(), "memberRef");
        int edgeDelta = 0;
        if ("RELATION".equals(command.getMemberType())) {
            require(mapper.selectRelation(tenantId, command.getMemberRef()) != null,
                    "cluster relation member must exist in the same tenant");
            edgeDelta = 1;
        }
        ClusterMember member = new ClusterMember().setClusterMemberId(UUID.randomUUID().toString())
                .setTenantId(tenantId).setClusterId(cluster.getClusterId()).setMemberType(command.getMemberType())
                .setMemberRef(command.getMemberRef()).setCreatedAt(now);
        require(mapper.insertClusterMember(member) == 1, "duplicate or invalid risk cluster member");
        require(mapper.addClusterMember(tenantId, cluster.getClusterId(), cluster.getVersion(), edgeDelta, now) == 1,
                "cluster version conflict");
        String before = cluster.getStatus();
        cluster.setMemberCount(cluster.getMemberCount() + 1).setEdgeCount(cluster.getEdgeCount() + edgeDelta)
                .setVersion(cluster.getVersion() + 1).setUpdatedAt(now);
        eventService.appendCluster(operationId, cluster, before, command, occurredAt, now);
        return clusterView(operationId, cluster, false);
    }

    private RiskView changeClusterStatus(Long tenantId, Long operationId, RiskCommand command, Instant occurredAt,
                                         LocalDateTime now) {
        Cluster cluster = requireMutableCluster(tenantId, command);
        require(CLUSTER_STATUSES.contains(command.getClusterStatus()), "unsupported clusterStatus");
        require(RISK_LEVELS.contains(command.getRiskLevel()), "unsupported riskLevel");
        requireClusterTransition(cluster.getStatus(), command.getClusterStatus());
        String before = cluster.getStatus();
        require(mapper.transitionCluster(tenantId, cluster.getClusterId(), cluster.getVersion(), before,
                command.getClusterStatus(), command.getRiskLevel(), now) == 1, "cluster state transition conflict");
        cluster.setStatus(command.getClusterStatus()).setRiskLevel(command.getRiskLevel())
                .setVersion(cluster.getVersion() + 1).setUpdatedAt(now);
        eventService.appendCluster(operationId, cluster, before, command, occurredAt, now);
        return clusterView(operationId, cluster, false);
    }

    private RiskView openReview(Long tenantId, Long operationId, RiskCommand command, Instant occurredAt,
                                LocalDateTime now) {
        requireId(command.getClusterId(), "clusterId");
        requireId(command.getReviewerPrincipalId(), "reviewerPrincipalId");
        Cluster cluster = mapper.selectClusterForUpdate(tenantId, command.getClusterId());
        require(cluster != null && !Set.of("DISMISSED", "CLOSED").contains(cluster.getStatus()),
                "review requires an active tenant-scoped cluster");
        ReviewCase review = new ReviewCase().setCaseId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setClusterId(cluster.getClusterId()).setStatus("OPEN")
                .setReviewerPrincipalId(command.getReviewerPrincipalId()).setVersion(1L)
                .setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertReviewCase(review) == 1, "failed to open risk review case");
        eventService.appendReview(operationId, review, null, command, occurredAt, now);
        return reviewView(operationId, review, false);
    }

    private RiskView transitionReview(Long tenantId, Long operationId, RiskCommand command, Instant occurredAt,
                                      LocalDateTime now) {
        ReviewCase review = requireMutableReview(tenantId, command);
        String before = review.getStatus();
        String after = command.getOperation() == RiskOperation.START_REVIEW
                ? requireTransition(before, "OPEN", "IN_REVIEW")
                : requireTransition(before, "DECIDED", "CLOSED");
        require(mapper.transitionReviewCase(tenantId, review.getCaseId(), review.getVersion(), before, after, now) == 1,
                "review state transition conflict");
        review.setStatus(after).setVersion(review.getVersion() + 1).setUpdatedAt(now);
        eventService.appendReview(operationId, review, before, command, occurredAt, now);
        return reviewView(operationId, review, false);
    }

    private RiskView decideReview(Long tenantId, Long operationId, RiskCommand command, Instant occurredAt,
                                  LocalDateTime now) {
        ReviewCase review = requireMutableReview(tenantId, command);
        require("IN_REVIEW".equals(review.getStatus()), "only in-review case can receive a decision");
        require(DECISION_TYPES.contains(command.getDecisionType()),
                "decisionType must be a non-punitive review outcome");
        requireCode(command.getReasonCode(), "reasonCode");
        requireId(command.getDecidedByPrincipalId(), "decidedByPrincipalId");
        require(command.getDecidedByPrincipalId().equals(review.getReviewerPrincipalId()),
                "decision actor must be the assigned human reviewer");
        Decision decision = new Decision().setDecisionId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setCaseId(review.getCaseId()).setClusterId(review.getClusterId())
                .setDecisionType(command.getDecisionType()).setReasonCode(command.getReasonCode())
                .setDecidedByPrincipalId(command.getDecidedByPrincipalId())
                .setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)).setCreatedAt(now);
        require(mapper.insertDecision(decision) == 1, "failed to persist immutable risk decision");
        require(mapper.transitionReviewCase(tenantId, review.getCaseId(), review.getVersion(), "IN_REVIEW", "DECIDED", now) == 1,
                "review decision conflict");
        review.setStatus("DECIDED").setVersion(review.getVersion() + 1).setUpdatedAt(now);
        eventService.appendReview(operationId, review, "IN_REVIEW", command, occurredAt, now);
        eventService.appendDecision(decision, command, occurredAt);
        return reviewView(operationId, review, false).setDecisionId(decision.getDecisionId());
    }

    private RiskView recordFeedback(Long tenantId, Long operationId, RiskCommand command, Instant occurredAt,
                                    LocalDateTime now) {
        requireId(command.getDecisionId(), "decisionId");
        require(FEEDBACK_TYPES.contains(command.getFeedbackType()), "unsupported feedbackType");
        requireCode(command.getReasonCode(), "reasonCode");
        requireId(command.getRecordedByPrincipalId(), "recordedByPrincipalId");
        Decision decision = mapper.selectDecision(tenantId, command.getDecisionId());
        require(decision != null, "feedback decision does not exist in the same tenant");
        if (command.getCaseId() != null) {
            require(command.getCaseId().equals(decision.getCaseId()), "feedback case does not match decision case");
        }
        Feedback feedback = new Feedback().setFeedbackId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setDecisionId(decision.getDecisionId()).setCaseId(decision.getCaseId())
                .setFeedbackType(command.getFeedbackType()).setReasonCode(command.getReasonCode())
                .setRecordedByPrincipalId(command.getRecordedByPrincipalId())
                .setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)).setCreatedAt(now);
        require(mapper.insertFeedback(feedback) == 1, "failed to persist immutable risk feedback");
        eventService.appendFeedback(feedback, command, occurredAt);
        return new RiskView().setOperationId(operationId).setDuplicate(false).setFeedbackId(feedback.getFeedbackId())
                .setDecisionId(decision.getDecisionId()).setCaseId(decision.getCaseId());
    }

    private Cluster requireMutableCluster(Long tenantId, RiskCommand command) {
        requireId(command.getClusterId(), "clusterId");
        requireExpectedVersion(command);
        Cluster cluster = mapper.selectClusterForUpdate(tenantId, command.getClusterId());
        require(cluster != null, "risk cluster does not exist");
        require(Objects.equals(cluster.getVersion(), command.getExpectedVersion()), "cluster version conflict");
        return cluster;
    }

    private ReviewCase requireMutableReview(Long tenantId, RiskCommand command) {
        requireId(command.getCaseId(), "caseId");
        requireExpectedVersion(command);
        ReviewCase review = mapper.selectReviewCaseForUpdate(tenantId, command.getCaseId());
        require(review != null, "risk review case does not exist");
        require(Objects.equals(review.getVersion(), command.getExpectedVersion()), "review case version conflict");
        return review;
    }

    private static void validateCommon(RiskCommand command) {
        require(command != null, "risk command is required");
        require(command.getOperation() != null, "risk operation is required");
        require(command.getIdempotencyKey() != null && ID.matcher(command.getIdempotencyKey()).matches(),
                "idempotencyKey must be a stable opaque identifier");
        if (command.getRunId() != null) requireId(command.getRunId(), "runId");
    }

    private static void validateRule(RiskCommand.RuleDefinition rule) {
        require(rule != null, "policy rule is required");
        requireCode(rule.getRuleCode(), "ruleCode");
        requireCode(rule.getSignalType(), "signalType");
        require(OPERATORS.contains(rule.getOperatorCode()), "unsupported operatorCode");
        require(rule.getThresholdValue() != null && NUMERIC_THRESHOLD.matcher(rule.getThresholdValue()).matches(),
                "thresholdValue must be a bounded numeric literal");
        requireCode(rule.getOutcomeCode(), "outcomeCode");
        require(rule.getExplanationTemplate() != null
                && EXPLANATION.matcher(rule.getExplanationTemplate()).matches(),
                "explanationTemplate must be bounded explanatory text");
    }

    private static void requireClusterTransition(String before, String after) {
        boolean valid = switch (before) {
            case "OPEN" -> Set.of("UNDER_REVIEW", "DISMISSED", "CLOSED").contains(after);
            case "UNDER_REVIEW" -> Set.of("CONFIRMED", "DISMISSED", "CLOSED").contains(after);
            case "CONFIRMED", "DISMISSED" -> "CLOSED".equals(after);
            default -> false;
        };
        require(valid, "risk cluster cannot transition from " + before + " to " + after);
    }

    private static String requireTransition(String actual, String expected, String after) {
        require(expected.equals(actual), "risk review cannot transition from " + actual + " to " + after);
        return after;
    }

    private static RiskView policyView(Long operationId, Policy policy, boolean duplicate) {
        return new RiskView().setOperationId(operationId).setDuplicate(duplicate).setPolicyId(policy.getPolicyId())
                .setPolicyVersion(policy.getCurrentVersion()).setPolicyStatus(policy.getStatus());
    }

    private static RiskView clusterView(Long operationId, Cluster cluster, boolean duplicate) {
        return new RiskView().setOperationId(operationId).setDuplicate(duplicate).setClusterId(cluster.getClusterId())
                .setClusterVersion(cluster.getVersion()).setClusterStatus(cluster.getStatus())
                .setMemberCount(cluster.getMemberCount()).setEdgeCount(cluster.getEdgeCount());
    }

    private static RiskView reviewView(Long operationId, ReviewCase review, boolean duplicate) {
        return new RiskView().setOperationId(operationId).setDuplicate(duplicate).setCaseId(review.getCaseId())
                .setClusterId(review.getClusterId()).setCaseVersion(review.getVersion())
                .setReviewStatus(review.getStatus());
    }

    private static void requireExpectedVersion(RiskCommand command) {
        require(command.getExpectedVersion() != null && command.getExpectedVersion() >= 0,
                "expectedVersion is required");
    }

    private static void requireCode(String value, String field) {
        require(value != null && CODE.matcher(value).matches(), field + " must be an uppercase domain code");
    }

    private static void requireId(String value, String field) {
        require(value != null && ID.matcher(value).matches(), field + " must be a stable opaque identifier");
    }

    private static void requireSafeRef(String value, String field) {
        require(value != null && SAFE_REF.matcher(value).matches(),
                field + " must be a digest or restricted-store token; raw PII is forbidden");
    }

    private static String firstNonNull(String... values) {
        for (String value : values) if (value != null) return value;
        return null;
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
