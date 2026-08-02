package cn.iocoder.yudao.module.cloudmold.quality.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.quality.api.ProcurementReceiptInspectionView;
import cn.iocoder.yudao.module.cloudmold.quality.controller.admin.vo.ProcurementReceiptInspectionPageReqVO;
import cn.iocoder.yudao.module.cloudmold.quality.dal.dataobject.ProcurementReceiptInspectionRecords.*;
import cn.iocoder.yudao.module.cloudmold.quality.dal.mysql.ProcurementReceiptInspectionMapper;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProcurementReceiptInspectionQueryServiceTest {
    private final ProcurementReceiptInspectionMapper mapper = mock(ProcurementReceiptInspectionMapper.class);
    private final ProcurementReceiptInspectionQueryService service =
            new ProcurementReceiptInspectionQueryService(mapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(23L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void returnsExactReceiptPoPhysicalDecisionAndDefectFacts() {
        when(mapper.selectInspection(23L, "inspection-1")).thenReturn(new Inspection()
                .setInspectionId("inspection-1").setInspectionCode("PRI-1").setReceiptId("receipt-1")
                .setPurchaseOrderId("po-1").setSupplierId("supplier-1").setOwnerType("MERCHANT")
                .setOwnerId("owner-1").setBusinessNo("GRN-1")
                .setStandardId("standard-1").setStandardVersion(3L)
                .setStandardVersionId("standard-version-3").setStandardContentSha256("a".repeat(64))
                .setStatus("COMPLETED").setFinalDecision("MIXED").setReceivedQuantity(new BigDecimal("12"))
                .setSampledQuantity(new BigDecimal("3")).setAcceptedQuantity(new BigDecimal("10"))
                .setRejectedQuantity(BigDecimal.ONE).setQuarantinedQuantity(BigDecimal.ONE)
                .setVersion(3L).setCompletedAt(LocalDateTime.of(2026, 8, 2, 0, 0)));
        when(mapper.selectLines(23L, "inspection-1")).thenReturn(List.of(new InspectionLine()
                .setInspectionLineId("line-1").setLineNumber(1).setReceiptLineId("receipt-line-1")
                .setPurchaseOrderId("po-1").setItemId("item-1").setScheduleId("schedule-1")
                .setCanonicalSkuId("sku-1").setUomCode("EA").setSupplierId("supplier-1")
                .setOwnerType("MERCHANT").setOwnerId("owner-1").setValuationPolicy("MOVING_AVERAGE")
                .setValuationPolicyVersion("V1").setValuationPolicyHash("d".repeat(64))
                .setUnitCostAmountMinor(100L).setCurrencyCode("CNY")
                .setReceivedQuantity(new BigDecimal("12"))
                .setSampledQuantity(new BigDecimal("3")).setAcceptedQuantity(new BigDecimal("10"))
                .setRejectedQuantity(BigDecimal.ONE).setQuarantinedQuantity(BigDecimal.ONE)
                .setStatus("COMPLETED").setVersion(2L)));
        when(mapper.selectSplits(23L, "inspection-1")).thenReturn(List.of(new InspectionSplit()
                .setInspectionSplitId("split-1").setInspectionLineId("line-1").setSplitNumber(1)
                .setWarehouseId("warehouse-1").setLocationId("location-1").setLotId("lot-1")
                .setUomCode("EA").setReceivedQuantity(new BigDecimal("12"))
                .setSampledQuantity(new BigDecimal("3")).setAcceptedQuantity(new BigDecimal("10"))
                .setRejectedQuantity(BigDecimal.ONE).setQuarantinedQuantity(BigDecimal.ONE)
                .setStatus("COMPLETED").setVersion(2L)));
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 2, 0, 0);
        when(mapper.selectResultBatches(23L, "inspection-1")).thenReturn(List.of(new ResultBatch()
                .setResultBatchId("batch-1").setInspectionVersionBefore(1L).setInspectionVersionAfter(2L)
                .setDecisionVersion(2L)
                .setActorPrincipalId("principal-quality-1").setOperationId(701L).setOccurredAt(occurredAt)));
        when(mapper.selectResultSplits(23L, "inspection-1")).thenReturn(List.of(new ResultSplit()
                .setResultSplitId("result-1").setQualityDecisionId("decision-1").setDecisionVersion(2L)
                .setResultBatchId("batch-1").setInspectionLineId("line-1")
                .setInspectionSplitId("split-1").setSampledQuantity(new BigDecimal("3"))
                .setAcceptedQuantity(new BigDecimal("10")).setRejectedQuantity(BigDecimal.ONE)
                .setQuarantinedQuantity(BigDecimal.ONE).setAcceptedDispositionCode("ACCEPT")
                .setRejectedDispositionCode("RETURN_TO_SUPPLIER").setQuarantineDispositionCode("HOLD")
                .setDecisionEvidenceSha256("b".repeat(64)).setEvidenceRef("evidence/result-1")
                .setActorPrincipalId("principal-quality-1").setOperationId(701L)
                .setFinanceReceiptEvidenceOperationId(1050L)
                .setFinanceReceiptEvidenceId("11000000-0000-0000-0000-000000000001")
                .setFinanceReceiptEvidenceVersion(1L).setFinanceQualityOperationId(1101L)
                .setFinanceQualityEvidenceId("12000000-0000-0000-0000-000000000001")
                .setFinanceQualityEvidenceVersion(2L)
                .setAcceptedInventoryOperationId(801L).setAcceptedLedgerTransactionId(901L)
                .setAcceptedInventoryAggregateVersion(41L)
                .setAcceptedWarehouseOperationId(1001L).setAcceptedWarehouseReceiptVersion(11L)
                .setAcceptedWarehouseReceiptLineVersion(21L)
                .setAcceptedWarehouseScheduleFulfillmentVersion(31L)
                .setAcceptedFinanceInventoryOperationId(1201L)
                .setAcceptedFinanceInventoryEvidenceId("13000000-0000-0000-0000-000000000001")
                .setAcceptedFinanceInventoryEvidenceVersion(41L)
                .setRejectedInventoryOperationId(802L).setRejectedLedgerTransactionId(902L)
                .setRejectedInventoryAggregateVersion(42L)
                .setRejectedWarehouseOperationId(1002L).setRejectedWarehouseReceiptVersion(12L)
                .setRejectedWarehouseReceiptLineVersion(22L)
                .setRejectedWarehouseScheduleFulfillmentVersion(32L)
                .setRejectedFinanceInventoryOperationId(1202L)
                .setRejectedFinanceInventoryEvidenceId("13000000-0000-0000-0000-000000000002")
                .setRejectedFinanceInventoryEvidenceVersion(42L)
                .setQuarantinedInventoryOperationId(803L).setQuarantinedLedgerTransactionId(903L)
                .setQuarantinedInventoryAggregateVersion(43L)
                .setQuarantinedWarehouseOperationId(1003L).setQuarantinedWarehouseReceiptVersion(13L)
                .setQuarantinedWarehouseReceiptLineVersion(23L)
                .setQuarantinedWarehouseScheduleFulfillmentVersion(33L)
                .setQuarantinedFinanceInventoryOperationId(1203L)
                .setQuarantinedFinanceInventoryEvidenceId("13000000-0000-0000-0000-000000000003")
                .setQuarantinedFinanceInventoryEvidenceVersion(43L)
                .setOccurredAt(occurredAt)));
        when(mapper.selectDefects(23L, "inspection-1")).thenReturn(List.of(new Defect()
                .setDefectId("defect-1").setResultSplitId("result-1").setDefectCode("SEAM_OPEN")
                .setDefectCategory("WORKMANSHIP").setSeverity("MAJOR").setAffectedQuantity(BigDecimal.ONE)
                .setEvidenceSha256("c".repeat(64)).setEvidenceRef("evidence/defect-1")));

        ProcurementReceiptInspectionView view = service.get("inspection-1");

        assertThat(view.getReceiptId()).isEqualTo("receipt-1");
        assertThat(view.getSupplierId()).isEqualTo("supplier-1");
        assertThat(view.getBusinessNo()).isEqualTo("GRN-1");
        assertThat(view.getLines()).singleElement().satisfies(line -> {
            assertThat(line.getReceiptLineId()).isEqualTo("receipt-line-1");
            assertThat(line.getItemId()).isEqualTo("item-1");
            assertThat(line.getScheduleId()).isEqualTo("schedule-1");
            assertThat(line.getSplits()).singleElement().satisfies(split -> {
                assertThat(split.getWarehouseId()).isEqualTo("warehouse-1");
                assertThat(split.getLocationId()).isEqualTo("location-1");
                assertThat(split.getLotId()).isEqualTo("lot-1");
            });
        });
        assertThat(view.getResultBatches()).singleElement().satisfies(batch ->
                assertThat(batch.getSplits()).singleElement().satisfies(result -> {
                    assertThat(result.getRejectedDispositionCode()).isEqualTo("RETURN_TO_SUPPLIER");
                    assertThat(result.getDecisionEvidenceSha256()).isEqualTo("b".repeat(64));
                    assertThat(result.getQualityDecisionId()).isEqualTo("decision-1");
                    assertThat(result.getFinanceReceiptEvidenceId())
                            .isEqualTo("11000000-0000-0000-0000-000000000001");
                    assertThat(result.getFinanceQualityEvidenceId())
                            .isEqualTo("12000000-0000-0000-0000-000000000001");
                    assertThat(result.getAcceptedLedgerTransactionId()).isEqualTo(901L);
                    assertThat(result.getAcceptedInventoryAggregateVersion()).isEqualTo(41L);
                    assertThat(result.getAcceptedFinanceInventoryEvidenceId())
                            .isEqualTo("13000000-0000-0000-0000-000000000001");
                    assertThat(result.getRejectedLedgerTransactionId()).isEqualTo(902L);
                    assertThat(result.getQuarantinedLedgerTransactionId()).isEqualTo(903L);
                    assertThat(result.getAcceptedWarehouseOperationId()).isEqualTo(1001L);
                    assertThat(result.getAcceptedWarehouseReceiptLineVersion()).isEqualTo(21L);
                    assertThat(result.getRejectedWarehouseScheduleFulfillmentVersion()).isEqualTo(32L);
                    assertThat(result.getQuarantinedWarehouseReceiptVersion()).isEqualTo(13L);
                    assertThat(result.getDefects()).singleElement().satisfies(defect ->
                            assertThat(defect.getDefectCode()).isEqualTo("SEAM_OPEN"));
                }));
    }

    @Test
    void pagesTenantScopedInspectionHeadersWithCanonicalFilters() {
        ProcurementReceiptInspectionPageReqVO request = new ProcurementReceiptInspectionPageReqVO();
        request.setPageNo(2);
        request.setPageSize(20);
        request.setStatus(" completed ");
        request.setSupplierId("supplier-1");
        when(mapper.countInspections(23L, "COMPLETED", null, null, "supplier-1", null))
                .thenReturn(1L);
        when(mapper.selectInspections(23L, "COMPLETED", null, null, "supplier-1", null, 20L, 20))
                .thenReturn(List.of(new Inspection().setInspectionId("inspection-1")
                        .setInspectionCode("PRI-1").setSupplierId("supplier-1").setStatus("COMPLETED")
                        .setUpdatedAt(LocalDateTime.of(2026, 8, 2, 1, 0))));

        var page = service.getPage(request);

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getList()).singleElement().satisfies(item -> {
            assertThat(item.getInspectionId()).isEqualTo("inspection-1");
            assertThat(item.getSupplierId()).isEqualTo("supplier-1");
            assertThat(item.getUpdatedAt()).isEqualTo(java.time.Instant.parse("2026-08-02T01:00:00Z"));
        });
    }
}
