package cn.iocoder.yudao.module.cloudmold.finance.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.InventoryControlFinanceAdminVOs.PostingDetail;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.InventoryControlFinanceAdminVOs.PostingPageItem;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.InventoryControlFinanceAdminVOs.PostingPageRequest;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.InventoryControlFinanceRecords.InventoryControlPosting;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.SupplierReturnReversalRecords.SupplierDebitAdjustment;
import cn.iocoder.yudao.module.cloudmold.finance.dal.mysql.InventoryControlFinanceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class InventoryControlFinanceQueryServiceTest {
    private final InventoryControlFinanceMapper mapper = mock(InventoryControlFinanceMapper.class);
    private final ProcureToPayQueryService journalQueryService = mock(ProcureToPayQueryService.class);
    private final InventoryControlFinanceQueryService service =
            new InventoryControlFinanceQueryService(mapper, journalQueryService);

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void pageUnifiesSupplierReturnAndInventoryControlImpacts() {
        TenantContextHolder.setTenantId(162L);
        PostingPageItem stockCount = new PostingPageItem();
        stockCount.setInventoryControlPostingId("posting-1");
        stockCount.setSourceType("STOCK_COUNT_ADJUSTMENT");
        stockCount.setUpdatedAt(LocalDateTime.of(2026, 8, 2, 10, 0));
        PostingPageItem supplierReturn = new PostingPageItem();
        supplierReturn.setInventoryControlPostingId("adj-1");
        supplierReturn.setSourceType("SUPPLIER_RETURN");
        supplierReturn.setUpdatedAt(LocalDateTime.of(2026, 8, 2, 11, 0));
        when(mapper.selectPostingPage(162L, null, null, null, 0, 20)).thenReturn(List.of(stockCount));
        when(mapper.countInventoryPostingPage(162L, null, null, null)).thenReturn(1L);
        when(mapper.selectSupplierReturnPage(162L, null, null, 0, 20)).thenReturn(List.of(supplierReturn));
        when(mapper.countSupplierReturnPage(162L, null, null)).thenReturn(1L);

        PostingPageRequest request = new PostingPageRequest();
        request.setPageNo(1);
        request.setPageSize(20);
        var page = service.page(request);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getList()).extracting(PostingPageItem::getSourceType)
                .containsExactly("SUPPLIER_RETURN", "STOCK_COUNT_ADJUSTMENT");
    }

    @Test
    void supplierReturnDetailIncludesFinanceOwnedLineage() {
        TenantContextHolder.setTenantId(162L);
        when(mapper.selectPosting(162L, "adj-1")).thenReturn(null);
        when(mapper.selectSupplierDebitAdjustment(162L, "adj-1")).thenReturn(new SupplierDebitAdjustment()
                .setSupplierDebitAdjustmentId("adj-1").setSupplierReturnId("return-1").setLegalEntityId("entity-1")
                .setLedgerId("ledger-1").setPostingRuleId("rule-1").setPostingRuleVersion(1L)
                .setJournalEntryId("journal-1").setReversalEvidenceSha256("a".repeat(64))
                .setCurrencyCode("CNY").setApReversalAmountMinor(226L).setValuationReversalAmountMinor(200L)
                .setStatus("POSTED").setVersion(1L).setAccountingPeriodId("period-1")
                .setAccountingDate(LocalDate.of(2026, 8, 2)).setPostedByPrincipalId("actor-1")
                .setCreatedAt(LocalDateTime.of(2026, 8, 2, 12, 0)).setUpdatedAt(LocalDateTime.of(2026, 8, 2, 12, 1)));
        when(mapper.selectSupplierReturnSourceLines(162L, "adj-1")).thenReturn(List.of(
                new cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.InventoryControlFinanceAdminVOs.FinancialImpactSourceLine()));
        when(mapper.selectSupplierReturnApLineage(162L, "adj-1")).thenReturn(List.of(
                new cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.InventoryControlFinanceAdminVOs.FinancialImpactApLineage()));

        PostingDetail detail = service.detail("adj-1");

        assertThat(detail.getSourceType()).isEqualTo("SUPPLIER_RETURN");
        assertThat(detail.getValuationImpactAmountMinor()).isEqualTo("200");
        assertThat(detail.getApImpactAmountMinor()).isEqualTo("226");
        assertThat(detail.getSourceLines()).hasSize(1);
        assertThat(detail.getApLineage()).hasSize(1);
    }
}
