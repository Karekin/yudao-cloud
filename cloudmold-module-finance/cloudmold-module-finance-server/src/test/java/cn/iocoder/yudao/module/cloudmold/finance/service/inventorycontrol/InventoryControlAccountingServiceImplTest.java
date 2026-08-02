package cn.iocoder.yudao.module.cloudmold.finance.service.inventorycontrol;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.finance.api.inventorycontrol.InventoryControlAccountingCommands;
import cn.iocoder.yudao.module.cloudmold.finance.api.inventorycontrol.InventoryControlAccountingResult;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.FinanceCommandEnvelope;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.AccountingPeriod;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.InventoryControlFinanceRecords.*;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureToPayRecords.JournalEntry;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureToPayRecords.JournalLine;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureToPayRecords.PostingAccount;
import cn.iocoder.yudao.module.cloudmold.finance.dal.mysql.InventoryControlFinanceMapper;
import cn.iocoder.yudao.module.cloudmold.finance.service.actor.FinanceActorPrincipalPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryControlAccountingServiceImplTest {
    private static final long TENANT_ID = 162L;
    private static final String CREATOR = "finance-creator";
    private static final String REVIEWER = "finance-reviewer";

    private final InventoryControlFinanceMapper mapper = mock(InventoryControlFinanceMapper.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final FinanceActorPrincipalPort principal = mock(FinanceActorPrincipalPort.class);
    private final InventoryControlAccountingServiceImpl service =
            new InventoryControlAccountingServiceImpl(mapper, outbox, principal);
    private final AtomicReference<String> requestHash = new AtomicReference<>();
    private final AtomicReference<String> attemptToken = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(mapper.insertOrResolveOperation(eq(TENANT_ID), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    requestHash.set(invocation.getArgument(3));
                    attemptToken.set(invocation.getArgument(4));
                    return 1;
                });
        when(mapper.selectLastInsertId()).thenReturn(801L);
        when(mapper.selectOperationForUpdate(801L, TENANT_ID)).thenAnswer(invocation -> new Operation()
                .setOperationId(801L).setTenantId(TENANT_ID).setRequestHash(requestHash.get())
                .setAttemptToken(attemptToken.get()).setStatus(0));
        when(mapper.markOperationSucceeded(eq(801L), eq(TENANT_ID), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void postsStockCountShrinkageWithBalancedJournalAndAllocation() {
        when(mapper.selectStockCountSourceForUpdate(TENANT_ID, "count-1", "line-1"))
                .thenReturn(stockCountSource(new BigDecimal("-2.00000000")));
        when(mapper.selectPostingBySourceForUpdate(TENANT_ID, "STOCK_COUNT_ADJUSTMENT", "adj-1")).thenReturn(null);
        when(mapper.selectLedgerForUpdate(TENANT_ID, "ledger-1")).thenReturn(ledger());
        when(mapper.selectPeriodForUpdate(TENANT_ID, "period-1")).thenReturn(period());
        when(mapper.selectOpenValuationLayersForSource(TENANT_ID, "ledger-1", "MERCHANT", "merchant-1", "sku-1", "lot-1"))
                .thenReturn(List.of(layer("layer-1", "5.00000000", 5000L, 3L)));
        when(mapper.selectOpenControlValuationLayersForSource(TENANT_ID, "ledger-1", "MERCHANT", "merchant-1", "sku-1", "lot-1"))
                .thenReturn(List.of());
        when(mapper.selectPostingAccounts(TENANT_ID, "rule-1", 1L, "ledger-1", "STOCK_COUNT_ADJUSTMENT"))
                .thenReturn(List.of(account("INVENTORY_LOSS", "expense-loss"), account("INVENTORY", "inventory")));
        when(mapper.insertJournalEntry(any())).thenReturn(1);
        when(mapper.insertJournalLine(any())).thenReturn(1);
        when(mapper.insertJournalSourceEffect(anyString(), eq(TENANT_ID), eq("STOCK_COUNT_ADJUSTMENT"),
                eq("adj-1"), eq("STOCK_COUNT_POSTING"), anyString(), any())).thenReturn(1);
        when(mapper.insertJournalHistory(anyString(), eq(TENANT_ID), anyString(), isNull(), eq("POSTED"),
                eq(CREATOR), isNull(), eq(1L), any())).thenReturn(1);
        when(mapper.updateValuationLayerRemainingCas(eq(TENANT_ID), eq("layer-1"), eq(3L), eq(4L),
                eq(new BigDecimal("3.00000000")), eq(3000L), eq("OPEN"), any())).thenReturn(1);
        when(mapper.insertPosting(any())).thenReturn(1);
        when(mapper.insertPostingAllocation(any())).thenReturn(1);

        InventoryControlAccountingResult result = service.postStockCountAdjustment(
                InventoryControlAccountingCommands.PostStockCountAdjustment.builder()
                        .envelope(envelope("stock-count-post"))
                        .stockCountId("count-1")
                        .stockCountLineId("line-1")
                        .expectedStockCountVersion(4L)
                        .expectedLineVersion(3L)
                        .ledgerId("ledger-1")
                        .accountingPeriodId("period-1")
                        .accountingDate(LocalDate.of(2026, 8, 2))
                        .postingRuleId("rule-1")
                        .postingRuleVersion(1L)
                        .journalCode("STK-C001-10")
                        .postingEvidenceSha256("a".repeat(64))
                        .build(),
                CREATOR);

        assertThat(result.getStatus()).isEqualTo("POSTED");
        assertThat(result.getSourceType()).isEqualTo("STOCK_COUNT_ADJUSTMENT");
        assertThat(result.getTotalAmountMinor()).isEqualTo(2000L);
        verify(mapper, times(2)).insertJournalLine(any(JournalLine.class));
        verify(mapper).insertPosting(argThat(value ->
                "STOCK_COUNT_ADJUSTMENT".equals(value.getSourceType())
                        && "adj-1".equals(value.getSourceReferenceId())
                        && Long.valueOf(2000L).equals(value.getTotalAmountMinor())));
    }

    @Test
    void rejectsStockCountGainWithoutValuationOrigin() {
        when(mapper.selectStockCountSourceForUpdate(TENANT_ID, "count-1", "line-1"))
                .thenReturn(stockCountSource(new BigDecimal("1.00000000")));

        assertThatThrownBy(() -> service.postStockCountAdjustment(
                InventoryControlAccountingCommands.PostStockCountAdjustment.builder()
                        .envelope(envelope("stock-count-gain"))
                        .stockCountId("count-1")
                        .stockCountLineId("line-1")
                        .expectedStockCountVersion(4L)
                        .expectedLineVersion(3L)
                        .ledgerId("ledger-1")
                        .accountingPeriodId("period-1")
                        .accountingDate(LocalDate.of(2026, 8, 2))
                        .postingRuleId("rule-1")
                        .postingRuleVersion(1L)
                        .postingEvidenceSha256("b".repeat(64))
                        .build(),
                CREATOR)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approved stock count gain basis not found");
    }

    @Test
    void submitsApprovesAndPostsStockCountGainWithApprovedBasis() {
        when(mapper.selectStockCountSourceForUpdate(TENANT_ID, "count-1", "line-1"))
                .thenReturn(stockCountSource(new BigDecimal("2.00000000")));
        when(mapper.selectGainBasisByLineForUpdate(TENANT_ID, "line-1", 3L)).thenReturn(null,
                new StockCountGainBasis().setStockCountGainBasisId("basis-1").setStockCountId("count-1")
                        .setStockCountLineId("line-1").setStockCountVersion(4L).setStockCountLineVersion(3L)
                        .setValuationPolicyId("VAL-1").setValuationPolicyVersion("V1").setValuationPolicyHash("d".repeat(64))
                        .setGainQuantity(new BigDecimal("2.00000000")).setUnitOfMeasure("PCS")
                        .setUnitCostAmountMinor(1000L).setTotalCostAmountMinor(2000L).setCurrencyCode("CNY")
                        .setEvidenceSha256("e".repeat(64)).setStatus("APPROVED")
                        .setSubmittedByPrincipalId(CREATOR).setApprovedByPrincipalId(REVIEWER).setVersion(2L));
        when(mapper.insertGainBasis(any())).thenReturn(1);
        when(mapper.insertGainBasisHistory(anyString(), eq(TENANT_ID), eq("basis-1"), any(), any(), any(), any(), anyLong(), any()))
                .thenReturn(1);
        when(mapper.selectGainBasis(TENANT_ID, "basis-1")).thenReturn(new StockCountGainBasis()
                .setStockCountGainBasisId("basis-1").setStockCountId("count-1").setStockCountLineId("line-1")
                .setValuationPolicyId("VAL-1").setValuationPolicyVersion("V1").setValuationPolicyHash("d".repeat(64))
                .setGainQuantity(new BigDecimal("2.00000000")).setUnitOfMeasure("PCS").setUnitCostAmountMinor(1000L)
                .setTotalCostAmountMinor(2000L).setCurrencyCode("CNY").setStatus("SUBMITTED")
                .setSubmittedByPrincipalId(CREATOR).setVersion(1L));
        when(mapper.approveGainBasis(eq(TENANT_ID), eq("basis-1"), eq(1L), eq(2L), eq(REVIEWER), any())).thenReturn(1);
        when(mapper.selectPostingBySourceForUpdate(TENANT_ID, "STOCK_COUNT_ADJUSTMENT", "basis-1")).thenReturn(null);
        when(mapper.selectLedgerForUpdate(TENANT_ID, "ledger-1")).thenReturn(ledger());
        when(mapper.selectPeriodForUpdate(TENANT_ID, "period-1")).thenReturn(period());
        when(mapper.selectPostingAccounts(TENANT_ID, "rule-1", 1L, "ledger-1", "STOCK_COUNT_ADJUSTMENT"))
                .thenReturn(List.of(account("INVENTORY", "inventory"), account("INVENTORY_GAIN", "gain-income")));
        when(mapper.insertJournalEntry(any())).thenReturn(1);
        when(mapper.insertJournalLine(any())).thenReturn(1);
        when(mapper.insertJournalSourceEffect(anyString(), eq(TENANT_ID), eq("STOCK_COUNT_ADJUSTMENT"),
                eq("basis-1"), eq("STOCK_COUNT_GAIN_POSTING"), anyString(), any())).thenReturn(1);
        when(mapper.insertJournalHistory(anyString(), eq(TENANT_ID), anyString(), isNull(), eq("POSTED"),
                eq(CREATOR), isNull(), eq(1L), any())).thenReturn(1);
        when(mapper.insertControlValuationLayer(any())).thenReturn(1);
        when(mapper.insertPosting(any())).thenReturn(1);

        InventoryControlAccountingResult submitted = service.submitStockCountGainBasis(
                InventoryControlAccountingCommands.SubmitStockCountGainBasis.builder()
                        .envelope(envelope("gain-basis-submit")).stockCountGainBasisId("basis-1").basisCode("GB-1")
                        .stockCountId("count-1").stockCountLineId("line-1").expectedStockCountVersion(4L)
                        .expectedLineVersion(3L).valuationPolicyId("VAL-1").valuationPolicyVersion("V1")
                        .valuationPolicyHash("d".repeat(64)).unitOfMeasure("PCS").unitCostAmountMinor(1000L)
                        .currencyCode("CNY").evidenceSha256("e".repeat(64)).build(), CREATOR);
        assertThat(submitted.getStatus()).isEqualTo("SUBMITTED");

        InventoryControlAccountingResult approved = service.approveStockCountGainBasis(
                InventoryControlAccountingCommands.ApproveStockCountGainBasis.builder()
                        .envelope(envelope("gain-basis-approve")).stockCountGainBasisId("basis-1").expectedVersion(1L)
                        .note("checked").build(), REVIEWER);
        assertThat(approved.getStatus()).isEqualTo("APPROVED");

        InventoryControlAccountingResult posted = service.postStockCountAdjustment(
                InventoryControlAccountingCommands.PostStockCountAdjustment.builder()
                        .envelope(envelope("gain-post")).stockCountId("count-1").stockCountLineId("line-1")
                        .expectedStockCountVersion(4L).expectedLineVersion(3L).ledgerId("ledger-1")
                        .accountingPeriodId("period-1").accountingDate(LocalDate.of(2026, 8, 2))
                        .postingRuleId("rule-1").postingRuleVersion(1L).postingEvidenceSha256("f".repeat(64)).build(),
                CREATOR);
        assertThat(posted.getStatus()).isEqualTo("POSTED");
        assertThat(posted.getTotalAmountMinor()).isEqualTo(2000L);
    }

    @Test
    void reversesInventoryScrapAndRestoresConsumedValuationLayer() {
        when(mapper.selectPostingForUpdate(TENANT_ID, "posting-1")).thenReturn(posting());
        when(mapper.selectJournalForUpdate(TENANT_ID, "journal-1")).thenReturn(journalEntry());
        when(mapper.countJournalReversal(TENANT_ID, "journal-1")).thenReturn(0);
        when(mapper.selectPeriodForUpdate(TENANT_ID, "period-1")).thenReturn(period());
        when(mapper.selectJournalLines(TENANT_ID, "journal-1")).thenReturn(List.of(
                journalLine("line-1", 1, 2000L, 0L), journalLine("line-2", 2, 0L, 2000L)));
        when(mapper.selectJournalLineDimensions(TENANT_ID, "line-1")).thenReturn(List.of());
        when(mapper.selectJournalLineDimensions(TENANT_ID, "line-2")).thenReturn(List.of());
        when(mapper.insertJournalEntry(any())).thenReturn(1);
        when(mapper.insertJournalLine(any())).thenReturn(1);
        when(mapper.insertJournalSourceEffect(anyString(), eq(TENANT_ID), eq("JOURNAL_REVERSAL"),
                eq("journal-1"), eq("REVERSAL"), anyString(), any())).thenReturn(1);
        when(mapper.insertJournalHistory(anyString(), eq(TENANT_ID), anyString(), isNull(), eq("POSTED"),
                eq(REVIEWER), isNull(), eq(1L), any())).thenReturn(1);
        when(mapper.insertJournalReversalLink(anyString(), eq(TENANT_ID), eq("journal-1"), anyString(),
                eq("c".repeat(64)), eq(REVIEWER), any())).thenReturn(1);
        when(mapper.selectPostingAllocations(TENANT_ID, "posting-1")).thenReturn(List.of(allocation()));
        when(mapper.selectValuationLayerForUpdate(TENANT_ID, "layer-1")).thenReturn(layer("layer-1", "0.00000000", 0L, 7L));
        when(mapper.updateValuationLayerRemainingCas(eq(TENANT_ID), eq("layer-1"), eq(7L), eq(8L),
                eq(new BigDecimal("2.00000000")), eq(2000L), eq("OPEN"), any())).thenReturn(1);
        when(mapper.markPostingReversed(eq(TENANT_ID), eq("posting-1"), eq(1L), eq(2L), anyString(), eq(REVIEWER), any()))
                .thenReturn(1);

        InventoryControlAccountingResult result = service.reverse(
                InventoryControlAccountingCommands.Reverse.builder()
                        .envelope(envelope("scrap-reverse"))
                        .inventoryControlPostingId("posting-1")
                        .expectedVersion(1L)
                        .reversalJournalCode("REV-SCR-1")
                        .accountingPeriodId("period-1")
                        .accountingDate(LocalDate.of(2026, 8, 2))
                        .reversalEvidenceSha256("c".repeat(64))
                        .reasonCode("MANUAL_FIX")
                        .build(),
                REVIEWER);

        assertThat(result.getStatus()).isEqualTo("REVERSED");
        assertThat(result.getReversalJournalEntryId()).isNotBlank();
        verify(mapper).markPostingReversed(eq(TENANT_ID), eq("posting-1"), eq(1L), eq(2L), anyString(), eq(REVIEWER), any());
    }

    private static StockCountSource stockCountSource(BigDecimal difference) {
        return new StockCountSource()
                .setStockCountId("count-1").setStockCountCode("COUNT-0001")
                .setStockCountStatus("ADJUSTED").setStockCountVersion(4L)
                .setLineId("line-1").setLineNumber(10).setLineStatus("ADJUSTED").setLineVersion(3L)
                .setOwnerType("MERCHANT").setOwnerId("merchant-1").setCanonicalSkuId("sku-1").setLotId("lot-1")
                .setBaseUomCode("PCS").setDifferenceQuantity(difference)
                .setAdjustmentId("adj-1").setAdjustmentLedgerTransactionId(101L);
    }

    private static InventoryControlPosting posting() {
        return new InventoryControlPosting()
                .setInventoryControlPostingId("posting-1").setTenantId(TENANT_ID)
                .setSourceType("INVENTORY_SCRAP").setSourceDocumentId("scrap-1").setSourceLineId("scrap-line-1")
                .setSourceReferenceId("disp-line-1").setSourceDocumentVersion(3L).setSourceLineVersion(2L)
                .setInventoryLedgerTransactionId(55L).setLegalEntityId("entity-1").setLedgerId("ledger-1")
                .setAccountingPeriodId("period-1").setAccountingDate(LocalDate.of(2026, 8, 2))
                .setPostingRuleId("rule-scrap").setPostingRuleVersion(1L)
                .setQuantity(new BigDecimal("2.00000000")).setUnitOfMeasure("PCS").setCurrencyCode("CNY")
                .setTotalAmountMinor(2000L).setJournalEntryId("journal-1").setJournalCode("SCR-0001")
                .setPostingEvidenceSha256("z".repeat(64)).setStatus("POSTED").setCreatedByPrincipalId(CREATOR)
                .setVersion(1L).setCreatedAt(LocalDateTime.now()).setUpdatedAt(LocalDateTime.now());
    }

    private static InventoryControlPostingAllocation allocation() {
        return new InventoryControlPostingAllocation()
                .setAllocationId("alloc-1").setTenantId(TENANT_ID).setInventoryControlPostingId("posting-1")
                .setSequenceNo(1).setValuationLayerSourceType("PROCUREMENT_RECEIPT").setValuationLayerId("layer-1")
                .setAllocatedQuantity(new BigDecimal("2.00000000")).setAllocatedCostAmountMinor(2000L)
                .setCreatedAt(LocalDateTime.now());
    }

    private static JournalEntry journalEntry() {
        return new JournalEntry().setJournalEntryId("journal-1").setTenantId(TENANT_ID)
                .setLegalEntityId("entity-1").setLedgerId("ledger-1").setPeriodId("period-1")
                .setAccountingDate(LocalDate.of(2026, 8, 2)).setJournalCode("SCR-0001")
                .setSourceType("INVENTORY_SCRAP").setSourceId("disp-line-1").setCurrencyCode("CNY")
                .setDocumentCurrencyCode("CNY").setDebitTotalMinor(2000L).setCreditTotalMinor(2000L)
                .setEvidenceSha256("z".repeat(64)).setStatus("POSTED")
                .setPreparedByPrincipalId(CREATOR).setPostedByPrincipalId(CREATOR).setVersion(1L);
    }

    private static Ledger ledger() {
        return new Ledger().setLedgerId("ledger-1").setTenantId(TENANT_ID)
                .setLegalEntityId("entity-1").setFunctionalCurrencyCode("CNY").setStatus("ACTIVE");
    }

    private static AccountingPeriod period() {
        return new AccountingPeriod().setPeriodId("period-1").setTenantId(TENANT_ID)
                .setPeriodStart(LocalDate.of(2026, 8, 1)).setPeriodEnd(LocalDate.of(2026, 8, 31))
                .setCurrencyCode("CNY").setStatus("OPEN");
    }

    private static ValuationLayerCandidate layer(String id, String quantity, Long cost, Long version) {
        return new ValuationLayerCandidate().setValuationLayerId(id).setTenantId(TENANT_ID).setLedgerId("ledger-1")
                .setValuationPolicyId("VAL-1").setValuationPolicyVersion("V1")
                .setRemainingQuantity(new BigDecimal(quantity)).setRemainingCostAmountMinor(cost)
                .setUnitCostAmountMinor(1000L).setCurrencyCode("CNY").setStatus("OPEN").setVersion(version)
                .setCreatedAt(LocalDateTime.of(2026, 8, 1, 0, 0));
    }

    private static PostingAccount account(String role, String accountId) {
        return new PostingAccount().setAccountRole(role).setLedgerId("ledger-1")
                .setAccountId(accountId).setAccountCode(role + "-CODE");
    }

    private static JournalLine journalLine(String id, int no, long debit, long credit) {
        return new JournalLine().setJournalLineId(id).setTenantId(TENANT_ID).setJournalEntryId("journal-1")
                .setLedgerId("ledger-1").setLineNumber(no).setAccountId("acct-" + no).setAccountCode("ACCT-" + no)
                .setDebitAmountMinor(debit).setCreditAmountMinor(credit).setTransactionCurrencyCode("CNY")
                .setTransactionAmountMinor(Math.max(debit, credit)).setCreatedAt(LocalDateTime.now());
    }

    private static FinanceCommandEnvelope envelope(String idempotencyKey) {
        return FinanceCommandEnvelope.builder()
                .correlationId(UUID.randomUUID().toString())
                .idempotencyKey(idempotencyKey)
                .occurredAt(Instant.parse("2026-08-02T12:00:00Z"))
                .runId("run-1")
                .build();
    }
}
