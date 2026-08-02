package cn.iocoder.yudao.module.cloudmold.finance.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.FinanceCommandEnvelope;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.JournalDimensionAssignment;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.ProcureToPayResult;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.SupplierReturnReversalCommands;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.AccountingPeriod;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureToPayRecords.*;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.SupplierReturnReversalRecords.*;
import cn.iocoder.yudao.module.cloudmold.finance.dal.mysql.SupplierReturnReversalMapper;
import cn.iocoder.yudao.module.cloudmold.finance.service.actor.FinanceActorPrincipalPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupplierReturnReversalServiceTest {

    private final SupplierReturnReversalMapper mapper = mock(SupplierReturnReversalMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final FinanceActorPrincipalPort actorPort = mock(FinanceActorPrincipalPort.class);
    private final SupplierReturnReversalService service = spy(
            new SupplierReturnReversalService(mapper, outboxAppender, actorPort));

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        doReturn("attempt-1").when(service).nextAttemptToken();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void postsAcceptedSupplierReturnFinanceReversalAtomically() {
        SupplierReturnReversalCommands.Post command = command();
        when(mapper.selectLastInsertId()).thenReturn(11L);
        when(mapper.selectOperationForUpdate(1L, 11L)).thenReturn(new Operation()
                .setOperationId(11L).setTenantId(1L).setAttemptToken("attempt-1")
                .setRequestHash(DigestUtil.sha256Hex("principal-01\n" + JsonUtils.toJsonString(command)))
                .setStatus(0));
        when(mapper.selectWarehouseReturn(1L, "return-01")).thenReturn(new WarehouseReturn()
                .setReturnId("return-01").setReturnCode("SRTRN-0001").setPurchaseOrderId("po-01")
                .setReceiptId("receipt-01").setSupplierId("supplier-01").setOwnerType("MERCHANT")
                .setOwnerId("merchant-01").setWarehouseId("warehouse-01").setStatus("DISPATCHED").setVersion(3L));
        when(mapper.selectWarehouseReturnLines(1L, "return-01")).thenReturn(List.of(new WarehouseReturnLine()
                .setReturnLineId("line-01").setLineNumber(10).setReceiptLineId("receipt-line-01")
                .setPurchaseOrderItemId("item-01").setPurchaseOrderScheduleId("schedule-01")
                .setQualityDecisionId("decision-01").setDecisionVersion(4L).setSourceDisposition("ACCEPTED")
                .setCanonicalSkuId("sku-01").setWarehouseId("warehouse-01").setLocationId("location-01")
                .setLotId("lot-01").setDispatchedQuantity(new BigDecimal("2.00000000"))
                .setUomCode("PIECE").setValuationPolicyId("b9645ea2-11b6-4ba0-87a2-f1416042f60a")
                .setValuationPolicyVersion("v1").setValuationPolicyHash("a".repeat(64))
                .setUnitCostAmountMinor(100L).setCurrencyCode("CNY")));
        when(mapper.selectPeriodForUpdate(1L, "period-01")).thenReturn(new AccountingPeriod()
                .setPeriodId("period-01").setStatus("OPEN")
                .setPeriodStart(LocalDate.of(2026, 8, 1)).setPeriodEnd(LocalDate.of(2026, 8, 31))
                .setCurrencyCode("CNY"));
        when(mapper.selectPostingAccounts(1L, "rule-01", 1L, "ledger-01", "SUPPLIER_RETURN"))
                .thenReturn(List.of(
                        account("AP", "acc-ap", "220201"),
                        account("INVENTORY", "acc-inv", "140501"),
                        account("INPUT_TAX", "acc-tax", "222101"),
                        account("PURCHASE_PRICE_VARIANCE", "acc-ppv", "640101")
                ));
        when(mapper.selectLatestPoEvidence(1L, "item-01")).thenReturn(new PurchaseOrderLineEvidence()
                .setPurchaseOrderId("po-01").setPurchaseOrderItemId("item-01").setDeliveryScheduleId("schedule-01")
                .setLegalEntityId("le-01").setSupplierId("supplier-01").setCurrencyCode("CNY")
                .setOrderedQuantity(new BigDecimal("10.00000000")).setUnitOfMeasure("PIECE")
                .setNetAmountMinor(1000L).setTaxAmountMinor(130L).setGrossAmountMinor(1130L));
        when(mapper.selectLatestReceiptEvidence(1L, "receipt-line-01")).thenReturn(new ReceiptLineEvidence()
                .setReceiptId("receipt-01").setReceiptLineId("receipt-line-01").setPurchaseOrderId("po-01")
                .setPurchaseOrderItemId("item-01").setDeliveryScheduleId("schedule-01")
                .setReceivedQuantity(new BigDecimal("10.00000000")).setUnitOfMeasure("PIECE"));
        when(mapper.selectQualityEvidence(1L, "decision-01", 4L)).thenReturn(new QualityDispositionEvidence()
                .setQualityDispositionId("decision-01").setReceiptLineId("receipt-line-01")
                .setPurchaseOrderItemId("item-01").setAcceptedQuantity(new BigDecimal("10.00000000"))
                .setUnitOfMeasure("PIECE").setSourceVersion(4L));
        when(mapper.selectValuationLayerForUpdate(1L, "ledger-01", "receipt-line-01", "decision-01", "item-01"))
                .thenReturn(new InventoryValuationLayer()
                        .setValuationLayerId("layer-01").setLedgerId("ledger-01")
                        .setInventoryMovementId("movement-01").setInventoryMovementVersion(7L)
                        .setReceiptLineId("receipt-line-01").setQualityDispositionId("decision-01")
                        .setPurchaseOrderItemId("item-01").setValuationPolicyId("B9645EA2-11B6-4BA0-87A2-F1416042F60A")
                        .setValuationPolicyVersion("v1").setQuantity(new BigDecimal("10.00000000"))
                        .setUnitOfMeasure("PIECE").setUnitCostAmountMinor(100L).setTotalCostAmountMinor(1000L)
                        .setCurrencyCode("CNY").setRemainingQuantity(new BigDecimal("10.00000000"))
                        .setRemainingCostAmountMinor(1000L).setVersion(2L));
        when(mapper.selectActiveAllocationCandidates(1L, "receipt-line-01", "decision-01")).thenReturn(List.of(
                new SupplierReturnAllocationCandidate()
                        .setSupplierInvoiceId("invoice-01").setInvoiceLineId("invoice-line-01")
                        .setApOpenItemId("ap-01").setLegalEntityId("le-01").setSupplierId("supplier-01")
                        .setCurrencyCode("CNY").setInvoiceLineQuantity(new BigDecimal("10.00000000"))
                        .setInvoiceLineNetAmountMinor(1000L).setInvoiceLineTaxAmountMinor(130L)
                        .setInvoiceLineGrossAmountMinor(1130L).setAllocatedQuantity(new BigDecimal("10.00000000"))
                        .setUnitOfMeasure("PIECE").setAvailableOpenAmountMinor(1130L)
        ));
        when(mapper.selectApForUpdate(1L, "ap-01")).thenReturn(new ApOpenItem()
                .setApOpenItemId("ap-01").setOpenAmountMinor(1130L).setVersion(5L));
        when(mapper.selectOpenInstallmentsForUpdate(1L, "ap-01")).thenReturn(List.of(new ApInstallment()
                .setApInstallmentId("inst-01").setAmountMinor(1130L).setSettledAmountMinor(0L).setVersion(1L)));
        when(mapper.countActiveDimensionValue(anyLong(), anyString(), anyString())).thenReturn(1);

        stubWrites();

        ProcureToPayResult result = service.post(command, "principal-01");

        assertThat(result.getStatus()).isEqualTo("POSTED");
        assertThat(result.getAggregateType()).isEqualTo("finance_supplier_debit_adjustment");
        assertThat(result.getJournalEntryId()).isNotBlank();
        verify(mapper).insertSupplierDebitAdjustment(any(SupplierDebitAdjustment.class));
        verify(mapper).insertSupplierReturnValuationEffect(anyString(), eq(1L), eq("layer-01"), eq("line-01"), eq("movement-01"),
                eq(7L), eq(new BigDecimal("2.00000000")), eq(200L), eq("CNY"), anyString(), any());
        verify(mapper).insertSupplierReturnApApplication(anyString(), eq(1L), eq("ap-01"), anyString(), eq(226L), anyString(), any());
        InOrder persistenceOrder = inOrder(mapper);
        persistenceOrder.verify(mapper).insertJournalEntry(any(JournalEntry.class));
        persistenceOrder.verify(mapper).insertSupplierReturnValuationEffect(anyString(), eq(1L), eq("layer-01"),
                eq("line-01"), eq("movement-01"), eq(7L), eq(new BigDecimal("2.00000000")), eq(200L), eq("CNY"),
                anyString(), any());
        persistenceOrder.verify(mapper).insertSupplierDebitAdjustment(any(SupplierDebitAdjustment.class));
        persistenceOrder.verify(mapper).insertSupplierReturnApReversal(any(SupplierReturnApReversal.class));
        persistenceOrder.verify(mapper).insertSupplierReturnApApplication(anyString(), eq(1L), eq("ap-01"),
                anyString(), eq(226L), anyString(), any());
    }

    private void stubWrites() {
        when(mapper.insertOrResolveOperation(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(mapper.applyInstallmentSettlement(anyLong(), anyString(), anyLong(), anyLong(), any())).thenReturn(1);
        when(mapper.applyApSettlement(anyLong(), anyString(), anyLong(), anyLong(), any())).thenReturn(1);
        when(mapper.updateInvoiceSettlementFromAp(anyLong(), anyString(), any())).thenReturn(1);
        when(mapper.applyValuationReturn(anyLong(), anyString(), anyLong(), any(), anyLong(), any())).thenReturn(1);
        when(mapper.insertSupplierReturnValuationEffect(anyString(), anyLong(), anyString(), anyString(), anyString(), anyLong(),
                any(), anyLong(), anyString(), anyString(), any())).thenReturn(1);
        when(mapper.insertSupplierReturnApReversal(any())).thenReturn(1);
        when(mapper.insertSupplierReturnApApplication(anyString(), anyLong(), anyString(), anyString(), anyLong(), anyString(), any()))
                .thenReturn(1);
        when(mapper.insertJournalEntry(any())).thenReturn(1);
        when(mapper.insertJournalLine(any())).thenReturn(1);
        when(mapper.insertJournalLineDimension(anyString(), anyLong(), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(mapper.insertJournalSourceEffect(anyString(), anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(mapper.insertJournalHistory(anyLong(), anyString(), any(), anyString(), anyString(), anyString(), anyLong(), any()))
                .thenReturn(1);
        when(mapper.insertSupplierDebitAdjustment(any())).thenReturn(1);
        when(mapper.insertSupplierDebitAdjustmentLine(any())).thenReturn(1);
        when(mapper.markOperationSucceeded(anyLong(), anyLong(), anyString(), anyString(), any())).thenReturn(1);
    }

    private static SupplierReturnReversalCommands.Post command() {
        return SupplierReturnReversalCommands.Post.builder()
                .envelope(FinanceCommandEnvelope.builder()
                        .correlationId("corr-01").causationId("cause-01").runId("run-01")
                        .idempotencyKey("idem-01").occurredAt(Instant.parse("2026-08-02T13:00:00Z")).build())
                .supplierReturnId("return-01")
                .expectedSupplierReturnVersion(3L)
                .ledgerId("ledger-01")
                .accountingPeriodId("period-01")
                .accountingDate(LocalDate.of(2026, 8, 2))
                .postingRuleId("rule-01")
                .postingRuleVersion(1L)
                .reversalEvidenceSha256("c".repeat(64))
                .reasonCode("RETURN_TO_SUPPLIER")
                .journalDimensions(List.of(JournalDimensionAssignment.builder()
                        .dimensionTypeId("dim-type-01").dimensionValueId("dim-value-01").build()))
                .build();
    }

    private static PostingAccount account(String role, String accountId, String code) {
        return new PostingAccount().setAccountRole(role).setLedgerId("ledger-01").setAccountId(accountId).setAccountCode(code);
    }
}
