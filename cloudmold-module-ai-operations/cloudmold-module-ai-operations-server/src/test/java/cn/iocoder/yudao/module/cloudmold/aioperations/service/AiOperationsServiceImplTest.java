package cn.iocoder.yudao.module.cloudmold.aioperations.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.aioperations.api.*;
import cn.iocoder.yudao.module.cloudmold.aioperations.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.aioperations.dal.mysql.*;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiOperationsServiceImplTest {

    private final AiOperationsOperationMapper operationMapper = mock(AiOperationsOperationMapper.class);
    private final AiApplicationMapper applicationMapper = mock(AiApplicationMapper.class);
    private final AiWorkflowDefinitionMapper workflowDefinitionMapper = mock(AiWorkflowDefinitionMapper.class);
    private final AiWorkflowVersionMapper workflowVersionMapper = mock(AiWorkflowVersionMapper.class);
    private final AiWorkflowRunMapper workflowRunMapper = mock(AiWorkflowRunMapper.class);
    private final AiInvocationAttemptMapper invocationAttemptMapper = mock(AiInvocationAttemptMapper.class);
    private final AiOutcomeFeedbackMapper outcomeFeedbackMapper = mock(AiOutcomeFeedbackMapper.class);
    private final AiStatusHistoryMapper statusHistoryMapper = mock(AiStatusHistoryMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final AiOperationsServiceImpl service = new AiOperationsServiceImpl(operationMapper, applicationMapper,
            workflowDefinitionMapper, workflowVersionMapper, workflowRunMapper, invocationAttemptMapper,
            outcomeFeedbackMapper, statusHistoryMapper, outboxAppender);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(17L);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult(
                "11111111-1111-4111-8111-111111111111", "a".repeat(64), false));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void registersTenantScopedApplicationWithExactEventAndHistory() {
        prepareNewOperation(AiOperationsOperation.REGISTER_APPLICATION);

        AiOperationsCommandResult result = service.execute(envelope(AiOperationsOperation.REGISTER_APPLICATION)
                .application(AiOperationsCommand.ApplicationDefinition.builder()
                        .applicationCode("INTELLIGENCE_ASSISTANT").name("Intelligence Assistant").build()).build());

        assertThat(result.getAggregateType()).isEqualTo("ai_application");
        assertThat(result.getStatus()).isEqualTo("DRAFT");
        verify(applicationMapper).insert(argThat((AiApplicationDO row) -> row.getTenantId().equals(17L)
                && row.getApplicationCode().equals("INTELLIGENCE_ASSISTANT") && row.getVersion().equals(1L)));
        verify(statusHistoryMapper).insert(argThat((AiStatusHistoryDO row) -> row.getTenantId().equals(17L)
                && row.getAggregateType().equals("ai_application") && row.getAggregateVersion().equals(1L)
                && row.getPreviousStatus() == null && row.getCurrentStatus().equals("DRAFT")));
        AppendDomainEventCommand event = captureEvent();
        assertEvent(event, "ai.application.status_changed", "ai_application", 1L);
        assertThat(event.getPayload()).containsKeys("application_id")
                .containsEntry("application_code", "INTELLIGENCE_ASSISTANT")
                .containsEntry("name", "Intelligence Assistant")
                .containsEntry("current_status", "DRAFT")
                .containsEntry("operation", "REGISTER_APPLICATION")
                .doesNotContainKey("previous_status");
    }

    @Test
    void replaysImmutableResultForSameTenantIdempotencyKey() {
        AiOperationsCommand command = envelope(AiOperationsOperation.REGISTER_APPLICATION)
                .application(AiOperationsCommand.ApplicationDefinition.builder()
                        .applicationCode("OPS_APP").name("Operations App").build()).build();
        AiOperationsCommandResult first = AiOperationsCommandResult.builder().operationId(42L)
                .aggregateType("ai_application").aggregateId("application-existing")
                .aggregateVersion(1L).status("DRAFT").build();
        when(operationMapper.selectLastInsertId()).thenReturn(42L);
        when(operationMapper.selectForUpdate(42L, 17L)).thenReturn(new AiOperationsOperationDO()
                .setOperationId(42L).setAttemptToken("original-attempt")
                .setRequestHash(AiOperationsServiceImpl.fingerprint(17L, command)).setStatus(10)
                .setResultJson(JsonUtils.toJsonString(first)));

        AiOperationsCommandResult replay = service.execute(command);

        assertThat(replay.isDuplicate()).isTrue();
        assertThat(replay.getAggregateId()).isEqualTo("application-existing");
        verifyNoInteractions(applicationMapper, workflowDefinitionMapper, workflowVersionMapper, workflowRunMapper,
                invocationAttemptMapper, outcomeFeedbackMapper, statusHistoryMapper, outboxAppender);
    }

    @Test
    void publishesImmutableWorkflowVersionWithDefinitionCasContract() {
        prepareNewOperation(AiOperationsOperation.PUBLISH_WORKFLOW_VERSION);
        when(applicationMapper.selectForUpdate(17L, "application-1")).thenReturn(new AiApplicationDO()
                .setApplicationId("application-1").setStatus("ACTIVE").setVersion(2L));

        AiOperationsCommandResult result = service.execute(envelope(AiOperationsOperation.PUBLISH_WORKFLOW_VERSION)
                .workflowVersion(AiOperationsCommand.WorkflowVersionDefinition.builder()
                        .workflowId("workflow-1").workflowCode("RISK_REVIEW")
                        .applicationId("application-1").workflowVersion(1L)
                        .definitionRef("restricted/workflows/risk-review/v1")
                        .definitionSha256("a".repeat(64)).build()).build());

        assertThat(result.getAggregateType()).isEqualTo("ai_workflow");
        assertThat(result.getAggregateVersion()).isEqualTo(1L);
        verify(workflowDefinitionMapper).insert(argThat((AiWorkflowDefinitionDO row) ->
                row.getTenantId().equals(17L) && row.getApplicationId().equals("application-1")
                        && row.getCurrentVersion().equals(1L)));
        verify(workflowVersionMapper).insert(argThat((AiWorkflowVersionDO row) ->
                row.getTenantId().equals(17L) && row.getWorkflowVersion().equals(1L)
                        && row.getDefinitionSha256().equals("a".repeat(64))));
        AppendDomainEventCommand event = captureEvent();
        assertEvent(event, "ai.workflow.version.published", "ai_workflow", 1L);
        assertThat(event.getPayload().keySet()).containsExactlyInAnyOrder("workflow_id", "workflow_code",
                "application_id", "workflow_version", "definition_sha256", "published_at", "operation");
        assertThat(event.getPayload()).doesNotContainKeys("definition_ref", "graph");
    }

    @Test
    void startsRunAndPublishesRequiredRunFields() {
        prepareNewOperation(AiOperationsOperation.START_WORKFLOW_RUN);
        when(applicationMapper.selectForUpdate(17L, "application-1")).thenReturn(new AiApplicationDO()
                .setApplicationId("application-1").setStatus("ACTIVE"));
        when(workflowDefinitionMapper.selectForUpdate(17L, "workflow-1")).thenReturn(new AiWorkflowDefinitionDO()
                .setWorkflowId("workflow-1").setApplicationId("application-1").setCurrentVersion(1L));
        when(workflowVersionMapper.selectByVersion(17L, "workflow-1", 1L)).thenReturn(new AiWorkflowVersionDO()
                .setWorkflowVersionId("workflow-version-1").setWorkflowId("workflow-1").setWorkflowVersion(1L));

        AiOperationsCommandResult result = service.execute(envelope(AiOperationsOperation.START_WORKFLOW_RUN)
                .workflowRun(AiOperationsCommand.WorkflowRunDefinition.builder().runKey("source/run-100")
                        .applicationId("application-1").workflowId("workflow-1").workflowVersion(1L)
                        .triggerType("API").businessRef("ticket/T-100").expectedInvocationCount(1).build()).build());

        assertThat(result.getStatus()).isEqualTo("RUNNING");
        verify(workflowRunMapper).insert(argThat((AiWorkflowRunDO row) -> row.getTenantId().equals(17L)
                && row.getExpectedInvocationCount() == 1 && row.getVersion().equals(1L)));
        AppendDomainEventCommand event = captureEvent();
        assertEvent(event, "ai.workflow.run.status_changed", "ai_workflow_run", 1L);
        assertThat(event.getPayload()).containsEntry("expected_invocation_count", 1)
                .containsEntry("current_status", "RUNNING").containsKey("started_at")
                .doesNotContainKeys("finished_at", "error_code", "business_ref");
    }

    @Test
    void completesRunOnlyAtExpectedVersionAndInvocationDenominator() {
        prepareNewOperation(AiOperationsOperation.COMPLETE_WORKFLOW_RUN);
        LocalDateTime started = LocalDateTime.parse("2026-07-16T00:00:00");
        when(workflowRunMapper.selectForUpdate(17L, "run-1")).thenReturn(new AiWorkflowRunDO()
                .setRunId("run-1").setApplicationId("application-1").setWorkflowId("workflow-1")
                .setWorkflowVersion(1L).setTriggerType("API").setStatus("RUNNING")
                .setExpectedInvocationCount(1).setStartedAt(started).setVersion(1L));
        when(invocationAttemptMapper.countByRun(17L, "run-1")).thenReturn(1);
        when(workflowRunMapper.updateStatusCas(eq(17L), eq("run-1"), eq(1L), eq("SUCCEEDED"),
                any(LocalDateTime.class), isNull(), any(LocalDateTime.class))).thenReturn(1);

        AiOperationsCommandResult result = service.execute(envelope(AiOperationsOperation.COMPLETE_WORKFLOW_RUN)
                .workflowRun(AiOperationsCommand.WorkflowRunDefinition.builder()
                        .runId("run-1").expectedVersion(1L).build()).build());

        assertThat(result.getAggregateVersion()).isEqualTo(2L);
        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        verify(statusHistoryMapper).insert(argThat((AiStatusHistoryDO row) ->
                row.getPreviousStatus().equals("RUNNING") && row.getCurrentStatus().equals("SUCCEEDED")
                        && row.getAggregateVersion().equals(2L)));
    }

    @Test
    void recordsConservedTokensAndEvidenceBackedCostWithoutRawContent() {
        prepareNewOperation(AiOperationsOperation.RECORD_MODEL_INVOCATION);
        when(workflowRunMapper.selectForUpdate(17L, "run-1")).thenReturn(new AiWorkflowRunDO()
                .setRunId("run-1").setApplicationId("application-1").setWorkflowId("workflow-1")
                .setStatus("RUNNING").setExpectedInvocationCount(1));

        AiOperationsCommandResult result = service.execute(envelope(AiOperationsOperation.RECORD_MODEL_INVOCATION)
                .invocationAttempt(AiOperationsCommand.InvocationAttemptDefinition.builder()
                        .attemptKey("provider/request-1").runId("run-1").stepRef("llm-node-1").attemptNo(1)
                        .providerCode("OPENAI").modelCode("gpt-5.4").providerRequestRef("req/request-1")
                        .outcome("SUCCEEDED").inputTokens(800L).cachedInputTokens(200L)
                        .outputTokens(200L).totalTokens(1000L).latencyMillis(1250L)
                        .costAmountMinor(3L).currencyCode("CNY").pricingVersionRef("pricing/openai-2026-07")
                        .build()).build());

        assertThat(result.getAggregateType()).isEqualTo("ai_model_invocation");
        verify(invocationAttemptMapper).insert(argThat((AiInvocationAttemptDO row) -> row.getTenantId().equals(17L)
                && row.getTotalTokens().equals(row.getInputTokens() + row.getOutputTokens())
                && row.getCachedInputTokens() <= row.getInputTokens() && row.getCostAmountMinor().equals(3L)));
        AppendDomainEventCommand event = captureEvent();
        assertEvent(event, "ai.model.invocation.recorded", "ai_model_invocation", 1L);
        assertThat(event.getPayload().keySet()).containsExactlyInAnyOrder("attempt_id", "run_id", "application_id",
                "workflow_id", "provider_code", "model_code", "attempt_no", "outcome", "input_tokens",
                "cached_input_tokens", "output_tokens", "total_tokens", "latency_millis", "cost_amount_minor",
                "currency_code", "pricing_version_ref", "occurred_at");
        assertThat(event.getPayload().keySet()).noneMatch(AiOperationsServiceImplTest::isForbiddenFieldName);
    }

    @Test
    void rejectsTokenConservationViolationBeforeAppend() {
        prepareNewOperation(AiOperationsOperation.RECORD_MODEL_INVOCATION);
        AiOperationsCommand command = envelope(AiOperationsOperation.RECORD_MODEL_INVOCATION)
                .invocationAttempt(AiOperationsCommand.InvocationAttemptDefinition.builder()
                        .attemptKey("provider/request-2").runId("run-1").stepRef("llm-node-1").attemptNo(1)
                        .providerCode("OPENAI").modelCode("gpt-5.4").outcome("SUCCEEDED")
                        .inputTokens(800L).cachedInputTokens(200L).outputTokens(200L).totalTokens(999L)
                        .latencyMillis(1L).build()).build();

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("totalTokens must equal inputTokens plus outputTokens");
        verify(invocationAttemptMapper, never()).insert(any(AiInvocationAttemptDO.class));
        verifyNoInteractions(outboxAppender);
    }

    @Test
    void rejectsInvocationBeyondFrozenDenominator() {
        prepareNewOperation(AiOperationsOperation.RECORD_MODEL_INVOCATION);
        when(workflowRunMapper.selectForUpdate(17L, "run-1")).thenReturn(new AiWorkflowRunDO()
                .setRunId("run-1").setApplicationId("application-1").setWorkflowId("workflow-1")
                .setStatus("RUNNING").setExpectedInvocationCount(1));
        when(invocationAttemptMapper.countByRun(17L, "run-1")).thenReturn(1);
        AiOperationsCommand command = envelope(AiOperationsOperation.RECORD_MODEL_INVOCATION)
                .invocationAttempt(AiOperationsCommand.InvocationAttemptDefinition.builder()
                        .attemptKey("provider/request-overflow").runId("run-1").stepRef("llm-node-2").attemptNo(1)
                        .providerCode("OPENAI").modelCode("gpt-5.4").outcome("SUCCEEDED")
                        .inputTokens(1L).cachedInputTokens(0L).outputTokens(1L).totalTokens(2L)
                        .latencyMillis(1L).build()).build();

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invocation would exceed expectedInvocationCount");
        verify(invocationAttemptMapper, never()).insert(any(AiInvocationAttemptDO.class));
        verifyNoInteractions(outboxAppender);
    }

    @Test
    void recordsFeedbackWithOnlyGovernedDimensions() {
        prepareNewOperation(AiOperationsOperation.RECORD_OUTCOME_FEEDBACK);
        when(workflowRunMapper.selectForUpdate(17L, "run-1")).thenReturn(new AiWorkflowRunDO()
                .setRunId("run-1").setStatus("SUCCEEDED"));

        service.execute(envelope(AiOperationsOperation.RECORD_OUTCOME_FEEDBACK)
                .outcomeFeedback(AiOperationsCommand.OutcomeFeedbackDefinition.builder()
                        .feedbackKey("quality/T-100/v1").runId("run-1").feedbackType("QUALITY")
                        .outcomeCode("POSITIVE").evaluatorType("HUMAN")
                        .evidenceRef("restricted/feedback/F-100").build()).build());

        AppendDomainEventCommand event = captureEvent();
        assertEvent(event, "ai.outcome.feedback.recorded", "ai_outcome_feedback", 1L);
        assertThat(event.getPayload().keySet()).containsExactlyInAnyOrder("feedback_id", "run_id",
                "feedback_type", "outcome_code", "evaluator_type", "occurred_at");
        assertThat(event.getPayload()).doesNotContainKey("evidence_ref");
    }

    @Test
    void rejectsPiiAndApiSurfaceHasNoRawOrSecretFields() {
        prepareNewOperation(AiOperationsOperation.REGISTER_APPLICATION);
        AiOperationsCommand command = envelope(AiOperationsOperation.REGISTER_APPLICATION)
                .application(AiOperationsCommand.ApplicationDefinition.builder()
                        .applicationCode("PII_APP").name("owner@example.com").build()).build();

        assertThatThrownBy(() -> service.execute(command)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("application name contains forbidden sensitive material");
        verify(applicationMapper, never()).insert(any(AiApplicationDO.class));
        verifyNoInteractions(outboxAppender);

        Set<String> fieldNames = Stream.concat(Stream.of(AiOperationsCommand.class),
                        Arrays.stream(AiOperationsCommand.class.getDeclaredClasses()))
                .flatMap(type -> Arrays.stream(type.getDeclaredFields())).map(Field::getName)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(fieldNames).noneMatch(AiOperationsServiceImplTest::isForbiddenFieldName);
    }

    @Test
    void rejectsApplicationCasVersionMismatchBeforeMutation() {
        prepareNewOperation(AiOperationsOperation.ACTIVATE_APPLICATION);
        when(applicationMapper.selectForUpdate(17L, "application-1")).thenReturn(new AiApplicationDO()
                .setApplicationId("application-1").setStatus("DRAFT").setVersion(2L));

        assertThatThrownBy(() -> service.execute(envelope(AiOperationsOperation.ACTIVATE_APPLICATION)
                .application(AiOperationsCommand.ApplicationDefinition.builder()
                        .applicationId("application-1").expectedVersion(1L).build()).build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("aggregate version conflict");
        verify(applicationMapper, never()).updateStatusCas(anyLong(), anyString(), anyLong(), anyString(), any());
        verifyNoInteractions(outboxAppender);
    }

    private AppendDomainEventCommand captureEvent() {
        ArgumentCaptor<AppendDomainEventCommand> event = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outboxAppender).append(event.capture());
        return event.getValue();
    }

    private static void assertEvent(AppendDomainEventCommand event, String type, String aggregateType,
                                    Long aggregateVersion) {
        assertThat(event.getEventType()).isEqualTo(type);
        assertThat(event.getSchemaVersion()).isEqualTo(1);
        assertThat(event.getSourceSystem()).isEqualTo("cloudmold-ai-operations");
        assertThat(event.getTenantId()).isEqualTo(17L);
        assertThat(event.getAggregateType()).isEqualTo(aggregateType);
        assertThat(event.getAggregateVersion()).isEqualTo(aggregateVersion);
        assertThat(event.getDestination()).isEqualTo("lakehouse");
    }

    private void prepareNewOperation(AiOperationsOperation operation) {
        AtomicReference<String> attemptToken = new AtomicReference<>();
        when(operationMapper.insertOrResolve(eq(17L), anyString(), eq(operation.name()), anyString(), anyString(),
                any(LocalDateTime.class))).thenAnswer(invocation -> {
            attemptToken.set(invocation.getArgument(4));
            return 1;
        });
        when(operationMapper.selectLastInsertId()).thenReturn(42L);
        when(operationMapper.selectForUpdate(42L, 17L)).thenAnswer(ignored -> new AiOperationsOperationDO()
                .setOperationId(42L).setTenantId(17L).setAttemptToken(attemptToken.get()).setStatus(0));
        when(operationMapper.markSucceeded(eq(42L), eq(17L), anyString(), anyString(), anyString(),
                any(LocalDateTime.class))).thenReturn(1);
    }

    private static AiOperationsCommand.AiOperationsCommandBuilder envelope(AiOperationsOperation operation) {
        return AiOperationsCommand.builder().operation(operation)
                .idempotencyKey("ai-ops/" + operation.name().toLowerCase(Locale.ROOT))
                .correlationId("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
                .occurredAt(Instant.parse("2026-07-16T01:00:00Z"));
    }

    private static boolean isForbiddenFieldName(String field) {
        String normalized = field.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
        return Set.of("prompt", "response", "graph", "api_key", "email", "ip", "ip_address",
                "error_stack", "stack_trace", "password", "authorization").contains(normalized);
    }
}
