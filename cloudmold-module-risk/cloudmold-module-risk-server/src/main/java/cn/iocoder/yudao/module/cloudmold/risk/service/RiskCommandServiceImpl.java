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
    private static final Pattern EVENT_CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{1,127}");
    private static final Pattern SOURCE_TABLE = Pattern.compile("[a-z][a-z0-9_]{1,127}");
    private static final Pattern LEVEL_CODE = Pattern.compile("[A-Z][A-Z0-9_-]{0,31}");
    private static final Set<String> SIGNAL_SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> MEDIUM_TYPES = Set.of("PHONE", "DEVICE", "IP", "ADDRESS", "PAYMENT_ACCOUNT");
    private static final Set<String> OPERATORS = Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE");
    private static final Set<String> CLUSTER_STATUSES = Set.of("OPEN", "UNDER_REVIEW", "CONFIRMED", "DISMISSED", "CLOSED");
    private static final Set<String> RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> MEMBER_TYPES = Set.of("PRINCIPAL", "RELATION");
    private static final Set<String> DECISION_TYPES = Set.of("DISMISS", "MONITOR", "ESCALATE", "CONFIRM_RISK");
    private static final Set<String> FEEDBACK_TYPES = Set.of("CONFIRMED", "CORRECTED", "NOT_ACTIONABLE", "NEEDS_REVIEW");
    private static final Set<String> ORDER_RISK_TYPES = Set.of("FRAUD", "ABUSE", "PAYMENT_RISK", "POLICY_VIOLATION");
    private static final Set<String> DISPUTE_TYPES = Set.of("CHARGEBACK", "PAYMENT_DISPUTE");
    private static final Set<String> DISPUTE_STATUSES = Set.of("OPEN", "WON", "LOST", "REVERSED", "CANCELLED");
    private static final Set<String> LOSS_ENTRY_TYPES = Set.of(
            "CONFIRMED_RISK_LOSS", "CHARGEBACK_LOSS", "SERVICE_COMPENSATION_LOSS", "REVERSAL");
    private static final String CURRENCY_CNY = "CNY";

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
            case CREATE_INTELLIGENCE_EVENT_TAXONOMY ->
                    createIntelligenceEventTaxonomy(tenantId, operationId, command, occurredAt, now);
            case PUBLISH_INTELLIGENCE_EVENT_TAXONOMY_VERSION ->
                    publishIntelligenceEventTaxonomyVersion(tenantId, operationId, command, occurredAt, now);
            case RETIRE_INTELLIGENCE_EVENT_TAXONOMY ->
                    retireIntelligenceEventTaxonomy(tenantId, operationId, command, occurredAt, now);
            case DETECT_SIGNAL -> detectSignal(tenantId, operationId, command, occurredAt, now);
            case OBSERVE_RELATIONSHIP -> observeRelationship(tenantId, operationId, command, occurredAt, now);
            case CREATE_CLUSTER -> createCluster(tenantId, operationId, command, occurredAt, now);
            case ADD_CLUSTER_MEMBER -> addClusterMember(tenantId, operationId, command, occurredAt, now);
            case CHANGE_CLUSTER_STATUS -> changeClusterStatus(tenantId, operationId, command, occurredAt, now);
            case OPEN_REVIEW -> openReview(tenantId, operationId, command, occurredAt, now);
            case START_REVIEW, CLOSE_REVIEW -> transitionReview(tenantId, operationId, command, occurredAt, now);
            case DECIDE_REVIEW -> decideReview(tenantId, operationId, command, occurredAt, now);
            case RECORD_FEEDBACK -> recordFeedback(tenantId, operationId, command, occurredAt, now);
            case LINK_ORDER_REVIEW_CASE -> linkOrderReviewCase(tenantId, operationId, command, occurredAt, now);
            case OPEN_PAYMENT_DISPUTE -> openPaymentDispute(tenantId, operationId, command, occurredAt, now);
            case RESOLVE_PAYMENT_DISPUTE -> resolvePaymentDispute(tenantId, operationId, command, occurredAt, now);
            case POST_LOSS_ENTRY -> postLossEntry(tenantId, operationId, command, occurredAt, now);
        };
        String aggregateId = firstNonNull(result.getFeedbackId(), result.getDecisionId(), result.getLossEntryId(),
                result.getDisputeId(), result.getOrderRiskCaseId(), result.getCaseId(),
                result.getClusterId(), result.getRelationId(), result.getSignalId(), result.getPolicyId());
        if (result.getTaxonomyId() != null) aggregateId = result.getTaxonomyId();
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
    public RiskView getIntelligenceEventTaxonomy(String taxonomyId) {
        requireId(taxonomyId, "taxonomyId");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        IntelligenceTaxonomy taxonomy = mapper.selectIntelligenceTaxonomy(tenantId, taxonomyId);
        require(taxonomy != null, "intelligence event taxonomy does not exist");
        List<String> levels = taxonomy.getCurrentDefinitionVersion() > 0
                ? mapper.selectIntelligenceTaxonomyLevelCodes(tenantId, taxonomyId,
                taxonomy.getCurrentDefinitionVersion()) : List.of();
        return taxonomyView(null, taxonomy, null, levels, false);
    }

    @Override
    @Transactional(readOnly = true)
    public IntelligenceEventTaxonomyReference validateIntelligenceEventLevel(
            String taxonomyId, Long definitionVersion, String eventCode, String intelligenceLevel,
            Instant observedAt) {
        requireId(taxonomyId, "taxonomyId");
        require(definitionVersion != null && definitionVersion > 0, "taxonomy definitionVersion must be positive");
        requireEventCode(eventCode);
        requireLevelCode(intelligenceLevel);
        require(observedAt != null, "observedAt is required");
        IntelligenceTaxonomyReferenceRow row = mapper.selectEffectiveIntelligenceTaxonomyReference(
                TenantContextHolder.getRequiredTenantId(), taxonomyId, definitionVersion, eventCode,
                intelligenceLevel, LocalDateTime.ofInstant(observedAt, ZoneOffset.UTC));
        require(row != null, "observation must reference the effective non-retired taxonomy version and level");
        return IntelligenceEventTaxonomyReference.builder().taxonomyId(row.getTaxonomyId())
                .taxonomyVersionId(row.getTaxonomyVersionId()).definitionVersion(row.getDefinitionVersion())
                .eventCode(row.getEventCode()).intelligenceLevel(row.getLevelCode())
                .levelsSha256(row.getLevelsSha256())
                .effectiveFrom(row.getEffectiveFrom().toInstant(ZoneOffset.UTC)).build();
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

    private RiskView createIntelligenceEventTaxonomy(Long tenantId, Long operationId, RiskCommand command,
                                                      Instant occurredAt, LocalDateTime now) {
        requireEventCode(command.getEventCode());
        IntelligenceTaxonomy taxonomy = new IntelligenceTaxonomy().setTaxonomyId(UUID.randomUUID().toString())
                .setTenantId(tenantId).setEventCode(command.getEventCode()).setStatus("DRAFT")
                .setCurrentDefinitionVersion(0L).setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertIntelligenceTaxonomy(taxonomy) == 1,
                "failed to create intelligence event taxonomy");
        eventService.appendTaxonomyHistory(operationId, taxonomy, null, command, occurredAt, now);
        return taxonomyView(operationId, taxonomy, null, List.of(), false);
    }

    private RiskView publishIntelligenceEventTaxonomyVersion(Long tenantId, Long operationId, RiskCommand command,
                                                              Instant occurredAt, LocalDateTime now) {
        requireId(command.getTaxonomyId(), "taxonomyId");
        requireExpectedVersion(command);
        requireId(command.getApprovedByPrincipalId(), "approvedByPrincipalId");
        validateTaxonomySourceEvidence(command, occurredAt, now);
        require(command.getEffectiveFrom() != null && !command.getEffectiveFrom().isBefore(occurredAt),
                "effectiveFrom must not backdate a taxonomy correction");
        require(!command.getEffectiveFrom().isAfter(occurredAt.plus(Duration.ofDays(366))),
                "effectiveFrom must be within the bounded scheduling horizon");
        require(command.getIntelligenceLevels() != null && !command.getIntelligenceLevels().isEmpty()
                        && command.getIntelligenceLevels().size() <= 32,
                "intelligenceLevels must contain between 1 and 32 ordered codes");
        LinkedHashSet<String> uniqueLevels = new LinkedHashSet<>();
        for (String level : command.getIntelligenceLevels()) {
            requireLevelCode(level);
            require(uniqueLevels.add(level), "intelligence level codes must be unique within a version");
        }
        List<String> levels = List.copyOf(uniqueLevels);
        IntelligenceTaxonomy taxonomy = mapper.selectIntelligenceTaxonomyForUpdate(tenantId, command.getTaxonomyId());
        require(taxonomy != null, "intelligence event taxonomy does not exist");
        require(Objects.equals(taxonomy.getVersion(), command.getExpectedVersion()), "taxonomy version conflict");
        require(!"RETIRED".equals(taxonomy.getStatus()), "retired taxonomy cannot publish another version");
        IntelligenceTaxonomyVersion currentVersion = taxonomy.getCurrentDefinitionVersion() > 0
                ? mapper.selectIntelligenceTaxonomyVersion(tenantId, taxonomy.getTaxonomyId(),
                taxonomy.getCurrentDefinitionVersion()) : null;
        if (currentVersion != null) {
            require(command.getEffectiveFrom().isAfter(currentVersion.getEffectiveFrom().toInstant(ZoneOffset.UTC)),
                    "taxonomy version effectiveFrom must increase strictly");
        }
        long nextDefinitionVersion = taxonomy.getCurrentDefinitionVersion() + 1;
        String levelsSha256 = DigestUtil.sha256Hex(JsonUtils.toJsonString(levels));
        IntelligenceTaxonomyVersion version = new IntelligenceTaxonomyVersion()
                .setTaxonomyVersionId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setTaxonomyId(taxonomy.getTaxonomyId()).setDefinitionVersion(nextDefinitionVersion)
                .setLevelCount(levels.size()).setLevelsSha256(levelsSha256)
                .setApprovedByPrincipalId(command.getApprovedByPrincipalId())
                .setSourceSystem(command.getSourceSystem()).setSourceTable(command.getSourceTable())
                .setSourceRecordKey(command.getSourceRecordKey()).setSourceVersion(command.getSourceVersion())
                .setSourceObservedAt(LocalDateTime.ofInstant(command.getSourceObservedAt(), ZoneOffset.UTC))
                .setSourceEvidenceRef(command.getSourceEvidenceRef())
                .setSourceEvidenceSha256(command.getSourceEvidenceSha256())
                .setEffectiveFrom(LocalDateTime.ofInstant(command.getEffectiveFrom(), ZoneOffset.UTC))
                .setPublishedAt(now);
        require(mapper.insertIntelligenceTaxonomyVersion(version) == 1,
                "failed to persist immutable taxonomy version");
        int sequence = 0;
        for (String level : levels) {
            require(mapper.insertIntelligenceTaxonomyLevel(new IntelligenceTaxonomyLevel()
                    .setLevelDefinitionId(UUID.randomUUID().toString()).setTenantId(tenantId)
                    .setTaxonomyVersionId(version.getTaxonomyVersionId()).setTaxonomyId(taxonomy.getTaxonomyId())
                    .setDefinitionVersion(nextDefinitionVersion).setLevelSequence(++sequence).setLevelCode(level)
                    .setCreatedAt(now)) == 1, "failed to persist immutable taxonomy level");
        }
        String previousStatus = taxonomy.getStatus();
        require(mapper.publishIntelligenceTaxonomy(tenantId, taxonomy.getTaxonomyId(), taxonomy.getVersion(), now) == 1,
                "taxonomy publish conflict");
        taxonomy.setStatus("PUBLISHED").setCurrentDefinitionVersion(nextDefinitionVersion)
                .setVersion(taxonomy.getVersion() + 1).setUpdatedAt(now);
        eventService.appendTaxonomyVersion(operationId, taxonomy, version, levels, previousStatus,
                command, occurredAt, now);
        return taxonomyView(operationId, taxonomy, version, levels, false);
    }

    private RiskView retireIntelligenceEventTaxonomy(Long tenantId, Long operationId, RiskCommand command,
                                                      Instant occurredAt, LocalDateTime now) {
        requireId(command.getTaxonomyId(), "taxonomyId");
        requireExpectedVersion(command);
        requireId(command.getRetiredByPrincipalId(), "retiredByPrincipalId");
        requireCode(command.getReasonCode(), "reasonCode");
        validateTaxonomySourceEvidence(command, occurredAt, now);
        IntelligenceTaxonomy taxonomy = mapper.selectIntelligenceTaxonomyForUpdate(tenantId, command.getTaxonomyId());
        require(taxonomy != null, "intelligence event taxonomy does not exist");
        require(Objects.equals(taxonomy.getVersion(), command.getExpectedVersion()), "taxonomy version conflict");
        require("PUBLISHED".equals(taxonomy.getStatus()), "only a published taxonomy can be retired");
        IntelligenceTaxonomyVersion currentVersion = mapper.selectIntelligenceTaxonomyVersion(
                tenantId, taxonomy.getTaxonomyId(), taxonomy.getCurrentDefinitionVersion());
        require(currentVersion != null, "published taxonomy has no current immutable version");
        require(!occurredAt.isBefore(currentVersion.getEffectiveFrom().toInstant(ZoneOffset.UTC)),
                "taxonomy cannot retire before its current version becomes effective");
        long nextVersion = taxonomy.getVersion() + 1;
        LocalDateTime retiredAt = LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC);
        require(mapper.retireIntelligenceTaxonomy(tenantId, taxonomy.getTaxonomyId(), taxonomy.getVersion(),
                retiredAt, now) == 1, "taxonomy retirement conflict");
        IntelligenceTaxonomyRetirement retirement = new IntelligenceTaxonomyRetirement()
                .setRetirementId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setTaxonomyId(taxonomy.getTaxonomyId()).setTaxonomyVersion(nextVersion)
                .setRetiredByPrincipalId(command.getRetiredByPrincipalId()).setReasonCode(command.getReasonCode())
                .setSourceSystem(command.getSourceSystem()).setSourceTable(command.getSourceTable())
                .setSourceRecordKey(command.getSourceRecordKey()).setSourceVersion(command.getSourceVersion())
                .setSourceObservedAt(LocalDateTime.ofInstant(command.getSourceObservedAt(), ZoneOffset.UTC))
                .setSourceEvidenceRef(command.getSourceEvidenceRef())
                .setSourceEvidenceSha256(command.getSourceEvidenceSha256()).setRetiredAt(retiredAt).setCreatedAt(now);
        require(mapper.insertIntelligenceTaxonomyRetirement(retirement) == 1,
                "failed to persist immutable taxonomy retirement");
        String previousStatus = taxonomy.getStatus();
        taxonomy.setStatus("RETIRED").setVersion(nextVersion).setRetiredAt(retiredAt).setUpdatedAt(now);
        List<String> levels = mapper.selectIntelligenceTaxonomyLevelCodes(
                tenantId, taxonomy.getTaxonomyId(), taxonomy.getCurrentDefinitionVersion());
        eventService.appendTaxonomyRetirement(operationId, taxonomy, retirement, levels,
                previousStatus, command, occurredAt, now);
        return taxonomyView(operationId, taxonomy, currentVersion, levels, false);
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

    private RiskView linkOrderReviewCase(Long tenantId, Long operationId, RiskCommand command, Instant occurredAt,
                                         LocalDateTime now) {
        requireId(command.getCaseId(), "caseId");
        requireId(command.getOrderId(), "orderId");
        require(ORDER_RISK_TYPES.contains(command.getRiskType()), "unsupported riskType");
        requireCode(command.getReasonCode(), "reasonCode");
        require(mapper.selectOrderRiskCaseByCaseId(tenantId, command.getCaseId()) == null,
                "risk review case already links an order");
        ReviewCase review = mapper.selectReviewCase(tenantId, command.getCaseId());
        require(review != null, "risk review case does not exist");
        OrderReference order = requireOrderReference(tenantId, command.getOrderId());
        PaymentReference payment = requireOptionalPaymentReference(tenantId, command.getPaymentId());
        if (payment != null) {
            require(Objects.equals(payment.getOrderId(), order.getOrderId()), "payment does not belong to canonical order");
        }
        OrderRiskCase value = new OrderRiskCase().setOrderRiskCaseId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setCaseId(review.getCaseId()).setOrderId(order.getOrderId()).setPaymentId(command.getPaymentId())
                .setRiskType(command.getRiskType()).setReasonCode(command.getReasonCode()).setCreatedAt(now);
        require(mapper.insertOrderRiskCase(value) == 1, "failed to persist constrained order risk case");
        eventService.appendOrderRiskCase(value, review, command, occurredAt);
        return reviewView(operationId, review, false).setOrderRiskCaseId(value.getOrderRiskCaseId())
                .setOrderId(value.getOrderId()).setPaymentId(value.getPaymentId());
    }

    private RiskView openPaymentDispute(Long tenantId, Long operationId, RiskCommand command, Instant occurredAt,
                                        LocalDateTime now) {
        requireId(command.getOrderId(), "orderId");
        requireId(command.getPaymentId(), "paymentId");
        require(DISPUTE_TYPES.contains(command.getDisputeType()), "unsupported disputeType");
        requireCode(command.getReasonCode(), "reasonCode");
        require(command.getAmountMinor() != null && command.getAmountMinor() > 0, "amountMinor must be positive");
        require(CURRENCY_CNY.equals(command.getCurrencyCode()), "risk commerce first slice supports CNY only");
        requireId(command.getExternalRef(), "externalRef");
        OrderReference order = requireOrderReference(tenantId, command.getOrderId());
        PaymentReference payment = requirePaymentReference(tenantId, command.getPaymentId());
        require(Objects.equals(payment.getOrderId(), order.getOrderId()), "payment does not belong to canonical order");
        ReviewCase review = null;
        if (command.getCaseId() != null) {
            review = mapper.selectReviewCase(tenantId, command.getCaseId());
            require(review != null, "linked risk review case does not exist");
        }
        String disputeId = command.getDisputeId() == null ? UUID.randomUUID().toString() : command.getDisputeId();
        requireId(disputeId, "disputeId");
        PaymentDispute dispute = new PaymentDispute().setDisputeId(disputeId).setTenantId(tenantId)
                .setOrderId(order.getOrderId()).setPaymentId(payment.getPaymentId())
                .setCaseId(review == null ? null : review.getCaseId()).setDecisionId(null)
                .setDisputeType(command.getDisputeType()).setStatus("OPEN").setReasonCode(command.getReasonCode())
                .setAmountMinor(command.getAmountMinor()).setCurrencyCode(command.getCurrencyCode())
                .setExternalRef(command.getExternalRef()).setVersion(1L)
                .setOpenedAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)).setResolvedAt(null)
                .setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertPaymentDispute(dispute) == 1, "failed to persist payment dispute");
        eventService.appendPaymentDispute(operationId, dispute, null, command, occurredAt, now);
        return new RiskView().setOperationId(operationId).setDuplicate(false).setOrderId(dispute.getOrderId())
                .setPaymentId(dispute.getPaymentId()).setDisputeId(dispute.getDisputeId())
                .setDisputeStatus(dispute.getStatus()).setCaseId(dispute.getCaseId());
    }

    private RiskView resolvePaymentDispute(Long tenantId, Long operationId, RiskCommand command, Instant occurredAt,
                                           LocalDateTime now) {
        requireId(command.getDisputeId(), "disputeId");
        requireExpectedVersion(command);
        require(DISPUTE_STATUSES.contains(command.getDisputeStatus()), "unsupported disputeStatus");
        require(!"OPEN".equals(command.getDisputeStatus()), "payment dispute resolution requires a terminal status");
        requireCode(command.getReasonCode(), "reasonCode");
        PaymentDispute dispute = mapper.selectPaymentDisputeForUpdate(tenantId, command.getDisputeId());
        require(dispute != null, "payment dispute does not exist");
        require(Objects.equals(dispute.getVersion(), command.getExpectedVersion()), "payment dispute version conflict");
        require("OPEN".equals(dispute.getStatus()), "payment dispute is not open");
        Decision decision = null;
        if (command.getDecisionId() != null) {
            decision = mapper.selectDecision(tenantId, command.getDecisionId());
            require(decision != null, "linked risk decision does not exist");
            if (dispute.getCaseId() != null) {
                require(Objects.equals(dispute.getCaseId(), decision.getCaseId()),
                        "linked risk decision does not belong to dispute review case");
            }
        }
        String before = dispute.getStatus();
        LocalDateTime resolvedAt = LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC);
        require(mapper.resolvePaymentDispute(tenantId, dispute.getDisputeId(), dispute.getVersion(), before,
                command.getDisputeStatus(), command.getReasonCode(), command.getDecisionId(), resolvedAt, now) == 1,
                "payment dispute transition conflict");
        dispute.setDecisionId(command.getDecisionId()).setStatus(command.getDisputeStatus())
                .setReasonCode(command.getReasonCode()).setResolvedAt(resolvedAt)
                .setVersion(dispute.getVersion() + 1).setUpdatedAt(now);
        eventService.appendPaymentDispute(operationId, dispute, before, command, occurredAt, now);
        return new RiskView().setOperationId(operationId).setDuplicate(false).setOrderId(dispute.getOrderId())
                .setPaymentId(dispute.getPaymentId()).setDisputeId(dispute.getDisputeId())
                .setDisputeStatus(dispute.getStatus()).setDecisionId(decision == null ? null : decision.getDecisionId())
                .setCaseId(dispute.getCaseId());
    }

    private RiskView postLossEntry(Long tenantId, Long operationId, RiskCommand command, Instant occurredAt,
                                   LocalDateTime now) {
        requireId(command.getOrderId(), "orderId");
        require(LOSS_ENTRY_TYPES.contains(command.getLossEntryType()), "unsupported lossEntryType");
        require(command.getSignedAmountMinor() != null && command.getSignedAmountMinor() != 0,
                "signedAmountMinor must be non-zero");
        require(CURRENCY_CNY.equals(command.getCurrencyCode()), "risk commerce first slice supports CNY only");
        requireOrderReference(tenantId, command.getOrderId());
        requireOptionalPaymentReference(tenantId, command.getPaymentId());
        if (command.getPaymentId() != null) {
            PaymentReference payment = requirePaymentReference(tenantId, command.getPaymentId());
            require(Objects.equals(payment.getOrderId(), command.getOrderId()), "payment does not belong to canonical order");
        }
        if ("CHARGEBACK_LOSS".equals(command.getLossEntryType())) {
            requireId(command.getDisputeId(), "disputeId");
        }
        if ("CONFIRMED_RISK_LOSS".equals(command.getLossEntryType())) {
            requireId(command.getDecisionId(), "decisionId");
        }
        if (command.getDisputeId() != null) {
            PaymentDispute dispute = mapper.selectPaymentDispute(tenantId, command.getDisputeId());
            require(dispute != null, "linked payment dispute does not exist");
            require(Objects.equals(dispute.getOrderId(), command.getOrderId()),
                    "linked payment dispute does not belong to canonical order");
            if (command.getPaymentId() != null) {
                require(Objects.equals(dispute.getPaymentId(), command.getPaymentId()),
                        "linked payment dispute does not belong to canonical payment");
            }
        }
        if (command.getDecisionId() != null) {
            require(mapper.selectDecision(tenantId, command.getDecisionId()) != null, "linked risk decision does not exist");
        }
        LossEntry value = new LossEntry().setLossEntryId(
                        command.getLossEntryId() == null ? UUID.randomUUID().toString() : command.getLossEntryId())
                .setTenantId(tenantId).setOrderId(command.getOrderId()).setPaymentId(command.getPaymentId())
                .setDisputeId(command.getDisputeId()).setDecisionId(command.getDecisionId())
                .setEntryType(command.getLossEntryType()).setSignedAmountMinor(command.getSignedAmountMinor())
                .setCurrencyCode(command.getCurrencyCode()).setExternalRef(command.getExternalRef())
                .setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)).setCreatedAt(now);
        requireId(value.getLossEntryId(), "lossEntryId");
        require(mapper.insertLossEntry(value) == 1, "failed to persist risk loss entry");
        eventService.appendLossEntry(value, command, occurredAt);
        return new RiskView().setOperationId(operationId).setDuplicate(false).setOrderId(value.getOrderId())
                .setPaymentId(value.getPaymentId()).setDisputeId(value.getDisputeId())
                .setDecisionId(value.getDecisionId()).setLossEntryId(value.getLossEntryId());
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

    private OrderReference requireOrderReference(Long tenantId, String orderId) {
        OrderReference order = mapper.selectOrderReference(tenantId, orderId);
        require(order != null, "canonical order does not exist");
        return order;
    }

    private PaymentReference requirePaymentReference(Long tenantId, String paymentId) {
        PaymentReference payment = mapper.selectPaymentReference(tenantId, paymentId);
        require(payment != null, "canonical payment does not exist");
        return payment;
    }

    private PaymentReference requireOptionalPaymentReference(Long tenantId, String paymentId) {
        if (paymentId == null) return null;
        return requirePaymentReference(tenantId, paymentId);
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

    private static RiskView taxonomyView(Long operationId, IntelligenceTaxonomy taxonomy,
                                         IntelligenceTaxonomyVersion version, List<String> levels,
                                         boolean duplicate) {
        return new RiskView().setOperationId(operationId).setDuplicate(duplicate)
                .setTaxonomyId(taxonomy.getTaxonomyId()).setTaxonomyVersion(taxonomy.getVersion())
                .setTaxonomyStatus(taxonomy.getStatus())
                .setTaxonomyVersionId(version == null ? null : version.getTaxonomyVersionId())
                .setTaxonomyDefinitionVersion(taxonomy.getCurrentDefinitionVersion())
                .setEventCode(taxonomy.getEventCode()).setIntelligenceLevels(levels)
                .setRetiredAt(taxonomy.getRetiredAt() == null ? null
                        : taxonomy.getRetiredAt().toInstant(ZoneOffset.UTC).toString());
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

    private static void validateTaxonomySourceEvidence(RiskCommand command, Instant occurredAt,
                                                       LocalDateTime persistedAt) {
        requireCode(command.getSourceSystem(), "sourceSystem");
        require(command.getSourceTable() != null && SOURCE_TABLE.matcher(command.getSourceTable()).matches(),
                "sourceTable must be a lowercase physical source identifier");
        requireId(command.getSourceRecordKey(), "sourceRecordKey");
        requireId(command.getSourceVersion(), "sourceVersion");
        require(command.getSourceObservedAt() != null && !command.getSourceObservedAt().isAfter(occurredAt),
                "sourceObservedAt must not follow occurredAt");
        require(!command.getSourceObservedAt().isAfter(persistedAt.toInstant(ZoneOffset.UTC)),
                "sourceObservedAt must not be in the future relative to persisted evidence");
        requireSafeRef(command.getSourceEvidenceRef(), "sourceEvidenceRef");
        require(command.getSourceEvidenceSha256() != null
                        && HMAC_TOKEN.matcher(command.getSourceEvidenceSha256()).matches(),
                "sourceEvidenceSha256 must be lowercase SHA-256");
    }

    private static void requireEventCode(String value) {
        require(value != null && EVENT_CODE.matcher(value).matches(),
                "eventCode must preserve a bounded source event identifier");
    }

    private static void requireLevelCode(String value) {
        require(value != null && LEVEL_CODE.matcher(value).matches(),
                "intelligence level must be a bounded uppercase code");
    }

    private static String firstNonNull(String... values) {
        for (String value : values) if (value != null) return value;
        return null;
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
