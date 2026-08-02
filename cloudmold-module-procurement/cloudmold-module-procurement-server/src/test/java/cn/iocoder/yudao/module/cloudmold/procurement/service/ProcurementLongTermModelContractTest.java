package cn.iocoder.yudao.module.cloudmold.procurement.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.procurement.api.AwardReleaseCommand;
import cn.iocoder.yudao.module.cloudmold.procurement.api.AwardReleaseResult;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.OrderStatusHistory;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseOrderAwardSource;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseOrderDeliverySchedule;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseOrderItem;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementSourcingRecords.Award;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementSourcingRecords.AwardSnapshot;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementSourcingRecords.AwardSnapshotLine;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql.ProcurementMapper;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql.ProcurementSourcingMapper;
import cn.iocoder.yudao.module.cloudmold.procurement.service.actor.ProcurementActorPrincipalPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcurementLongTermModelContractTest {
    private static final String ACTOR = "principal-buyer-01";

    private final ProcurementMapper procurementMapper = mock(ProcurementMapper.class);
    private final ProcurementSourcingMapper sourcingMapper = mock(ProcurementSourcingMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final ProcurementActorPrincipalPort actorPrincipalPort = mock(ProcurementActorPrincipalPort.class);
    private final AwardReleaseService service = new AwardReleaseService(
            procurementMapper, sourcingMapper, outboxAppender, actorPrincipalPort);
    private final AtomicReference<String> requestHash = new AtomicReference<>();
    private final AtomicReference<String> attemptToken = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(31L);
        when(procurementMapper.insertOrResolveOperation(eq(31L), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    requestHash.set(invocation.getArgument(3));
                    attemptToken.set(invocation.getArgument(4));
                    return 1;
                });
        when(procurementMapper.selectLastInsertId()).thenReturn(501L);
        when(procurementMapper.selectOperationForUpdate(501L, 31L)).thenAnswer(invocation ->
                new Operation().setOperationId(501L).setTenantId(31L)
                        .setRequestHash(requestHash.get()).setAttemptToken(attemptToken.get()).setStatus(0));
        when(sourcingMapper.selectAwardForUpdate(31L, "award-01")).thenReturn(new Award()
                .setAwardId("award-01").setTenantId(31L).setStatus("APPROVED").setVersion(4L));
        when(sourcingMapper.selectAwardSnapshotForUpdate(31L, "award-01", 4L)).thenReturn(snapshot());
        when(sourcingMapper.selectAwardSnapshotLines(31L, "award-01:v4")).thenReturn(List.of(
                line("award-line-10", 10, "supplier-alpha", "CNY", "sku-01", "12.000000", "12000", "1560", "13560", LocalDate.of(2026, 8, 5), "valuation-policy-01"),
                line("award-line-20", 20, "supplier-beta", "USD", "sku-02", "8.000000", "8000", "1040", "9040", LocalDate.of(2026, 8, 7), "valuation-policy-02"),
                line("award-line-30", 30, "supplier-alpha", "CNY", "sku-03", "5.000000", "5000", "650", "5650", LocalDate.of(2026, 8, 9), "valuation-policy-03")));
        when(procurementMapper.countAwardLineConsumptions(31L, "award-01", 4L)).thenReturn(0L);
        when(procurementMapper.insertOrder(any())).thenReturn(1);
        when(procurementMapper.insertItems(anyList())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        when(procurementMapper.insertSchedules(anyList())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        when(procurementMapper.insertAwardSources(anyList())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        when(procurementMapper.insertStatusHistory(any(OrderStatusHistory.class))).thenReturn(1);
        when(procurementMapper.markOperationSucceeded(eq(501L), eq(31L), eq("procurement_award"), eq("award-01"), anyString(), any()))
                .thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void splitsApprovedSnapshotIntoOneDraftOrderPerSupplierAndCurrency() {
        AwardReleaseResult result = service.releaseApprovedAward(command(), ACTOR);

        assertThat(result.getAwardId()).isEqualTo("award-01");
        assertThat(result.getAwardVersion()).isEqualTo(4L);
        assertThat(result.getPurchaseOrders()).hasSize(2);
        assertThat(result.getPurchaseOrders())
                .extracting(order -> order.getSupplierId() + ":" + order.getCurrencyCode())
                .containsExactly("supplier-alpha:CNY", "supplier-beta:USD");
        assertThat(result.getPurchaseOrders().get(0).getItems()).hasSize(2);
        assertThat(result.getPurchaseOrders().get(0).getHeaderGrossAmountMinor()).isEqualTo(19210L);
        assertThat(result.getPurchaseOrders().get(1).getItems()).hasSize(1);
        verify(procurementMapper, org.mockito.Mockito.times(2)).insertOrder(argThat(order ->
                order.getSourceBusinessType().equals("SOURCING_AWARD")
                        && order.getSourceBusinessRef().equals("award-01")
                        && order.getReasonCode().equals("AWARD_RELEASED")
                        && order.getTaxCalculationPolicyCode().equals("STANDARD_V1")
                        && order.getRoundingPolicyCode().equals("HALF_UP")));
        verify(procurementMapper, org.mockito.Mockito.times(2)).insertAwardSources(argThat((List<PurchaseOrderAwardSource> rows) ->
                rows.stream().allMatch(row -> row.getSourceSnapshotId().equals("award-01:v4"))));
        verify(outboxAppender, org.mockito.Mockito.times(2)).append(argThat(event ->
                event.getEventType().equals("procurement.order.created")
                        && event.getAggregateType().equals("procurement_order")));
    }

    @Test
    void replaysDuplicateIdempotencyWithoutCreatingOrdersAgain() {
        when(procurementMapper.selectOperationForUpdate(501L, 31L)).thenAnswer(invocation ->
                new Operation().setOperationId(501L).setTenantId(31L)
                        .setRequestHash(requestHash.get()).setAttemptToken("another-attempt").setStatus(10)
                        .setResultJson("{\"operationId\":501,\"duplicate\":false,\"aggregateType\":\"procurement_award\",\"awardId\":\"award-01\",\"awardVersion\":4,\"purchaseOrders\":[]}"));

        AwardReleaseResult replay = service.releaseApprovedAward(command(), ACTOR);

        assertThat(replay.isDuplicate()).isTrue();
        verify(procurementMapper, never()).insertOrder(any());
    }

    @Test
    void rejectsIncompleteSnapshotWithoutFrozenValuationPolicy() {
        when(sourcingMapper.selectAwardSnapshotLines(31L, "award-01:v4")).thenReturn(List.of(
                line("award-line-10", 10, "supplier-alpha", "CNY", "sku-01", "12.000000", "12000", "1560", "13560", LocalDate.of(2026, 8, 5), null)));

        assertThatThrownBy(() -> service.releaseApprovedAward(command(), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("valuationPolicyId must be a safe opaque reference");
        verify(procurementMapper, never()).insertOrder(any());
        verify(outboxAppender, never()).append(any());
    }

    @Test
    void rejectsSecondReleaseWhenAwardLinesWereAlreadyConsumed() {
        when(procurementMapper.countAwardLineConsumptions(31L, "award-01", 4L)).thenReturn(1L);

        assertThatThrownBy(() -> service.releaseApprovedAward(command(), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("approved award snapshot has already been released");
        verify(procurementMapper, never()).insertOrder(any());
    }

    private static AwardReleaseCommand command() {
        return AwardReleaseCommand.builder()
                .idempotencyKey("award-release:award-01:v4")
                .runId("run-award-release-01")
                .correlationId("11111111-1111-4111-8111-111111111111")
                .occurredAt(Instant.parse("2026-08-02T09:00:00Z"))
                .awardId("award-01")
                .expectedAwardVersion(4L)
                .build();
    }

    private static AwardSnapshot snapshot() {
        return new AwardSnapshot()
                .setSnapshotId("award-01:v4")
                .setTenantId(31L)
                .setAwardId("award-01")
                .setAwardVersion(4L)
                .setEventId("event-01")
                .setEventVersion(7L)
                .setRequisitionId("pr-01")
                .setRequisitionVersion(3L)
                .setLegalEntityId("legal-entity-01")
                .setTaxCalculationPolicyCode("STANDARD_V1")
                .setRoundingPolicyCode("HALF_UP")
                .setStatus("APPROVED")
                .setDecisionReasonCode("BEST_VALUE")
                .setApprovedByPrincipalId("principal-approver-01")
                .setApprovedAt(LocalDateTime.of(2026, 8, 2, 9, 0, 0))
                .setCreatedAt(LocalDateTime.of(2026, 8, 2, 9, 0, 0));
    }

    private static AwardSnapshotLine line(String awardLineId, int lineNumber, String supplierId, String currencyCode,
                                          String skuId, String quantity, String net, String tax, String gross,
                                          LocalDate promisedDate, String valuationPolicyId) {
        AwardSnapshotLine line = new AwardSnapshotLine();
        line.setSnapshotLineId("award-01:v4:" + lineNumber);
        line.setSnapshotId("award-01:v4");
        line.setTenantId(31L);
        line.setAwardId("award-01");
        line.setAwardLineId(awardLineId);
        line.setLineNumber(lineNumber);
        line.setSourcingLineId("sourcing-line-" + lineNumber);
        line.setSourcingScheduleId("sourcing-schedule-" + lineNumber);
        line.setQuotationRevisionLineId("revision-line-" + lineNumber);
        line.setQuotationRevisionScheduleId("revision-schedule-" + lineNumber);
        line.setSupplierId(supplierId);
        line.setCanonicalSkuId(skuId);
        line.setCanonicalWarehouseId("warehouse-01");
        line.setAwardedQuantity(new BigDecimal(quantity));
        line.setUomCode("EA");
        line.setCurrencyCode(currencyCode);
        line.setUnitNetPriceMinor(new BigDecimal("1000.000000"));
        line.setTaxCode("VAT13");
        line.setTaxRateBps(1300);
        line.setPromisedDeliveryDate(promisedDate);
        line.setLineNetAmountMinor(Long.parseLong(net));
        line.setLineTaxAmountMinor(Long.parseLong(tax));
        line.setLineGrossAmountMinor(Long.parseLong(gross));
        line.setPolicyId("evaluation-policy-01");
        line.setPolicyVersion(1);
        line.setEvaluationWeightedScoreBps(9200);
        line.setEvaluationSummarySha256("a".repeat(64));
        line.setReviewerEvidenceSha256("b".repeat(64));
        line.setRequisitionLineId("requisition-line-" + lineNumber);
        line.setRequisitionScheduleId("requisition-schedule-" + lineNumber);
        line.setValuationPolicyId(valuationPolicyId);
        line.setValuationPolicyVersion("v1");
        line.setValuationPolicyHash("c".repeat(64));
        return line;
    }
}
