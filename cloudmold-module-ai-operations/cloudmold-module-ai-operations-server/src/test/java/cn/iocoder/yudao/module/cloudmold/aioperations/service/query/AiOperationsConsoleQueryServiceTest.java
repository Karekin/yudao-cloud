package cn.iocoder.yudao.module.cloudmold.aioperations.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo.AiArtifactPageReqVO;
import cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo.AiObservationPageReqVO;
import cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo.AiWorkflowPageReqVO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Import(AiOperationsConsoleQueryService.class)
class AiOperationsConsoleQueryServiceTest extends BaseDbUnitTest {

    @Resource
    private AiOperationsConsoleQueryService service;

    @Resource
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        TenantContextHolder.setTenantId(1L);
        seedWorkflowFixture();
        seedRunDetailFixture();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldAggregateWorkflowPageWithCurrentVersionAndLastRun() {
        AiWorkflowPageReqVO request = new AiWorkflowPageReqVO();
        request.setPageNo(1);
        request.setPageSize(10);
        request.setApplicationCode(" OPS_APP ");

        PageResult<AiWorkflowPageItem> page = service.getWorkflowPage(request);

        assertThat(page.getTotal()).isEqualTo(2);
        AiWorkflowPageItem latest = page.getList().get(0);
        assertThat(latest.getApplicationId()).isEqualTo("app-1");
        assertThat(latest.getApplicationStatus()).isEqualTo("ACTIVE");
        assertThat(latest.getWorkflowId()).isEqualTo("workflow-1");
        assertThat(latest.getWorkflowVersionId()).isEqualTo("wfver-1-v2");
        assertThat(latest.getWorkflowVersion()).isEqualTo(2L);
        assertThat(latest.getRunCount()).isEqualTo(3L);
        assertThat(latest.getRunningRunCount()).isEqualTo(1L);
        assertThat(latest.getSucceededRunCount()).isEqualTo(1L);
        assertThat(latest.getFailedRunCount()).isEqualTo(1L);
        assertThat(latest.getCancelledRunCount()).isEqualTo(0L);
        assertThat(latest.getLastRunId()).isEqualTo("run-3");
        assertThat(latest.getLastRunStatus()).isEqualTo("RUNNING");
        assertThat(latest.getLastRunStartedAt()).isEqualTo(time("2026-07-20T11:00:00"));

        AiWorkflowPageItem noRuns = page.getList().get(1);
        assertThat(noRuns.getWorkflowId()).isEqualTo("workflow-2");
        assertThat(noRuns.getRunCount()).isZero();
        assertThat(noRuns.getLastRunId()).isNull();
    }

