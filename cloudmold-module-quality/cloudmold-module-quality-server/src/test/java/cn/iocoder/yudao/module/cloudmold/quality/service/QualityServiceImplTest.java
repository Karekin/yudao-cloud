package cn.iocoder.yudao.module.cloudmold.quality.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.quality.api.*;
import cn.iocoder.yudao.module.cloudmold.quality.dal.dataobject.QualityRecords.*;
import cn.iocoder.yudao.module.cloudmold.quality.dal.mysql.QualityMapper;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QualityServiceImplTest {
    private final QualityMapper mapper = mock(QualityMapper.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final QualityServiceImpl service = new QualityServiceImpl(mapper, outbox);
    private final AtomicReference<String> requestHash = new AtomicReference<>();
    private final AtomicReference<String> attemptToken = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(23L);
        when(mapper.insertOrResolveOperation(eq(23L), anyString(), anyString(), anyString(),
                anyString(), any())).thenAnswer(invocation -> {
                    requestHash.set(invocation.getArgument(3));
                    attemptToken.set(invocation.getArgument(4));
                    return 1;
                });
        when(mapper.selectLastInsertId()).thenReturn(201L);
        when(mapper.selectOperationForUpdate(201L, 23L)).thenAnswer(invocation ->
                new Operation().setOperationId(201L).setTenantId(23L)
                        .setRequestHash(requestHash.get()).setAttemptToken(attemptToken.get()).setStatus(0));
        when(mapper.insertStandard(any())).thenReturn(1);
        when(mapper.insertStandardVersion(any())).thenReturn(1);
        when(mapper.publishStandard(anyLong(), anyString(), anyLong(), any())).thenReturn(1);
        when(mapper.insertTaskHistory(any())).thenReturn(1);
        when(mapper.markOperationSucceeded(eq(201L), eq(23L), anyString(), anyString(),
                anyString(), any())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createsTenantScopedDraftStandardAndEmitsEvent() {
        QualityResult result = service.execute(base(QualityOperation.CREATE_STANDARD)
                .standard(QualityCommand.StandardDefinition.builder()
                        .standardCode("AUTH-SHOE-001").categoryCode("SHOES")
                        .brandCode("BRAND_A").contentSha256("a".repeat(64)).build())
                .build());

        assertThat(result.getStatus()).isEqualTo("DRAFT");
        assertThat(result.getAggregateVersion()).isEqualTo(1L);
        verify(mapper).insertStandard(argThat(row -> row.getTenantId().equals(23L)
                && row.getCurrentVersion().equals(0L)
                && row.getAggregateVersion().equals(1L)
                && row.getDraftContentSha256().equals("a".repeat(64))));
        verify(outbox).append(argThat(event -> event.getEventType().equals("quality.standard.created")
                && event.getTenantId().equals(23L)));
    }

    @Test
    void publishesImmutableStandardVersionBeforeAdvancingHead() {
        when(mapper.selectStandardForUpdate(23L, "standard-01")).thenReturn(new Standard()
                .setStandardId("standard-01").setTenantId(23L).setStandardCode("AUTH-SHOE-001")
                .setDraftContentSha256("b".repeat(64)).setStatus("DRAFT")
                .setCurrentVersion(0L).setAggregateVersion(1L));
        QualityResult result = service.execute(base(QualityOperation.PUBLISH_STANDARD)
                .standard(QualityCommand.StandardDefinition.builder()
                        .standardId("standard-01").expectedVersion(1L)
                        .approverPrincipalId("principal-reviewer-01").build())
                .build());

        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
        assertThat(result.getAggregateVersion()).isEqualTo(2L);
        verify(mapper).insertStandardVersion(argThat(version ->
                version.getStandardId().equals("standard-01")
                        && version.getStandardVersion().equals(1L)
                        && version.getContentSha256().equals("b".repeat(64))));
        verify(mapper).publishStandard(23L, "standard-01", 1L, java.time.LocalDateTime.of(
                2026, 7, 25, 0, 0));
    }

    @Test
    void refusesAssignmentWhenAuthenticatorCertificationIsNotActive() {
        when(mapper.selectInspectionTaskForUpdate(23L, "task-01")).thenReturn(new InspectionTask()
                .setTaskId("task-01").setTenantId(23L).setStandardId("standard-01")
                .setStatus("CREATED").setVersion(1L));
        QualityCommand command = base(QualityOperation.ASSIGN_INSPECTION_TASK)
                .inspectionTask(QualityCommand.InspectionTaskDefinition.builder()
                        .taskId("task-01").expectedVersion(1L)
                        .authenticatorPrincipalId("principal-auth-01").build())
                .build();

        assertThatThrownBy(() -> service.execute(command))
                .hasMessage("authenticator has no active certification for this standard");
        verify(mapper, never()).transitionInspectionTask(anyLong(), anyString(), anyLong(),
                anyString(), anyString(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any());
        verifyNoInteractions(outbox);
    }

    @Test
    void rejectsInvalidCorrelationIdAtCommandBoundary() {
        QualityCommand command = base(QualityOperation.CREATE_STANDARD)
                .correlationId("not-a-uuid").build();

        assertThatThrownBy(() -> service.execute(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("correlationId must be a UUID");
        verify(mapper, never()).insertOrResolveOperation(anyLong(), anyString(), anyString(),
                anyString(), anyString(), any());
        verifyNoInteractions(outbox);
    }

    @Test
    void refusesToAssignPrimaryReviewerAsIndependentRechecker() {
        when(mapper.selectInspectionTaskForUpdate(23L, "task-01")).thenReturn(
                decidedTask().setAuthenticatorPrincipalId("principal-auth-01"));

        QualityCommand command = base(QualityOperation.ASSIGN_RECHECK_REVIEWER)
                .inspectionTask(QualityCommand.InspectionTaskDefinition.builder()
                        .taskId("task-01").expectedVersion(4L)
                        .secondaryAuthenticatorPrincipalId("principal-auth-01")
                        .recheckReasonCode("RISK_SAMPLE").build())
                .build();

        assertThatThrownBy(() -> service.execute(command))
                .hasMessage("secondary reviewer must be independent from the primary reviewer");
        verify(mapper, never()).assignRecheckReviewer(anyLong(), anyString(), anyLong(),
                anyString(), anyString(), any());
        verifyNoInteractions(outbox);
    }

    @Test
    void routesDisagreeingIndependentReviewToThirdPartyAdjudication() {
        when(mapper.selectInspectionTaskForUpdate(23L, "task-01")).thenReturn(
                decidedTask().setStatus("RECHECK_REQUIRED")
                        .setSecondaryAuthenticatorPrincipalId("principal-auth-02"));
        when(mapper.submitRecheckDecision(eq(23L), eq("task-01"), eq(4L),
                eq("CONFLICTED"), eq("FAIL"), eq("COUNTERFEIT_MARK"),
                eq("sha256:" + "b".repeat(64)), isNull(), isNull(), isNull(), any()))
                .thenReturn(1);

        QualityResult result = service.execute(base(QualityOperation.SUBMIT_RECHECK_DECISION)
                .inspectionTask(QualityCommand.InspectionTaskDefinition.builder()
                        .taskId("task-01").expectedVersion(4L).decision("FAIL")
                        .defectCode("COUNTERFEIT_MARK")
                        .evidenceRef("sha256:" + "b".repeat(64)).build())
                .build());

        assertThat(result.getStatus()).isEqualTo("CONFLICTED");
        assertThat(result.getAggregateVersion()).isEqualTo(5L);
        verify(mapper).insertTaskHistory(argThat(history ->
                history.getPreviousStatus().equals("RECHECK_REQUIRED")
                        && history.getCurrentStatus().equals("CONFLICTED")
                        && history.getActorPrincipalId().equals("principal-auth-02")));
        verify(outbox).append(argThat(event ->
                event.getEventType().equals("quality.inspection_task.recheck_conflicted")));
    }

    private static InspectionTask decidedTask() {
        return new InspectionTask().setTaskId("task-01").setTenantId(23L)
                .setStandardId("standard-01").setStandardVersion(1L)
                .setStandardVersionId("standard-version-01").setSubjectType("INBOUND_ITEM")
                .setSubjectRef("receipt-item-01").setCanonicalSkuId("sku-01")
                .setLotId("lot-01").setWarehouseId("warehouse-01").setPriority("HIGH")
                .setStatus("DECIDED").setAuthenticatorPrincipalId("principal-auth-01")
                .setDecision("PASS").setEvidenceRef("sha256:" + "a".repeat(64))
                .setVersion(4L);
    }

    private static QualityCommand.QualityCommandBuilder base(QualityOperation operation) {
        return QualityCommand.builder().operation(operation)
                .idempotencyKey("quality-idempotency-" + operation)
                .runId("run-001").correlationId("22222222-2222-4222-8222-222222222222")
                .occurredAt(Instant.parse("2026-07-25T00:00:00Z"));
    }
}
