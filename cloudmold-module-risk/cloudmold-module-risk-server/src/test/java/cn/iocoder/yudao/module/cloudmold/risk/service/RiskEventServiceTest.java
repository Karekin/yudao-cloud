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

    @Test
    void shouldEmitVersionedSourceBackedIntelligenceTaxonomyWithoutInventingSeveritySemantics() {
        Instant occurredAt = Instant.parse("2026-07-17T10:00:00Z");
        LocalDateTime now = LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC);
        RiskCommand publish = RiskCommand.builder()
                .operation(RiskOperation.PUBLISH_INTELLIGENCE_EVENT_TAXONOMY_VERSION)
                .idempotencyKey("taxonomy-publish-event-01").runId("taxonomy-run-01")
                .correlationId("taxonomy-correlation-01").build();
        IntelligenceTaxonomy taxonomy = new IntelligenceTaxonomy().setTaxonomyId("taxonomy-01")
                .setTenantId(1L).setEventCode("event_new_001").setStatus("PUBLISHED")
                .setCurrentDefinitionVersion(1L).setVersion(2L);
        IntelligenceTaxonomyVersion version = new IntelligenceTaxonomyVersion()
                .setTaxonomyVersionId("taxonomy-version-01").setTenantId(1L).setTaxonomyId("taxonomy-01")
                .setDefinitionVersion(1L).setLevelsSha256("a".repeat(64))
                .setApprovedByPrincipalId("principal-data-owner-1").setEffectiveFrom(now)
                .setSourceSystem("YSHOPPING").setSourceTable("ods_intelligence_event_code_level_df")
                .setSourceRecordKey("19").setSourceVersion("source-v1").setSourceObservedAt(now.minusSeconds(1))
                .setSourceEvidenceRef("restricted:taxonomy_evidence_0001")
                .setSourceEvidenceSha256("b".repeat(64));
        IntelligenceTaxonomyRetirement retirement = new IntelligenceTaxonomyRetirement()
                .setRetirementId("retirement-01").setTenantId(1L).setTaxonomyId("taxonomy-01")
                .setTaxonomyVersion(3L).setRetiredByPrincipalId("principal-data-owner-1")
                .setReasonCode("SOURCE_ROW_DELETED").setRetiredAt(now.plusMinutes(1))
                .setSourceSystem("YSHOPPING").setSourceTable("ods_intelligence_event_code_level_df")
                .setSourceRecordKey("19").setSourceVersion("source-v2").setSourceObservedAt(now.plusSeconds(30))
                .setSourceEvidenceRef("restricted:taxonomy_evidence_0002")
                .setSourceEvidenceSha256("c".repeat(64));

        service.appendTaxonomyVersion(1L, taxonomy, version, List.of("A", "B", "C"), "DRAFT",
                publish, occurredAt, now);
        taxonomy.setStatus("RETIRED").setVersion(3L).setRetiredAt(now.plusMinutes(1));
        RiskCommand retire = RiskCommand.builder().operation(RiskOperation.RETIRE_INTELLIGENCE_EVENT_TAXONOMY)
                .idempotencyKey("taxonomy-retire-event-01").runId("taxonomy-run-01")
                .correlationId("taxonomy-correlation-01").build();
        service.appendTaxonomyRetirement(2L, taxonomy, retirement, List.of("A", "B", "C"), "PUBLISHED",
                retire, occurredAt.plusSeconds(60), now.plusMinutes(1));

        ArgumentCaptor<AppendDomainEventCommand> captor = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outbox, times(2)).append(captor.capture());
        Map<String, AppendDomainEventCommand> events = new HashMap<>();
        captor.getAllValues().forEach(event -> events.put(event.getEventType(), event));
        AppendDomainEventCommand published = events.get("risk.intelligence_event_taxonomy.version_published");
        AppendDomainEventCommand retired = events.get("risk.intelligence_event_taxonomy.retired");
        assertThat(published.getAggregateType()).isEqualTo("risk_intelligence_event_taxonomy");
        assertThat(published.getAggregateVersion()).isEqualTo(2L);
        assertThat(published.getPayload()).containsEntry("event_code", "event_new_001")
                .containsEntry("level_codes", List.of("A", "B", "C"))
                .containsEntry("source_table", "ods_intelligence_event_code_level_df")
                .containsEntry("source_record_key", "19")
                .doesNotContainKeys("severity", "risk_level", "automatic_enforcement");
        assertThat(retired.getAggregateVersion()).isEqualTo(3L);
        assertThat(retired.getPayload()).containsEntry("current_status", "RETIRED")
                .containsEntry("reason_code", "SOURCE_ROW_DELETED")
                .containsEntry("level_codes", List.of("A", "B", "C"));
        assertThat(published.getHeaders()).containsEntry("pii_safe", true)
                .containsEntry("automatic_enforcement", false);
        assertThat(retired.getHeaders()).containsEntry("pii_safe", true)
                .containsEntry("automatic_enforcement", false);
    }
}
