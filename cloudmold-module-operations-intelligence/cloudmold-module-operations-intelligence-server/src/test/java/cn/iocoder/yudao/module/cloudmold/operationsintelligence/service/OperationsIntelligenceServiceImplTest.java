package cn.iocoder.yudao.module.cloudmold.operationsintelligence.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.*;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.api.*;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.dal.dataobject.OperationsIntelligenceRecords.*;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.dal.mysql.OperationsIntelligenceStoreMapper;
import cn.iocoder.yudao.module.cloudmold.risk.api.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OperationsIntelligenceServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-07-17T14:00:00Z");

    private final OperationsIntelligenceStoreMapper mapper = mock(OperationsIntelligenceStoreMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final RiskQueryApi riskQueryApi = mock(RiskQueryApi.class);
    private final OperationsIntelligenceServiceImpl service =
            new OperationsIntelligenceServiceImpl(mapper, outboxAppender, riskQueryApi);
    private final AtomicReference<Observation> observation = new AtomicReference<>();
    private final AtomicReference<ModelResult> modelResult = new AtomicReference<>();
    private final AtomicReference<Clue> clue = new AtomicReference<>();
    private final AtomicReference<Alert> alert = new AtomicReference<>();
    private final AtomicReference<String> requestHash = new AtomicReference<>();
    private final AtomicReference<String> attemptToken = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(17L);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult(
                "11111111-1111-4111-8111-111111111111", "a".repeat(64), false));
        when(riskQueryApi.validateIntelligenceEventLevel(eq("taxonomy-1"), eq(1L),
                eq("event_new_001"), eq("B"), any())).thenReturn(IntelligenceEventTaxonomyReference.builder()
                .taxonomyId("taxonomy-1").taxonomyVersionId("taxonomy-version-1").definitionVersion(1L)
                .eventCode("event_new_001").intelligenceLevel("B").levelsSha256("9".repeat(64))
                .effectiveFrom(NOW.minusSeconds(3600)).build());
        when(mapper.insertOrResolveOperation(eq(17L), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    requestHash.set(invocation.getArgument(3));
                    attemptToken.set(invocation.getArgument(4));
                    return 1;
                });
        when(mapper.selectLastInsertId()).thenReturn(1L);
        when(mapper.selectOperationForUpdate(1L, 17L)).thenAnswer(invocation -> new Operation()
                .setOperationId(1L).setTenantId(17L).setRequestHash(requestHash.get())
                .setAttemptToken(attemptToken.get()).setStatus(0));
        when(mapper.markOperationSucceeded(anyLong(), eq(17L), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(mapper.insertObservation(any())).thenAnswer(invocation -> {
            observation.set(invocation.getArgument(0)); return 1;
        });
        when(mapper.selectObservation(eq(17L), anyString())).thenAnswer(invocation -> {
            Observation row = observation.get();
            return row != null && row.getObservationId().equals(invocation.getArgument(1)) ? row : null;
        });
        when(mapper.selectObservationBySource(eq(17L), anyString(), anyString())).thenReturn(null);
        when(mapper.insertModelResult(any())).thenAnswer(invocation -> {
            modelResult.set(invocation.getArgument(0)); return 1;
        });
        when(mapper.selectModelResult(eq(17L), anyString())).thenAnswer(invocation -> {
            ModelResult row = modelResult.get();
            return row != null && row.getModelResultId().equals(invocation.getArgument(1)) ? row : null;
        });
        when(mapper.insertClue(any())).thenAnswer(invocation -> {
            clue.set(invocation.getArgument(0)); return 1;
        });
        when(mapper.selectClue(eq(17L), anyString())).thenAnswer(invocation -> currentClue(invocation.getArgument(1)));
        when(mapper.selectClueForUpdate(eq(17L), anyString())).thenAnswer(
                invocation -> currentClue(invocation.getArgument(1)));
        when(mapper.reviewClue(eq(17L), anyString(), anyLong(), anyString(), any())).thenAnswer(invocation -> {
            Clue row = clue.get();
            row.setStatus(invocation.getArgument(3)).setVersion(row.getVersion() + 1);
            return 1;
        });
        when(mapper.insertClueReview(any())).thenReturn(1);
        when(mapper.insertAlert(any())).thenAnswer(invocation -> {
            alert.set(invocation.getArgument(0)); return 1;
        });
        when(mapper.selectAlert(eq(17L), anyString())).thenAnswer(invocation -> currentAlert(invocation.getArgument(1)));
        when(mapper.selectAlertForUpdate(eq(17L), anyString())).thenAnswer(
                invocation -> currentAlert(invocation.getArgument(1)));
        when(mapper.transitionAlert(eq(17L), anyString(), anyLong(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(1);
        when(mapper.insertAlertHistory(any())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void recordsPurposeLimitedObservationWithoutRawContent() {
        OperationsIntelligenceResult result = service.execute(observationCommand());

        assertThat(result.getAggregateType()).isEqualTo("intelligence_observation");
        assertThat(result.getStatus()).isEqualTo("RECORDED");
        assertThat(observation.get()).extracting(Observation::getSourceSystem,
                Observation::getObservationType, Observation::getSubjectType)
                .containsExactly("YSHOPPING_INTELLIGENCE", "THIRD_PARTY_CONTENT", "CONTENT");
        AppendDomainEventCommand event = lastEvent();
        assertThat(event.getEventType()).isEqualTo("operations_intelligence.observation.recorded");
        assertThat(event.getSchemaVersion()).isEqualTo(2);
        assertThat(event.getPayload()).containsKeys("evidence_ref", "content_sha256", "subject_ref")
                .containsEntry("taxonomy_id", "taxonomy-1")
                .containsEntry("taxonomy_version_id", "taxonomy-version-1")
                .containsEntry("taxonomy_definition_version", 1L)
                .containsEntry("event_code", "event_new_001")
                .containsEntry("intelligence_level_code", "B")
                .doesNotContainKeys("content", "title", "description", "message", "prompt");
        assertThat(event.getHeaders()).containsEntry("raw_content_stored", false)
                .containsEntry("automatic_enforcement", false);
    }

    @Test
    void keepsModelResultAndHumanClueReviewAtSeparateGrains() {
        service.execute(observationCommand());
        OperationsIntelligenceResult model = service.execute(base(
                OperationsIntelligenceOperation.RECORD_MODEL_RESULT, "model-result-1")
                .modelResult(OperationsIntelligenceCommand.ModelResultDefinition.builder()
                        .modelResultId("model-result-1").observationId("observation-1")
                        .invocationAttemptRef("invocation-1").modelVersionRef("model-v7")
                        .outcomeCode("SUCCEEDED").scoreBasisPoints(8700).retryNo(0)
                        .evidenceRef("restricted:model_result_0001")
                        .resultSha256("b".repeat(64)).build()).build());
        assertThat(model.getAggregateType()).isEqualTo("intelligence_model_result");

        OperationsIntelligenceResult recorded = service.execute(base(
                OperationsIntelligenceOperation.RECORD_CLUE, "clue-record-1")
                .clue(OperationsIntelligenceCommand.ClueDefinition.builder().clueId("clue-1")
                        .observationId("observation-1").modelResultId("model-result-1")
                        .clueType("FRAUD_PROMOTION").sourceCode("DOUYIN")
                        .sourcePublishedAt(NOW.minusSeconds(3600)).evidenceRef("restricted:clue_evidence_0001")
                        .evidenceSha256("c".repeat(64)).build()).build());
        assertThat(recorded.getStatus()).isEqualTo("OBSERVED");

        OperationsIntelligenceResult reviewed = service.execute(base(
                OperationsIntelligenceOperation.REVIEW_CLUE, "clue-review-1")
                .clue(OperationsIntelligenceCommand.ClueDefinition.builder().clueId("clue-1")
                        .expectedVersion(1L).reviewerPrincipalId("principal-reviewer-1")
                        .reviewDecision("ACCEPT").reasonCode("EVIDENCE_CONFIRMED").build()).build());
        assertThat(reviewed.getStatus()).isEqualTo("ACCEPTED");
        assertThat(reviewed.getAggregateVersion()).isEqualTo(2L);
        verify(mapper).insertClueReview(argThat(row -> row.getDecision().equals("ACCEPT")
                && row.getReviewerPrincipalId().equals("principal-reviewer-1")));
        assertThat(lastEvent().getEventType()).isEqualTo("operations_intelligence.clue.reviewed");
    }

    @Test
    void acceptedClueCanDriveAuditedAlertLifecycleButNeverAnAutomaticEffect() {
        service.execute(observationCommand());
        clue.set(new Clue().setClueId("clue-accepted").setTenantId(17L).setObservationId("observation-1")
                .setClueType("FRAUD_PROMOTION").setSourceCode("DOUYIN").setStatus("ACCEPTED").setVersion(2L));
        OperationsIntelligenceResult opened = service.execute(base(
                OperationsIntelligenceOperation.OPEN_ALERT, "alert-open-1")
                .alert(alertInput("alert-1", 0L).sourceType("CLUE").sourceRef("clue-accepted").build()).build());
        assertThat(opened.getStatus()).isEqualTo("OPEN");
        service.execute(base(OperationsIntelligenceOperation.NOTICE_ALERT, "alert-notice-1")
                .alert(transitionInput("alert-1", 1L, "principal-notifier", "DELIVERED")).build());
        service.execute(base(OperationsIntelligenceOperation.CLAIM_ALERT, "alert-claim-1")
                .alert(transitionInput("alert-1", 2L, "principal-owner", "CLAIMED_FOR_REVIEW")).build());
        OperationsIntelligenceResult resolved = service.execute(base(
                OperationsIntelligenceOperation.RESOLVE_ALERT, "alert-resolve-1")
                .alert(transitionInput("alert-1", 3L, "principal-owner", "SOURCE_REMEDIATED")).build());
        assertThat(resolved.getStatus()).isEqualTo("RESOLVED");
        assertThat(resolved.getAggregateVersion()).isEqualTo(4L);
        verify(mapper, times(4)).insertAlertHistory(any(AlertHistory.class));
        AppendDomainEventCommand event = lastEvent();
        assertThat(event.getEventType()).isEqualTo("operations_intelligence.alert.status_changed");
        assertThat(event.getPayload()).containsEntry("current_status", "RESOLVED")
                .doesNotContainKeys("business_effect", "refund", "block", "task_status");
        assertThat(event.getHeaders()).containsEntry("automatic_enforcement", false);
    }

    @Test
    void rejectsRawContentUnreviewedClueAlertsAndInvalidStateShortcuts() {
        assertThatThrownBy(() -> service.execute(base(
                OperationsIntelligenceOperation.RECORD_OBSERVATION, "raw-observation")
                .observation(OperationsIntelligenceCommand.ObservationDefinition.builder()
                        .sourceSystem("YSHOPPING_INTELLIGENCE").sourceEventId("event-raw")
                        .observationType("THIRD_PARTY_CONTENT").subjectType("CONTENT").subjectRef("content-raw")
                        .evidenceRef("https://example.com/raw-message").contentSha256("a".repeat(64))
                        .observedAt(NOW.minusSeconds(1)).build()).build()))
                .hasMessageContaining("restricted or sha256 evidence reference");

        clue.set(new Clue().setClueId("clue-unreviewed").setTenantId(17L).setStatus("OBSERVED").setVersion(1L));
        assertThatThrownBy(() -> service.execute(base(
                OperationsIntelligenceOperation.OPEN_ALERT, "unreviewed-alert")
                .alert(alertInput("alert-unreviewed", 0L).sourceType("CLUE").sourceRef("clue-unreviewed").build())
                .build())).hasMessage("only a human-accepted clue can open an alert");

        alert.set(new Alert().setAlertId("alert-open").setTenantId(17L).setAlertCode("ALERT_001")
                .setSourceType("METADATA_TASK").setSourceRef("task-1").setSeverity("HIGH")
                .setCategory("TECHNICAL").setSubcategory("OFFLINE_TASK").setEvidenceRef("sha256:" + "d".repeat(64))
                .setTitleSha256("e".repeat(64)).setStatus("OPEN").setVersion(1L));
        assertThatThrownBy(() -> service.execute(base(
                OperationsIntelligenceOperation.RESOLVE_ALERT, "shortcut-resolve")
                .alert(transitionInput("alert-open", 1L, "principal-owner", "FIXED")).build()))
                .hasMessage("alert cannot transition from OPEN to RESOLVED");
    }

    @Test
    void rejectsObservationWhenTaxonomyReferenceIsNotEffective() {
        when(riskQueryApi.validateIntelligenceEventLevel(anyString(), anyLong(), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException(
                        "observation must reference the effective non-retired taxonomy version and level"));
        assertThatThrownBy(() -> service.execute(observationCommand()))
                .hasMessage("observation must reference the effective non-retired taxonomy version and level");
        verify(mapper, never()).insertObservation(any());
        verify(outboxAppender, never()).append(any());
    }

    private OperationsIntelligenceCommand observationCommand() {
        return base(OperationsIntelligenceOperation.RECORD_OBSERVATION, "observation-record-1")
                .observation(OperationsIntelligenceCommand.ObservationDefinition.builder()
                        .observationId("observation-1").sourceSystem("YSHOPPING_INTELLIGENCE")
                        .sourceEventId("snippet-775f28").observationType("THIRD_PARTY_CONTENT")
                        .taxonomyId("taxonomy-1").taxonomyDefinitionVersion(1L)
                        .eventCode("event_new_001").intelligenceLevelCode("B")
                        .subjectType("CONTENT").subjectRef("content-775f28")
                        .evidenceRef("restricted:observation_0001").contentSha256("a".repeat(64))
                        .observedAt(NOW.minusSeconds(30)).build()).build();
    }

    private OperationsIntelligenceCommand.OperationsIntelligenceCommandBuilder base(
            OperationsIntelligenceOperation operation, String idempotencyKey) {
        return OperationsIntelligenceCommand.builder().operation(operation).idempotencyKey(idempotencyKey)
                .runId("ops-int-run-1").correlationId("ops-int-correlation-1").occurredAt(NOW);
    }

    private OperationsIntelligenceCommand.AlertDefinition.AlertDefinitionBuilder alertInput(String id, Long version) {
        return OperationsIntelligenceCommand.AlertDefinition.builder().alertId(id).alertCode("ALERT_001")
                .sourceType("METADATA_TASK").sourceRef("task-1").severity("HIGH")
                .category("TECHNICAL").subcategory("OFFLINE_TASK")
                .evidenceRef("sha256:" + "d".repeat(64)).titleSha256("e".repeat(64))
                .expectedVersion(version).actorPrincipalId("principal-creator");
    }

    private OperationsIntelligenceCommand.AlertDefinition transitionInput(
            String id, Long version, String actor, String reason) {
        return OperationsIntelligenceCommand.AlertDefinition.builder().alertId(id).expectedVersion(version)
                .actorPrincipalId(actor).reasonCode(reason).build();
    }

    private Clue currentClue(String id) {
        return clue.get() != null && clue.get().getClueId().equals(id) ? clue.get() : null;
    }

    private Alert currentAlert(String id) {
        return alert.get() != null && alert.get().getAlertId().equals(id) ? alert.get() : null;
    }

    private AppendDomainEventCommand lastEvent() {
        ArgumentCaptor<AppendDomainEventCommand> captor = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outboxAppender, atLeastOnce()).append(captor.capture());
        return captor.getAllValues().get(captor.getAllValues().size() - 1);
    }
}
