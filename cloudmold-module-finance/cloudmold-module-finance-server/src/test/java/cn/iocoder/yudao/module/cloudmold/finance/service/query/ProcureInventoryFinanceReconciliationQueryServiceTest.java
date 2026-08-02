package cn.iocoder.yudao.module.cloudmold.finance.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.ProcureInventoryFinanceReconciliationAdminVOs.*;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureInventoryFinanceReconciliationRecords.ReconciliationDifference;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureInventoryFinanceReconciliationRecords.ReconciliationLine;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureInventoryFinanceReconciliationRecords.ReconciliationRun;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureInventoryFinanceReconciliationRecords.ReconciliationWatermark;
import cn.iocoder.yudao.module.cloudmold.finance.dal.mysql.ProcureInventoryFinanceReconciliationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProcureInventoryFinanceReconciliationQueryServiceTest {
    private final ProcureInventoryFinanceReconciliationMapper mapper = mock(ProcureInventoryFinanceReconciliationMapper.class);
    private final ProcureInventoryFinanceReconciliationQueryService service =
            new ProcureInventoryFinanceReconciliationQueryService(mapper);

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void runDetailIncludesImmutableWatermarks() {
        TenantContextHolder.setTenantId(162L);
        ReconciliationRun run = new ReconciliationRun().setRunId("run-1").setRunCode("PIFR-001")
                .setLegalEntityId("entity-1").setCurrencyCode("CNY").setReconciliationPolicyVersion("PIFR_V1")
                .setStatus("COMPLETED").setLineCount(3).setMatchedCount(1).setDifferentCount(1)
                .setMissingCount(1).setUncomparableCount(0).setRequestedByPrincipalId("actor-1")
                .setStartedAt(LocalDateTime.now()).setCompletedAt(LocalDateTime.now());
        ReconciliationWatermark watermark = new ReconciliationWatermark().setDomainCode("FINANCE_AP_JOURNAL")
                .setSourceTable("ap_open_item_journal_entry").setMaxAggregateVersion(5L).setRecordCount(2);
        when(mapper.selectRun(162L, "run-1")).thenReturn(run);
        when(mapper.selectRunWatermarks(162L, "run-1")).thenReturn(List.of(watermark));

        RunDetail detail = service.run("run-1");

        assertThat(detail.getRunCode()).isEqualTo("PIFR-001");
        assertThat(detail.getWatermarks()).hasSize(1);
        assertThat(detail.getWatermarks().get(0).getDomainCode()).isEqualTo("FINANCE_AP_JOURNAL");
    }

    @Test
    void lineDetailIncludesDifferences() {
        TenantContextHolder.setTenantId(162L);
        ReconciliationLine line = new ReconciliationLine().setLineId("line-1").setRunId("run-1")
                .setLineType("QUALIFIED_RECEIPT").setLineKey("po-item-1|receipt-line-1|movement-1")
                .setLegalEntityId("entity-1").setCurrencyCode("CNY").setPurchaseOrderId("po-1")
                .setPurchaseOrderItemId("po-item-1").setReceiptLineId("receipt-line-1")
                .setInventoryMovementId("movement-1").setJournalEntryId("journal-1")
                .setResponsibilityDomain("FINANCE").setMatchStatus("DIFFERENT")
                .setPrimaryDifferenceCode("FINANCE_AMOUNT_MISMATCH")
                .setQuantityDifference(new java.math.BigDecimal("0.000000"))
                .setAmountDifferenceMinor(100L).setDifferenceCount(1);
        ReconciliationDifference difference = new ReconciliationDifference().setDifferenceId("diff-1")
                .setDifferenceCode("FINANCE_AMOUNT_MISMATCH").setSourceDomain("FINANCE")
                .setExpectedValue("9000").setActualValue("9100").setBlocking(Boolean.TRUE);
        when(mapper.selectLine(162L, "run-1", "line-1")).thenReturn(line);
        when(mapper.selectLineDifferences(162L, "run-1", "line-1")).thenReturn(List.of(difference));

        LineDetail detail = service.line("run-1", "line-1");

        assertThat(detail.getLineKey()).contains("movement-1");
        assertThat(detail.getResponsibilityDomain()).isEqualTo("FINANCE");
        assertThat(detail.getPrimaryDifferenceCode()).isEqualTo("FINANCE_AMOUNT_MISMATCH");
        assertThat(detail.getQuantityDifference()).isEqualTo("0.000000");
        assertThat(detail.getAmountDifferenceMinor()).isEqualTo("100");
        assertThat(detail.getDifferences()).hasSize(1);
        assertThat(detail.getDifferences().get(0).getDifferenceCode()).isEqualTo("FINANCE_AMOUNT_MISMATCH");
    }
}
