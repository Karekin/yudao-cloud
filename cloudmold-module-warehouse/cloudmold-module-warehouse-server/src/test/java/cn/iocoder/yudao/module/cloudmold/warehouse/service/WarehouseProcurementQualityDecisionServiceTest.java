package cn.iocoder.yudao.module.cloudmold.warehouse.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseProcurementQualityDecisionCommand;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseProcurementQualityDecisionResult;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseProcurementQualityDisposition;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.InboundOperationDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.InboundStatusHistoryDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.ProcurementQualityEffectDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.ReceiptDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.ReceiptLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.ScheduleFulfillmentDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.InboundOperationMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.InboundStatusHistoryMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.ProcurementQualityEffectMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.ReceiptLineMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.ReceiptMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.ScheduleFulfillmentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WarehouseProcurementQualityDecisionServiceTest {

    private static final BigDecimal ZERO = new BigDecimal("0.000000");
    private final InboundOperationMapper operationMapper = mock(InboundOperationMapper.class);
    private final ReceiptMapper receiptMapper = mock(ReceiptMapper.class);
    private final ReceiptLineMapper receiptLineMapper = mock(ReceiptLineMapper.class);
    private final ScheduleFulfillmentMapper scheduleMapper = mock(ScheduleFulfillmentMapper.class);
    private final ProcurementQualityEffectMapper effectMapper = mock(ProcurementQualityEffectMapper.class);
    private final InboundStatusHistoryMapper historyMapper = mock(InboundStatusHistoryMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final WarehouseProcurementQualityDecisionService service =
            new WarehouseProcurementQualityDecisionService(operationMapper, receiptMapper, receiptLineMapper,
                    scheduleMapper, effectMapper, historyMapper, outboxAppender);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(historyMapper.insert(any(InboundStatusHistoryDO.class))).thenReturn(1);
        when(effectMapper.insert(any(ProcurementQualityEffectDO.class))).thenReturn(1);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event", "a".repeat(64), false));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void appliesAcceptedSplitToReceiptLineScheduleAndReceipt() {
        prepareNewOperation();
        ReceiptDO receipt = receipt(1L, "PENDING_QUALITY");
        ReceiptLineDO line = line("10.000000", "0.000000", "0.000000", "0.000000", 1L);
        ScheduleFulfillmentDO schedule = schedule("10.000000", "0.000000", "0.000000", "0.000000", 2L);
        when(receiptMapper.selectForUpdate(1L, "receipt-01")).thenReturn(receipt);
        when(receiptLineMapper.selectForUpdate(1L, "receipt-line-01")).thenReturn(line);
        when(scheduleMapper.selectForUpdate(1L, "po-item-01", "schedule-01")).thenReturn(schedule);
        when(receiptLineMapper.updateQualityCas(eq(1L), eq("receipt-line-01"), eq(1L),
                eq(new BigDecimal("6.000000")), eq(new BigDecimal("4.000000")), eq(ZERO), eq(ZERO),
                eq("PARTIAL_QUALITY_DECIDED"), eq("quality-decision-01"), any())).thenReturn(1);
        when(scheduleMapper.updateQualityCas(eq(1L), eq("schedule-fulfillment-01"), eq(2L),
                eq(new BigDecimal("6.000000")), eq(new BigDecimal("4.000000")), eq(ZERO), eq(ZERO),
                any())).thenReturn(1);
        ReceiptLineDO updated = line("6.000000", "4.000000", "0.000000", "0.000000", 2L);
        when(receiptLineMapper.selectByReceipt(1L, "receipt-01")).thenReturn(List.of(updated));
        when(receiptMapper.updateStatusCas(eq(1L), eq("receipt-01"), eq(1L),
                eq("PARTIAL_QUALITY_DECIDED"), any())).thenReturn(1);

        WarehouseProcurementQualityDecisionResult result = service.execute(command(
                WarehouseProcurementQualityDisposition.ACCEPTED, "4.000000", "quality-accept-01"));

        assertThat(result.getReceiptVersion()).isEqualTo(2L);
        assertThat(result.getReceiptLineVersion()).isEqualTo(2L);
        assertThat(result.getScheduleFulfillmentVersion()).isEqualTo(3L);
        assertThat(result.getFinanceReceiptEvidenceOperationId()).isEqualTo(901L);
        assertThat(result.getFinanceReceiptEvidenceId()).isEqualTo("finance-receipt-evidence-01");
        assertThat(result.getFinanceReceiptEvidenceVersion()).isEqualTo(1L);
        assertThat(result.getPendingQualityQuantity()).isEqualByComparingTo("6.000000");
        assertThat(result.getAcceptedQuantity()).isEqualByComparingTo("4.000000");
        ArgumentCaptor<ProcurementQualityEffectDO> effect = ArgumentCaptor.forClass(ProcurementQualityEffectDO.class);
        verify(effectMapper).insert(effect.capture());
        assertThat(effect.getValue().getDisposition()).isEqualTo("ACCEPTED");
        assertThat(effect.getValue().getReceiptLineVersionAfter()).isEqualTo(2L);
        verify(outboxAppender).append(argThat(event ->
                "warehouse.procurement_receipt.quality_applied".equals(event.getEventType())
                        && "ACCEPTED".equals(event.getPayload().get("disposition"))));
    }

    @Test
    void rejectsDispositionBeyondPendingWithoutWritingEffect() {
        prepareNewOperation();
        when(receiptMapper.selectForUpdate(1L, "receipt-01")).thenReturn(receipt(1L, "PENDING_QUALITY"));
        when(receiptLineMapper.selectForUpdate(1L, "receipt-line-01"))
                .thenReturn(line("2.000000", "8.000000", "0.000000", "0.000000", 1L));
        when(scheduleMapper.selectForUpdate(1L, "po-item-01", "schedule-01"))
                .thenReturn(schedule("2.000000", "8.000000", "0.000000", "0.000000", 1L));

        assertThatThrownBy(() -> service.execute(command(
                WarehouseProcurementQualityDisposition.REJECTED, "3.000000", "quality-reject-over")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("quality disposition exceeds pending quantity");
        verifyNoInteractions(effectMapper, outboxAppender);
        verify(operationMapper, never()).markSucceeded(anyLong(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void duplicateReturnsImmutableFirstResultWithoutReapplying() {
        WarehouseProcurementQualityDecisionCommand command = command(
                WarehouseProcurementQualityDisposition.QUARANTINED, "1.000000", "quality-quarantine-01");
        WarehouseProcurementQualityDecisionResult stored = WarehouseProcurementQualityDecisionResult.builder()
                .operationId(91L).receiptId("receipt-01").receiptLineId("receipt-line-01")
                .receiptVersion(4L).receiptLineVersion(5L).scheduleFulfillmentVersion(6L)
                .financeReceiptEvidenceOperationId(901L)
                .financeReceiptEvidenceId("finance-receipt-evidence-01").financeReceiptEvidenceVersion(1L)
                .receiptStatus("QUALITY_QUARANTINED").receiptLineStatus("QUALITY_QUARANTINED")
                .pendingQualityQuantity(ZERO).acceptedQuantity(ZERO).rejectedQuantity(ZERO)
                .quarantinedQuantity(new BigDecimal("1.000000")).duplicate(false).build();
        when(operationMapper.selectLastInsertId()).thenReturn(91L);
        when(operationMapper.selectForUpdate(91L, 1L)).thenReturn(new InboundOperationDO()
                .setOperationId(91L).setTenantId(1L).setAttemptToken("original-attempt")
                .setRequestHash(cn.hutool.crypto.digest.DigestUtil.sha256Hex(
                        1L + "\u001f" + JsonUtils.toJsonString(command)))
                .setStatus(10).setResultJson(JsonUtils.toJsonString(stored)));

        WarehouseProcurementQualityDecisionResult replay = service.execute(command);

        assertThat(replay.isDuplicate()).isTrue();
        assertThat(replay.getScheduleFulfillmentVersion()).isEqualTo(6L);
        assertThat(replay.getFinanceReceiptEvidenceVersion()).isEqualTo(1L);
        verifyNoInteractions(receiptMapper, receiptLineMapper, scheduleMapper, effectMapper, historyMapper, outboxAppender);
    }

    @Test
    void derivesMixedAndPutawayStatusesFromConservedQuantities() {
        assertThat(WarehouseProcurementQualityDecisionService.deriveLineStatus(
                new BigDecimal("10"), ZERO, new BigDecimal("5"), new BigDecimal("3"),
                new BigDecimal("2"), ZERO)).isEqualTo("QUALITY_MIXED");
        assertThat(WarehouseProcurementQualityDecisionService.deriveLineStatus(
                new BigDecimal("10"), new BigDecimal("2"), new BigDecimal("8"), ZERO,
                ZERO, new BigDecimal("4"))).isEqualTo("PARTIALLY_PUTAWAY");
    }

    private void prepareNewOperation() {
        AtomicReference<String> token = new AtomicReference<>();
        when(operationMapper.insertOrResolve(eq(1L), anyString(), anyString(),
                eq("PROCUREMENT_QUALITY_DECISION"), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    token.set(invocation.getArgument(5));
                    return 1;
                });
        when(operationMapper.selectLastInsertId()).thenReturn(91L);
        when(operationMapper.selectForUpdate(91L, 1L)).thenAnswer(ignored -> new InboundOperationDO()
                .setOperationId(91L).setTenantId(1L).setAttemptToken(token.get()).setStatus(0));
        when(operationMapper.markSucceeded(eq(91L), eq(1L), eq("receipt-01"), anyString(), any())).thenReturn(1);
    }

    private WarehouseProcurementQualityDecisionCommand command(WarehouseProcurementQualityDisposition disposition,
                                                               String quantity, String idempotencyKey) {
        return WarehouseProcurementQualityDecisionCommand.builder().idempotencyKey(idempotencyKey)
                .sourceEventId(idempotencyKey + "-event").qualityDecisionId("quality-decision-01")
                .decisionVersion(2L).inspectionSplitId("inspection-split-01").disposition(disposition)
                .receiptId("receipt-01").receiptLineId("receipt-line-01").procurementOrderId("po-01")
                .procurementOrderItemId("po-item-01").deliveryScheduleId("schedule-01")
                .warehouseId("warehouse-01").locationId("location-01").lotId("lot-01")
                .baseUomCode("PIECE").quantity(new BigDecimal(quantity)).evidenceRef("evidence-sha256-ref")
                .correlationId("correlation-01").occurredAt(Instant.parse("2026-08-02T10:00:00Z")).build();
    }

    private ReceiptDO receipt(Long version, String status) {
        return new ReceiptDO().setReceiptId("receipt-01").setProcurementOrderId("po-01")
                .setWarehouseId("warehouse-01").setStatus(status).setVersion(version);
    }

    private ReceiptLineDO line(String pending, String accepted, String rejected, String quarantined, Long version) {
        return new ReceiptLineDO().setReceiptLineId("receipt-line-01").setReceiptId("receipt-01")
                .setProcurementOrderId("po-01").setProcurementOrderItemId("po-item-01")
                .setDeliveryScheduleId("schedule-01").setWarehouseId("warehouse-01")
                .setReceiptLocationId("location-01").setLotId("lot-01").setBaseUomCode("PIECE")
                .setReceivedQuantity(new BigDecimal("10.000000")).setPendingQualityQuantity(new BigDecimal(pending))
                .setAcceptedQuantity(new BigDecimal(accepted)).setRejectedQuantity(new BigDecimal(rejected))
                .setQuarantinedQuantity(new BigDecimal(quarantined)).setCumulativePutawayQuantity(ZERO)
                .setFinanceReceiptEvidenceOperationId(901L)
                .setFinanceReceiptEvidenceId("finance-receipt-evidence-01")
                .setFinanceReceiptEvidenceVersion(1L)
                .setVersion(version);
    }

    private ScheduleFulfillmentDO schedule(String pending, String accepted, String rejected,
                                           String quarantined, Long version) {
        return new ScheduleFulfillmentDO().setScheduleFulfillmentId("schedule-fulfillment-01")
                .setProcurementOrderId("po-01").setProcurementOrderItemId("po-item-01")
                .setDeliveryScheduleId("schedule-01").setReceivedQuantity(new BigDecimal("10.000000"))
                .setPendingQualityQuantity(new BigDecimal(pending)).setAcceptedQuantity(new BigDecimal(accepted))
                .setRejectedQuantity(new BigDecimal(rejected)).setQuarantinedQuantity(new BigDecimal(quarantined))
                .setVersion(version);
    }
}
