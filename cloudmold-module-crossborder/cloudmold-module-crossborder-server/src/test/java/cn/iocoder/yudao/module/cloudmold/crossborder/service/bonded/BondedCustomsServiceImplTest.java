package cn.iocoder.yudao.module.cloudmold.crossborder.service.bonded;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.crossborder.api.bonded.*;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.bonded.BondedCustomsRecords.CaseRecord;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.bonded.BondedCustomsRecords.EligibilityAssessmentRecord;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.bonded.BondedCustomsRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.bonded.BondedCustomsRecords.StatusHistoryRecord;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.mysql.bonded.BondedCustomsCaseMapper;
import cn.iocoder.yudao.module.cloudmold.crossborder.service.actor.CrossBorderActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.crossborder.service.query.bonded.BondedCustomsQueryService;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BondedCustomsServiceImplTest {
    private static final Long TENANT_ID = 162L;
    private static final String ACTOR = "principal-bonded-01";
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-29T11:20:00Z");

    private final BondedCustomsCaseMapper mapper = mock(BondedCustomsCaseMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final CrossBorderActorPrincipalPort actorPrincipalPort = mock(CrossBorderActorPrincipalPort.class);
    private final BondedCustomsServiceImpl service = new BondedCustomsServiceImpl(mapper, outboxAppender, actorPrincipalPort);
    private final BondedCustomsQueryService queryService = new BondedCustomsQueryService(mapper);

    private final AtomicReference<String> requestHash = new AtomicReference<>();
    private final AtomicReference<String> attemptToken = new AtomicReference<>();
    private final AtomicReference<CaseRecord> caseState = new AtomicReference<>();
    private final AtomicReference<EligibilityAssessmentRecord> assessmentState = new AtomicReference<>();
    private final List<StatusHistoryRecord> historyState = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(mapper.insertOrResolveOperation(eq(TENANT_ID), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    requestHash.set(invocation.getArgument(3));
                    attemptToken.set(invocation.getArgument(4));
                    return 1;
                });
        when(mapper.selectLastInsertId()).thenReturn(901L);
        when(mapper.selectOperationForUpdate(901L, TENANT_ID)).thenAnswer(invocation ->
                new Operation().setOperationId(901L).setTenantId(TENANT_ID)
                        .setRequestHash(requestHash.get()).setAttemptToken(attemptToken.get()).setStatus(0));
        when(mapper.markOperationSucceeded(eq(901L), eq(TENANT_ID), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(mapper.insertCase(any())).thenAnswer(invocation -> {
            CaseRecord value = invocation.getArgument(0);
            caseState.set(copyCase(value));
            return 1;
        });
        when(mapper.selectCaseForUpdate(eq(TENANT_ID), anyString())).thenAnswer(invocation -> {
            CaseRecord current = caseState.get();
            String caseId = invocation.getArgument(1);
            if (current == null || !TENANT_ID.equals(current.getTenantId()) || !caseId.equals(current.getCaseId())) {
                return null;
            }
            return copyCase(current);
        });
        when(mapper.selectCase(eq(TENANT_ID), anyString())).thenAnswer(invocation -> {
            CaseRecord current = caseState.get();
            String caseId = invocation.getArgument(1);
            if (current == null || !TENANT_ID.equals(current.getTenantId()) || !caseId.equals(current.getCaseId())) {
                return null;
            }
            return copyCase(current);
        });
        when(mapper.updateCase(any(), eq(TENANT_ID), anyString(), anyLong(), anyString())).thenAnswer(invocation -> {
            CaseRecord next = invocation.getArgument(0);
            String caseId = invocation.getArgument(2);
            Long expectedVersion = invocation.getArgument(3);
            String expectedStatus = invocation.getArgument(4);
            CaseRecord current = caseState.get();
            if (current == null || !caseId.equals(current.getCaseId())
                    || !expectedVersion.equals(current.getVersion())
                    || !expectedStatus.equals(current.getStatus())) {
                return 0;
            }
            caseState.set(copyCase(next));
            return 1;
        });
        when(mapper.insertAssessment(any())).thenAnswer(invocation -> {
            EligibilityAssessmentRecord value = invocation.getArgument(0);
            assessmentState.set(copyAssessment(value));
            return 1;
        });
        when(mapper.selectAssessment(eq(TENANT_ID), anyString())).thenAnswer(invocation -> {
            EligibilityAssessmentRecord current = assessmentState.get();
            String assessmentId = invocation.getArgument(1);
            if (current == null || !TENANT_ID.equals(current.getTenantId())
                    || !assessmentId.equals(current.getAssessmentId())) {
                return null;
            }
            return copyAssessment(current);
        });
        when(mapper.insertHistory(any())).thenAnswer(invocation -> {
            StatusHistoryRecord value = invocation.getArgument(0);
            historyState.add(copyHistory(value));
            return 1;
        });
        when(mapper.selectHistory(eq(TENANT_ID), anyString())).thenAnswer(invocation -> historyState.stream()
                .filter(item -> invocation.getArgument(1).equals(item.getCaseId()))
                .map(BondedCustomsServiceImplTest::copyHistory)
                .toList());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        historyState.clear();
        caseState.set(null);
        assessmentState.set(null);
    }

    @Test
    void completesBondedLifecycleToTerminalCase() {
        service.execute(createCaseCommand("bonded-01"), ACTOR);
        service.execute(base(BondedCustomsOperation.ASSESS_ELIGIBILITY, "bonded-01", 1L)
                .eligibilityAssessment(assessment()).build(), ACTOR);
        service.execute(base(BondedCustomsOperation.CLASSIFY_GOODS, "bonded-01", 2L)
                .goodsClassification(classification()).build(), ACTOR);
        service.execute(base(BondedCustomsOperation.MATCH_TRIPLE_ORDERS, "bonded-01", 3L)
                .tripleOrder(matchedTripleOrder()).build(), ACTOR);
        service.execute(base(BondedCustomsOperation.CALCULATE_TAX, "bonded-01", 4L)
                .taxCalculation(tax()).build(), ACTOR);
        service.execute(base(BondedCustomsOperation.APPROVE_DECLARATION, "bonded-01", 5L)
                .approvalRef("APR-BC-001").build(), ACTOR);
        service.execute(base(BondedCustomsOperation.SUBMIT_DECLARATION, "bonded-01", 6L)
                .approvalRef("APR-BC-001").declarationRef("DECL-BC-001").build(), ACTOR);
        service.execute(base(BondedCustomsOperation.ACCEPT_CUSTOMS, "bonded-01", 7L)
                .customsAcceptanceRef("CUSTOMS-ACC-001").build(), ACTOR);
        service.execute(base(BondedCustomsOperation.RELEASE_BONDED_STOCK, "bonded-01", 8L)
                .bondedReleaseRef("BONDED-REL-001").build(), ACTOR);
        service.execute(base(BondedCustomsOperation.CONFIRM_DELIVERY, "bonded-01", 9L)
                .deliveryConfirmationRef("DELIVERY-001").build(), ACTOR);
        BondedCustomsResult closed = service.execute(base(BondedCustomsOperation.CLOSE_CASE, "bonded-01", 10L)
                .closeReason("Bonded flow complete").build(), ACTOR);

        assertThat(closed.getStatus()).isEqualTo("CLOSED");
        assertThat(closed.getTripleMatchStatus()).isEqualTo("TRIPLE_MATCHED");
        assertThat(closed.getCustomsStatus()).isEqualTo("CUSTOMS_ACCEPTED");
        assertThat(closed.getBondedReleaseStatus()).isEqualTo("BONDED_RELEASED");
        assertThat(closed.getDeliveryStatus()).isEqualTo("DELIVERED");
        assertThat(closed.getAggregateVersion()).isEqualTo(11L);

        BondedCustomsCaseView view = queryService.get("bonded-01");
        assertThat(view.getStatus()).isEqualTo("CLOSED");
        assertThat(view.getTripleMatchStatus()).isEqualTo("TRIPLE_MATCHED");
        assertThat(view.getCustomsStatus()).isEqualTo("CUSTOMS_ACCEPTED");
        assertThat(view.getBondedReleaseStatus()).isEqualTo("BONDED_RELEASED");
        assertThat(view.getDeliveryStatus()).isEqualTo("DELIVERED");
        assertThat(view.getMode()).isEqualTo("BONDED_RETAIL_IMPORT");
        assertThat(view.getTripleOrder().getOrderAmountMinor()).isEqualTo(12800L);
        assertThat(view.getTaxCalculation().getTotalTaxMinor()).isEqualTo(1510L);
        assertThat(view.getGoodsClassification().getPositiveListCode()).isEqualTo("POSITIVE_LIST_A");
        assertThat(view.getHistory()).hasSize(11);
        verify(outboxAppender, times(11)).append(any());
    }

    @Test
    void returnsImmutableReplayForDuplicateOperation() {
        BondedCustomsResult persisted = BondedCustomsResult.builder()
                .operationId(901L).duplicate(false).aggregateType("bonded_customs_case")
                .aggregateId("bonded-dup").aggregateVersion(1L).status("DRAFT")
                .tripleMatchStatus("PENDING").customsStatus("PENDING")
                .bondedReleaseStatus("PENDING").deliveryStatus("PENDING")
                .caseId("bonded-dup").caseNo("CMBC123").build();
        when(mapper.selectOperationForUpdate(901L, TENANT_ID)).thenAnswer(invocation ->
                new Operation().setOperationId(901L).setTenantId(TENANT_ID)
                        .setRequestHash(requestHash.get()).setAttemptToken("other-attempt")
                        .setStatus(BondedCustomsServiceImpl.OPERATION_SUCCEEDED)
                        .setResultJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(persisted)));

        BondedCustomsResult replay = service.execute(createCaseCommand("bonded-dup"), ACTOR);

        assertThat(replay.getDuplicate()).isTrue();
        verify(mapper, never()).insertCase(any());
        verifyNoInteractions(outboxAppender);
    }

    @Test
    void rejectsIllegalTransitionBeforeTripleMatch() {
        caseState.set(new CaseRecord().setCaseId("bonded-02").setTenantId(TENANT_ID)
                .setStatus("GOODS_CLASSIFIED").setVersion(3L));

        assertThatThrownBy(() -> service.execute(base(BondedCustomsOperation.CALCULATE_TAX, "bonded-02", 3L)
                        .taxCalculation(tax()).build(), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bonded customs case must be in TRIPLE_MATCHED status");
    }

    @Test
    void rejectsCrossTenantAccess() {
        caseState.set(new CaseRecord().setCaseId("bonded-03").setTenantId(9L)
                .setStatus("GOODS_CLASSIFIED").setVersion(3L));

        assertThatThrownBy(() -> service.execute(base(BondedCustomsOperation.MATCH_TRIPLE_ORDERS, "bonded-03", 3L)
                        .tripleOrder(matchedTripleOrder()).build(), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bonded customs case not found");
    }

    @Test
    void rejectsVersionConflict() {
        caseState.set(new CaseRecord().setCaseId("bonded-04").setTenantId(TENANT_ID)
                .setStatus("GOODS_CLASSIFIED").setVersion(4L));

        assertThatThrownBy(() -> service.execute(base(BondedCustomsOperation.MATCH_TRIPLE_ORDERS, "bonded-04", 3L)
                        .tripleOrder(matchedTripleOrder()).build(), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bonded customs case version conflict");
    }

    @Test
    void rejectsTripleOrderMismatch() {
        caseState.set(new CaseRecord().setCaseId("bonded-05").setTenantId(TENANT_ID)
                .setStatus("GOODS_CLASSIFIED").setVersion(3L));

        BondedCustomsCommand.TripleOrder mismatch = BondedCustomsCommand.TripleOrder.builder()
                .orderRef("ORDER-001")
                .paymentRef("PAY-001")
                .logisticsRef("LOG-001")
                .orderAmountMinor(12800L)
                .paymentAmountMinor(12799L)
                .logisticsAmountMinor(12800L)
                .currency("CNY")
                .buyerIdentityHash("0123456789abcdef0123456789abcdef")
                .receiverIdentityHash("0123456789abcdef0123456789abcdef")
                .declarantIdentityHash("0123456789abcdef0123456789abcdef")
                .orderSnapshotRef("restricted:ordersnapshot0001")
                .paymentSnapshotRef("restricted:paymentsnapshot0001")
                .logisticsSnapshotRef("restricted:logisticssnapshot001")
                .build();
        assertThatThrownBy(() -> service.execute(base(BondedCustomsOperation.MATCH_TRIPLE_ORDERS, "bonded-05", 3L)
                        .tripleOrder(mismatch).build(), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("triple order amounts must match exactly");
    }

    @Test
    void rejectsApprovalDriftOnSubmission() {
        caseState.set(new CaseRecord().setCaseId("bonded-06").setTenantId(TENANT_ID)
                .setStatus("DECLARATION_APPROVED").setVersion(6L).setApprovalRef("APR-BC-001"));

        assertThatThrownBy(() -> service.execute(base(BondedCustomsOperation.SUBMIT_DECLARATION, "bonded-06", 6L)
                        .approvalRef("APR-BC-002").declarationRef("DECL-BC-002").build(), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("approvalRef does not match the approved declaration");
    }

    @Test
    void rejectsQueryForNonTerminalCase() {
        TenantContextHolder.setTenantId(TENANT_ID);
        caseState.set(new CaseRecord().setCaseId("bonded-07").setTenantId(TENANT_ID)
                .setStatus("CUSTOMS_ACCEPTED").setTripleMatchStatus("TRIPLE_MATCHED")
                .setCustomsStatus("CUSTOMS_ACCEPTED").setBondedReleaseStatus("PENDING")
                .setDeliveryStatus("PENDING").setVersion(8L));

        assertThatThrownBy(() -> queryService.get("bonded-07"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bonded customs case is not terminal");
    }

    private BondedCustomsCommand createCaseCommand(String caseId) {
        return BondedCustomsCommand.builder()
                .operation(BondedCustomsOperation.CREATE_CASE)
                .idempotencyKey("idem-create-" + caseId)
                .runId("run-bonded-001")
                .correlationId("b4d447f1-27f4-49c0-850e-c4c09241c395")
                .occurredAt(OCCURRED_AT)
                .caseId(caseId)
                .canonicalOrderId("canonical-order-001")
                .tripleOrder(BondedCustomsCommand.TripleOrder.builder()
                        .orderRef("ORDER-001")
                        .paymentRef("PAY-001")
                        .logisticsRef("LOG-001")
                        .build())
                .build();
    }

    private BondedCustomsCommand.BondedCustomsCommandBuilder base(BondedCustomsOperation operation, String caseId,
                                                                  Long expectedVersion) {
        return BondedCustomsCommand.builder()
                .operation(operation)
                .idempotencyKey("idem-" + operation.name().toLowerCase() + "-" + expectedVersion)
                .runId("run-bonded-001")
                .correlationId("b4d447f1-27f4-49c0-850e-c4c09241c395")
                .occurredAt(OCCURRED_AT)
                .caseId(caseId)
                .expectedVersion(expectedVersion);
    }

    private BondedCustomsCommand.EligibilityAssessment assessment() {
        return BondedCustomsCommand.EligibilityAssessment.builder()
                .facts(List.of("mode=BONDED_RETAIL_IMPORT", "goods_value_minor=12800"))
                .options(List.of("ALLOW_DECLARATION", "MANUAL_REVIEW"))
                .recommendation("ALLOW_DECLARATION")
                .risks(List.of("positive list verification pending"))
                .confidence(new BigDecimal("0.9400"))
                .missingFacts(List.of("warehouse customs batch number"))
                .evidenceRef("restricted:bondedeligibility0001")
                .build();
    }

    private BondedCustomsCommand.GoodsClassification classification() {
        return BondedCustomsCommand.GoodsClassification.builder()
                .hsCode("610910")
                .positiveListCode("POSITIVE_LIST_A")
                .goodsName("Cotton T-Shirt")
                .evidenceRef("restricted:bondedclassification0001")
                .build();
    }

    private BondedCustomsCommand.TripleOrder matchedTripleOrder() {
        return BondedCustomsCommand.TripleOrder.builder()
                .orderRef("ORDER-001")
                .paymentRef("PAY-001")
                .logisticsRef("LOG-001")
                .orderAmountMinor(12800L)
                .paymentAmountMinor(12800L)
                .logisticsAmountMinor(12800L)
                .currency("CNY")
                .buyerIdentityHash("0123456789abcdef0123456789abcdef")
                .receiverIdentityHash("0123456789abcdef0123456789abcdef")
                .declarantIdentityHash("0123456789abcdef0123456789abcdef")
                .orderSnapshotRef("restricted:ordersnapshot0001")
                .paymentSnapshotRef("restricted:paymentsnapshot0001")
                .logisticsSnapshotRef("restricted:logisticssnapshot001")
                .build();
    }

    private BondedCustomsCommand.TaxCalculation tax() {
        return BondedCustomsCommand.TaxCalculation.builder()
                .dutiableAmountMinor(12800L)
                .consumptionTaxMinor(210L)
                .valueAddedTaxMinor(1300L)
                .totalTaxMinor(1510L)
                .currency("CNY")
                .evidenceRef("restricted:bondedtax0001")
                .build();
    }

    private static CaseRecord copyCase(CaseRecord source) {
        return new CaseRecord()
                .setCaseId(source.getCaseId())
                .setTenantId(source.getTenantId())
                .setCaseNo(source.getCaseNo())
                .setMode(source.getMode())
                .setCanonicalOrderId(source.getCanonicalOrderId())
                .setStatus(source.getStatus())
                .setTripleMatchStatus(source.getTripleMatchStatus())
                .setCustomsStatus(source.getCustomsStatus())
                .setBondedReleaseStatus(source.getBondedReleaseStatus())
                .setDeliveryStatus(source.getDeliveryStatus())
                .setOrderRef(source.getOrderRef())
                .setPaymentRef(source.getPaymentRef())
                .setLogisticsRef(source.getLogisticsRef())
                .setOrderAmountMinor(source.getOrderAmountMinor())
                .setPaymentAmountMinor(source.getPaymentAmountMinor())
                .setLogisticsAmountMinor(source.getLogisticsAmountMinor())
                .setCurrency(source.getCurrency())
                .setBuyerIdentityHash(source.getBuyerIdentityHash())
                .setReceiverIdentityHash(source.getReceiverIdentityHash())
                .setDeclarantIdentityHash(source.getDeclarantIdentityHash())
                .setOrderSnapshotRef(source.getOrderSnapshotRef())
                .setPaymentSnapshotRef(source.getPaymentSnapshotRef())
                .setLogisticsSnapshotRef(source.getLogisticsSnapshotRef())
                .setAssessmentId(source.getAssessmentId())
                .setHsCode(source.getHsCode())
                .setPositiveListCode(source.getPositiveListCode())
                .setGoodsName(source.getGoodsName())
                .setGoodsEvidenceRef(source.getGoodsEvidenceRef())
                .setDutiableAmountMinor(source.getDutiableAmountMinor())
                .setConsumptionTaxMinor(source.getConsumptionTaxMinor())
                .setValueAddedTaxMinor(source.getValueAddedTaxMinor())
                .setTotalTaxMinor(source.getTotalTaxMinor())
                .setTaxCurrency(source.getTaxCurrency())
                .setTaxEvidenceRef(source.getTaxEvidenceRef())
                .setApprovalRef(source.getApprovalRef())
                .setDeclarationRef(source.getDeclarationRef())
                .setCustomsAcceptanceRef(source.getCustomsAcceptanceRef())
                .setBondedReleaseRef(source.getBondedReleaseRef())
                .setDeliveryConfirmationRef(source.getDeliveryConfirmationRef())
                .setCloseReason(source.getCloseReason())
                .setCreatedByPrincipalId(source.getCreatedByPrincipalId())
                .setVersion(source.getVersion())
                .setCreatedAt(source.getCreatedAt())
                .setUpdatedAt(source.getUpdatedAt())
                .setClosedAt(source.getClosedAt());
    }

    private static EligibilityAssessmentRecord copyAssessment(EligibilityAssessmentRecord source) {
        return new EligibilityAssessmentRecord()
                .setAssessmentId(source.getAssessmentId())
                .setTenantId(source.getTenantId())
                .setCaseId(source.getCaseId())
                .setFactsJson(source.getFactsJson())
                .setOptionsJson(source.getOptionsJson())
                .setRecommendation(source.getRecommendation())
                .setRisksJson(source.getRisksJson())
                .setConfidence(source.getConfidence())
                .setMissingFactsJson(source.getMissingFactsJson())
                .setEvidenceRef(source.getEvidenceRef())
                .setAssessedByPrincipalId(source.getAssessedByPrincipalId())
                .setAssessedAt(source.getAssessedAt())
                .setCreatedAt(source.getCreatedAt());
    }

    private static StatusHistoryRecord copyHistory(StatusHistoryRecord source) {
        return new StatusHistoryRecord()
                .setHistoryId(source.getHistoryId())
                .setTenantId(source.getTenantId())
                .setCaseId(source.getCaseId())
                .setAggregateVersion(source.getAggregateVersion())
                .setOperation(source.getOperation())
                .setPreviousStatus(source.getPreviousStatus())
                .setCurrentStatus(source.getCurrentStatus())
                .setActorPrincipalId(source.getActorPrincipalId())
                .setOccurredAt(source.getOccurredAt())
                .setDetailJson(source.getDetailJson())
                .setCreatedAt(source.getCreatedAt());
    }
}
