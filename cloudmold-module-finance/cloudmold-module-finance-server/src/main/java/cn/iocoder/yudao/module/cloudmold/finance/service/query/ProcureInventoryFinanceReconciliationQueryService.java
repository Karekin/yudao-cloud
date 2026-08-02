package cn.iocoder.yudao.module.cloudmold.finance.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.ProcureInventoryFinanceReconciliationAdminVOs.*;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureInventoryFinanceReconciliationRecords.ReconciliationDifference;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureInventoryFinanceReconciliationRecords.ReconciliationLine;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureInventoryFinanceReconciliationRecords.ReconciliationRun;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureInventoryFinanceReconciliationRecords.ReconciliationWatermark;
import cn.iocoder.yudao.module.cloudmold.finance.dal.mysql.ProcureInventoryFinanceReconciliationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProcureInventoryFinanceReconciliationQueryService {
    private final ProcureInventoryFinanceReconciliationMapper mapper;

    public PageResult<RunPageItem> runs(RunPageRequest request) {
        long total = mapper.countRunPage(tenant(), norm(request.getStatus()), norm(request.getLegalEntityId()),
                normCurrency(request.getCurrencyCode()), norm(request.getKeyword()));
        return total == 0 ? PageResult.empty() : new PageResult<>(mapper.selectRunPage(tenant(),
                norm(request.getStatus()), norm(request.getLegalEntityId()), normCurrency(request.getCurrencyCode()),
                norm(request.getKeyword()), offset(request.getPageNo(), request.getPageSize()),
                size(request.getPageSize())).stream().map(this::runPageItem).toList(), total);
    }

    public RunDetail run(String runId) {
        ReconciliationRun run = mapper.selectRun(tenant(), runId);
        if (run == null) return null;
        RunDetail detail = new RunDetail();
        detail.setRunId(run.getRunId());
        detail.setRunCode(run.getRunCode());
        detail.setLegalEntityId(run.getLegalEntityId());
        detail.setCurrencyCode(run.getCurrencyCode());
        detail.setReconciliationPolicyVersion(run.getReconciliationPolicyVersion());
        detail.setStatus(run.getStatus());
        detail.setLineCount(run.getLineCount());
        detail.setMatchedCount(run.getMatchedCount());
        detail.setDifferentCount(run.getDifferentCount());
        detail.setMissingCount(run.getMissingCount());
        detail.setUncomparableCount(run.getUncomparableCount());
        detail.setRequestedByPrincipalId(run.getRequestedByPrincipalId());
        detail.setStartedAt(run.getStartedAt());
        detail.setCompletedAt(run.getCompletedAt());
        detail.setWatermarks(mapper.selectRunWatermarks(tenant(), runId).stream().map(this::watermarkItem).toList());
        return detail;
    }

    public PageResult<LinePageItem> lines(LinePageRequest request) {
        long total = mapper.countLinePage(tenant(), request.getRunId(), norm(request.getMatchStatus()),
                norm(request.getLineType()), norm(request.getKeyword()));
        return total == 0 ? PageResult.empty() : new PageResult<>(mapper.selectLinePage(tenant(), request.getRunId(),
                norm(request.getMatchStatus()), norm(request.getLineType()), norm(request.getKeyword()),
                offset(request.getPageNo(), request.getPageSize()), size(request.getPageSize())).stream()
                .map(this::linePageItem).toList(), total);
    }

    public LineDetail line(String runId, String lineId) {
        ReconciliationLine line = mapper.selectLine(tenant(), runId, lineId);
        if (line == null) return null;
        LineDetail detail = new LineDetail();
        copyLine(line, detail);
        detail.setDeliveryScheduleId(line.getDeliveryScheduleId());
        detail.setSupplierReturnId(line.getSupplierReturnId());
        detail.setSupplierInvoiceId(line.getSupplierInvoiceId());
        detail.setApOpenItemId(line.getApOpenItemId());
        detail.setJournalEntryId(line.getJournalEntryId());
        detail.setDifferences(mapper.selectLineDifferences(tenant(), runId, lineId).stream().map(this::differenceItem).toList());
        return detail;
    }

    private RunPageItem runPageItem(ReconciliationRun run) {
        RunPageItem item = new RunPageItem();
        item.setRunId(run.getRunId());
        item.setRunCode(run.getRunCode());
        item.setLegalEntityId(run.getLegalEntityId());
        item.setCurrencyCode(run.getCurrencyCode());
        item.setStatus(run.getStatus());
        item.setLineCount(run.getLineCount());
        item.setMatchedCount(run.getMatchedCount());
        item.setDifferentCount(run.getDifferentCount());
        item.setMissingCount(run.getMissingCount());
        item.setUncomparableCount(run.getUncomparableCount());
        item.setStartedAt(run.getStartedAt());
        item.setCompletedAt(run.getCompletedAt());
        return item;
    }

    private WatermarkItem watermarkItem(ReconciliationWatermark watermark) {
        WatermarkItem item = new WatermarkItem();
        item.setDomainCode(watermark.getDomainCode());
        item.setSourceTable(watermark.getSourceTable());
        item.setMaxAggregateVersion(watermark.getMaxAggregateVersion());
        item.setMaxObservedAt(watermark.getMaxObservedAt());
        item.setRecordCount(watermark.getRecordCount());
        return item;
    }

    private LinePageItem linePageItem(ReconciliationLine line) {
        LinePageItem item = new LinePageItem();
        copyLine(line, item);
        return item;
    }

    private void copyLine(ReconciliationLine line, LinePageItem item) {
        item.setLineId(line.getLineId());
        item.setLineType(line.getLineType());
        item.setLineKey(line.getLineKey());
        item.setLegalEntityId(line.getLegalEntityId());
        item.setCurrencyCode(line.getCurrencyCode());
        item.setPurchaseOrderId(line.getPurchaseOrderId());
        item.setPurchaseOrderItemId(line.getPurchaseOrderItemId());
        item.setReceiptLineId(line.getReceiptLineId());
        item.setInventoryMovementId(line.getInventoryMovementId());
        item.setSupplierReturnLineId(line.getSupplierReturnLineId());
        item.setSupplierInvoiceLineId(line.getSupplierInvoiceLineId());
        item.setResponsibilityDomain(line.getResponsibilityDomain());
        item.setMatchStatus(line.getMatchStatus());
        item.setPrimaryDifferenceCode(line.getPrimaryDifferenceCode());
        item.setDifferenceCount(line.getDifferenceCount());
        item.setProcurementQuantity(text(line.getProcurementQuantity()));
        item.setInventoryQuantity(text(line.getInventoryQuantity()));
        item.setFinanceQuantity(text(line.getFinanceQuantity()));
        item.setQuantityDifference(text(line.getQuantityDifference()));
        item.setProcurementAmountMinor(text(line.getProcurementAmountMinor()));
        item.setInventoryAmountMinor(text(line.getInventoryAmountMinor()));
        item.setFinanceAmountMinor(text(line.getFinanceAmountMinor()));
        item.setAmountDifferenceMinor(text(line.getAmountDifferenceMinor()));
        item.setProcurementVersion(line.getProcurementVersion());
        item.setInventoryVersion(line.getInventoryVersion());
        item.setFinanceVersion(line.getFinanceVersion());
    }

    private DifferenceItem differenceItem(ReconciliationDifference difference) {
        DifferenceItem item = new DifferenceItem();
        item.setDifferenceId(difference.getDifferenceId());
        item.setDifferenceCode(difference.getDifferenceCode());
        item.setSourceDomain(difference.getSourceDomain());
        item.setExpectedValue(difference.getExpectedValue());
        item.setActualValue(difference.getActualValue());
        item.setBlocking(difference.getBlocking());
        return item;
    }

    private static long tenant() {
        return TenantContextHolder.getRequiredTenantId();
    }

    private static int size(Integer value) {
        if (value == null || value < 1 || value > 100) {
            throw new IllegalArgumentException("pageSize must be 1..100");
        }
        return value;
    }

    private static long offset(Integer pageNo, Integer pageSize) {
        if (pageNo == null || pageNo < 1) {
            throw new IllegalArgumentException("pageNo must be positive");
        }
        return (long) (pageNo - 1) * size(pageSize);
    }

    private static String norm(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normCurrency(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
