package cn.iocoder.yudao.module.cloudmold.finance.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.*;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.AccountingPeriod;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureToPayRecords.*;
import cn.iocoder.yudao.module.cloudmold.finance.dal.mysql.ProcureToPayMapper;
import cn.iocoder.yudao.module.cloudmold.finance.service.actor.FinanceActorPrincipalPort;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcureToPayServiceImplTest {
    private static final long TENANT_ID = 162L;
    private static final String CREATOR = "finance-creator";
    private static final String APPROVER = "finance-approver";
    private static final String POSTER = "finance-poster";
    private final ProcureToPayMapper mapper = mock(ProcureToPayMapper.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final FinanceActorPrincipalPort principal = mock(FinanceActorPrincipalPort.class);
    private final ProcureToPayServiceImpl service = new ProcureToPayServiceImpl(mapper, outbox, principal);
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
        when(mapper.selectLastInsertId()).thenReturn(501L);
        when(mapper.selectOperationForUpdate(501L, TENANT_ID)).thenAnswer(invocation -> new Operation()
                .setOperationId(501L).setTenantId(TENANT_ID).setRequestHash(requestHash.get())
                .setAttemptToken(attemptToken.get()).setStatus(0));
        when(mapper.markOperationSucceeded(eq(501L), eq(TENANT_ID), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void matchesQualifiedReceiptAndPostsAtomicApWithBalancedJournalLines() {
        SupplierInvoice invoice = invoice("SUBMITTED", "NOT_STARTED", 2L);
        SupplierInvoiceLine line = invoiceLine(9000L, 1000L, 10000L);
        when(mapper.selectInvoiceForUpdate(TENANT_ID, "invoice-1")).thenReturn(invoice,
                invoice("APPROVED", "MATCHED", 5L));
        when(mapper.selectMatchPolicy(TENANT_ID, "policy-standard", 3L)).thenReturn(matchPolicy(0L));
        when(mapper.transitionInvoiceMatch(eq(TENANT_ID), eq("invoice-1"), anyLong(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(mapper.selectInvoiceLines(TENANT_ID, "invoice-1")).thenReturn(List.of(line));
        when(mapper.selectLatestPoLineForUpdate(TENANT_ID, "po-item-1")).thenReturn(poEvidence(9000L, 1000L));
        when(mapper.selectMatchCandidates(TENANT_ID, "po-item-1")).thenReturn(List.of(candidate("10")));
        when(mapper.insertMatchRun(any())).thenReturn(1);
        when(mapper.insertMatchLine(any())).thenReturn(1);
        when(mapper.insertMatchAllocation(any())).thenReturn(1);
        when(mapper.completeMatchRun(eq(TENANT_ID), anyString(), eq("MATCHED"), any())).thenReturn(1);

        ProcureToPayResult matched = service.runThreeWayMatch(SupplierInvoiceCommands.Match.builder()
                .envelope(envelope("match-1")).supplierInvoiceId("invoice-1").expectedVersion(2L)
                .matchPolicyId("policy-standard").matchPolicyVersion(3L).build(), CREATOR);

        assertThat(matched.getStatus()).isEqualTo("MATCHED");
        verify(mapper, never()).insertMatchException(any());
        verify(mapper).insertMatchLine(argThat(value -> value.getPurchaseOrderLineVersion() == 1L));
        verify(mapper).insertMatchAllocation(argThat(value -> value.getReceiptLineVersion() == 1L
                && value.getQualityDispositionVersion() == 1L
                && value.getInventoryMovementVersion() == 1L));

        when(mapper.selectPeriodForUpdate(TENANT_ID, "period-1")).thenReturn(openPeriod());
        when(mapper.insertApOpenItem(any())).thenReturn(1);
        when(mapper.insertApInstallment(anyString(), eq(TENANT_ID), anyString(), eq(1), any(), eq(10000L), any()))
                .thenReturn(1);
        when(mapper.selectPaymentTermRules(TENANT_ID, "term-1", 1L)).thenReturn(List.of(new PaymentTermRule()
                .setInstallmentNumber(1).setDueDaysAfterIssue(30).setAllocationBasisPoints(10000)));
        when(mapper.sumMatchedReceiptValuation(TENANT_ID, "invoice-1")).thenReturn(9000L);
        when(mapper.selectPostingAccounts(TENANT_ID, "invoice-posting", 1L, "ledger-1", "SUPPLIER_INVOICE"))
                .thenReturn(invoicePostingAccounts());
        when(mapper.insertJournalEntry(any())).thenReturn(1);
        when(mapper.insertJournalLine(any())).thenReturn(1);
        when(mapper.insertJournalSourceEffect(anyString(), eq(TENANT_ID), eq("SUPPLIER_INVOICE"),
                eq("invoice-1"), eq("INVOICE_POSTING"), anyString(), any())).thenReturn(1);
        when(mapper.markInvoicePosted(eq(TENANT_ID), eq("invoice-1"), eq(5L), anyString(), any())).thenReturn(1);

        ProcureToPayResult posted = service.post(SupplierInvoiceCommands.Transition.builder()
                .envelope(envelope("post-1")).supplierInvoiceId("invoice-1").expectedVersion(5L)
                .reasonCode("MATCHED_INVOICE").build(), POSTER);

        assertThat(posted.getStatus()).isEqualTo("POSTED");
        assertThat(posted.getApOpenItemId()).isNotBlank();
        assertThat(posted.getJournalEntryId()).isNotBlank();
        ArgumentCaptor<JournalLine> lineCaptor = ArgumentCaptor.forClass(JournalLine.class);
        verify(mapper, atLeast(3)).insertJournalLine(lineCaptor.capture());
        assertThat(lineCaptor.getAllValues().stream().mapToLong(JournalLine::getDebitAmountMinor).sum())
                .isEqualTo(10000L);
        assertThat(lineCaptor.getAllValues().stream().mapToLong(JournalLine::getCreditAmountMinor).sum())
                .isEqualTo(10000L);
    }

    @Test
    void createsSupplierReturnPostingRuleWithTheExactReversalRoles() {
        when(mapper.insertPostingRule(anyString(), eq(TENANT_ID), eq("SUPPLIER_RETURN_V1"),
                eq("ledger-1"), eq("SUPPLIER_RETURN"), eq(1L), any())).thenReturn(1);
        when(mapper.insertPostingRuleLine(anyString(), eq(TENANT_ID), anyString(), eq(1L),
                eq("ledger-1"), anyString(), anyString())).thenReturn(1);

        ProcureToPayResult result = service.createPostingRule(P2pFinanceSetupCommands.PostingRule.builder()
                .envelope(envelope("supplier-return-posting-rule"))
                .postingRuleId("supplier-return-posting-rule")
                .ruleCode("SUPPLIER_RETURN_V1")
                .ledgerId("ledger-1")
                .sourceType("SUPPLIER_RETURN")
                .ruleVersion(1L)
                .lines(List.of(
                        postingRuleLine("INVENTORY", "account-inventory"),
                        postingRuleLine("INPUT_TAX", "account-input-tax"),
                        postingRuleLine("PURCHASE_PRICE_VARIANCE", "account-ppv"),
                        postingRuleLine("AP", "account-ap")))
                .build(), CREATOR);

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        verify(mapper, times(4)).insertPostingRuleLine(anyString(), eq(TENANT_ID),
                eq("supplier-return-posting-rule"), eq(1L), eq("ledger-1"), anyString(), anyString());
    }

    @Test
    void priceBeyondToleranceCreatesBlockingExceptionAndNeverCreatesAp() {
        when(mapper.selectInvoiceForUpdate(TENANT_ID, "invoice-1"))
                .thenReturn(invoice("SUBMITTED", "NOT_STARTED", 2L));
        when(mapper.selectMatchPolicy(TENANT_ID, "policy-standard", 3L)).thenReturn(matchPolicy(100L));
        when(mapper.transitionInvoiceMatch(eq(TENANT_ID), eq("invoice-1"), anyLong(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(mapper.selectInvoiceLines(TENANT_ID, "invoice-1"))
                .thenReturn(List.of(invoiceLine(10000L, 0L, 10000L)));
        when(mapper.selectLatestPoLineForUpdate(TENANT_ID, "po-item-1")).thenReturn(poEvidence(9000L, 0L));
        when(mapper.selectMatchCandidates(TENANT_ID, "po-item-1")).thenReturn(List.of(candidate("10")));
        when(mapper.insertMatchRun(any())).thenReturn(1);
        when(mapper.insertMatchLine(any())).thenReturn(1);
        when(mapper.insertMatchAllocation(any())).thenReturn(1);
        when(mapper.insertMatchException(any())).thenReturn(1);
        when(mapper.completeMatchRun(eq(TENANT_ID), anyString(), eq("EXCEPTION"), any())).thenReturn(1);

        ProcureToPayResult result = service.runThreeWayMatch(SupplierInvoiceCommands.Match.builder()
                .envelope(envelope("match-price-exception")).supplierInvoiceId("invoice-1").expectedVersion(2L)
                .matchPolicyId("policy-standard").matchPolicyVersion(3L).build(), CREATOR);

        assertThat(result.getStatus()).isEqualTo("EXCEPTION");
        verify(mapper).insertMatchException(argThat(value -> value.getExceptionType().equals("PRICE")
                && value.getBlocking()));
        verify(mapper, never()).insertApOpenItem(any());
        verify(mapper, never()).insertJournalEntry(any());
    }

    @Test
    void partialPaymentSettlementReducesApAndCreatesBalancedPaymentJournal() {
        PaymentInstruction payment = new PaymentInstruction().setPaymentInstructionId("payment-1")
                .setPaymentCode("PAY-1").setLegalEntityId("entity-1").setLedgerId("ledger-1")
                .setAccountingPeriodId("period-1").setSupplierId("supplier-1").setCurrencyCode("CNY")
                .setTotalAmountMinor(4000L).setPostingRuleId("payment-posting").setPostingRuleVersion(1L)
                .setStatus("EXECUTED").setCreatedByPrincipalId(CREATOR).setVersion(4L);
        PaymentAllocation allocation = new PaymentAllocation().setPaymentAllocationId("allocation-1")
                .setPaymentInstructionId("payment-1").setApOpenItemId("ap-1").setAmountMinor(4000L);
        ApOpenItem ap = ap(10000L, 0L, 10000L, 1L);
        when(mapper.selectPaymentForUpdate(TENANT_ID, "payment-1")).thenReturn(payment);
        when(mapper.selectPeriodForUpdate(TENANT_ID, "period-1")).thenReturn(openPeriod());
        when(mapper.selectPaymentAllocations(TENANT_ID, "payment-1")).thenReturn(List.of(allocation));
        when(mapper.selectApForUpdate(TENANT_ID, "ap-1")).thenReturn(ap, ap);
        when(mapper.selectPostingAccounts(TENANT_ID, "payment-posting", 1L, "ledger-1", "SUPPLIER_PAYMENT"))
                .thenReturn(List.of(posting("AP", "account-ap", "2101"),
                        posting("BANK_CLEARING", "account-bank", "1002")));
        when(mapper.insertPaymentSettlement(anyString(), eq(TENANT_ID), eq("payment-1"), any(), eq(4000L),
                eq("CNY"), anyString(), anyString(), any())).thenReturn(1);
        when(mapper.insertJournalEntry(any())).thenReturn(1);
        when(mapper.insertJournalLine(any())).thenReturn(1);
        when(mapper.insertJournalSourceEffect(anyString(), eq(TENANT_ID), eq("SUPPLIER_PAYMENT"),
                eq("payment-1"), eq("PAYMENT_SETTLEMENT"), anyString(), any())).thenReturn(1);
        when(mapper.insertApSettlementApplication(anyString(), eq(TENANT_ID), eq("ap-1"), anyString(),
                eq(4000L), anyString(), any())).thenReturn(1);
        when(mapper.applyApSettlement(eq(TENANT_ID), eq("ap-1"), eq(1L), eq(4000L), any())).thenReturn(1);
        when(mapper.selectOpenInstallmentsForUpdate(TENANT_ID, "ap-1")).thenReturn(List.of(
                new ApInstallment().setApInstallmentId("installment-1").setAmountMinor(3000L)
                        .setSettledAmountMinor(0L).setVersion(1L),
                new ApInstallment().setApInstallmentId("installment-2").setAmountMinor(7000L)
                        .setSettledAmountMinor(0L).setVersion(1L)));
        when(mapper.applyInstallmentSettlement(eq(TENANT_ID), anyString(), eq(1L), anyLong(), any()))
                .thenReturn(1);
        when(mapper.transitionPayment(eq(TENANT_ID), eq("payment-1"), eq(4L), eq("EXECUTED"),
                eq("SETTLED"), eq(POSTER), any()))
                .thenReturn(1);

        ProcureToPayResult result = service.settle(SupplierPaymentCommands.Settlement.builder()
                .envelope(envelope("settle-partial")).paymentInstructionId("payment-1").expectedVersion(4L)
                .settlementId("settlement-1").settlementDate(LocalDate.of(2026, 8, 2))
                .settledAmountMinor(4000L).currencyCode("CNY").bankReference("BANK-1")
                .settlementEvidenceSha256("a".repeat(64)).build(), POSTER);

        assertThat(result.getStatus()).isEqualTo("SETTLED");
        verify(mapper).applyApSettlement(eq(TENANT_ID), eq("ap-1"), eq(1L), eq(4000L), any());
        verify(mapper).applyInstallmentSettlement(eq(TENANT_ID), eq("installment-1"), eq(1L), eq(3000L), any());
        verify(mapper).applyInstallmentSettlement(eq(TENANT_ID), eq("installment-2"), eq(1L), eq(1000L), any());
        ArgumentCaptor<JournalLine> lines = ArgumentCaptor.forClass(JournalLine.class);
        verify(mapper, times(2)).insertJournalLine(lines.capture());
        assertThat(lines.getAllValues().stream().mapToLong(JournalLine::getDebitAmountMinor).sum()).isEqualTo(4000L);
        assertThat(lines.getAllValues().stream().mapToLong(JournalLine::getCreditAmountMinor).sum()).isEqualTo(4000L);
    }

    @Test
    void rejectsPaymentAllocationAboveUnreservedApAmount() {
        when(mapper.selectPeriodForUpdate(TENANT_ID, "period-1")).thenReturn(openPeriod());
        when(mapper.selectPayeeInstrumentForUpdate(TENANT_ID, "instrument-1")).thenReturn(validPayee());
        when(mapper.selectPostingAccounts(TENANT_ID, "payment-posting", 1L, "ledger-1", "SUPPLIER_PAYMENT"))
                .thenReturn(List.of(posting("AP", "account-ap", "2101"),
                        posting("BANK_CLEARING", "account-bank", "1002")));
        when(mapper.selectApForUpdate(TENANT_ID, "ap-1")).thenReturn(ap(10000L, 0L, 10000L, 1L));
        when(mapper.sumReservedPaymentAmount(TENANT_ID, "ap-1")).thenReturn(0L);

        SupplierPaymentCommands.Create command = SupplierPaymentCommands.Create.builder()
                .envelope(envelope("payment-over-allocation")).paymentCode("PAY-OVER")
                .legalEntityId("entity-1").ledgerId("ledger-1").accountingPeriodId("period-1")
                .supplierId("supplier-1").payeeInstrumentId("instrument-1").currencyCode("CNY")
                .requestedExecutionDate(LocalDate.of(2026, 8, 2)).totalAmountMinor(11000L)
                .postingRuleId("payment-posting").postingRuleVersion(1L)
                .allocations(List.of(SupplierPaymentCommands.Allocation.builder()
                        .apOpenItemId("ap-1").amountMinor(11000L).build())).build();

        assertThatThrownBy(() -> service.create(command, CREATOR))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("exceeds available AP");
        verify(mapper, never()).insertPaymentInstruction(any());
        verify(mapper, never()).insertApSettlementApplication(anyString(), anyLong(), anyString(), anyString(),
                anyLong(), anyString(), any());
    }

    @Test
    void reversalCreatesExactInverseLinesWithoutUpdatingOriginalPostedJournal() {
        JournalEntry original = new JournalEntry().setJournalEntryId("journal-original")
                .setJournalCode("PI-1").setLegalEntityId("entity-1").setLedgerId("ledger-1")
                .setPeriodId("period-1").setSourceType("SUPPLIER_INVOICE").setSourceId("invoice-1")
                .setCurrencyCode("CNY").setDebitTotalMinor(10000L).setCreditTotalMinor(10000L)
                .setEvidenceSha256("b".repeat(64)).setStatus("POSTED").setPostedByPrincipalId(POSTER).setVersion(1L);
        JournalLine debit = new JournalLine().setJournalLineId("line-1").setLineNumber(1)
                .setLedgerId("ledger-1").setAccountId("account-grir").setAccountCode("GRIR")
                .setDebitAmountMinor(10000L).setCreditAmountMinor(0L).setTransactionCurrencyCode("CNY")
                .setTransactionAmountMinor(10000L);
        JournalLine credit = new JournalLine().setJournalLineId("line-2").setLineNumber(2)
                .setLedgerId("ledger-1").setAccountId("account-ap").setAccountCode("AP")
                .setDebitAmountMinor(0L).setCreditAmountMinor(10000L).setTransactionCurrencyCode("CNY")
                .setTransactionAmountMinor(10000L);
        when(mapper.selectJournalForUpdate(TENANT_ID, "journal-original")).thenReturn(original);
        when(mapper.countJournalReversal(TENANT_ID, "journal-original")).thenReturn(0);
        when(mapper.selectPeriodForUpdate(TENANT_ID, "period-1")).thenReturn(openPeriod());
        when(mapper.selectJournalLines(TENANT_ID, "journal-original")).thenReturn(List.of(debit, credit));
        when(mapper.selectJournalLineDimensions(TENANT_ID, "line-1")).thenReturn(List.of(
                JournalDimensionAssignment.builder().dimensionTypeId("cost-center")
                        .dimensionValueId("plant-a").build()));
        when(mapper.selectJournalLineDimensions(TENANT_ID, "line-2")).thenReturn(List.of());
        when(mapper.insertJournalLineDimension(anyString(), eq(TENANT_ID), anyString(),
                eq("cost-center"), eq("plant-a"), any())).thenReturn(1);
        when(mapper.insertJournalEntry(any())).thenReturn(1);
        when(mapper.insertJournalLine(any())).thenReturn(1);
        when(mapper.insertJournalSourceEffect(anyString(), eq(TENANT_ID), eq("JOURNAL_REVERSAL"),
                eq("journal-original"), eq("REVERSAL"), anyString(), any())).thenReturn(1);
        when(mapper.insertJournalReversalLink(anyString(), eq(TENANT_ID), eq("journal-original"),
                anyString(), anyString(), eq(APPROVER), any())).thenReturn(1);

        ProcureToPayResult result = service.reverse(JournalCommands.Reverse.builder()
                .envelope(envelope("journal-reversal")).originalJournalEntryId("journal-original")
                .expectedVersion(1L).reversalJournalCode("REV-PI-1").accountingPeriodId("period-1")
                .accountingDate(LocalDate.of(2026, 8, 2)).reversalEvidenceSha256("c".repeat(64))
                .reasonCode("CORRECTION").build(), APPROVER);

        assertThat(result.getStatus()).isEqualTo("POSTED");
        ArgumentCaptor<JournalLine> lines = ArgumentCaptor.forClass(JournalLine.class);
        verify(mapper, times(2)).insertJournalLine(lines.capture());
        assertThat(lines.getAllValues().get(0).getDebitAmountMinor()).isZero();
        assertThat(lines.getAllValues().get(0).getCreditAmountMinor()).isEqualTo(10000L);
        assertThat(lines.getAllValues().get(1).getDebitAmountMinor()).isEqualTo(10000L);
        assertThat(lines.getAllValues().get(1).getCreditAmountMinor()).isZero();
        verify(mapper).insertJournalReversalLink(anyString(), eq(TENANT_ID), eq("journal-original"),
                eq(result.getJournalEntryId()), anyString(), eq(APPROVER), any());
        verify(mapper).insertJournalLineDimension(anyString(), eq(TENANT_ID), anyString(),
                eq("cost-center"), eq("plant-a"), any());
    }

    @Test
    void qualifiedReceiptUsesFrozenCostEvidenceAndPostsInventoryAgainstGrir() {
        InventoryMovementEvidence evidence = new InventoryMovementEvidence().setInventoryMovementId("movement-1")
                .setInboxId(81L).setReceiptLineId("receipt-line-1").setQualityDispositionId("quality-1")
                .setPurchaseOrderItemId("po-item-1").setMovementQuantity(new BigDecimal("10"))
                .setUnitOfMeasure("PCS").setUnitCostAmountMinor(900L).setMovementCostAmountMinor(9000L)
                .setDisposition("ACCEPTED").setCurrencyCode("CNY").setValuationPolicyId("valuation-1")
                .setValuationPolicyVersion("V2")
                .setSourceVersion(7L);
        when(mapper.selectInventoryEvidenceForUpdate(TENANT_ID, "movement-1", 7L)).thenReturn(evidence);
        when(mapper.countActiveValuationPolicy(TENANT_ID, "valuation-1", "V2", "ledger-1")).thenReturn(1);
        when(mapper.selectPoEvidenceForInventory(TENANT_ID, "movement-1", 7L)).thenReturn(poEvidence(9000L, 0L));
        when(mapper.selectInbox(TENANT_ID, 81L)).thenReturn(new EventInbox().setEvidenceSha256("e".repeat(64)));
        when(mapper.selectPeriodForUpdate(TENANT_ID, "period-1")).thenReturn(openPeriod());
        when(mapper.selectPostingAccounts(TENANT_ID, "receipt-posting", 1L, "ledger-1", "QUALIFIED_RECEIPT"))
                .thenReturn(List.of(posting("INVENTORY", "account-inventory", "1405"),
                        posting("GRIR", "account-grir", "2202")));
        when(mapper.insertValuationLayer(any())).thenReturn(1);
        when(mapper.insertJournalEntry(any())).thenReturn(1);
        when(mapper.insertJournalLine(any())).thenReturn(1);
        when(mapper.insertJournalSourceEffect(anyString(), eq(TENANT_ID), eq("QUALIFIED_RECEIPT"),
                eq("movement-1"), eq("QUALIFIED_RECEIPT"), anyString(), any())).thenReturn(1);
        when(mapper.insertQualifiedReceiptValuationEffect(anyString(), eq(TENANT_ID), anyString(),
                eq("movement-1"), eq(7L), eq(new BigDecimal("10")), eq(9000L), eq("CNY"), anyString(), any()))
                .thenReturn(1);

        ProcureToPayResult result = service.postQualifiedReceipt(P2pEvidenceCommands.PostQualifiedReceipt.builder()
                .envelope(envelope("qualified-receipt")).inventoryMovementId("movement-1")
                .inventoryMovementVersion(7L).ledgerId("ledger-1").accountingPeriodId("period-1")
                .accountingDate(LocalDate.of(2026, 8, 2)).postingRuleId("receipt-posting")
                .postingRuleVersion(1L).build(), POSTER);

        assertThat(result.getStatus()).isEqualTo("POSTED");
        ArgumentCaptor<JournalLine> lines = ArgumentCaptor.forClass(JournalLine.class);
        verify(mapper, times(2)).insertJournalLine(lines.capture());
        assertThat(lines.getAllValues()).extracting(JournalLine::getAccountId)
                .containsExactly("account-inventory", "account-grir");
        assertThat(lines.getAllValues().stream().mapToLong(JournalLine::getDebitAmountMinor).sum()).isEqualTo(9000L);
        assertThat(lines.getAllValues().stream().mapToLong(JournalLine::getCreditAmountMinor).sum()).isEqualTo(9000L);
    }

    @Test
    void zeroCostQualifiedReceiptCreatesAuditableValuationWithoutZeroJournal() {
        InventoryMovementEvidence evidence = new InventoryMovementEvidence().setInventoryMovementId("movement-zero")
                .setInboxId(82L).setReceiptLineId("receipt-line-1").setQualityDispositionId("quality-1")
                .setPurchaseOrderItemId("po-item-1").setMovementQuantity(new BigDecimal("2"))
                .setUnitOfMeasure("PCS").setUnitCostAmountMinor(0L).setMovementCostAmountMinor(0L)
                .setDisposition("ACCEPTED").setCurrencyCode("CNY").setValuationPolicyId("valuation-1")
                .setValuationPolicyVersion("V2").setSourceVersion(8L);
        when(mapper.selectInventoryEvidenceForUpdate(TENANT_ID, "movement-zero", 8L)).thenReturn(evidence);
        when(mapper.countActiveValuationPolicy(TENANT_ID, "valuation-1", "V2", "ledger-1")).thenReturn(1);
        when(mapper.selectPoEvidenceForInventory(TENANT_ID, "movement-zero", 8L)).thenReturn(poEvidence(0L, 0L));
        when(mapper.selectInbox(TENANT_ID, 82L)).thenReturn(new EventInbox().setEvidenceSha256("f".repeat(64)));
        when(mapper.selectPeriodForUpdate(TENANT_ID, "period-1")).thenReturn(openPeriod());
        when(mapper.selectPostingAccounts(TENANT_ID, "receipt-posting", 1L, "ledger-1", "QUALIFIED_RECEIPT"))
                .thenReturn(List.of(posting("INVENTORY", "account-inventory", "1405"),
                        posting("GRIR", "account-grir", "2202")));
        when(mapper.insertValuationLayer(any())).thenReturn(1);
        when(mapper.insertQualifiedReceiptValuationEffect(anyString(), eq(TENANT_ID), anyString(),
                eq("movement-zero"), eq(8L), eq(new BigDecimal("2")), eq(0L), eq("CNY"), isNull(), any()))
                .thenReturn(1);

        ProcureToPayResult result = service.postQualifiedReceipt(P2pEvidenceCommands.PostQualifiedReceipt.builder()
                .envelope(envelope("qualified-receipt-zero")).inventoryMovementId("movement-zero")
                .inventoryMovementVersion(8L).ledgerId("ledger-1").accountingPeriodId("period-1")
                .accountingDate(LocalDate.of(2026, 8, 2)).postingRuleId("receipt-posting")
                .postingRuleVersion(1L).build(), POSTER);

        assertThat(result.getStatus()).isEqualTo("POSTED");
        assertThat(result.getJournalEntryId()).isNull();
        verify(mapper, never()).insertJournalEntry(any());
        verify(mapper, never()).insertJournalLine(any());
    }

    @Test
    void duplicateSourceEventReturnsExistingEvidenceWithoutTypedInsertOrOutbox() {
        when(mapper.insertInbox(any())).thenAnswer(invocation -> {
            ((EventInbox) invocation.getArgument(0)).setInboxId(77L);
            return 0;
        });
        when(mapper.selectInboxForUpdate(TENANT_ID, 77L)).thenReturn(sourceInbox(
                "po-event-1", "procurement.purchase_order.line_published", "po-item-1", 1L,
                "a".repeat(64), "previous-attempt"));
        when(mapper.selectPoEvidenceIdByInbox(TENANT_ID, 77L)).thenReturn("po-evidence-1");

        ProcureToPayResult result = service.ingestPurchaseOrderLine(purchaseOrderEvidenceCommand("a".repeat(64)), CREATOR);

        assertThat(result.getDuplicate()).isTrue();
        assertThat(result.getAggregateId()).isEqualTo("po-evidence-1");
        verify(mapper, never()).insertPurchaseOrderLineEvidence(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void duplicateSourceEventWithDifferentEvidenceHashFailsClosed() {
        when(mapper.insertInbox(any())).thenAnswer(invocation -> {
            ((EventInbox) invocation.getArgument(0)).setInboxId(77L);
            return 0;
        });
        when(mapper.selectInboxForUpdate(TENANT_ID, 77L)).thenReturn(sourceInbox(
                "po-event-1", "procurement.purchase_order.line_published", "po-item-1", 1L,
                "b".repeat(64), "previous-attempt"));

        assertThatThrownBy(() -> service.ingestPurchaseOrderLine(
                purchaseOrderEvidenceCommand("a".repeat(64)), CREATOR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conflicts with different immutable evidence payload");
        verify(mapper, never()).insertPurchaseOrderLineEvidence(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void rejectedInventoryMovementAcceptsCanonicalStringPolicyAndZeroCostSnapshot() {
        AtomicReference<EventInbox> insertedInbox = new AtomicReference<>();
        when(mapper.insertInbox(any())).thenAnswer(invocation -> {
            EventInbox value = invocation.getArgument(0);
            value.setInboxId(88L);
            insertedInbox.set(value);
            return 1;
        });
        when(mapper.selectInboxForUpdate(TENANT_ID, 88L)).thenAnswer(invocation -> insertedInbox.get());
        when(mapper.selectMaxInventoryEvidenceVersion(TENANT_ID, "movement-rejected-1")).thenReturn(null);
        when(mapper.selectQualityEvidenceForUpdate(TENANT_ID, "quality-1", 3L)).thenReturn(
                new QualityDispositionEvidence().setQualityDispositionId("quality-1")
                        .setReceiptLineId("receipt-line-1").setPurchaseOrderItemId("po-item-1")
                        .setAcceptedQuantity(new BigDecimal("4")).setRejectedQuantity(new BigDecimal("2"))
                        .setHeldQuantity(BigDecimal.ONE).setUnitOfMeasure("PCS").setSourceVersion(3L));
        when(mapper.insertInventoryMovementEvidence(any())).thenReturn(1);

        P2pEvidenceCommands.InventoryMovement command = P2pEvidenceCommands.InventoryMovement.builder()
                .envelope(envelope("inventory-rejected-zero")).sourceEventId("inventory-event-1")
                .sourceVersion(1L).evidenceSha256("c".repeat(64))
                .sourceOccurredAt(Instant.parse("2026-08-02T00:00:00Z"))
                .inventoryMovementId("movement-rejected-1").receiptLineId("receipt-line-1")
                .qualityDispositionId("quality-1").qualityDispositionVersion(3L).disposition("REJECTED")
                .purchaseOrderItemId("po-item-1").movementQuantity(new BigDecimal("2"))
                .unitOfMeasure("PCS").unitCostAmountMinor(0L).movementCostAmountMinor(0L)
                .currencyCode("CNY").valuationPolicyId("valuation-standard")
                .valuationPolicyVersion("V1").build();

        ProcureToPayResult result = service.ingestInventoryMovement(command, CREATOR);

        assertThat(result.getDuplicate()).isFalse();
        verify(mapper).insertInventoryMovementEvidence(argThat(value -> "REJECTED".equals(value.getDisposition())
                && "V1".equals(value.getValuationPolicyVersion())
                && value.getUnitCostAmountMinor() == 0L && value.getMovementCostAmountMinor() == 0L));
    }

    private static EventInbox sourceInbox(String eventId, String eventType, String aggregateId,
                                          long version, String evidenceHash, String token) {
        return new EventInbox().setInboxId(77L).setTenantId(TENANT_ID).setSourceEventId(eventId)
                .setSourceEventType(eventType).setSourceSchemaVersion(1).setSourceAggregateId(aggregateId)
                .setSourceAggregateVersion(version).setEvidenceSha256(evidenceHash).setAttemptToken(token)
                .setSourceOccurredAt(LocalDateTime.of(2026, 8, 2, 0, 0));
    }

    private static P2pEvidenceCommands.PurchaseOrderLine purchaseOrderEvidenceCommand(String hash) {
        return P2pEvidenceCommands.PurchaseOrderLine.builder().envelope(envelope("po-evidence-command"))
                .sourceEventId("po-event-1").sourceVersion(1L).evidenceSha256(hash)
                .sourceOccurredAt(Instant.parse("2026-08-02T00:00:00Z"))
                .purchaseOrderId("po-1").purchaseOrderItemId("po-item-1")
                .legalEntityId("entity-1").supplierId("supplier-1").currencyCode("CNY")
                .orderedQuantity(new BigDecimal("10")).unitOfMeasure("PCS")
                .unitNetPrice(new BigDecimal("9.00")).netAmountMinor(9000L)
                .taxAmountMinor(1000L).grossAmountMinor(10000L).build();
    }

    private static FinanceCommandEnvelope envelope(String key) {
        return FinanceCommandEnvelope.builder().idempotencyKey(key).runId("run-1")
                .correlationId("22222222-2222-4222-8222-222222222222")
                .occurredAt(Instant.parse("2026-08-02T00:00:00Z")).build();
    }

    private static SupplierInvoice invoice(String lifecycle, String match, long version) {
        return new SupplierInvoice().setSupplierInvoiceId("invoice-1").setInvoiceCode("INV-1")
                .setLegalEntityId("entity-1").setLedgerId("ledger-1").setAccountingPeriodId("period-1")
                .setSupplierId("supplier-1").setCurrencyCode("CNY").setIssueDate(LocalDate.of(2026, 8, 1))
                .setAccountingDate(LocalDate.of(2026, 8, 2)).setDueDate(LocalDate.of(2026, 8, 31))
                .setNetAmountMinor(9000L).setTaxAmountMinor(1000L).setGrossAmountMinor(10000L)
                .setLifecycleStatus(lifecycle).setMatchStatus(match).setSettlementStatus("UNPAID")
                .setEvidenceSha256("d".repeat(64)).setCreatedByPrincipalId(CREATOR)
                .setApprovedByPrincipalId(APPROVER).setPaymentTermId("term-1").setPaymentTermVersion(1L)
                .setPostingRuleId("invoice-posting").setPostingRuleVersion(1L)
                .setVersion(version);
    }

    private static SupplierInvoiceLine invoiceLine(long net, long tax, long gross) {
        return new SupplierInvoiceLine().setInvoiceLineId("invoice-line-1").setSupplierInvoiceId("invoice-1")
                .setLineNumber(1).setPurchaseOrderId("po-1").setPurchaseOrderItemId("po-item-1")
                .setSkuId("sku-1").setQuantity(new BigDecimal("10")).setUnitOfMeasure("PCS")
                .setUnitNetPrice(new BigDecimal("9.00")).setNetAmountMinor(net).setTaxCode("VAT")
                .setTaxRate(new BigDecimal("0.10")).setTaxAmountMinor(tax).setGrossAmountMinor(gross).setVersion(1L);
    }

    private static PurchaseOrderLineEvidence poEvidence(long net, long tax) {
        return new PurchaseOrderLineEvidence().setPurchaseOrderId("po-1").setPurchaseOrderItemId("po-item-1")
                .setLegalEntityId("entity-1").setSupplierId("supplier-1").setCurrencyCode("CNY")
                .setOrderedQuantity(new BigDecimal("10")).setUnitOfMeasure("PCS")
                .setUnitNetPrice(new BigDecimal("9.00")).setNetAmountMinor(net).setTaxAmountMinor(tax)
                .setGrossAmountMinor(net + tax).setSourceVersion(1L);
    }

    private static MatchCandidate candidate(String quantity) {
        return new MatchCandidate().setReceiptLineId("receipt-line-1").setReceiptLineVersion(1L)
                .setQualityDispositionId("quality-1").setQualityDispositionVersion(1L)
                .setInventoryMovementId("movement-1").setInventoryMovementVersion(1L)
                .setPurchaseOrderItemId("po-item-1")
                .setAcceptedQuantity(new BigDecimal(quantity)).setPreviouslyAllocatedQuantity(BigDecimal.ZERO)
                .setUnitOfMeasure("PCS");
    }

    private static ApOpenItem ap(long original, long settled, long open, long version) {
        return new ApOpenItem().setApOpenItemId("ap-1").setSupplierInvoiceId("invoice-1")
                .setLegalEntityId("entity-1").setLedgerId("ledger-1").setSupplierId("supplier-1")
                .setCurrencyCode("CNY").setOriginalAmountMinor(original).setSettledAmountMinor(settled)
                .setOpenAmountMinor(open).setStatus(settled == 0 ? "OPEN" : "PARTIALLY_SETTLED").setVersion(version);
    }

    private static AccountingPeriod openPeriod() {
        return new AccountingPeriod().setPeriodId("period-1").setPeriodStart(LocalDate.of(2026, 8, 1))
                .setPeriodEnd(LocalDate.of(2026, 8, 31)).setCurrencyCode("CNY").setStatus("OPEN").setVersion(1L);
    }

    private static MatchPolicy matchPolicy(long priceTolerance) {
        return new MatchPolicy().setMatchPolicyId("policy-standard").setLegalEntityId("entity-1")
                .setPolicyVersion(3L).setPriceToleranceAmountMinor(priceTolerance)
                .setTaxToleranceAmountMinor(0L).setQuantityTolerance(BigDecimal.ZERO).setStatus("ACTIVE");
    }

    private static PostingAccount posting(String role, String accountId, String accountCode) {
        return new PostingAccount().setAccountRole(role).setLedgerId("ledger-1")
                .setAccountId(accountId).setAccountCode(accountCode);
    }

    private static P2pFinanceSetupCommands.PostingRuleLine postingRuleLine(String role, String accountId) {
        return P2pFinanceSetupCommands.PostingRuleLine.builder()
                .postingRuleLineId("posting-line-" + role.toLowerCase(java.util.Locale.ROOT))
                .accountRole(role).accountId(accountId).build();
    }

    private static List<PostingAccount> invoicePostingAccounts() {
        return List.of(posting("GRIR", "account-grir", "2202"),
                posting("INPUT_TAX", "account-tax", "2221"),
                posting("PURCHASE_PRICE_VARIANCE", "account-ppv", "6602"),
                posting("AP", "account-ap", "2201"));
    }

    private static PayeeInstrument validPayee() {
        return new PayeeInstrument().setPayeeInstrumentId("instrument-1").setLegalEntityId("entity-1")
                .setSupplierId("supplier-1").setCurrencyCode("CNY").setStatus("ACTIVE")
                .setVerificationStatus("VERIFIED").setValidFrom(LocalDate.of(2026, 1, 1));
    }
}
