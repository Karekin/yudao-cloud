package cn.iocoder.yudao.module.cloudmold.finance.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.ProcureToPayAdminVOs.*;
import cn.iocoder.yudao.module.cloudmold.finance.dal.mysql.ProcureToPayMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProcureToPayQueryService {
    private final ProcureToPayMapper mapper;

    public PageResult<SupplierInvoicePageItem> invoices(PageRequest request) {
        long total = mapper.countInvoicePage(tenant(), norm(request.getStatus()), norm(request.getKeyword()));
        return total == 0 ? PageResult.empty() : new PageResult<>(mapper.selectInvoicePage(tenant(),
                norm(request.getStatus()), norm(request.getKeyword()), offset(request), size(request)), total);
    }

    public SupplierInvoiceDetail invoice(String invoiceId) {
        SupplierInvoiceDetail detail = mapper.selectInvoiceDetail(tenant(), invoiceId);
        if (detail == null) return null;
        List<SupplierInvoiceMatchLine> lines = mapper.selectInvoiceLineViews(tenant(), invoiceId);
        lines.forEach(line -> line.setLineage(new InvoiceLineage()
                .setInvoiceLineId(line.getLineageInvoiceLineId())
                .setPurchaseOrderId(line.getLineagePurchaseOrderId())
                .setPurchaseOrderItemId(line.getLineagePurchaseOrderItemId())
                .setDeliveryScheduleId(line.getLineageDeliveryScheduleId())
                .setReceiptLineId(line.getLineageReceiptLineId())
                .setQualityDispositionId(line.getLineageQualityDispositionId())
                .setInventoryMovementId(line.getLineageInventoryMovementId())));
        detail.setInvoiceLines(lines);
        detail.setApInstallments(mapper.selectInvoiceInstallments(tenant(), invoiceId));
        detail.setPaymentInstructions(mapper.selectInvoicePayments(tenant(), invoiceId));
        detail.setJournalEntryIds(mapper.selectInvoiceJournalIds(tenant(), invoiceId));
        return detail;
    }

    public PageResult<MatchExceptionPageItem> matchExceptions(PageRequest request) {
        long total = mapper.countMatchExceptionPage(tenant(), norm(request.getStatus()), norm(request.getKeyword()));
        return total == 0 ? PageResult.empty() : new PageResult<>(mapper.selectMatchExceptionPage(tenant(),
                norm(request.getStatus()), norm(request.getKeyword()), offset(request), size(request)), total);
    }

    public PageResult<ApInstallmentPageItem> apInstallments(PageRequest request) {
        long total = mapper.countApInstallmentPage(tenant(), norm(request.getStatus()), norm(request.getKeyword()));
        return total == 0 ? PageResult.empty() : new PageResult<>(mapper.selectApInstallmentPage(tenant(),
                norm(request.getStatus()), norm(request.getKeyword()), offset(request), size(request)), total);
    }

    public PageResult<SupplierPaymentPageItem> payments(PageRequest request) {
        long total = mapper.countPaymentPage(tenant(), norm(request.getStatus()), norm(request.getKeyword()));
        return total == 0 ? PageResult.empty() : new PageResult<>(mapper.selectPaymentPage(tenant(),
                norm(request.getStatus()), norm(request.getKeyword()), offset(request), size(request)), total);
    }

    public PageResult<JournalPageItem> journals(PageRequest request) {
        long total = mapper.countJournalPage(tenant(), norm(request.getStatus()), norm(request.getKeyword()));
        return total == 0 ? PageResult.empty() : new PageResult<>(mapper.selectJournalPage(tenant(),
                norm(request.getStatus()), norm(request.getKeyword()), offset(request), size(request)), total);
    }

    public JournalDetail journal(String journalId) {
        JournalDetail detail = mapper.selectJournalDetail(tenant(), journalId);
        if (detail == null) return null;
        List<JournalLine> lines = mapper.selectJournalLineViews(tenant(), journalId);
        lines.forEach(line -> line.setDimensions(mapper.selectJournalLineDimensionViews(tenant(), line.getJournalLineId())));
        detail.setLines(lines);
        return detail;
    }

    public PageResult<MatchPolicyItem> matchPolicies(PageRequest request) {
        long total = mapper.countMatchPolicyPage(tenant(), norm(request.getStatus()));
        return total == 0 ? PageResult.empty() : new PageResult<>(mapper.selectMatchPolicyPage(tenant(),
                norm(request.getStatus()), offset(request), size(request)), total);
    }

    private static long tenant() { return TenantContextHolder.getRequiredTenantId(); }
    private static int size(PageRequest request) {
        if (request.getPageSize() == null || request.getPageSize() < 1 || request.getPageSize() > 100)
            throw new IllegalArgumentException("pageSize must be 1..100");
        return request.getPageSize();
    }
    private static long offset(PageRequest request) {
        if (request.getPageNo() == null || request.getPageNo() < 1)
            throw new IllegalArgumentException("pageNo must be positive");
        return (long) (request.getPageNo() - 1) * size(request);
    }
    private static String norm(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
