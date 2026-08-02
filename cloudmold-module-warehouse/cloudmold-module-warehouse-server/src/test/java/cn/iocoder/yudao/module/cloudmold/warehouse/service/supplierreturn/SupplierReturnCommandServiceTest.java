package cn.iocoder.yudao.module.cloudmold.warehouse.service.supplierreturn;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.inventory.api.*;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementOrderView;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementQueryApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseReferenceValidationApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.supplierreturn.SupplierReturnCommand;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.supplierreturn.SupplierReturnOperation;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.supplierreturn.SupplierReturnResult;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.supplierreturn.SupplierReturnSourceDisposition;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.SupplierReturnDocumentDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.SupplierReturnLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.SupplierReturnOperationDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.SupplierReturnReferenceMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.SupplierReturnStoreMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupplierReturnCommandServiceTest {

    private final SupplierReturnStoreMapper storeMapper = mock(SupplierReturnStoreMapper.class);
    private final SupplierReturnReferenceMapper referenceMapper = mock(SupplierReturnReferenceMapper.class);
    private final ProcurementQueryApi procurementQueryApi = mock(ProcurementQueryApi.class);
    private final WarehouseReferenceValidationApi warehouseReferenceValidationApi =
            mock(WarehouseReferenceValidationApi.class);
    private final InventoryProcurementReceiptApi inventoryProcurementReceiptApi =
            mock(InventoryProcurementReceiptApi.class);
    private final cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender outboxAppender =
            mock(cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender.class);

    private final SupplierReturnCommandService service = spy(new SupplierReturnCommandService(
            storeMapper, referenceMapper, procurementQueryApi, warehouseReferenceValidationApi,
            inventoryProcurementReceiptApi, outboxAppender));

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
    void createsDraftSupplierReturnUsingAuthoritativeQualitySnapshot() {
        SupplierReturnCommand command = createDraftCommand();
        prepareOperation(command, 11L);
        when(procurementQueryApi.requireCurrent("po-01")).thenReturn(
                ProcurementOrderView.builder().orderId("po-01").supplierId("supplier-01").build());
        when(storeMapper.selectDocument(1L, "return-01")).thenReturn(null);
        when(referenceMapper.selectQualityDecisionReference(1L, "decision-01", 2L))
                .thenReturn(qualityReference());
        when(referenceMapper.selectReceiptLineReference(1L, "receipt-line-01"))
                .thenReturn(receiptReference());
        when(storeMapper.sumActiveReturnQuantityByQualityKeyExcludingReturn(
                1L, "decision-01", 2L, "split-01", "ACCEPTED", null))
                .thenReturn(new BigDecimal("1.000000"));
        when(storeMapper.insertDocument(any())).thenReturn(1);
        when(storeMapper.insertLine(any())).thenReturn(1);
        when(storeMapper.insertStatusHistory(any())).thenReturn(1);
        when(storeMapper.markOperationSucceeded(eq(1L), eq(11L), eq("return-01"), anyString(), any()))
                .thenReturn(1);

        SupplierReturnResult result = service.execute(command, "principal-01");

        assertThat(result.getStatus()).isEqualTo("DRAFT");
        assertThat(result.getReturnId()).isEqualTo("return-01");
        verify(warehouseReferenceValidationApi).requireActiveWarehouse("warehouse-01");
        verifyNoInteractions(inventoryProcurementReceiptApi);
    }

    @Test
    void dispatchesApprovedSupplierReturnThroughInventoryBoundary() {
        SupplierReturnCommand command = dispatchCommand();
        prepareOperation(command, 21L);
        when(storeMapper.selectDocumentForUpdate(1L, "return-01")).thenReturn(new SupplierReturnDocumentDO()
                .setReturnId("return-01").setReturnCode("SRTRN-0001").setPurchaseOrderId("po-01")
                .setReceiptId("receipt-01").setSupplierId("supplier-01").setOwnerType("MERCHANT")
                .setOwnerId("merchant-01").setReasonCode("QUALITY_REJECT").setRemark("dispatch")
                .setStatus("APPROVED").setVersion(2L));
        when(storeMapper.selectLineForUpdate(1L, "return-01", "line-01")).thenReturn(new SupplierReturnLineDO()
                .setReturnLineId("line-01").setLineNumber(10).setReceiptLineId("receipt-line-01")
                .setPurchaseOrderItemId("item-01").setPurchaseOrderScheduleId("schedule-01")
                .setQualityDecisionId("decision-01").setDecisionVersion(2L).setSourceDisposition("ACCEPTED")
                .setCanonicalSkuId("sku-01").setWarehouseId("warehouse-01").setLocationId("location-01")
                .setLotId("lot-01").setReturnQuantity(new BigDecimal("4.000000"))
                .setDispatchedQuantity(new BigDecimal("1.000000")).setOutstandingQuantity(new BigDecimal("3.000000"))
                .setUomCode("PIECE").setValuationPolicyId("valuation-policy-01")
                .setValuationPolicyVersion("v1").setValuationPolicyHash("a".repeat(64))
                .setUnitCostAmountMinor(250L).setCurrencyCode("CNY").setQualityEvidenceRef("sha256:" + "b".repeat(64))
                .setStatus("APPROVED").setVersion(3L));
        when(storeMapper.insertDispatchBatch(any())).thenReturn(1);
        when(inventoryProcurementReceiptApi.execute(any())).thenReturn(InventoryProcurementReceiptResult.builder()
                .operationId(51L).ledgerTransactionId(61L).receiptId("receipt-01").receiptLineId("receipt-line-01")
                .unitCostAmountMinor(250L).movementCostAmountMinor(750L).currencyCode("CNY")
                .valuationPolicyId("valuation-policy-01").valuationPolicyVersion("v1")
                .valuationPolicyHash("a".repeat(64)).targetAggregateVersion(7L).build());
        when(storeMapper.updateLineDispatchCas(eq(1L), eq("line-01"), eq(3L), eq(4L),
                eq(new BigDecimal("4.000000")), eq(new BigDecimal("0.000000")), eq("DISPATCHED"), any()))
                .thenReturn(1);
        when(storeMapper.insertDispatchLine(any())).thenReturn(1);
        when(storeMapper.insertStatusHistory(any())).thenReturn(1);
        when(storeMapper.selectLines(1L, "return-01")).thenReturn(List.of(new SupplierReturnLineDO()
                .setReturnLineId("line-01").setOutstandingQuantity(new BigDecimal("0.000000"))));
        when(storeMapper.updateDocumentStatusCas(eq(1L), eq("return-01"), eq(2L), eq(3L), eq("DISPATCHED"),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(storeMapper.markOperationSucceeded(eq(1L), eq(21L), eq("return-01"), anyString(), any()))
                .thenReturn(1);

        SupplierReturnResult result = service.execute(command, "principal-01");

        assertThat(result.getStatus()).isEqualTo("DISPATCHED");
        assertThat(result.getBatchId()).isEqualTo("batch-01");
        verify(inventoryProcurementReceiptApi).execute(argThat(input ->
                input.getOperation() == InventoryProcurementReceiptOperation.RETURN_TO_SUPPLIER
                        && input.getDisposition() == InventoryProcurementReceiptDisposition.ACCEPTED
                        && new BigDecimal("3.000000").compareTo(input.getQuantity()) == 0));
    }

    private void prepareOperation(SupplierReturnCommand command, Long operationId) {
        when(storeMapper.selectLastInsertId()).thenReturn(operationId);
        when(storeMapper.selectOperationForUpdate(1L, operationId)).thenReturn(new SupplierReturnOperationDO()
                .setOperationId(operationId).setTenantId(1L).setAttemptToken("attempt-1")
                .setRequestHash(cn.hutool.crypto.digest.DigestUtil.sha256Hex(
                        1L + "\n" + cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(command)))
                .setStatus(0));
    }

    private static SupplierReturnCommand createDraftCommand() {
        return SupplierReturnCommand.builder()
                .operation(SupplierReturnOperation.CREATE_DRAFT)
                .idempotencyKey("idem-create")
                .sourceEventId("source-create")
                .occurredAt(Instant.parse("2026-08-02T12:00:00Z"))
                .create(SupplierReturnCommand.CreateDefinition.builder()
                        .returnId("return-01").returnCode("SRTRN-0001").purchaseOrderId("po-01")
                        .receiptId("receipt-01").reasonCode("QUALITY_REJECT")
                        .lines(List.of(SupplierReturnCommand.LineDefinition.builder()
                                .returnLineId("line-01").lineNumber(10).qualityDecisionId("decision-01")
                                .decisionVersion(2L).sourceDisposition(SupplierReturnSourceDisposition.ACCEPTED)
                                .returnQuantity(new BigDecimal("4.000000")).build()))
                        .build())
                .build();
    }

    private static SupplierReturnCommand dispatchCommand() {
        return SupplierReturnCommand.builder()
                .operation(SupplierReturnOperation.DISPATCH)
                .idempotencyKey("idem-dispatch")
                .sourceEventId("source-dispatch")
                .occurredAt(Instant.parse("2026-08-02T13:00:00Z"))
                .returnId("return-01").expectedVersion(2L)
                .dispatchBatch(SupplierReturnCommand.DispatchBatchDefinition.builder()
                        .batchId("batch-01").batchNo("SRDSP-0001")
                        .lines(List.of(SupplierReturnCommand.DispatchLineDefinition.builder()
                                .executionLineId("exec-01").returnLineId("line-01")
                                .dispatchQuantity(new BigDecimal("3.000000")).build()))
                        .build())
                .build();
    }

    private static SupplierReturnReferenceMapper.QualityDecisionReference qualityReference() {
        return new SupplierReturnReferenceMapper.QualityDecisionReference()
                .setQualityDecisionId("decision-01").setDecisionVersion(2L).setInspectionSplitId("split-01")
                .setEvidenceRef("sha256:" + "b".repeat(64)).setReceiptLineId("receipt-line-01")
                .setPurchaseOrderId("po-01").setPurchaseOrderItemId("item-01")
                .setPurchaseOrderScheduleId("schedule-01").setCanonicalSkuId("sku-01").setUomCode("PIECE")
                .setSupplierId("supplier-01").setOwnerType("MERCHANT").setOwnerId("merchant-01")
                .setValuationPolicyId("valuation-policy-01").setValuationPolicyVersion("v1")
                .setValuationPolicyHash("a".repeat(64)).setUnitCostAmountMinor(250L).setCurrencyCode("CNY")
                .setWarehouseId("warehouse-01").setLocationId("location-01").setLotId("lot-01")
                .setAcceptedQuantity(new BigDecimal("6.000000")).setRejectedQuantity(BigDecimal.ZERO.setScale(6))
                .setQuarantinedQuantity(BigDecimal.ZERO.setScale(6));
    }

    private static SupplierReturnReferenceMapper.ReceiptLineReference receiptReference() {
        return new SupplierReturnReferenceMapper.ReceiptLineReference()
                .setReceiptId("receipt-01").setProcurementOrderId("po-01").setSupplierId("supplier-01")
                .setWarehouseId("warehouse-01").setReceiptLineId("receipt-line-01")
                .setProcurementOrderItemId("item-01").setDeliveryScheduleId("schedule-01")
                .setCanonicalSkuId("sku-01").setOwnerType("MERCHANT").setOwnerId("merchant-01")
                .setBaseUomCode("PIECE").setValuationPolicyId("valuation-policy-01")
                .setValuationPolicyVersion("v1").setValuationPolicyHash("a".repeat(64))
                .setUnitCostAmountMinor(250L).setCurrencyCode("CNY").setReceiptLocationId("location-01")
                .setLotId("lot-01");
    }
}
