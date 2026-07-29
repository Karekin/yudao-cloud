package cn.iocoder.yudao.module.cloudmold.crossborder.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.crossborder.api.CrossBorderCaseView;
import cn.iocoder.yudao.module.cloudmold.crossborder.api.CrossBorderCommand;
import cn.iocoder.yudao.module.cloudmold.crossborder.api.CrossBorderOperation;
import cn.iocoder.yudao.module.cloudmold.crossborder.api.CrossBorderResult;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.CrossBorderRecords.CaseRecord;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.CrossBorderRecords.ComplianceAssessmentRecord;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.CrossBorderRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.CrossBorderRecords.StatusHistoryRecord;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.mysql.CrossBorderCaseMapper;
import cn.iocoder.yudao.module.cloudmold.crossborder.service.actor.CrossBorderActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.crossborder.service.query.CrossBorderQueryService;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CrossBorderCaseServiceImplTest {
    private static final Long TENANT_ID = 162L;
    private static final String ACTOR = "principal-crossborder-01";
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-29T10:15:30Z");

    private final CrossBorderCaseMapper mapper = mock(CrossBorderCaseMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final CrossBorderActorPrincipalPort actorPrincipalPort = mock(CrossBorderActorPrincipalPort.class);
    private final CrossBorderCaseServiceImpl service =
            new CrossBorderCaseServiceImpl(mapper, outboxAppender, actorPrincipalPort);
    private final CrossBorderQueryService queryService = new CrossBorderQueryService(mapper);

    private final AtomicReference<String> requestHash = new AtomicReference<>();
    private final AtomicReference<String> attemptToken = new AtomicReference<>();
    private final AtomicReference<CaseRecord> caseState = new AtomicReference<>();
    private final AtomicReference<ComplianceAssessmentRecord> assessmentState = new AtomicReference<>();
    private final List<StatusHistoryRecord> historyState = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(mapper.insertOrResolveOperation(eq(TENANT_ID), anyString(), anyString(), anyString(),
                anyString(), any())).thenAnswer(invocation -> {
                    requestHash.set(invocation.getArgument(3));
                    attemptToken.set(invocation.getArgument(4));
                    return 1;
                });
        when(mapper.selectLastInsertId()).thenReturn(801L);
        when(mapper.selectOperationForUpdate(801L, TENANT_ID)).thenAnswer(invocation ->
                new Operation().setOperationId(801L).setTenantId(TENANT_ID)
                        .setRequestHash(requestHash.get()).setAttemptToken(attemptToken.get()).setStatus(0));
        when(mapper.markOperationSucceeded(eq(801L), eq(TENANT_ID), anyString(), anyString(), anyString(), any()))
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
            ComplianceAssessmentRecord value = invocation.getArgument(0);
            assessmentState.set(copyAssessment(value));
            return 1;
        });
        when(mapper.selectAssessment(eq(TENANT_ID), anyString())).thenAnswer(invocation -> {
            ComplianceAssessmentRecord current = assessmentState.get();
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
                .map(CrossBorderCaseServiceImplTest::copyHistory)
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
    void returnsImmutableReplayForSuccessfulIdempotentDuplicate() {
        CrossBorderResult persisted = CrossBorderResult.builder()
                .operationId(801L)
                .duplicate(false)
                .aggregateType("crossborder_direct_mail_case")
                .aggregateId("case-dup")
                .aggregateVersion(1L)
                .status("DRAFT")
                .caseId("case-dup")
                .caseNo("CMCBAAA")
                .build();
        when(mapper.selectOperationForUpdate(801L, TENANT_ID)).thenAnswer(invocation ->
                new Operation().setOperationId(801L).setTenantId(TENANT_ID)
                        .setRequestHash(requestHash.get()).setAttemptToken("other-attempt")
                        .setStatus(CrossBorderCaseServiceImpl.OPERATION_SUCCEEDED)
                        .setResultJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(persisted)));

        CrossBorderResult replay = service.execute(createCaseCommand("case-dup"), ACTOR);

        assertThat(replay.getDuplicate()).isTrue();
        assertThat(replay.getCaseId()).isEqualTo("case-dup");
        verify(mapper, never()).insertCase(any());
        verifyNoInteractions(outboxAppender);
    }

    @Test
    void rejectsInvalidTransitionBeforeComplianceApproval() {
        caseState.set(new CaseRecord().setCaseId("case-01").setTenantId(TENANT_ID)
                .setCaseNo("CMCB01").setOrderId("order-01").setFulfillmentId("ful-01")
                .setTradeMode("DIRECT_MAIL").setOriginCountry("CN").setDestinationCountry("US")
                .setStatus("DRAFT").setVersion(1L)
                .setCreatedAt(LocalDateTime.ofInstant(OCCURRED_AT, ZoneOffset.UTC))
                .setUpdatedAt(LocalDateTime.ofInstant(OCCURRED_AT, ZoneOffset.UTC)));

        assertThatThrownBy(() -> service.execute(base(CrossBorderOperation.SUBMIT_CUSTOMS, "case-01", 1L)
                        .approvalRef("APR-001")
                        .customsDeclarationRef("CUSTOMS-REF-01")
                        .build(), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cross-border case must be in HANDED_OVER status");
        verifyNoInteractions(outboxAppender);
    }

    @Test
    void failsClosedAcrossTenantBoundaries() {
        caseState.set(new CaseRecord().setCaseId("case-tenant-9").setTenantId(9L)
                .setStatus("COMPLIANCE_ASSESSED").setVersion(2L));

        assertThatThrownBy(() -> service.execute(base(CrossBorderOperation.SELECT_ROUTE, "case-tenant-9", 2L)
                        .route(route()).build(), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cross-border case not found");
    }

    @Test
    void rejectsVersionConflictsBeforeMutation() {
        caseState.set(new CaseRecord().setCaseId("case-02").setTenantId(TENANT_ID)
                .setStatus("COMPLIANCE_ASSESSED").setVersion(3L));

        assertThatThrownBy(() -> service.execute(base(CrossBorderOperation.SELECT_ROUTE, "case-02", 2L)
                        .route(route()).build(), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cross-border case version conflict");
        verifyNoInteractions(outboxAppender);
    }

    @Test
    void closesDirectMailCaseThroughTerminalHappyPath() {
        CrossBorderResult created = service.execute(createCaseCommand("case-01"), ACTOR);
        CrossBorderResult assessed = service.execute(base(CrossBorderOperation.RECORD_COMPLIANCE_ASSESSMENT,
                "case-01", 1L).assessment(assessment()).build(), ACTOR);
        CrossBorderResult routed = service.execute(base(CrossBorderOperation.SELECT_ROUTE,
                "case-01", 2L).route(route()).build(), ACTOR);
        CrossBorderResult approved = service.execute(base(CrossBorderOperation.APPROVE_COMPLIANCE,
                "case-01", 3L).approvalRef("APR-001").build(), ACTOR);
        CrossBorderResult declared = service.execute(base(CrossBorderOperation.ASSEMBLE_DECLARATION,
                "case-01", 4L).declaration(declaration("DECL-001")).build(), ACTOR);
        CrossBorderResult validated = service.execute(base(CrossBorderOperation.VALIDATE_THREE_DOCUMENTS,
                "case-01", 5L).documents(documents()).build(), ACTOR);
        CrossBorderResult booked = service.execute(base(CrossBorderOperation.BOOK_CARRIER,
                "case-01", 6L).booking(booking()).build(), ACTOR);
        CrossBorderResult labeled = service.execute(base(CrossBorderOperation.RECORD_LABEL,
                "case-01", 7L).label(label()).build(), ACTOR);
        CrossBorderResult handedOver = service.execute(base(CrossBorderOperation.HANDOVER,
                "case-01", 8L).handoverRef("HANDOVER-001").build(), ACTOR);
        CrossBorderResult submitted = service.execute(base(CrossBorderOperation.SUBMIT_CUSTOMS,
                "case-01", 9L).approvalRef("APR-001")
                .customsDeclarationRef("CUS-DECL-001").build(), ACTOR);
        CrossBorderResult released = service.execute(base(CrossBorderOperation.RECORD_CUSTOMS_RELEASE,
                "case-01", 10L).customsReleaseRef("CUS-REL-001").build(), ACTOR);
        CrossBorderResult delivered = service.execute(base(CrossBorderOperation.RECORD_DELIVERY,
                "case-01", 11L).deliveryEvidenceRef("restricted:deliveryproof0001").build(), ACTOR);
        CrossBorderResult closed = service.execute(base(CrossBorderOperation.CLOSE_CASE,
                "case-01", 12L).closeReason("Delivered and archived").build(), ACTOR);

        assertThat(created.getStatus()).isEqualTo("DRAFT");
        assertThat(assessed.getStatus()).isEqualTo("COMPLIANCE_ASSESSED");
        assertThat(routed.getStatus()).isEqualTo("ROUTE_SELECTED");
        assertThat(approved.getStatus()).isEqualTo("COMPLIANCE_APPROVED");
        assertThat(declared.getStatus()).isEqualTo("DECLARATION_ASSEMBLED");
        assertThat(validated.getStatus()).isEqualTo("DOCUMENTS_VALIDATED");
        assertThat(booked.getStatus()).isEqualTo("CARRIER_BOOKED");
        assertThat(labeled.getTrackingNumber()).isEqualTo("TRACK-001");
        assertThat(handedOver.getStatus()).isEqualTo("HANDED_OVER");
        assertThat(submitted.getCustomsDeclarationRef()).isEqualTo("CUS-DECL-001");
        assertThat(released.getCustomsReleaseRef()).isEqualTo("CUS-REL-001");
        assertThat(delivered.getStatus()).isEqualTo("DELIVERED");
        assertThat(closed.getStatus()).isEqualTo("CLOSED");
        assertThat(closed.getAggregateVersion()).isEqualTo(13L);

        CrossBorderCaseView terminal = queryService.get("case-01");
        assertThat(terminal.getStatus()).isEqualTo("CLOSED");
        assertThat(terminal.getCustomsStatus()).isEqualTo("RELEASED");
        assertThat(terminal.getDeliveryStatus()).isEqualTo("DELIVERED");
        assertThat(terminal.getVersion()).isEqualTo(13L);
        assertThat(terminal.getApprovalRef()).isEqualTo("APR-001");
        assertThat(terminal.getDocuments().getOrderRef()).isEqualTo("ORDER-DOC-001");
        assertThat(terminal.getBooking().getBookingRef()).isEqualTo("BOOK-001");
        assertThat(terminal.getLabel().getTrackingNumber()).isEqualTo("TRACK-001");
        assertThat(terminal.getHistory()).hasSize(13);
        assertThat(terminal.getHistory().get(0).getCurrentStatus()).isEqualTo("DRAFT");
        assertThat(terminal.getHistory().get(12).getCurrentStatus()).isEqualTo("CLOSED");
        verify(outboxAppender, times(13)).append(any());
    }

    private CrossBorderCommand createCaseCommand(String caseId) {
        return CrossBorderCommand.builder()
                .operation(CrossBorderOperation.CREATE_CASE)
                .idempotencyKey("idem-create-" + caseId)
                .runId("run-crossborder-001")
                .correlationId("b4d447f1-27f4-49c0-850e-c4c09241c395")
                .occurredAt(OCCURRED_AT)
                .caseId(caseId)
                .orderId("order-01")
                .fulfillmentId("fulfillment-01")
                .tradeMode("DIRECT_MAIL")
                .originCountry("CN")
                .destinationCountry("US")
                .build();
    }

    private CrossBorderCommand.CrossBorderCommandBuilder base(CrossBorderOperation operation, String caseId,
                                                              Long expectedVersion) {
        return CrossBorderCommand.builder()
                .operation(operation)
                .idempotencyKey("idem-" + operation.name().toLowerCase() + "-" + expectedVersion)
                .runId("run-crossborder-001")
                .correlationId("b4d447f1-27f4-49c0-850e-c4c09241c395")
                .occurredAt(OCCURRED_AT)
                .caseId(caseId)
                .expectedVersion(expectedVersion);
    }

    private CrossBorderCommand.ComplianceAssessment assessment() {
        return CrossBorderCommand.ComplianceAssessment.builder()
                .facts(List.of("订单已支付", "origin_country=CN"))
                .options(List.of("CN_US_DM_STANDARD", "CN_US_DM_FAST"))
                .recommendation("CN_US_DM_STANDARD")
                .risks(List.of("battery check pending"))
                .confidence(new BigDecimal("0.9300"))
                .missingFacts(List.of("final customs broker confirmation"))
                .evidenceRef("restricted:crossborderevidence0001")
                .build();
    }

    private CrossBorderCommand.RouteDefinition route() {
        return CrossBorderCommand.RouteDefinition.builder()
                .routeCode("CN_US_DM_STANDARD")
                .carrierCode("YUNEXPRESS")
                .serviceLevel("STANDARD")
                .slaDays(9)
                .build();
    }

    private CrossBorderCommand.DeclarationDefinition declaration(String declarationId) {
        return CrossBorderCommand.DeclarationDefinition.builder()
                .declarationId(declarationId)
                .hsCode("620443")
                .goodsDescription("Women dress")
                .quantity(new BigDecimal("2"))
                .declaredAmountMinor(23900L)
                .currency("USD")
                .evidenceRef("restricted:declarationevidence0001")
                .build();
    }

    private CrossBorderCommand.DocumentsDefinition documents() {
        return CrossBorderCommand.DocumentsDefinition.builder()
                .orderRef("ORDER-DOC-001")
                .paymentRef("PAY-DOC-001")
                .logisticsRef("LOG-DOC-001")
                .validationEvidenceRef("restricted:documentevidence0001")
                .build();
    }

    private CrossBorderCommand.BookingDefinition booking() {
        return CrossBorderCommand.BookingDefinition.builder()
                .bookingRef("BOOK-001")
                .carrierCode("YUNEXPRESS")
                .serviceLevel("STANDARD")
                .build();
    }

    private CrossBorderCommand.LabelDefinition label() {
        return CrossBorderCommand.LabelDefinition.builder()
                .labelRef("LABEL-001")
                .trackingNumber("TRACK-001")
                .build();
    }

    private static CaseRecord copyCase(CaseRecord source) {
        return new CaseRecord()
                .setCaseId(source.getCaseId())
                .setTenantId(source.getTenantId())
                .setCaseNo(source.getCaseNo())
                .setOrderId(source.getOrderId())
                .setFulfillmentId(source.getFulfillmentId())
                .setTradeMode(source.getTradeMode())
                .setOriginCountry(source.getOriginCountry())
                .setDestinationCountry(source.getDestinationCountry())
                .setStatus(source.getStatus())
                .setAssessmentId(source.getAssessmentId())
                .setRouteCode(source.getRouteCode())
                .setRouteCarrierCode(source.getRouteCarrierCode())
                .setRouteServiceLevel(source.getRouteServiceLevel())
                .setRouteSlaDays(source.getRouteSlaDays())
                .setApprovalRef(source.getApprovalRef())
                .setDeclarationId(source.getDeclarationId())
                .setHsCode(source.getHsCode())
                .setGoodsDescription(source.getGoodsDescription())
                .setQuantity(source.getQuantity())
                .setDeclaredAmountMinor(source.getDeclaredAmountMinor())
                .setCurrency(source.getCurrency())
                .setDeclarationEvidenceRef(source.getDeclarationEvidenceRef())
                .setDocumentOrderRef(source.getDocumentOrderRef())
                .setDocumentPaymentRef(source.getDocumentPaymentRef())
                .setDocumentLogisticsRef(source.getDocumentLogisticsRef())
                .setDocumentValidationEvidenceRef(source.getDocumentValidationEvidenceRef())
                .setBookingRef(source.getBookingRef())
                .setBookingCarrierCode(source.getBookingCarrierCode())
                .setBookingServiceLevel(source.getBookingServiceLevel())
                .setLabelRef(source.getLabelRef())
                .setTrackingNumber(source.getTrackingNumber())
                .setHandoverRef(source.getHandoverRef())
                .setCustomsDeclarationRef(source.getCustomsDeclarationRef())
                .setCustomsReleaseRef(source.getCustomsReleaseRef())
                .setDeliveryEvidenceRef(source.getDeliveryEvidenceRef())
                .setCloseReason(source.getCloseReason())
                .setCreatedByPrincipalId(source.getCreatedByPrincipalId())
                .setVersion(source.getVersion())
                .setCreatedAt(source.getCreatedAt())
                .setUpdatedAt(source.getUpdatedAt())
                .setClosedAt(source.getClosedAt());
    }

    private static ComplianceAssessmentRecord copyAssessment(ComplianceAssessmentRecord source) {
        return new ComplianceAssessmentRecord()
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
