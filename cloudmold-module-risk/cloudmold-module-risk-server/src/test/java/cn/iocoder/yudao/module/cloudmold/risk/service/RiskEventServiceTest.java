package cn.iocoder.yudao.module.cloudmold.risk.service;

import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.*;
import cn.iocoder.yudao.module.cloudmold.risk.api.*;
import cn.iocoder.yudao.module.cloudmold.risk.dal.dataobject.RiskRecords.*;
import cn.iocoder.yudao.module.cloudmold.risk.dal.mysql.RiskStoreMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RiskEventServiceTest {
    private final RiskStoreMapper mapper = mock(RiskStoreMapper.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final RiskEventService service = new RiskEventService(mapper, outbox);

    @Test
    void shouldEmitOnlyFixedPiiSafeEventsWithExactAggregateTypesAndPayloads() {
        Instant occurredAt = Instant.parse("2026-07-16T00:00:00Z");
        LocalDateTime now = LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC);
        RiskCommand command = RiskCommand.builder().operation(RiskOperation.PUBLISH_POLICY_VERSION)
                .idempotencyKey("event-test-01").runId("risk-run-01").correlationId("corr-01")
                .causationId("cause-01").reasonCode("MANUAL_REVIEW").build();
        Policy policy = new Policy().setPolicyId("policy-01").setTenantId(1L).setPolicyCode("GRAPH_POLICY")
                .setStatus("PUBLISHED").setCurrentVersion(2L);
        PolicyVersion policyVersion = new PolicyVersion().setRulesSha256("a".repeat(64))
                .setApprovedByPrincipalId("principal-approver-1").setEffectiveFrom(now.plusMinutes(1));
        Signal signal = new Signal().setSignalId("signal-01").setTenantId(1L)
                .setSubjectPrincipalId("principal-subject-1").setPolicyId("policy-01").setPolicyVersion(2L)
                .setSignalType("SHARED_DEVICE").setSeverity("HIGH").setEvidenceRef("sha256:" + "b".repeat(64));
        Relation relation = new Relation().setRelationId("relation-01").setTenantId(1L)
                .setSubjectPrincipalId("principal-a").setRelatedPrincipalId("principal-b").setMediumType("DEVICE")
                .setMediumToken("c".repeat(64)).setKeyVersion(4).setFirstSeenAt(now.minusHours(1))
                .setLastSeenAt(now).setConfidenceBasisPoints(9000);
        Cluster cluster = new Cluster().setClusterId("cluster-01").setTenantId(1L).setClusterCode("GRAPH_001")
                .setStatus("UNDER_REVIEW").setRiskLevel("HIGH").setMemberCount(2).setEdgeCount(1).setVersion(3L);
        ReviewCase review = new ReviewCase().setCaseId("case-01").setTenantId(1L).setClusterId("cluster-01")
                .setStatus("DECIDED").setReviewerPrincipalId("principal-reviewer-1").setVersion(3L);
        Decision decision = new Decision().setDecisionId("decision-01").setTenantId(1L).setCaseId("case-01")
                .setClusterId("cluster-01").setDecisionType("MONITOR").setReasonCode("MORE_EVIDENCE_REQUIRED")
                .setDecidedByPrincipalId("principal-reviewer-1");
        Feedback feedback = new Feedback().setFeedbackId("feedback-01").setTenantId(1L)
                .setDecisionId("decision-01").setCaseId("case-01").setFeedbackType("CONFIRMED")
                .setReasonCode("REVIEW_VALIDATED").setRecordedByPrincipalId("principal-qa-1");

        service.appendPolicy(1L, policy, policyVersion, "DRAFT", command, occurredAt, now);
        service.appendSignal(signal, command, occurredAt);
        service.appendRelationship(relation, command, occurredAt);
        service.appendCluster(2L, cluster, "OPEN", command, occurredAt, now);
        service.appendReview(3L, review, "IN_REVIEW", command, occurredAt, now);
        service.appendDecision(decision, command, occurredAt);
        service.appendFeedback(feedback, command, occurredAt);

        ArgumentCaptor<AppendDomainEventCommand> captor = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outbox, times(7)).append(captor.capture());
        Map<String, AppendDomainEventCommand> events = new HashMap<>();
        for (AppendDomainEventCommand event : captor.getAllValues()) events.put(event.getEventType(), event);

        assertThat(events).containsOnlyKeys("risk.policy.version_published", "risk.signal.detected",
                "risk.relationship.observed", "risk.cluster.status_changed", "risk.review.status_changed",
                "risk.decision.recorded", "risk.feedback.recorded");
        assertThat(events.get("risk.policy.version_published").getAggregateType()).isEqualTo("risk_policy");
        assertThat(events.get("risk.signal.detected").getAggregateType()).isEqualTo("risk_signal");
        assertThat(events.get("risk.relationship.observed").getAggregateType()).isEqualTo("risk_relationship");
        assertThat(events.get("risk.cluster.status_changed").getAggregateType()).isEqualTo("risk_cluster");
        assertThat(events.get("risk.review.status_changed").getAggregateType()).isEqualTo("risk_review_case");
        assertThat(events.get("risk.decision.recorded").getAggregateType()).isEqualTo("risk_decision");
        assertThat(events.get("risk.feedback.recorded").getAggregateType()).isEqualTo("risk_feedback");

        for (AppendDomainEventCommand event : events.values()) {
            assertThat(event.getSchemaVersion()).isEqualTo(1);
            assertThat(event.getSourceSystem()).isEqualTo("cloudmold-risk");
            assertThat(event.getTenantId()).isEqualTo(1L);
            assertThat(event.getHeaders()).containsEntry("pii_safe", true)
                    .containsEntry("automatic_enforcement", false);
            assertThat(event.getPayload().keySet()).noneMatch(key -> Set.of("phone", "ip", "address", "device_id",
                    "block_account", "charge_amount", "punishment").contains(key));
        }
        assertThat(events.get("risk.relationship.observed").getPayload())
                .containsEntry("medium_type", "DEVICE").containsEntry("medium_token", "c".repeat(64))
                .containsEntry("key_version", 4);
        assertThat(events.get("risk.policy.version_published").getPayload().keySet()).containsExactlyInAnyOrder(
                "policy_id", "policy_code", "policy_version", "rules_sha256", "previous_status",
                "current_status", "approved_by_principal_id", "effective_from", "operation");
        assertThat(events.get("risk.signal.detected").getPayload().keySet()).containsExactlyInAnyOrder(
                "signal_id", "subject_principal_id", "policy_id", "policy_version", "signal_type", "severity",
                "evidence_ref", "occurred_at");
    }
}