    @Test
    void shouldAssembleRunDetailWithDeterministicOrdering() {
        AiWorkflowRunDetailView detail = service.getRunDetail(" run-1 ");

        assertThat(detail.getRun().getRunId()).isEqualTo("run-1");
        assertThat(detail.getRun().getBusinessRef()).isEqualTo("ticket/T-100");
        assertThat(detail.getRun().getExpectedInvocationCount()).isEqualTo(3);
        assertThat(detail.getApplication().getApplicationCode()).isEqualTo("OPS_APP");
        assertThat(detail.getWorkflow().getWorkflowCode()).isEqualTo("RISK_REVIEW");
        assertThat(detail.getWorkflow().getWorkflowVersion()).isEqualTo(2L);
        assertThat(detail.getWorkflow().getDefinitionSha256()).isEqualTo("b".repeat(64));

        assertThat(detail.getInvocationAttempts()).extracting(AiObservationPageItem::getAttemptId)
                .containsExactly("attempt-2", "attempt-1", "attempt-3");
        assertThat(detail.getFeedbackArtifacts()).extracting(AiWorkflowRunFeedbackItem::getFeedbackId)
                .containsExactly("feedback-2", "feedback-1");
        assertThat(detail.getStatusHistory()).extracting(AiWorkflowRunStatusHistoryItem::getAggregateVersion)
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void shouldListOnlyEvidenceArtifactsForCurrentTenant() {
        AiArtifactPageReqVO request = new AiArtifactPageReqVO();
        request.setPageNo(1);
        request.setPageSize(10);
        request.setRunId(" run-1 ");
        request.setOutcomeCode("positive");

        PageResult<AiArtifactPageItem> page = service.getArtifactPage(request);

        assertThat(page.getTotal()).isEqualTo(1);
        AiArtifactPageItem item = page.getList().get(0);
        assertThat(item.getFeedbackId()).isEqualTo("feedback-1");
        assertThat(item.getEvidenceRef()).isEqualTo("restricted/feedback/F-100");
        assertThat(item.getApplicationCode()).isEqualTo("OPS_APP");
        assertThat(item.getWorkflowCode()).isEqualTo("RISK_REVIEW");
    }

    @Test
    void shouldListObservationsInReverseChronologicalOrderWithNormalizedFilters() {
        AiObservationPageReqVO request = new AiObservationPageReqVO();
        request.setPageNo(1);
        request.setPageSize(10);
        request.setOutcome(" failed ");
        request.setProviderCode(" openai ");

        PageResult<AiObservationPageItem> page = service.getObservationPage(request);

        assertThat(page.getTotal()).isEqualTo(3);
        assertThat(page.getList()).extracting(AiObservationPageItem::getAttemptId)
                .containsExactly("attempt-4", "attempt-3", "attempt-1");
        assertThat(page.getList().get(0).getErrorCode()).isEqualTo("MODEL_TIMEOUT");
    }

    private void seedWorkflowFixture() {
        insertApplication(1L, "app-1", "OPS_APP", "Operations App", "ACTIVE");
        insertWorkflow(1L, "workflow-1", "app-1", "RISK_REVIEW", 2L);
        insertWorkflowVersion(1L, "wfver-1-v1", "workflow-1", "app-1", 1L, "a".repeat(64),
                "2026-07-20T08:00:00");
        insertWorkflowVersion(1L, "wfver-1-v2", "workflow-1", "app-1", 2L, "b".repeat(64),
                "2026-07-20T09:00:00");
        insertWorkflow(1L, "workflow-2", "app-1", "SUMMARY", 1L);
        insertWorkflowVersion(1L, "wfver-2-v1", "workflow-2", "app-1", 1L, "c".repeat(64),
                "2026-07-19T09:00:00");

        insertRun(1L, "run-2", "run-key-2", "app-1", "workflow-1", "wfver-1-v2", 2L, "SUCCEEDED", null,
                "2026-07-20T09:00:00", "2026-07-20T09:15:00", 1);
        insertRun(1L, "run-1", "run-key-1", "app-1", "workflow-1", "wfver-1-v2", 2L, "FAILED",
                "MODEL_TIMEOUT", "2026-07-20T10:00:00", "2026-07-20T10:15:00", 3);
        insertRun(1L, "run-3", "run-key-3", "app-1", "workflow-1", "wfver-1-v2", 2L, "RUNNING", null,
                "2026-07-20T11:00:00", null, 2);

        insertApplication(2L, "app-2", "OPS_APP", "Other Tenant App", "ACTIVE");
        insertWorkflow(2L, "workflow-1", "app-2", "RISK_REVIEW", 1L);
        insertWorkflowVersion(2L, "wfver-other", "workflow-1", "app-2", 1L, "d".repeat(64),
                "2026-07-20T08:00:00");
        insertRun(2L, "other-tenant-run", "run-key-other", "app-2", "workflow-1", "wfver-other", 1L,
                "SUCCEEDED", null, "2026-07-20T12:00:00", "2026-07-20T12:05:00", 1);
    }

    private void seedRunDetailFixture() {
        insertAttempt(1L, "attempt-1", "attempt-key-1", "run-1", "app-1", "workflow-1", "step-b", 2,
                "OPENAI", "gpt-5.4", "FAILED", 10, 0, 5, 15, 1000L, 2L, "CNY",
                "pricing/openai-2026-07", "MODEL_TIMEOUT", "2026-07-20T10:02:00");
        insertAttempt(1L, "attempt-2", "attempt-key-2", "run-1", "app-1", "workflow-1", "step-a", 1,
                "OPENAI", "gpt-5.4", "SUCCEEDED", 20, 5, 6, 26, 800L, 1L, "CNY",
                "pricing/openai-2026-07", null, "2026-07-20T10:01:00");
        insertAttempt(1L, "attempt-3", "attempt-key-3", "run-1", "app-1", "workflow-1", "step-b", 3,
                "OPENAI", "gpt-5.4", "FAILED", 10, 0, 5, 15, 1100L, 2L, "CNY",
                "pricing/openai-2026-07", "MODEL_TIMEOUT", "2026-07-20T10:03:00");
        insertAttempt(1L, "attempt-4", "attempt-key-4", "run-3", "app-1", "workflow-1", "step-c", 1,
                "OPENAI", "gpt-5.4", "FAILED", 8, 0, 2, 10, 900L, 1L, "CNY",
                "pricing/openai-2026-07", "MODEL_TIMEOUT", "2026-07-20T11:01:00");
        insertAttempt(2L, "attempt-other", "attempt-key-other", "other-tenant-run", "app-2", "workflow-1",
                "step-x", 1, "OPENAI", "gpt-5.4", "FAILED", 1, 0, 1, 2, 1L, 1L, "CNY",
                "pricing/openai-2026-07", "MODEL_TIMEOUT", "2026-07-20T12:00:00");

        insertFeedback(1L, "feedback-1", "feedback-key-1", "run-1", "QUALITY", "POSITIVE", "HUMAN",
                "restricted/feedback/F-100", "2026-07-20T10:05:00");
        insertFeedback(1L, "feedback-2", "feedback-key-2", "run-1", "QUALITY", "NEGATIVE", "AUTOMATED",
                null, "2026-07-20T10:04:00");
        insertFeedback(2L, "feedback-other", "feedback-key-other", "other-tenant-run", "QUALITY", "POSITIVE",
                "HUMAN", "restricted/feedback/F-200", "2026-07-20T12:06:00");

        insertHistory(1L, "history-3", "run-1", 3L, "RECORD_MODEL_INVOCATION", "RUNNING", "FAILED",
                "MODEL_TIMEOUT", "2026-07-20T10:15:00");
        insertHistory(1L, "history-1", "run-1", 1L, "START_WORKFLOW_RUN", null, "RUNNING", null,
                "2026-07-20T10:00:00");
        insertHistory(1L, "history-2", "run-1", 2L, "RECORD_MODEL_INVOCATION", "RUNNING", "RUNNING", null,
                "2026-07-20T10:03:00");
    }

    private void insertApplication(Long tenantId, String applicationId, String applicationCode, String name, String status) {
        jdbcTemplate.update("""
                INSERT INTO cloudmold_ai_ops_application (
                    application_id, tenant_id, application_code, name, status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 1, ?, ?)
                """, applicationId, tenantId, applicationCode, name, status,
                timestamp("2026-07-20T08:00:00"), timestamp("2026-07-20T08:00:00"));
    }

    private void insertWorkflow(Long tenantId, String workflowId, String applicationId, String workflowCode,
                                Long currentVersion) {
        jdbcTemplate.update("""
                INSERT INTO cloudmold_ai_ops_workflow_definition (
                    workflow_id, tenant_id, application_id, workflow_code, current_version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, workflowId, tenantId, applicationId, workflowCode, currentVersion,
                timestamp("2026-07-20T08:00:00"), timestamp("2026-07-20T08:00:00"));
    }

    private void insertWorkflowVersion(Long tenantId, String workflowVersionId, String workflowId, String applicationId,
                                       Long workflowVersion, String definitionSha, String publishedAt) {
        jdbcTemplate.update("""
                INSERT INTO cloudmold_ai_ops_workflow_version (
                    workflow_version_id, tenant_id, workflow_id, application_id, workflow_version,
                    definition_ref, definition_sha256, published_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, workflowVersionId, tenantId, workflowId, applicationId, workflowVersion,
                "restricted/workflows/" + workflowId + "/v" + workflowVersion, definitionSha,
                timestamp(publishedAt), timestamp(publishedAt));
    }

    private void insertRun(Long tenantId, String runId, String runKey, String applicationId, String workflowId,
                           String workflowVersionId, Long workflowVersion, String status, String errorCode,
                           String startedAt, String finishedAt, int expectedInvocationCount) {
        jdbcTemplate.update("""
                INSERT INTO cloudmold_ai_ops_workflow_run (
                    run_id, tenant_id, run_key, application_id, workflow_id, workflow_version_id,
                    workflow_version, trigger_type, business_ref, status, expected_invocation_count,
                    started_at, finished_at, error_code, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'API', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, runId, tenantId, runKey, applicationId, workflowId, workflowVersionId, workflowVersion,
                "ticket/T-100", status, expectedInvocationCount, timestamp(startedAt), nullableTimestamp(finishedAt),
                errorCode, status.equals("RUNNING") ? 1L : 3L, timestamp(startedAt),
                nullableTimestamp(finishedAt) != null ? nullableTimestamp(finishedAt) : timestamp(startedAt));
    }

    private void insertAttempt(Long tenantId, String attemptId, String attemptKey, String runId, String applicationId,
                               String workflowId, String stepRef, int attemptNo, String providerCode,
                               String modelCode, String outcome, long inputTokens, long cachedInputTokens,
                               long outputTokens, long totalTokens, Long latencyMillis, Long costAmountMinor,
                               String currencyCode, String pricingVersionRef, String errorCode, String occurredAt) {
        jdbcTemplate.update("""
                INSERT INTO cloudmold_ai_ops_invocation_attempt (
                    attempt_id, tenant_id, attempt_key, run_id, application_id, workflow_id, step_ref, attempt_no,
                    provider_code, model_code, provider_request_ref, outcome, input_tokens, cached_input_tokens,
                    output_tokens, total_tokens, latency_millis, cost_amount_minor, currency_code,
                    pricing_version_ref, error_code, occurred_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, attemptId, tenantId, attemptKey, runId, applicationId, workflowId, stepRef, attemptNo,
                providerCode, modelCode, "provider/" + attemptId, outcome, inputTokens, cachedInputTokens,
                outputTokens, totalTokens, latencyMillis, costAmountMinor, currencyCode, pricingVersionRef,
                errorCode, timestamp(occurredAt), timestamp(occurredAt));
    }

    private void insertFeedback(Long tenantId, String feedbackId, String feedbackKey, String runId, String feedbackType,
                                String outcomeCode, String evaluatorType, String evidenceRef, String occurredAt) {
        jdbcTemplate.update("""
                INSERT INTO cloudmold_ai_ops_outcome_feedback (
                    feedback_id, tenant_id, feedback_key, run_id, feedback_type, outcome_code, evaluator_type,
                    evidence_ref, occurred_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, feedbackId, tenantId, feedbackKey, runId, feedbackType, outcomeCode, evaluatorType,
                evidenceRef, timestamp(occurredAt), timestamp(occurredAt));
    }

    private void insertHistory(Long tenantId, String historyId, String aggregateId, Long aggregateVersion,
                               String operationType, String previousStatus, String currentStatus, String errorCode,
                               String occurredAt) {
        jdbcTemplate.update("""
                INSERT INTO cloudmold_ai_ops_status_history (
                    history_id, tenant_id, aggregate_type, aggregate_id, aggregate_version, operation_id,
                    operation_type, previous_status, current_status, error_code, occurred_at, created_at
                ) VALUES (?, ?, 'ai_workflow_run', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, historyId, tenantId, aggregateId, aggregateVersion, aggregateVersion, operationType,
                previousStatus, currentStatus, errorCode, timestamp(occurredAt), timestamp(occurredAt));
    }

    private static LocalDateTime time(String value) {
        return LocalDateTime.parse(value);
    }

    private static Timestamp timestamp(String value) {
        return Timestamp.valueOf(time(value));
    }

    private static Timestamp nullableTimestamp(String value) {
        return value == null ? null : timestamp(value);
    }
}
