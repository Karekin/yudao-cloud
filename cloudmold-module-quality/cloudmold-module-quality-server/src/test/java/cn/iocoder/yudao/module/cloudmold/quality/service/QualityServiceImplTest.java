package cn.iocoder.yudao.module.cloudmold.quality.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.*;
import cn.iocoder.yudao.module.cloudmold.quality.api.*;
import cn.iocoder.yudao.module.cloudmold.quality.dal.dataobject.QualityRecords.*;
import cn.iocoder.yudao.module.cloudmold.quality.dal.mysql.QualityMapper;
import cn.iocoder.yudao.module.cloudmold.quality.service.actor.QualityActorPrincipalPort;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QualityServiceImplTest {
    private final QualityMapper mapper = mock(QualityMapper.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final QualityActorPrincipalPort actorPrincipalPort = mock(QualityActorPrincipalPort.class);
    private final InventoryLotQueryApi inventoryLotQueryApi = mock(InventoryLotQueryApi.class);
    private final InventoryLotCommandApi inventoryLotCommandApi = mock(InventoryLotCommandApi.class);
    private final QualityServiceImpl service = new QualityServiceImpl(
            mapper, outbox, actorPrincipalPort, inventoryLotQueryApi, inventoryLotCommandApi);
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
        QualityResult result = execute(base(QualityOperation.CREATE_STANDARD)
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
        verify(actorPrincipalPort).requireActive("principal-auth-01");
    }

    @Test
    void publishesImmutableStandardVersionBeforeAdvancingHead() {
        when(mapper.selectStandardForUpdate(23L, "standard-01")).thenReturn(new Standard()
                .setStandardId("standard-01").setTenantId(23L).setStandardCode("AUTH-SHOE-001")
                .setDraftContentSha256("b".repeat(64)).setStatus("DRAFT")
                .setCurrentVersion(0L).setAggregateVersion(1L));
        QualityResult result = execute(base(QualityOperation.PUBLISH_STANDARD)
                .standard(QualityCommand.StandardDefinition.builder()
                        .standardId("standard-01").expectedVersion(1L)
                        .approverPrincipalId("spoofed-principal").build())
                .build());

        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
        assertThat(result.getAggregateVersion()).isEqualTo(2L);
        verify(mapper).insertStandardVersion(argThat(version ->
                version.getStandardId().equals("standard-01")
                        && version.getStandardVersion().equals(1L)
                        && version.getContentSha256().equals("b".repeat(64))
                        && version.getApproverPrincipalId().equals("principal-reviewer-01")));
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

        assertThatThrownBy(() -> execute(command))
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

        assertThatThrownBy(() -> execute(command))
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

        assertThatThrownBy(() -> execute(command))
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

        QualityResult result = execute(base(QualityOperation.SUBMIT_RECHECK_DECISION)
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

    @Test
    void rejectsRecheckSubmissionBySomeoneOtherThanAssignedSecondaryAuthenticator() {
        when(mapper.selectInspectionTaskForUpdate(23L, "task-01")).thenReturn(
                decidedTask().setStatus("RECHECK_REQUIRED")
                        .setSecondaryAuthenticatorPrincipalId("principal-auth-02"));
        QualityCommand command = base(QualityOperation.SUBMIT_RECHECK_DECISION)
                .inspectionTask(QualityCommand.InspectionTaskDefinition.builder()
                        .taskId("task-01").expectedVersion(4L).decision("PASS")
                        .evidenceRef("sha256:" + "b".repeat(64)).build())
                .build();

        assertThatThrownBy(() -> service.execute(command, "principal-intruder"))
                .hasMessage("only the assigned secondary authenticator can submit the recheck");
        verify(mapper, never()).submitRecheckDecision(anyLong(), anyString(), anyLong(),
                anyString(), anyString(), any(), anyString(), any(), any(), any(), any());
        verifyNoInteractions(outbox);
    }

    @Test
    void rejectsInspectionStartBySomeoneOtherThanAssignedAuthenticator() {
        when(mapper.selectInspectionTaskForUpdate(23L, "task-01")).thenReturn(
                decidedTask().setStatus("ASSIGNED").setVersion(2L)
                        .setAuthenticatorPrincipalId("principal-auth-01"));
        QualityCommand command = base(QualityOperation.START_INSPECTION_TASK)
                .inspectionTask(QualityCommand.InspectionTaskDefinition.builder()
                        .taskId("task-01").expectedVersion(2L).build())
                .build();

        assertThatThrownBy(() -> service.execute(command, "principal-intruder"))
                .hasMessage("only the assigned authenticator can start the inspection task");
        verify(mapper, never()).transitionInspectionTask(anyLong(), anyString(), anyLong(),
                anyString(), anyString(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any());
        verifyNoInteractions(outbox);
    }

    @Test
    void rejectsCommandsWithoutAnAttestedActorEnvelope() {
        assertThatThrownBy(() -> service.execute(base(QualityOperation.CREATE_STANDARD).build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("attested actor Principal is required");
        verifyNoInteractions(actorPrincipalPort);
        verify(mapper, never()).insertOrResolveOperation(anyLong(), anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    void recallsGovernedInventoryLotBeforeOpeningQualityRecall() {
        String lotId = "11111111-1111-4111-8111-111111111111";
        InspectionTask failed = decidedTask().setLotId(lotId).setDecision("FAIL");
        when(mapper.selectInspectionTaskForUpdate(23L, "task-01")).thenReturn(failed);
        when(mapper.insertRecallAction(any())).thenReturn(1);
        when(inventoryLotQueryApi.requireCurrent(eq(lotId), any())).thenReturn(
                new InventoryLotView().setLotId(lotId).setCanonicalSkuId("sku-01")
                        .setStatus("ACTIVE").setVersion(7L));
        when(inventoryLotCommandApi.execute(any())).thenReturn(
                new InventoryLotResult().setLotId(lotId).setLotStatus("RECALLED").setLotVersion(8L));

        QualityResult result = execute(base(QualityOperation.OPEN_RECALL_ACTION)
                .recallAction(QualityCommand.RecallActionDefinition.builder()
                        .recallActionId("recall-01").inspectionTaskId("task-01")
                        .reasonCode("COUNTERFEIT").ownerPrincipalId("principal-quality-owner")
                        .build())
                .build());

        assertThat(result.getStatus()).isEqualTo("OPEN");
        verify(inventoryLotCommandApi).execute(argThat(command ->
                command.getOperation() == InventoryLotOperation.RECALL
                        && command.getLotId().equals(lotId)
                        && command.getExpectedLotVersion().equals(7L)
                        && command.getRecallReference().equals("quality-recall:recall-01")));
        verify(mapper).insertRecallAction(argThat(row -> row.getLotId().equals(lotId)));
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

    private QualityResult execute(QualityCommand command) {
        String actor = switch (command.getOperation()) {
            case PUBLISH_STANDARD -> "principal-reviewer-01";
            case SUBMIT_RECHECK_DECISION -> "principal-auth-02";
            case OPEN_RECALL_ACTION -> "principal-quality-owner";
            default -> "principal-auth-01";
        };
        return service.execute(command, actor);
    }

    private static QualityCommand.QualityCommandBuilder base(QualityOperation operation) {
        return QualityCommand.builder().operation(operation)
                .idempotencyKey("quality-idempotency-" + operation)
                .runId("run-001").correlationId("22222222-2222-4222-8222-222222222222")
                .occurredAt(Instant.parse("2026-07-25T00:00:00Z"));
    }
}
