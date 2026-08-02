package cn.iocoder.yudao.module.cloudmold.procurement.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.procurement.api.*;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.*;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementSourcingRecords.*;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql.*;
import cn.iocoder.yudao.module.cloudmold.procurement.service.actor.ProcurementActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.procurement.service.reference.ProcurementReferenceValidationPort;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcurementSourcingServiceTest {
    private final ProcurementMapper procurement=mock(ProcurementMapper.class);
    private final ProcurementSourcingMapper sourcing=mock(ProcurementSourcingMapper.class);
    private final OutboxAppender outbox=mock(OutboxAppender.class);
    private final ProcurementActorPrincipalPort actor=mock(ProcurementActorPrincipalPort.class);
    private final ProcurementReferenceValidationPort refs=mock(ProcurementReferenceValidationPort.class);
    private final ProcurementSourcingService service=new ProcurementSourcingService(procurement,sourcing,outbox,actor,refs);
    private final AtomicReference<String> hash=new AtomicReference<>(), token=new AtomicReference<>();

    @BeforeEach void setup(){ TenantContextHolder.setTenantId(31L);
        when(procurement.insertOrResolveOperation(eq(31L),anyString(),anyString(),anyString(),anyString(),any())).thenAnswer(i->{hash.set(i.getArgument(3));token.set(i.getArgument(4));return 1;});
        when(procurement.selectLastInsertId()).thenReturn(9L); when(procurement.selectOperationForUpdate(9L,31L)).thenAnswer(i->new Operation().setAttemptToken(token.get()).setRequestHash(hash.get()).setStatus(0));
        when(procurement.markOperationSucceeded(eq(9L),eq(31L),anyString(),anyString(),anyString(),any())).thenReturn(1);
        when(sourcing.insertHistory(any())).thenReturn(1); when(sourcing.insertAwardHistory(any())).thenReturn(1);
    }
    @AfterEach void clear(){TenantContextHolder.clear();}

    @Test void freezesExactApprovedRequisitionLineAndMultipleSchedules(){
        when(procurement.selectPurchaseRequisitionById(31L,"pr-1")).thenReturn(new PurchaseRequisition().setRequisitionId("pr-1").setStatus("APPROVED").setVersion(1L));
        when(procurement.selectPurchaseRequisitionLines(31L,"pr-1")).thenReturn(List.of(new PurchaseRequisitionLine().setLineId("prl-1").setCanonicalSkuId("sku-1").setRequestedQuantity(new BigDecimal("20")).setUomCode("EA")));
        when(procurement.selectPurchaseRequisitionSchedules(31L,"pr-1")).thenReturn(List.of(
                new PurchaseRequisitionDeliverySchedule().setScheduleId("prs-1").setLineId("prl-1").setCanonicalWarehouseId("wh-1").setScheduledQuantity(new BigDecimal("8")).setRequiredDeliveryDate(LocalDate.of(2026,8,20)),
                new PurchaseRequisitionDeliverySchedule().setScheduleId("prs-2").setLineId("prl-1").setCanonicalWarehouseId("wh-2").setScheduledQuantity(new BigDecimal("12")).setRequiredDeliveryDate(LocalDate.of(2026,8,25))));
        when(sourcing.insertEvent(any())).thenReturn(1); when(sourcing.insertLines(anyList())).thenReturn(1); when(sourcing.insertSchedules(anyList())).thenReturn(2);
        SourcingResult result=service.execute(base(SourcingOperation.CREATE_SOURCING_EVENT).event(SourcingCommand.EventDefinition.builder()
                .eventId("rfq-1").eventCode("RFQ_1").requisitionId("pr-1").title("Fall fabrics").quotationDeadline(LocalDateTime.of(2026,8,10,0,0))
                .lines(List.of(SourcingCommand.EventLineDefinition.builder().sourcingLineId("rfql-1").lineNumber(10).requisitionLineId("prl-1").schedules(List.of(
                        SourcingCommand.EventScheduleDefinition.builder().sourcingScheduleId("rfqs-1").scheduleNumber(10).requisitionScheduleId("prs-1").build(),
                        SourcingCommand.EventScheduleDefinition.builder().sourcingScheduleId("rfqs-2").scheduleNumber(20).requisitionScheduleId("prs-2").build())).build())).build()).build(),"buyer-1");
        assertThat(result.getStatus()).isEqualTo("DRAFT");
        verify(sourcing).insertSchedules(argThat(v->v.size()==2&&v.stream().map(SourcingSchedule::getCanonicalWarehouseId).collect(java.util.stream.Collectors.toSet()).equals(Set.of("wh-1","wh-2"))));
        verify(outbox).append(argThat(e->e.getSchemaVersion()==2&&e.getEventType().equals("procurement.sourcing_event.created")));
    }

    @Test void createsImmutableRevisionAndExactScheduleReferences(){
        when(sourcing.selectEventForUpdate(31L,"rfq-1")).thenReturn(event("QUOTING",3)); when(sourcing.countActiveInvitation(31L,"rfq-1","supplier-1")).thenReturn(1);
        when(sourcing.selectQuotationForUpdate(31L,"rfq-1","supplier-1")).thenReturn(null); when(sourcing.insertQuotation(any())).thenReturn(1);
        when(sourcing.selectLines(31L,"rfq-1")).thenReturn(List.of(line())); when(sourcing.selectSchedules(31L,"rfq-1")).thenReturn(List.of(schedule("rfqs-1",new BigDecimal("8")),schedule("rfqs-2",new BigDecimal("12"))));
        when(sourcing.insertRevision(any())).thenReturn(1); when(sourcing.insertRevisionLines(anyList())).thenReturn(1); when(sourcing.insertRevisionSchedules(anyList())).thenReturn(2); when(sourcing.activateInitialRevision(31L,"q-1","rev-1")).thenReturn(1); when(sourcing.bumpEvent(31L,"rfq-1","QUOTING",3L,LocalDateTime.of(2026,8,2,0,0))).thenReturn(1);
        SourcingResult result=service.execute(base(SourcingOperation.SUBMIT_QUOTATION_REVISION).quotationRevision(revision()).build(),"supplier-user-1");
        assertThat(result.getAggregateVersion()).isEqualTo(4L);
        verify(sourcing).insertRevision(argThat(r->r.getStatus().equals("SUBMITTED")&&r.getPayloadSha256().length()==64));
        verify(sourcing).insertRevisionSchedules(argThat(v->v.size()==2&&v.stream().map(QuotationRevisionSchedule::getSourcingScheduleId).collect(java.util.stream.Collectors.toSet()).equals(Set.of("rfqs-1","rfqs-2"))));
    }

    @Test void recordsVersionedEvaluationWithReviewerEvidence(){
        when(sourcing.selectEventForUpdate(31L,"rfq-1")).thenReturn(event("EVALUATING",8)); when(sourcing.selectPolicy(31L,"rfq-1","policy-1")).thenReturn(new EvaluationPolicy().setPolicyId("policy-1").setActiveVersion(1));
        when(sourcing.selectRevisionForUpdate(31L,"rev-1")).thenReturn(new QuotationRevision().setRevisionId("rev-1").setEventId("rfq-1").setStatus("SUBMITTED"));
        when(sourcing.selectDimensions(31L,"policy-1",1)).thenReturn(List.of(new EvaluationDimension().setDimensionId("dim-price").setWeightBps(6000).setMaximumScore(100),new EvaluationDimension().setDimensionId("dim-delivery").setWeightBps(4000).setMaximumScore(100)));
        when(sourcing.insertScore(any())).thenReturn(1); when(sourcing.insertDimensionScores(anyList())).thenReturn(2); when(sourcing.bumpEvent(eq(31L),eq("rfq-1"),eq("EVALUATING"),eq(8L),any())).thenReturn(1);
        service.execute(base(SourcingOperation.RECORD_EVALUATION_SCORE).evaluationScore(SourcingCommand.EvaluationScoreDefinition.builder().scoreId("score-1").eventId("rfq-1").policyId("policy-1").policyVersion(1).quotationRevisionId("rev-1")
                .reviewerEvidenceSha256("a".repeat(64)).expectedEventVersion(8L).dimensions(List.of(
                        SourcingCommand.DimensionScoreDefinition.builder().dimensionId("dim-price").score(90).evidenceReference("evidence://price").build(),
                        SourcingCommand.DimensionScoreDefinition.builder().dimensionId("dim-delivery").score(80).evidenceReference("evidence://delivery").build())).build()).build(),"reviewer-1");
        verify(sourcing).insertScore(argThat(s->s.getWeightedScoreBps()==8600&&s.getReviewerPrincipalId().equals("reviewer-1")&&s.getEvaluationSummarySha256().length()==64));
    }

    @Test void freezesEvaluationEvidenceOnExactAwardSchedule(){
        when(sourcing.selectEventForUpdate(31L,"rfq-1")).thenReturn(event("EVALUATING",10)); when(sourcing.selectPolicy(31L,"rfq-1","policy-1")).thenReturn(new EvaluationPolicy().setPolicyId("policy-1").setActiveVersion(1));
        when(sourcing.selectLines(31L,"rfq-1")).thenReturn(List.of(line().setRequestedQuantity(new BigDecimal("8")))); when(sourcing.selectSchedules(31L,"rfq-1")).thenReturn(List.of(schedule("rfqs-1",new BigDecimal("8"))));
        when(sourcing.selectActiveRevisionLines(31L,"rfq-1")).thenReturn(List.of(new QuotationRevisionLine().setRevisionLineId("qrl-1").setRevisionId("rev-1").setSourcingLineId("rfql-1").setUnitNetPriceMinor(new BigDecimal("100")).setTaxCode("VAT13").setTaxRateBps(1300)));
        when(sourcing.selectActiveRevisionSchedules(31L,"rfq-1")).thenReturn(List.of(new QuotationRevisionSchedule().setRevisionScheduleId("qrs-1").setRevisionId("rev-1").setRevisionLineId("qrl-1").setSourcingScheduleId("rfqs-1").setOfferedQuantity(new BigDecimal("8")).setPromisedDeliveryDate(LocalDate.of(2026,8,20))));
        when(sourcing.selectActiveRevisions(31L,"rfq-1")).thenReturn(List.of(new QuotationRevision().setRevisionId("rev-1").setQuotationId("q-1")));
        when(sourcing.selectQuotations(31L,"rfq-1")).thenReturn(List.of(new Quotation().setQuotationId("q-1").setSupplierId("supplier-1").setCurrencyCode("CNY")));
        when(sourcing.selectScores(31L,"rfq-1","policy-1",1,"rev-1")).thenReturn(List.of(new EvaluationScore().setWeightedScoreBps(8600).setEvaluationSummarySha256("b".repeat(64)).setReviewerEvidenceSha256("c".repeat(64))));
        when(sourcing.insertAward(any())).thenReturn(1); when(sourcing.insertAwardLines(anyList())).thenReturn(1); when(sourcing.bumpEvent(eq(31L),eq("rfq-1"),eq("EVALUATING"),eq(10L),any())).thenReturn(1);
        SourcingResult result=service.execute(base(SourcingOperation.CREATE_AWARD_DRAFT).award(SourcingCommand.AwardDefinition.builder().awardId("award-1").awardCode("AWARD_1").eventId("rfq-1").policyId("policy-1").policyVersion(1).decisionReasonCode("BEST_VALUE").expectedEventVersion(10L)
                .lines(List.of(SourcingCommand.AwardLineDefinition.builder().awardLineId("al-1").lineNumber(10).sourcingLineId("rfql-1").sourcingScheduleId("rfqs-1").quotationRevisionLineId("qrl-1").quotationRevisionScheduleId("qrs-1").supplierId("supplier-1").awardedQuantity(new BigDecimal("8")).build())).build()).build(),"buyer-1");
        assertThat(result.getStatus()).isEqualTo("DRAFT"); verify(sourcing).insertAwardLines(argThat(v->v.size()==1&&v.get(0).getEvaluationWeightedScoreBps()==8600&&v.get(0).getEvaluationSummarySha256().length()==64&&v.get(0).getReviewerEvidenceSha256().length()==64));
    }

    @Test void rejectsApprovalByAwardMakerOrEvaluationReviewer(){
        Award award=new Award().setAwardId("award-1").setAwardCode("AWARD_1").setEventId("rfq-1").setStatus("SUBMITTED").setVersion(2L).setCreatedByPrincipalId("buyer-1").setSubmittedByPrincipalId("submitter-1");
        when(sourcing.selectAwardForUpdate(31L,"award-1")).thenReturn(award); when(sourcing.selectAwardReviewerPrincipalIds(31L,"award-1")).thenReturn(List.of("reviewer-1"));
        SourcingCommand command=base(SourcingOperation.APPROVE_AWARD).awardTransition(SourcingCommand.AwardTransitionDefinition.builder().awardId("award-1").expectedAwardVersion(2L).expectedEventVersion(12L).reasonCode("APPROVED").build()).build();
        assertThatThrownBy(()->service.execute(command,"buyer-1")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maker-checker");
    }

    @Test void independentCheckerApprovesAndCreatesImmutableSnapshot(){
        Award award=new Award().setAwardId("award-1").setAwardCode("AWARD_1").setEventId("rfq-1").setStatus("SUBMITTED").setVersion(2L).setDecisionReasonCode("BEST_VALUE").setCreatedByPrincipalId("buyer-1").setSubmittedByPrincipalId("submitter-1");
        when(sourcing.selectAwardForUpdate(31L,"award-1")).thenReturn(award); when(sourcing.selectAwardReviewerPrincipalIds(31L,"award-1")).thenReturn(List.of("reviewer-1")); when(sourcing.selectEventForUpdate(31L,"rfq-1")).thenReturn(event("AWARD_SUBMITTED",12));
        when(sourcing.transitionAward(eq(31L),eq("award-1"),eq("SUBMITTED"),eq("APPROVED"),eq(2L),eq("checker-1"),any())).thenReturn(1);
        when(sourcing.selectAwardLines(31L,"award-1")).thenReturn(List.of(new AwardLine().setAwardLineId("al-1").setTenantId(31L).setAwardId("award-1").setLineNumber(10).setSourcingLineId("rfql-1").setSourcingScheduleId("rfqs-1").setQuotationRevisionLineId("qrl-1").setQuotationRevisionScheduleId("qrs-1").setSupplierId("supplier-1").setCanonicalSkuId("sku-1").setCanonicalWarehouseId("wh-1").setAwardedQuantity(new BigDecimal("8")).setUomCode("EA").setCurrencyCode("CNY").setUnitNetPriceMinor(new BigDecimal("100")).setTaxCode("VAT13").setTaxRateBps(1300).setPromisedDeliveryDate(LocalDate.of(2026,8,20)).setLineNetAmountMinor(800L).setLineTaxAmountMinor(104L).setLineGrossAmountMinor(904L).setPolicyId("policy-1").setPolicyVersion(1).setEvaluationWeightedScoreBps(8600).setEvaluationSummarySha256("b".repeat(64)).setReviewerEvidenceSha256("c".repeat(64))));
        when(sourcing.insertAwardSnapshot(any())).thenReturn(1); when(sourcing.insertAwardSnapshotLines(anyList())).thenReturn(1); when(sourcing.transitionEvent(eq(31L),eq("rfq-1"),eq("AWARD_SUBMITTED"),eq("AWARDED"),eq(12L),eq("checker-1"),isNull(),any())).thenReturn(1);
        SourcingResult result=service.execute(base(SourcingOperation.APPROVE_AWARD).awardTransition(SourcingCommand.AwardTransitionDefinition.builder().awardId("award-1").expectedAwardVersion(2L).expectedEventVersion(12L).reasonCode("APPROVED").build()).build(),"checker-1");
        assertThat(result.getStatus()).isEqualTo("APPROVED"); verify(sourcing).insertAwardSnapshot(argThat(s->s.getAwardVersion()==3L&&s.getEventVersion()==13L));
        verify(sourcing).insertAwardSnapshotLines(argThat(v->v.size()==1&&v.get(0).getQuotationRevisionScheduleId().equals("qrs-1")&&v.get(0).getEvaluationSummarySha256().length()==64));
    }

    private static SourcingEvent event(String status,long version){return new SourcingEvent().setEventId("rfq-1").setEventCode("RFQ_1").setStatus(status).setVersion(version).setQuotationDeadline(LocalDateTime.of(2026,8,10,0,0));}
    private static SourcingLine line(){return new SourcingLine().setSourcingLineId("rfql-1").setCanonicalSkuId("sku-1").setRequestedQuantity(new BigDecimal("20")).setUomCode("EA");}
    private static SourcingSchedule schedule(String id,BigDecimal qty){return new SourcingSchedule().setSourcingScheduleId(id).setSourcingLineId("rfql-1").setCanonicalWarehouseId(id.equals("rfqs-1")?"wh-1":"wh-2").setRequestedQuantity(qty);}
    private static SourcingCommand.QuotationRevisionDefinition revision(){return SourcingCommand.QuotationRevisionDefinition.builder().quotationId("q-1").quotationCode("QUOTE_1").revisionId("rev-1").revisionNumber(1).eventId("rfq-1").supplierId("supplier-1").currencyCode("CNY").expectedEventVersion(3L).lines(List.of(
            SourcingCommand.QuotationRevisionLineDefinition.builder().revisionLineId("qrl-1").lineNumber(10).sourcingLineId("rfql-1").offeredQuantity(new BigDecimal("20")).uomCode("EA").unitNetPriceMinor(new BigDecimal("100")).taxCode("VAT13").taxRateBps(1300).schedules(List.of(
                    SourcingCommand.QuotationRevisionScheduleDefinition.builder().revisionScheduleId("qrs-1").scheduleNumber(10).sourcingScheduleId("rfqs-1").offeredQuantity(new BigDecimal("8")).promisedDeliveryDate(LocalDate.of(2026,8,20)).build(),
                    SourcingCommand.QuotationRevisionScheduleDefinition.builder().revisionScheduleId("qrs-2").scheduleNumber(20).sourcingScheduleId("rfqs-2").offeredQuantity(new BigDecimal("12")).promisedDeliveryDate(LocalDate.of(2026,8,25)).build())).build())).build();}
    private static SourcingCommand.SourcingCommandBuilder base(SourcingOperation op){return SourcingCommand.builder().operation(op).idempotencyKey("idem-"+op).runId("run-1").correlationId("corr-1").causationId("cause-1").occurredAt(Instant.parse("2026-08-02T00:00:00Z"));}
}
