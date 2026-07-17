package cn.iocoder.yudao.module.cloudmold.risk.service;

import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.risk.api.RiskCommand;
import cn.iocoder.yudao.module.cloudmold.risk.dal.dataobject.RiskRecords.*;
import cn.iocoder.yudao.module.cloudmold.risk.dal.mysql.RiskStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RiskEventService {
    static final String SOURCE_SYSTEM = "cloudmold-risk";

    private final RiskStoreMapper mapper;
    private final OutboxAppender outboxAppender;

    public void appendPolicy(Long operationId, Policy policy, PolicyVersion version, String previousStatus,
                             RiskCommand command, Instant occurredAt, LocalDateTime now) {
        history(operationId, policy.getTenantId(), "POLICY", policy.getPolicyId(), policy.getCurrentVersion(),
                previousStatus, policy.getStatus(), command.getReasonCode(), occurredAt, now);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("policy_id", policy.getPolicyId());
        payload.put("policy_code", policy.getPolicyCode());
        payload.put("policy_version", policy.getCurrentVersion());
        payload.put("rules_sha256", version.getRulesSha256());
        payload.put("previous_status", previousStatus);
        payload.put("current_status", policy.getStatus());
        payload.put("approved_by_principal_id", version.getApprovedByPrincipalId());
        payload.put("effective_from", version.getEffectiveFrom().toInstant(ZoneOffset.UTC).toString());
        payload.put("operation", command.getOperation().name());
        append("risk.policy.version_published", "risk_policy", policy.getPolicyId(), policy.getCurrentVersion(),
                policy.getTenantId(), command, occurredAt, payload);
    }

    public void appendTaxonomyHistory(Long operationId, IntelligenceTaxonomy taxonomy, String previousStatus,
                                      RiskCommand command, Instant occurredAt, LocalDateTime now) {
        history(operationId, taxonomy.getTenantId(), "INTELLIGENCE_TAXONOMY", taxonomy.getTaxonomyId(),
                taxonomy.getVersion(), previousStatus, taxonomy.getStatus(), command.getReasonCode(), occurredAt, now);
    }

    public void appendTaxonomyVersion(Long operationId, IntelligenceTaxonomy taxonomy,
                                      IntelligenceTaxonomyVersion version, List<String> levels,
                                      String previousStatus, RiskCommand command, Instant occurredAt,
                                      LocalDateTime now) {
        appendTaxonomyHistory(operationId, taxonomy, previousStatus, command, occurredAt, now);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taxonomy_id", taxonomy.getTaxonomyId());
        payload.put("taxonomy_version_id", version.getTaxonomyVersionId());
        payload.put("definition_version", version.getDefinitionVersion());
        payload.put("event_code", taxonomy.getEventCode());
        payload.put("level_codes", levels);
        payload.put("levels_sha256", version.getLevelsSha256());
        payload.put("previous_status", previousStatus);
        payload.put("current_status", taxonomy.getStatus());
        payload.put("approved_by_principal_id", version.getApprovedByPrincipalId());
        payload.put("effective_from", version.getEffectiveFrom().toInstant(ZoneOffset.UTC).toString());
        addTaxonomySourceEvidence(payload, version.getSourceSystem(), version.getSourceTable(),
                version.getSourceRecordKey(), version.getSourceVersion(), version.getSourceObservedAt(),
                version.getSourceEvidenceRef(), version.getSourceEvidenceSha256());
        append("risk.intelligence_event_taxonomy.version_published", "risk_intelligence_event_taxonomy",
                taxonomy.getTaxonomyId(), taxonomy.getVersion(), taxonomy.getTenantId(), command, occurredAt, payload);
    }

    public void appendTaxonomyRetirement(Long operationId, IntelligenceTaxonomy taxonomy,
                                         IntelligenceTaxonomyRetirement retirement, List<String> levels,
                                         String previousStatus, RiskCommand command, Instant occurredAt,
                                         LocalDateTime now) {
        appendTaxonomyHistory(operationId, taxonomy, previousStatus, command, occurredAt, now);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taxonomy_id", taxonomy.getTaxonomyId());
        payload.put("definition_version", taxonomy.getCurrentDefinitionVersion());
        payload.put("event_code", taxonomy.getEventCode());
        payload.put("level_codes", levels);
        payload.put("previous_status", previousStatus);
        payload.put("current_status", taxonomy.getStatus());
        payload.put("retired_by_principal_id", retirement.getRetiredByPrincipalId());
        payload.put("reason_code", retirement.getReasonCode());
        payload.put("retired_at", retirement.getRetiredAt().toInstant(ZoneOffset.UTC).toString());
        addTaxonomySourceEvidence(payload, retirement.getSourceSystem(), retirement.getSourceTable(),
                retirement.getSourceRecordKey(), retirement.getSourceVersion(), retirement.getSourceObservedAt(),
                retirement.getSourceEvidenceRef(), retirement.getSourceEvidenceSha256());
        append("risk.intelligence_event_taxonomy.retired", "risk_intelligence_event_taxonomy",
                taxonomy.getTaxonomyId(), taxonomy.getVersion(), taxonomy.getTenantId(), command, occurredAt, payload);
    }

    private static void addTaxonomySourceEvidence(Map<String, Object> payload, String sourceSystem,
                                                   String sourceTable, String sourceRecordKey,
                                                   String sourceVersion, LocalDateTime sourceObservedAt,
                                                   String sourceEvidenceRef, String sourceEvidenceSha256) {
        payload.put("source_system", sourceSystem);
        payload.put("source_table", sourceTable);
        payload.put("source_record_key", sourceRecordKey);
        payload.put("source_version", sourceVersion);
        payload.put("source_observed_at", sourceObservedAt.toInstant(ZoneOffset.UTC).toString());
        payload.put("source_evidence_ref", sourceEvidenceRef);
        payload.put("source_evidence_sha256", sourceEvidenceSha256);
    }

    public void appendSignal(Signal signal, RiskCommand command, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("signal_id", signal.getSignalId());
        payload.put("subject_principal_id", signal.getSubjectPrincipalId());
        payload.put("policy_id", signal.getPolicyId());
        payload.put("policy_version", signal.getPolicyVersion());
        payload.put("signal_type", signal.getSignalType());
        payload.put("severity", signal.getSeverity());
        payload.put("evidence_ref", signal.getEvidenceRef());
        payload.put("occurred_at", occurredAt.toString());
        append("risk.signal.detected", "risk_signal", signal.getSignalId(), 1L, signal.getTenantId(), command,
                occurredAt, payload);
    }

    public void appendRelationship(Relation relation, RiskCommand command, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("relation_id", relation.getRelationId());
        payload.put("subject_principal_id", relation.getSubjectPrincipalId());
        payload.put("related_principal_id", relation.getRelatedPrincipalId());
        payload.put("medium_type", relation.getMediumType());
        payload.put("medium_token", relation.getMediumToken());
        payload.put("key_version", relation.getKeyVersion());
        payload.put("first_seen_at", relation.getFirstSeenAt().toInstant(ZoneOffset.UTC).toString());
        payload.put("last_seen_at", relation.getLastSeenAt().toInstant(ZoneOffset.UTC).toString());
        payload.put("confidence_basis_points", relation.getConfidenceBasisPoints());
        append("risk.relationship.observed", "risk_relationship", relation.getRelationId(), 1L,
                relation.getTenantId(), command, occurredAt, payload);
    }

    public void appendCluster(Long operationId, Cluster cluster, String previousStatus, RiskCommand command,
                              Instant occurredAt, LocalDateTime now) {
        history(operationId, cluster.getTenantId(), "CLUSTER", cluster.getClusterId(), cluster.getVersion(),
                previousStatus, cluster.getStatus(), command.getReasonCode(), occurredAt, now);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cluster_id", cluster.getClusterId());
        payload.put("cluster_code", cluster.getClusterCode());
        payload.put("previous_status", previousStatus);
        payload.put("current_status", cluster.getStatus());
        payload.put("risk_level", cluster.getRiskLevel());
        payload.put("member_count", cluster.getMemberCount());
        payload.put("edge_count", cluster.getEdgeCount());
        payload.put("operation", command.getOperation().name());
        append("risk.cluster.status_changed", "risk_cluster", cluster.getClusterId(), cluster.getVersion(),
                cluster.getTenantId(), command, occurredAt, payload);
    }

    public void appendReview(Long operationId, ReviewCase review, String previousStatus, RiskCommand command,
                             Instant occurredAt, LocalDateTime now) {
        history(operationId, review.getTenantId(), "REVIEW_CASE", review.getCaseId(), review.getVersion(),
                previousStatus, review.getStatus(), command.getReasonCode(), occurredAt, now);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("case_id", review.getCaseId());
        payload.put("cluster_id", review.getClusterId());
        payload.put("previous_status", previousStatus);
        payload.put("current_status", review.getStatus());
        payload.put("reviewer_principal_id", review.getReviewerPrincipalId());
        payload.put("operation", command.getOperation().name());
        append("risk.review.status_changed", "risk_review_case", review.getCaseId(), review.getVersion(),
                review.getTenantId(), command, occurredAt, payload);
    }

    public void appendDecision(Decision decision, RiskCommand command, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("decision_id", decision.getDecisionId());
        payload.put("case_id", decision.getCaseId());
        payload.put("cluster_id", decision.getClusterId());
        payload.put("decision_type", decision.getDecisionType());
        payload.put("reason_code", decision.getReasonCode());
        payload.put("decided_by_principal_id", decision.getDecidedByPrincipalId());
        payload.put("occurred_at", occurredAt.toString());
        append("risk.decision.recorded", "risk_decision", decision.getDecisionId(), 1L,
                decision.getTenantId(), command, occurredAt, payload);
    }

    public void appendFeedback(Feedback feedback, RiskCommand command, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("feedback_id", feedback.getFeedbackId());
        payload.put("decision_id", feedback.getDecisionId());
        payload.put("case_id", feedback.getCaseId());
        payload.put("feedback_type", feedback.getFeedbackType());
        payload.put("reason_code", feedback.getReasonCode());
        payload.put("recorded_by_principal_id", feedback.getRecordedByPrincipalId());
        payload.put("occurred_at", occurredAt.toString());
        append("risk.feedback.recorded", "risk_feedback", feedback.getFeedbackId(), 1L,
                feedback.getTenantId(), command, occurredAt, payload);
    }

    private void history(Long operationId, Long tenantId, String aggregateType, String aggregateId, Long version,
                         String previousStatus, String currentStatus, String reasonCode, Instant occurredAt,
                         LocalDateTime now) {
        mapper.insertHistory(new StatusHistory().setTenantId(tenantId).setAggregateType(aggregateType)
                .setAggregateId(aggregateId).setAggregateVersion(version).setPreviousStatus(previousStatus)
                .setCurrentStatus(currentStatus).setOperationId(operationId).setReasonCode(reasonCode)
                .setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)).setCreatedAt(now));
    }

    private void append(String eventType, String aggregateType, String aggregateId, Long version, Long tenantId,
                        RiskCommand command, Instant occurredAt, Map<String, Object> payload) {
        outboxAppender.append(AppendDomainEventCommand.builder().eventType(eventType).schemaVersion(1)
                .sourceSystem(SOURCE_SYSTEM).tenantId(tenantId).aggregateType(aggregateType)
                .aggregateId(aggregateId).aggregateVersion(version).eventSequence((short) 1)
                .occurredAt(occurredAt).traceId(command.getRunId()).correlationId(command.getCorrelationId())
                .causationId(command.getCausationId())
                .idempotencyKey(aggregateType + ":" + aggregateId + ":event:" + version)
                .payload(payload).headers(Map.of("pii_safe", true, "automatic_enforcement", false))
                .destination("lakehouse").build());
    }
}
