package cn.iocoder.yudao.module.cloudmold.partnermarketing.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.partnermarketing.api.PartnerMarketingCommand;
import cn.iocoder.yudao.module.cloudmold.partnermarketing.api.PartnerMarketingCommandResult;
import cn.iocoder.yudao.module.cloudmold.partnermarketing.api.PartnerMarketingOperation;
import cn.iocoder.yudao.module.cloudmold.partnermarketing.api.PartnerMarketingWorkflowView;
import cn.iocoder.yudao.module.cloudmold.partnermarketing.dal.dataobject.PartnerMarketingCaseDO;
import cn.iocoder.yudao.module.cloudmold.partnermarketing.dal.dataobject.PartnerMarketingCaseHistoryDO;
import cn.iocoder.yudao.module.cloudmold.partnermarketing.dal.dataobject.PartnerMarketingOperationDO;
import cn.iocoder.yudao.module.cloudmold.partnermarketing.dal.mysql.PartnerMarketingCaseMapper;
import cn.iocoder.yudao.module.cloudmold.partnermarketing.dal.mysql.PartnerMarketingOperationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PartnerMarketingServiceImplTest {

    private static final long TENANT_ID = 162L;
    private static final String CASE_ID = "pm-case-001";
    private static final String CASE_CODE = "PM_CASE_20260729_001";
    private static final String CREATOR = "principal_creator";
    private static final String RISK = "principal_risk";
    private static final String OUTREACH = "principal_outreach";
    private static final String BRIEF_REVIEWER = "principal_brief_reviewer";
    private static final String CONTENT_REVIEWER = "principal_content_reviewer";
    private static final String COMPLIANCE = "principal_compliance";
    private static final String ATTRIBUTION = "principal_attribution";
    private static final String FINANCE_CHECKER = "principal_finance_checker";
    private static final String TREASURY = "principal_treasury";
    private static final String CLOSER = "principal_closer";
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-29T12:00:00Z");
    private static final String CORRELATION_ID = "11111111-1111-4111-8111-111111111111";

    private final PartnerMarketingOperationMapper operationMapper = mock(PartnerMarketingOperationMapper.class);
    private final PartnerMarketingCaseMapper caseMapper = mock(PartnerMarketingCaseMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final PartnerMarketingServiceImpl service =
            new PartnerMarketingServiceImpl(operationMapper, caseMapper, outboxAppender);

    private final AtomicLong operationSeq = new AtomicLong(700L);
    private final AtomicReference<Long> lastOperationId = new AtomicReference<>();
    private final Map<String, PartnerMarketingOperationDO> operationsByKey = new LinkedHashMap<>();
    private final Map<Long, PartnerMarketingOperationDO> operationsById = new LinkedHashMap<>();
    private final AtomicReference<PartnerMarketingCaseDO> currentCase = new AtomicReference<>();
    private final List<PartnerMarketingCaseHistoryDO> history = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(outboxAppender.append(any())).thenReturn(null);

        when(operationMapper.insertOrResolve(eq(TENANT_ID), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    String idempotencyKey = invocation.getArgument(1);
                    String commandType = invocation.getArgument(2);
                    String requestHash = invocation.getArgument(3);
                    String attemptToken = invocation.getArgument(4);
                    PartnerMarketingOperationDO existing = operationsByKey.get(idempotencyKey);
                    if (existing == null) {
                        long operationId = operationSeq.incrementAndGet();
                        PartnerMarketingOperationDO row = new PartnerMarketingOperationDO()
                                .setOperationId(operationId)
                                .setTenantId(TENANT_ID)
                                .setIdempotencyKey(idempotencyKey)
                                .setCommandType(commandType)
                                .setRequestHash(requestHash)
                                .setAttemptToken(attemptToken)
                                .setStatus(0);
                        operationsByKey.put(idempotencyKey, row);
                        operationsById.put(operationId, row);
                        lastOperationId.set(operationId);
                    } else {
                        lastOperationId.set(existing.getOperationId());
                    }
                    return 1;
                });
        when(operationMapper.selectLastInsertId()).thenAnswer(invocation -> lastOperationId.get());
        when(operationMapper.selectForUpdate(anyLong(), eq(TENANT_ID)))
                .thenAnswer(invocation -> operationsById.get(invocation.getArgument(0)));
        when(operationMapper.markSucceeded(anyLong(), eq(TENANT_ID), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    Long operationId = invocation.getArgument(0);
                    PartnerMarketingOperationDO row = operationsById.get(operationId);
                    if (row == null || row.getStatus() != 0) {
                        return 0;
                    }
                    row.setStatus(10)
                            .setAggregateType(invocation.getArgument(2))
                            .setAggregateId(invocation.getArgument(3))
                            .setResultJson(invocation.getArgument(4));
                    return 1;
                });

        when(caseMapper.insert(org.mockito.ArgumentMatchers.any(PartnerMarketingCaseDO.class))).thenAnswer(invocation -> {
            currentCase.set(copy(invocation.getArgument(0), PartnerMarketingCaseDO.class));
            return 1;
        });
        when(caseMapper.selectForUpdate(eq(TENANT_ID), anyString())).thenAnswer(invocation -> {
            PartnerMarketingCaseDO row = currentCase.get();
            return row != null && Objects.equals(row.getCaseId(), invocation.getArgument(1))
                    ? copy(row, PartnerMarketingCaseDO.class) : null;
        });
        when(caseMapper.selectOneById(eq(TENANT_ID), anyString())).thenAnswer(invocation -> {
            PartnerMarketingCaseDO row = currentCase.get();
            return row != null && Objects.equals(row.getCaseId(), invocation.getArgument(1))
                    ? copy(row, PartnerMarketingCaseDO.class) : null;
        });
        when(caseMapper.updateWorkflowState(eq(TENANT_ID), any(PartnerMarketingCaseDO.class), anyLong()))
                .thenAnswer(invocation -> {
                    PartnerMarketingCaseDO incoming = invocation.getArgument(1);
                    Long expectedVersion = invocation.getArgument(2);
                    PartnerMarketingCaseDO row = currentCase.get();
                    if (row == null || !Objects.equals(row.getVersion(), expectedVersion)) {
                        return 0;
                    }
                    currentCase.set(copy(incoming, PartnerMarketingCaseDO.class));
                    return 1;
                });
        when(caseMapper.insertHistory(any())).thenAnswer(invocation -> {
            PartnerMarketingCaseHistoryDO item = copy(invocation.getArgument(0), PartnerMarketingCaseHistoryDO.class);
            item.setHistoryId((long) history.size() + 1);
            history.add(item);
            return 1;
        });
        when(caseMapper.selectHistory(eq(TENANT_ID), anyString())).thenAnswer(invocation -> {
            String caseId = invocation.getArgument(1);
            List<PartnerMarketingCaseHistoryDO> rows = history.stream()
                    .filter(item -> Objects.equals(item.getCaseId(), caseId))
                    .map(item -> copy(item, PartnerMarketingCaseHistoryDO.class))
                    .toList();
            return new ArrayList<>(rows);
        });
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void completesCanonicalWorkflowAndReturnsStrictTerminalView() {
        openCandidateCase();
        qualifyCandidate();
        startOutreach();
        submitBrief();
        approveBrief();
        submitContent();
        approveContent();
        verifyPublication();
        reconcileAttribution();
        approveSettlement();
        markSettlementPaid();

        PartnerMarketingCommandResult closeResult = service.execute(closeCase(CLOSER));
        PartnerMarketingWorkflowView workflow = service.getWorkflow(CASE_ID);

        assertThat(closeResult.getStatus()).isEqualTo("CLOSED");
        assertThat(closeResult.getAggregateVersion()).isEqualTo(12L);
        assertThat(workflow.getStatus()).isEqualTo(PartnerMarketingWorkflowView.Status.SUCCEEDED);
        assertThat(workflow.getTerminal()).isTrue();
        assertThat(workflow.getCurrentStatus()).isEqualTo("CLOSED");
        assertThat(workflow.getArtifacts()).extracting(PartnerMarketingWorkflowView.Artifact::getType)
                .contains("ATTRIBUTION", "SETTLEMENT", "STATUS_HISTORY");
        verify(outboxAppender, times(12)).append(any());
    }

    @Test
    void replaysImmutableDuplicateBeforeAdditionalWrites() {
        PartnerMarketingCommand command = openCandidateCase(CREATOR, "open-duplicate");

        PartnerMarketingCommandResult first = service.execute(command);
        PartnerMarketingCommandResult replay = service.execute(command);

        assertThat(first.getDuplicate()).isFalse();
        assertThat(replay.getDuplicate()).isTrue();
        assertThat(history).hasSize(1);
        verify(outboxAppender, times(1)).append(any());
    }

    @Test
    void rejectsOutOfOrderBriefApproval() {
        openCandidateCase();

        assertThatThrownBy(() -> service.execute(approveBrief(BRIEF_REVIEWER)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BRIEF_PENDING_APPROVAL");
    }

    @Test
    void rejectsStaleVersionAndKeepsTenantIsolation() {
        openCandidateCase();

        assertThatThrownBy(() -> service.execute(qualifyCandidate(RISK, 0L, "qualify-stale")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expectedVersion");

        TenantContextHolder.setTenantId(163L);
        PartnerMarketingWorkflowView workflow = service.getWorkflow(CASE_ID);
        assertThat(workflow.getStatus()).isEqualTo(PartnerMarketingWorkflowView.Status.PREPARE);
    }

    @Test
    void rejectsSelfApprovalAndSettlementDrift() {
        openCandidateCase();
        qualifyCandidate();
        startOutreach();
        submitBrief();

        assertThatThrownBy(() -> service.execute(approveBrief(OUTREACH, currentVersion(), "brief-approve-self")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("brief approver");

        approveBrief();
        submitContent();

        assertThatThrownBy(() -> service.execute(approveContent(CREATOR, currentVersion(), "content-approve-self")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content approver");

        approveContent();
        verifyPublication();
        reconcileAttribution();

        assertThatThrownBy(() -> service.execute(approveSettlement(ATTRIBUTION)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("settlement requester");
        assertThatThrownBy(() -> service.execute(approveSettlement(FINANCE_CHECKER, 2998L, "settlement-approve-drift")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly match");

        approveSettlement();

        assertThatThrownBy(() -> service.execute(markSettlementPaid(FINANCE_CHECKER, 3000L, "settlement-paid-self")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("payer should be independent");
        assertThatThrownBy(() -> service.execute(markSettlementPaid(TREASURY, 2990L, "settlement-paid-drift")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly match");

        assertThat(currentCase.get().getStatus()).isEqualTo("SETTLEMENT_APPROVED");
    }

    private void openCandidateCase() {
        service.execute(openCandidateCase(CREATOR, "open-001"));
    }

    private void qualifyCandidate() {
        service.execute(qualifyCandidate(RISK, currentVersion(), "qualify-001"));
    }

    private void startOutreach() {
        service.execute(startOutreach(OUTREACH, currentVersion(), "outreach-001"));
    }

    private void submitBrief() {
        service.execute(submitBrief(OUTREACH, currentVersion(), "brief-submit-001"));
    }

    private void approveBrief() {
        service.execute(approveBrief(BRIEF_REVIEWER, currentVersion(), "brief-approve-001"));
    }

    private void submitContent() {
        service.execute(submitContent(CREATOR, currentVersion(), "content-submit-001"));
    }

    private void approveContent() {
        service.execute(approveContent(CONTENT_REVIEWER, currentVersion(), "content-approve-001"));
    }

    private void verifyPublication() {
        service.execute(verifyPublication(COMPLIANCE, currentVersion(), "publication-verify-001"));
    }

    private void reconcileAttribution() {
        service.execute(reconcileAttribution(ATTRIBUTION, currentVersion(), "attribution-001"));
    }

    private void approveSettlement() {
        service.execute(approveSettlement(FINANCE_CHECKER, 3000L, "settlement-approve-001"));
    }

    private void markSettlementPaid() {
        service.execute(markSettlementPaid(TREASURY, 3000L, "settlement-paid-001"));
    }

    private Long currentVersion() {
        return currentCase.get().getVersion();
    }

    private static PartnerMarketingCommand openCandidateCase(String actor, String idempotencyKey) {
        return base(PartnerMarketingOperation.OPEN_CANDIDATE_CASE, actor, idempotencyKey)
                .candidateCase(PartnerMarketingCommand.CandidateCaseDefinition.builder()
                        .caseId(CASE_ID)
                        .caseCode(CASE_CODE)
                        .creatorPrincipalId(CREATOR)
                        .candidateHandle("@creator_global")
                        .platformCode("TIKTOK")
                        .regionCode("US")
                        .categoryCode("BEAUTY")
                        .reasonCode("PIPELINE_CREATE")
                        .build())
                .build();
    }

    private static PartnerMarketingCommand qualifyCandidate(String actor, Long expectedVersion, String idempotencyKey) {
        return base(PartnerMarketingOperation.QUALIFY_CANDIDATE, actor, idempotencyKey)
                .qualification(PartnerMarketingCommand.QualificationDefinition.builder()
                        .caseId(CASE_ID)
                        .expectedVersion(expectedVersion)
                        .riskLevel("LOW")
                        .riskDecision("PASS")
                        .riskEvidenceSha256("a".repeat(64))
                        .qualificationNote("FTC / GDPR risk cleared")
                        .reasonCode("RISK_PASS")
                        .build())
                .build();
    }

    private static PartnerMarketingCommand startOutreach(String actor, Long expectedVersion, String idempotencyKey) {
        return base(PartnerMarketingOperation.START_OUTREACH, actor, idempotencyKey)
                .outreach(PartnerMarketingCommand.OutreachDefinition.builder()
                        .caseId(CASE_ID)
                        .expectedVersion(expectedVersion)
                        .outreachChannelCode("EMAIL")
                        .outreachExternalRef("thread-20260729-01")
                        .reasonCode("FIRST_CONTACT")
                        .build())
                .build();
    }

    private static PartnerMarketingCommand submitBrief(String actor, Long expectedVersion, String idempotencyKey) {
        return base(PartnerMarketingOperation.SUBMIT_BRIEF, actor, idempotencyKey)
                .brief(PartnerMarketingCommand.BriefDefinition.builder()
                        .caseId(CASE_ID)
                        .expectedVersion(expectedVersion)
                        .campaignId("campaign-20260729")
                        .listingId("listing-20260729")
                        .cooperationModel("FIXED_FEE")
                        .budgetAmountMinor(4500L)
                        .currencyCode("USD")
                        .briefSummary("Launch creator review and shopping link")
                        .briefEvidenceSha256("b".repeat(64))
                        .reasonCode("BRIEF_SUBMIT")
                        .build())
                .build();
    }

    private static PartnerMarketingCommand approveBrief(String actor) {
        return approveBrief(actor, 1L, "brief-approve-default");
    }

    private static PartnerMarketingCommand approveBrief(String actor, Long expectedVersion, String idempotencyKey) {
        return base(PartnerMarketingOperation.APPROVE_BRIEF, actor, idempotencyKey)
                .brief(PartnerMarketingCommand.BriefDefinition.builder()
                        .caseId(CASE_ID)
                        .expectedVersion(expectedVersion)
                        .approvalEvidenceSha256("c".repeat(64))
                        .reasonCode("BRIEF_APPROVE")
                        .build())
                .build();
    }

    private static PartnerMarketingCommand submitContent(String actor, Long expectedVersion, String idempotencyKey) {
        return base(PartnerMarketingOperation.SUBMIT_CONTENT, actor, idempotencyKey)
                .content(PartnerMarketingCommand.ContentDefinition.builder()
                        .caseId(CASE_ID)
                        .expectedVersion(expectedVersion)
                        .contentSummary("Short video with shopping link and disclosure")
                        .contentEvidenceSha256("d".repeat(64))
                        .reasonCode("CONTENT_SUBMIT")
                        .build())
                .build();
    }

    private static PartnerMarketingCommand approveContent(String actor) {
        return approveContent(actor, 1L, "content-approve-default");
    }

    private static PartnerMarketingCommand approveContent(String actor, Long expectedVersion, String idempotencyKey) {
        return base(PartnerMarketingOperation.APPROVE_CONTENT, actor, idempotencyKey)
                .content(PartnerMarketingCommand.ContentDefinition.builder()
                        .caseId(CASE_ID)
                        .expectedVersion(expectedVersion)
                        .approvalEvidenceSha256("e".repeat(64))
                        .reasonCode("CONTENT_APPROVE")
                        .build())
                .build();
    }

    private static PartnerMarketingCommand verifyPublication(String actor, Long expectedVersion, String idempotencyKey) {
        return base(PartnerMarketingOperation.VERIFY_PUBLICATION_DISCLOSURE, actor, idempotencyKey)
                .publication(PartnerMarketingCommand.PublicationDefinition.builder()
                        .caseId(CASE_ID)
                        .expectedVersion(expectedVersion)
                        .externalPublishRef("post-20260729-01")
                        .externalPublishUrl("https://social.example/post-20260729-01")
                        .disclosureLabel("#ad")
                        .publishEvidenceSha256("f".repeat(64))
                        .reasonCode("DISCLOSURE_OK")
                        .build())
                .build();
    }

    private static PartnerMarketingCommand reconcileAttribution(String actor, Long expectedVersion, String idempotencyKey) {
        return base(PartnerMarketingOperation.RECONCILE_ATTRIBUTION, actor, idempotencyKey)
                .attribution(PartnerMarketingCommand.AttributionDefinition.builder()
                        .caseId(CASE_ID)
                        .expectedVersion(expectedVersion)
                        .attributedOrderCount(3)
                        .attributedOrderId("order-20260729-01")
                        .attributedPaymentId("payment-20260729-01")
                        .attributionSourceRef("click-bridge-20260729-01")
                        .grossSettlementAmountMinor(4500L)
                        .platformFeeAmountMinor(900L)
                        .taxWithholdingAmountMinor(600L)
                        .netPayableAmountMinor(3000L)
                        .attributionEvidenceSha256("1".repeat(64))
                        .reasonCode("ATTRIBUTION_RECONCILED")
                        .build())
                .build();
    }

    private static PartnerMarketingCommand approveSettlement(String actor) {
        return approveSettlement(actor, 3000L, "settlement-approve-default");
    }

    private static PartnerMarketingCommand approveSettlement(String actor, Long approvedAmount, String idempotencyKey) {
        return base(PartnerMarketingOperation.APPROVE_SETTLEMENT, actor, idempotencyKey)
                .settlementApproval(PartnerMarketingCommand.SettlementApprovalDefinition.builder()
                        .caseId(CASE_ID)
                        .expectedVersion(9L)
                        .approvedNetPayableAmountMinor(approvedAmount)
                        .approvalEvidenceSha256("2".repeat(64))
                        .reasonCode("SETTLEMENT_APPROVE")
                        .build())
                .build();
    }

    private static PartnerMarketingCommand markSettlementPaid(String actor, Long paidAmount, String idempotencyKey) {
        return base(PartnerMarketingOperation.MARK_SETTLEMENT_PAID, actor, idempotencyKey)
                .settlementPayment(PartnerMarketingCommand.SettlementPaymentDefinition.builder()
                        .caseId(CASE_ID)
                        .expectedVersion(10L)
                        .paidNetPayableAmountMinor(paidAmount)
                        .settlementReference("bank-ref-20260729-01")
                        .paymentEvidenceSha256("3".repeat(64))
                        .reasonCode("SETTLEMENT_PAID")
                        .build())
                .build();
    }

    private static PartnerMarketingCommand closeCase(String actor) {
        return base(PartnerMarketingOperation.CLOSE_CASE, actor, "case-close-001")
                .caseClose(PartnerMarketingCommand.CaseCloseDefinition.builder()
                        .caseId(CASE_ID)
                        .expectedVersion(11L)
                        .closureEvidenceSha256("4".repeat(64))
                        .reasonCode("CASE_CLOSED")
                        .build())
                .build();
    }

    private static PartnerMarketingCommand.PartnerMarketingCommandBuilder base(PartnerMarketingOperation operation,
                                                                              String actor, String idempotencyKey) {
        return PartnerMarketingCommand.builder()
                .operation(operation)
                .idempotencyKey(idempotencyKey)
                .runId("pm-run-20260729")
                .correlationId(CORRELATION_ID)
                .occurredAt(OCCURRED_AT)
                .actorPrincipalId(actor);
    }

    private static <T> T copy(T value, Class<T> type) {
        return JsonUtils.parseObject(JsonUtils.toJsonString(value), type);
    }
}
