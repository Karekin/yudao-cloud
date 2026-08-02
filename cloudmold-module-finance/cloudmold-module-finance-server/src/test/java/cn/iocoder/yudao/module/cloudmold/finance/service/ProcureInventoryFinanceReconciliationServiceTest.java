package cn.iocoder.yudao.module.cloudmold.finance.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.ProcureInventoryFinanceReconciliationAdminVOs.CreateRunRequest;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureInventoryFinanceReconciliationRecords.*;
import cn.iocoder.yudao.module.cloudmold.finance.dal.mysql.ProcureInventoryFinanceReconciliationMapper;
import cn.iocoder.yudao.module.cloudmold.finance.dal.mysql.ProcureToPayMapper;
import cn.iocoder.yudao.module.cloudmold.finance.service.actor.FinanceActorPrincipalPort;
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

class ProcureInventoryFinanceReconciliationServiceTest {
    private static final long TENANT_ID = 162L;
    private static final String ACTOR = "finance-recon-actor";
    private final ProcureToPayMapper operationMapper = mock(ProcureToPayMapper.class);
    private final ProcureInventoryFinanceReconciliationMapper mapper = mock(ProcureInventoryFinanceReconciliationMapper.class);
    private final FinanceActorPrincipalPort actorPort = mock(FinanceActorPrincipalPort.class);
    private final ProcureInventoryFinanceReconciliationService service =
            new ProcureInventoryFinanceReconciliationService(operationMapper, mapper, actorPort);
    private final AtomicReference<String> requestHash = new AtomicReference<>();
    private final AtomicReference<String> attemptToken = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(operationMapper.insertOrResolveOperation(eq(TENANT_ID), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    requestHash.set(invocation.getArgument(3));
                    attemptToken.set(invocation.getArgument(4));
                    return 1;
                });
        when(operationMapper.selectLastInsertId()).thenReturn(701L);
        when(operationMapper.selectOperationForUpdate(701L, TENANT_ID)).thenAnswer(invocation -> new Operation()
                .setOperationId(701L).setTenantId(TENANT_ID)
                .setRequestHash(requestHash.get()).setAttemptToken(attemptToken.get()).setStatus(0));
        when(operationMapper.markOperationSucceeded(eq(701L), eq(TENANT_ID), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(mapper.insertRun(any())).thenReturn(1);
        when(mapper.insertWatermarks(anyList())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        when(mapper.insertLines(anyList())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        when(mapper.insertDifferences(anyList())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createsImmutableRunWithMatchedDifferentMissingAndUncomparableLines() {
        when(mapper.selectQualifiedReceiptSources(TENANT_ID, "entity-1", "CNY")).thenReturn(List.of(
                qualifiedReceipt("MATCHED"),
                qualifiedReceiptMissingJournal(),
                qualifiedReceiptDifferentAmount(),
                qualifiedReceiptUncomparable()));
        when(mapper.selectSupplierReturnSources(TENANT_ID, "entity-1", "CNY")).thenReturn(List.of(
                supplierReturnMissingFinance()));
        when(mapper.selectSupplierInvoiceSources(TENANT_ID, "entity-1", "CNY")).thenReturn(List.of(
                supplierInvoiceMatched(),
                supplierInvoiceDifferent()));

        ProcureInventoryFinanceReconciliationResult result = service.createRun(request(), ACTOR);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getLineCount()).isEqualTo(7);
        assertThat(result.getMatchedCount()).isEqualTo(2);
        assertThat(result.getDifferentCount()).isEqualTo(2);
        assertThat(result.getMissingCount()).isEqualTo(2);
        assertThat(result.getUncomparableCount()).isEqualTo(1);
        ArgumentCaptor<List<ReconciliationLine>> lines = ArgumentCaptor.forClass(List.class);
        verify(mapper).insertLines(lines.capture());
        assertThat(lines.getValue()).extracting(ReconciliationLine::getMatchStatus)
                .containsExactlyInAnyOrder("MATCHED", "MISSING", "DIFFERENT", "UNCOMPARABLE",
                        "MISSING", "MATCHED", "DIFFERENT");
        assertThat(lines.getValue()).anySatisfy(line -> {
            if ("QUALIFIED_RECEIPT".equals(line.getLineType()) && "MATCHED".equals(line.getMatchStatus())) {
                assertThat(line.getResponsibilityDomain()).isEqualTo("NONE");
                assertThat(line.getPrimaryDifferenceCode()).isNull();
                assertThat(line.getQuantityDifference()).isEqualByComparingTo("0.000000");
                assertThat(line.getAmountDifferenceMinor()).isZero();
            }
        });
        assertThat(lines.getValue()).anySatisfy(line -> {
            if ("QUALIFIED_RECEIPT".equals(line.getLineType()) && "MISSING".equals(line.getMatchStatus())) {
                assertThat(line.getResponsibilityDomain()).isEqualTo("FINANCE");
                assertThat(line.getPrimaryDifferenceCode()).isEqualTo("FINANCE_JOURNAL_MISSING");
                assertThat(line.getQuantityDifference()).isEqualByComparingTo("0.000000");
                assertThat(line.getAmountDifferenceMinor()).isNull();
            }
        });
        assertThat(lines.getValue()).anySatisfy(line -> {
            if ("SUPPLIER_RETURN".equals(line.getLineType())) {
                assertThat(line.getResponsibilityDomain()).isEqualTo("FINANCE");
                assertThat(line.getPrimaryDifferenceCode()).isEqualTo("RETURN_FINANCE_EFFECT_MISSING");
                assertThat(line.getQuantityDifference()).isEqualByComparingTo("0.000000");
                assertThat(line.getAmountDifferenceMinor()).isNull();
            }
        });
        assertThat(lines.getValue()).anySatisfy(line -> {
            if ("SUPPLIER_INVOICE".equals(line.getLineType()) && "DIFFERENT".equals(line.getMatchStatus())) {
                assertThat(line.getResponsibilityDomain()).isEqualTo("PROCUREMENT");
                assertThat(line.getPrimaryDifferenceCode()).isEqualTo("THREE_WAY_MATCH_EXCEPTION");
                assertThat(line.getQuantityDifference()).isEqualByComparingTo("-1.000000");
            }
        });
        verify(mapper).insertWatermarks(argThat(values -> values.size() == 7));
        verify(mapper).insertDifferences(argThat(values -> !values.isEmpty()));
    }

    @Test
    void rejectsEmptyAuthoritativeSnapshot() {
        when(mapper.selectQualifiedReceiptSources(TENANT_ID, "entity-1", "CNY")).thenReturn(List.of());
        when(mapper.selectSupplierReturnSources(TENANT_ID, "entity-1", "CNY")).thenReturn(List.of());
        when(mapper.selectSupplierInvoiceSources(TENANT_ID, "entity-1", "CNY")).thenReturn(List.of());

        assertThatThrownBy(() -> service.createRun(request(), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reconciliation run requires non-empty authoritative source data");
        verify(mapper, never()).insertRun(any());
    }

    private static CreateRunRequest request() {
        CreateRunRequest request = new CreateRunRequest();
        request.setIdempotencyKey("recon-run-001");
        request.setRunId("run-001");
        request.setCorrelationId("11111111-1111-4111-8111-111111111111");
        request.setOccurredAt(Instant.parse("2026-08-02T10:00:00Z"));
        request.setLegalEntityId("entity-1");
        request.setCurrencyCode("CNY");
        return request;
    }

    private static QualifiedReceiptSource qualifiedReceipt(String ignored) {
        return new QualifiedReceiptSource()
                .setLegalEntityId("entity-1").setCurrencyCode("CNY")
                .setPurchaseOrderId("po-1").setPurchaseOrderItemId("po-item-1").setDeliveryScheduleId("schedule-1")
                .setPurchaseOrderLineVersion(2L).setReceiptLineId("receipt-line-1").setReceiptLineVersion(3L)
                .setQualityDispositionId("quality-1").setQualityDispositionVersion(4L)
                .setInventoryMovementId("movement-1").setInventoryMovementVersion(5L)
                .setAcceptedQuantity(new BigDecimal("10.000000")).setMovementQuantity(new BigDecimal("10.000000"))
                .setMovementCostAmountMinor(9000L).setValuationLayerId("layer-1").setValuationLayerVersion(1L)
                .setValuationAmountMinor(9000L).setJournalEntryId("journal-1").setJournalVersion(1L)
                .setJournalAmountMinor(9000L);
    }

    private static QualifiedReceiptSource qualifiedReceiptMissingJournal() {
        return new QualifiedReceiptSource()
                .setLegalEntityId("entity-1").setCurrencyCode("CNY")
                .setPurchaseOrderId("po-2").setPurchaseOrderItemId("po-item-2").setDeliveryScheduleId("schedule-2")
                .setPurchaseOrderLineVersion(2L).setReceiptLineId("receipt-line-2").setReceiptLineVersion(3L)
                .setQualityDispositionId("quality-2").setQualityDispositionVersion(4L)
                .setInventoryMovementId("movement-2").setInventoryMovementVersion(5L)
                .setAcceptedQuantity(new BigDecimal("8.000000")).setMovementQuantity(new BigDecimal("8.000000"))
                .setMovementCostAmountMinor(6400L).setValuationLayerId("layer-2").setValuationLayerVersion(1L)
                .setValuationAmountMinor(6400L);
    }

    private static QualifiedReceiptSource qualifiedReceiptDifferentAmount() {
        return new QualifiedReceiptSource()
                .setLegalEntityId("entity-1").setCurrencyCode("CNY")
                .setPurchaseOrderId("po-3").setPurchaseOrderItemId("po-item-3").setDeliveryScheduleId("schedule-3")
                .setPurchaseOrderLineVersion(2L).setReceiptLineId("receipt-line-3").setReceiptLineVersion(3L)
                .setQualityDispositionId("quality-3").setQualityDispositionVersion(4L)
                .setInventoryMovementId("movement-3").setInventoryMovementVersion(5L)
                .setAcceptedQuantity(new BigDecimal("5.000000")).setMovementQuantity(new BigDecimal("5.000000"))
                .setMovementCostAmountMinor(5000L).setValuationLayerId("layer-3").setValuationLayerVersion(1L)
                .setValuationAmountMinor(5100L).setJournalEntryId("journal-3").setJournalVersion(1L)
                .setJournalAmountMinor(5000L);
    }

    private static QualifiedReceiptSource qualifiedReceiptUncomparable() {
        return new QualifiedReceiptSource()
                .setLegalEntityId("entity-1").setCurrencyCode("CNY")
                .setPurchaseOrderId("po-4").setPurchaseOrderItemId("po-item-4").setDeliveryScheduleId("schedule-4")
                .setPurchaseOrderLineVersion(2L)
                .setInventoryMovementId("movement-4").setInventoryMovementVersion(5L)
                .setMovementQuantity(new BigDecimal("4.000000"))
                .setMovementCostAmountMinor(3200L).setValuationLayerId("layer-4").setValuationLayerVersion(1L)
                .setValuationAmountMinor(3200L).setJournalEntryId("journal-4").setJournalVersion(1L)
                .setJournalAmountMinor(3200L);
    }

    private static SupplierReturnSource supplierReturnMissingFinance() {
        return new SupplierReturnSource()
                .setLegalEntityId("entity-1").setCurrencyCode("CNY")
                .setPurchaseOrderId("po-5").setPurchaseOrderItemId("po-item-5").setDeliveryScheduleId("schedule-5")
                .setReceiptLineId("receipt-line-5").setSupplierReturnId("return-1").setSupplierReturnLineId("return-line-1")
                .setReturnLineVersion(2L).setExecutionLineId("exec-1").setExecutionLineVersion(3L)
                .setInventoryLedgerTransactionId(991L).setInventoryAggregateVersion(4L)
                .setReturnQuantity(new BigDecimal("2.000000")).setDispatchedQuantity(new BigDecimal("2.000000"))
                .setMovementCostAmountMinor(1800L);
    }

    private static SupplierInvoiceSource supplierInvoiceMatched() {
        return new SupplierInvoiceSource()
                .setLegalEntityId("entity-1").setCurrencyCode("CNY")
                .setPurchaseOrderId("po-6").setPurchaseOrderItemId("po-item-6").setPurchaseOrderLineVersion(1L)
                .setSupplierInvoiceId("invoice-1").setSupplierInvoiceLineId("invoice-line-1").setInvoiceLineVersion(2L)
                .setApOpenItemId("ap-1").setApOpenItemVersion(1L)
                .setJournalEntryId("journal-6").setJournalVersion(1L)
                .setInventoryMovementId("movement-6").setInventoryMovementVersion(1L)
                .setInvoiceQuantity(new BigDecimal("3.000000")).setAllocatedQuantity(new BigDecimal("3.000000"))
                .setInvoiceGrossAmountMinor(3000L).setMatchedInventoryAmountMinor(2700L)
                .setApOpenAmountMinor(3000L).setJournalAmountMinor(3000L)
                .setMatchResultStatus("MATCHED").setOpenExceptionCount(0);
    }

    private static SupplierInvoiceSource supplierInvoiceDifferent() {
        return new SupplierInvoiceSource()
                .setLegalEntityId("entity-1").setCurrencyCode("CNY")
                .setPurchaseOrderId("po-7").setPurchaseOrderItemId("po-item-7").setPurchaseOrderLineVersion(1L)
                .setSupplierInvoiceId("invoice-2").setSupplierInvoiceLineId("invoice-line-2").setInvoiceLineVersion(2L)
                .setInventoryMovementId("movement-7").setInventoryMovementVersion(1L)
                .setInvoiceQuantity(new BigDecimal("5.000000")).setAllocatedQuantity(new BigDecimal("4.000000"))
                .setInvoiceGrossAmountMinor(5000L).setMatchResultStatus("EXCEPTION").setOpenExceptionCount(1);
    }
}
