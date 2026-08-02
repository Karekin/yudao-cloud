package cn.iocoder.yudao.module.cloudmold.finance.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.InventoryControlFinanceAdminVOs.FinancialImpactApLineage;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.InventoryControlFinanceAdminVOs.FinancialImpactSourceLine;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.InventoryControlFinanceAdminVOs.PostingAllocationItem;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.InventoryControlFinanceAdminVOs.PostingDetail;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.InventoryControlFinanceAdminVOs.PostingPageItem;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.InventoryControlFinanceAdminVOs.PostingPageRequest;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.InventoryControlFinanceRecords.InventoryControlPosting;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.InventoryControlFinanceRecords.InventoryControlPostingAllocation;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.SupplierReturnReversalRecords.SupplierDebitAdjustment;
import cn.iocoder.yudao.module.cloudmold.finance.dal.mysql.InventoryControlFinanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryControlFinanceQueryService {

    private final InventoryControlFinanceMapper mapper;
    private final ProcureToPayQueryService journalQueryService;

    public PageResult<PostingPageItem> page(PostingPageRequest request) {
        String sourceType = norm(request.getSourceType());
        String status = norm(request.getStatus());
        String keyword = norm(request.getKeyword());
        long total = 0L;
        var rows = new ArrayList<PostingPageItem>();
        long required = offset(request) + size(request);
        int fetchSize = Math.toIntExact(required);
        if (sourceType == null || "STOCK_COUNT_ADJUSTMENT".equals(sourceType) || "INVENTORY_SCRAP".equals(sourceType)) {
            var direct = mapper.selectPostingPage(tenant(), sourceType, status, keyword, 0, fetchSize);
            rows.addAll(direct);
            total += mapper.countInventoryPostingPage(tenant(), sourceType, status, keyword);
        }
        if (sourceType == null || "SUPPLIER_RETURN".equals(sourceType)) {
            var returns = mapper.selectSupplierReturnPage(tenant(), status, keyword, 0, fetchSize);
            rows.addAll(returns);
            total += mapper.countSupplierReturnPage(tenant(), status, keyword);
        }
        if (rows.isEmpty()) {
            return PageResult.empty();
        }
        rows.sort(Comparator.comparing(PostingPageItem::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(PostingPageItem::getInventoryControlPostingId, Comparator.reverseOrder()));
        int from = (int) Math.min(offset(request), rows.size());
        int to = (int) Math.min(offset(request) + size(request), rows.size());
        return new PageResult<>(rows.subList(from, to), total == 0 ? rows.size() : total);
    }

    public PostingDetail detail(String postingId) {
        InventoryControlPosting posting = mapper.selectPosting(tenant(), postingId);
        if (posting != null) {
            PostingDetail detail = new PostingDetail();
            detail.setInventoryControlPostingId(posting.getInventoryControlPostingId());
            detail.setSourceType(posting.getSourceType());
            detail.setSourceDocumentId(posting.getSourceDocumentId());
            detail.setSourceLineId(posting.getSourceLineId());
            detail.setSourceReferenceId(posting.getSourceReferenceId());
            detail.setImpactType("STOCK_COUNT_ADJUSTMENT".equals(posting.getSourceType()) ? "SHRINKAGE" : "SCRAP");
            detail.setQuantity(posting.getQuantity().toPlainString());
            detail.setUnitOfMeasure(posting.getUnitOfMeasure());
            detail.setCurrencyCode(posting.getCurrencyCode());
            detail.setValuationImpactAmountMinor(String.valueOf(posting.getTotalAmountMinor()));
            detail.setApImpactAmountMinor("0");
            detail.setTotalAmountMinor(String.valueOf(posting.getTotalAmountMinor()));
            detail.setJournalEntryId(posting.getJournalEntryId());
            detail.setJournalCode(posting.getJournalCode());
            detail.setAccountingPeriodId(posting.getAccountingPeriodId());
            detail.setAccountingDate(posting.getAccountingDate());
            detail.setPostingStatus(posting.getStatus());
            detail.setJournalStatus("POSTED");
            detail.setBalanceStatus("BALANCED");
            detail.setAggregateVersion(posting.getVersion());
            detail.setUpdatedAt(posting.getUpdatedAt());
            detail.setLegalEntityId(posting.getLegalEntityId());
            detail.setLedgerId(posting.getLedgerId());
            detail.setPostingRuleId(posting.getPostingRuleId());
            detail.setPostingRuleVersion(posting.getPostingRuleVersion());
            detail.setPostingEvidenceSha256(posting.getPostingEvidenceSha256());
            detail.setReversalJournalEntryId(posting.getReversalJournalEntryId());
            detail.setCreatedByPrincipalId(posting.getCreatedByPrincipalId());
            detail.setReversedByPrincipalId(posting.getReversedByPrincipalId());
            detail.setCreatedAt(posting.getCreatedAt());
            detail.setAllocations(mapper.selectPostingAllocations(tenant(), postingId).stream().map(this::allocation).collect(Collectors.toList()));
            detail.setSourceLines(java.util.List.of());
            detail.setApLineage(java.util.List.of());
            detail.setJournal(journalQueryService.journal(posting.getJournalEntryId()));
            return detail;
        }
        SupplierDebitAdjustment adjustment = mapper.selectSupplierDebitAdjustment(tenant(), postingId);
        if (adjustment == null) return null;
        PostingDetail detail = new PostingDetail();
        detail.setInventoryControlPostingId(adjustment.getSupplierDebitAdjustmentId());
        detail.setSourceType("SUPPLIER_RETURN");
        detail.setSourceDocumentId(adjustment.getSupplierReturnId());
        detail.setSourceReferenceId(adjustment.getSupplierDebitAdjustmentId());
        detail.setImpactType("SUPPLIER_RETURN_REVERSAL");
        detail.setCurrencyCode(adjustment.getCurrencyCode());
        detail.setValuationImpactAmountMinor(String.valueOf(adjustment.getValuationReversalAmountMinor()));
        detail.setApImpactAmountMinor(String.valueOf(adjustment.getApReversalAmountMinor()));
        detail.setTotalAmountMinor(String.valueOf(adjustment.getApReversalAmountMinor()));
        detail.setJournalEntryId(adjustment.getJournalEntryId());
        detail.setJournal(journalQueryService.journal(adjustment.getJournalEntryId()));
        detail.setJournalCode(detail.getJournal() == null ? null : detail.getJournal().getJournalCode());
        detail.setAccountingPeriodId(adjustment.getAccountingPeriodId());
        detail.setAccountingDate(adjustment.getAccountingDate());
        detail.setPostingStatus(adjustment.getStatus());
        detail.setJournalStatus(detail.getJournal() == null ? null : detail.getJournal().getStatus());
        detail.setBalanceStatus("BALANCED");
        detail.setAggregateVersion(adjustment.getVersion());
        detail.setUpdatedAt(adjustment.getUpdatedAt());
        detail.setLegalEntityId(adjustment.getLegalEntityId());
        detail.setLedgerId(adjustment.getLedgerId());
        detail.setPostingRuleId(adjustment.getPostingRuleId());
        detail.setPostingRuleVersion(adjustment.getPostingRuleVersion());
        detail.setPostingEvidenceSha256(adjustment.getReversalEvidenceSha256());
        detail.setCreatedByPrincipalId(adjustment.getPostedByPrincipalId());
        detail.setCreatedAt(adjustment.getCreatedAt());
        detail.setAllocations(java.util.List.of());
        detail.setSourceLines(mapper.selectSupplierReturnSourceLines(tenant(), postingId));
        detail.setApLineage(mapper.selectSupplierReturnApLineage(tenant(), postingId));
        return detail;
    }

    private PostingAllocationItem allocation(InventoryControlPostingAllocation value) {
        PostingAllocationItem item = new PostingAllocationItem();
        item.setSequenceNo(value.getSequenceNo());
        item.setValuationLayerSourceType(value.getValuationLayerSourceType());
        item.setValuationLayerId(value.getValuationLayerId());
        item.setAllocatedQuantity(value.getAllocatedQuantity().toPlainString());
        item.setAllocatedCostAmountMinor(String.valueOf(value.getAllocatedCostAmountMinor()));
        return item;
    }

    private static long tenant() {
        return TenantContextHolder.getRequiredTenantId();
    }

    private static int size(PostingPageRequest request) {
        if (request.getPageSize() == null || request.getPageSize() < 1 || request.getPageSize() > 100) {
            throw new IllegalArgumentException("pageSize must be 1..100");
        }
        return request.getPageSize();
    }

    private static long offset(PostingPageRequest request) {
        if (request.getPageNo() == null || request.getPageNo() < 1) {
            throw new IllegalArgumentException("pageNo must be positive");
        }
        return (long) (request.getPageNo() - 1) * size(request);
    }

    private static String norm(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
