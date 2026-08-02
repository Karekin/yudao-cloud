package cn.iocoder.yudao.module.cloudmold.finance.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.ProcureInventoryFinanceReconciliationAdminVOs.CreateRunRequest;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureInventoryFinanceReconciliationRecords.*;
import cn.iocoder.yudao.module.cloudmold.finance.dal.mysql.ProcureInventoryFinanceReconciliationMapper;
import cn.iocoder.yudao.module.cloudmold.finance.dal.mysql.ProcureToPayMapper;
import cn.iocoder.yudao.module.cloudmold.finance.service.actor.FinanceActorPrincipalPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProcureInventoryFinanceReconciliationService {
    private static final int OPERATION_SUCCEEDED = 10;
    private static final String AGGREGATE_TYPE = "finance_procure_inventory_reconciliation_run";
    private static final String COMMAND_TYPE = "RUN_PROCURE_INVENTORY_FINANCE_RECONCILIATION";
    private static final String POLICY_VERSION = "PROCURE_INVENTORY_FINANCE_RECON_V1";
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");

    private final ProcureToPayMapper operationMapper;
    private final ProcureInventoryFinanceReconciliationMapper mapper;
    private final FinanceActorPrincipalPort actorPrincipalPort;

    @Transactional(rollbackFor = Exception.class)
    public ProcureInventoryFinanceReconciliationResult createRun(CreateRunRequest request, String actorPrincipalId) {
        validate(request, actorPrincipalId);
        actorPrincipalPort.requireActive(actorPrincipalId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.ofInstant(request.getOccurredAt(), ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(actorPrincipalId + "\n" + JsonUtils.toJsonString(request));
        String attemptToken = UUID.randomUUID().toString();
        operationMapper.insertOrResolveOperation(tenantId, request.getIdempotencyKey(), COMMAND_TYPE, requestHash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve reconciliation operation");
        Operation operation = operationMapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "reconciliation operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with a different reconciliation payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing reconciliation operation is incomplete");
            ProcureInventoryFinanceReconciliationResult replay = JsonUtils.parseObject(
                    operation.getResultJson(), ProcureInventoryFinanceReconciliationResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        List<QualifiedReceiptSource> qualifiedReceipts = mapper.selectQualifiedReceiptSources(
                tenantId, request.getLegalEntityId(), upper(request.getCurrencyCode()));
        List<SupplierReturnSource> supplierReturns = mapper.selectSupplierReturnSources(
                tenantId, request.getLegalEntityId(), upper(request.getCurrencyCode()));
        List<SupplierInvoiceSource> supplierInvoices = mapper.selectSupplierInvoiceSources(
                tenantId, request.getLegalEntityId(), upper(request.getCurrencyCode()));

        String runSeed = request.getLegalEntityId() + "\n" + upper(request.getCurrencyCode()) + "\n" + request.getIdempotencyKey();
        String runId = "pifr:" + DigestUtil.sha256Hex(runSeed).substring(0, 32);
        String runCode = "PIFR-" + DigestUtil.sha256Hex(runSeed).substring(0, 20).toUpperCase(Locale.ROOT);

        List<ReconciliationLine> lines = new ArrayList<>();
        List<ReconciliationDifference> differences = new ArrayList<>();
        qualifiedReceipts.forEach(source -> classifyQualifiedReceipt(runId, tenantId, now, source, lines, differences));
        supplierReturns.forEach(source -> classifySupplierReturn(runId, tenantId, now, source, lines, differences));
        supplierInvoices.forEach(source -> classifySupplierInvoice(runId, tenantId, now, source, lines, differences));
        require(!lines.isEmpty(), "reconciliation run requires non-empty authoritative source data");

        Map<String, Integer> counts = countStatuses(lines);
        ReconciliationRun run = new ReconciliationRun()
                .setRunId(runId)
                .setTenantId(tenantId)
                .setRunCode(runCode)
                .setLegalEntityId(request.getLegalEntityId())
                .setCurrencyCode(upper(request.getCurrencyCode()))
                .setReconciliationPolicyVersion(POLICY_VERSION)
                .setStatus("COMPLETED")
                .setLineCount(lines.size())
                .setMatchedCount(counts.getOrDefault("MATCHED", 0))
                .setDifferentCount(counts.getOrDefault("DIFFERENT", 0))
                .setMissingCount(counts.getOrDefault("MISSING", 0))
                .setUncomparableCount(counts.getOrDefault("UNCOMPARABLE", 0))
                .setRequestedByPrincipalId(actorPrincipalId)
                .setStartedAt(now)
                .setCompletedAt(now)
                .setCreatedAt(now);
        require(mapper.insertRun(run) == 1, "failed to persist reconciliation run");
        List<ReconciliationWatermark> watermarks = buildWatermarks(
                tenantId, runId, now, qualifiedReceipts, supplierReturns, supplierInvoices);
        require(mapper.insertWatermarks(watermarks) == watermarks.size(), "failed to persist reconciliation watermarks");
        require(mapper.insertLines(lines) == lines.size(), "failed to persist reconciliation lines");
        require(differences.isEmpty() || mapper.insertDifferences(differences) == differences.size(),
                "failed to persist reconciliation differences");

        ProcureInventoryFinanceReconciliationResult result = ProcureInventoryFinanceReconciliationResult.builder()
                .operationId(operationId)
                .duplicate(false)
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(runId)
                .runCode(runCode)
                .status(run.getStatus())
                .lineCount(run.getLineCount())
                .matchedCount(run.getMatchedCount())
                .differentCount(run.getDifferentCount())
                .missingCount(run.getMissingCount())
                .uncomparableCount(run.getUncomparableCount())
                .build();
        require(operationMapper.markOperationSucceeded(operationId, tenantId, AGGREGATE_TYPE, runId,
                        JsonUtils.toJsonString(result), now) == 1,
                "reconciliation operation completion conflict");
        return result;
    }

    private void classifyQualifiedReceipt(String runId, Long tenantId, LocalDateTime now,
                                          QualifiedReceiptSource source,
                                          List<ReconciliationLine> lines,
                                          List<ReconciliationDifference> differences) {
        String lineId = UUID.randomUUID().toString();
        String lineKey = source.getPurchaseOrderItemId() + "|" + source.getReceiptLineId() + "|" + source.getInventoryMovementId();
        List<ReconciliationDifference> local = new ArrayList<>();
        BigDecimal quantityDifference = quantityDifference(source.getAcceptedQuantity(), source.getMovementQuantity());
        Long valuationAmountDifference = amountDifference(source.getMovementCostAmountMinor(), source.getValuationAmountMinor());
        Long journalAmountDifference = amountDifference(source.getMovementCostAmountMinor(), source.getJournalAmountMinor());
        if (source.getReceiptLineId() == null || source.getQualityDispositionId() == null) {
            local.add(diff(runId, lineId, tenantId, now, "PROCUREMENT_LINKAGE_MISSING", "PROCUREMENT",
                    "receipt + quality lineage", "missing"));
        }
        if (quantityDifference != null && quantityDifference.signum() != 0) {
            local.add(diff(runId, lineId, tenantId, now, "QUANTITY_MISMATCH", "INVENTORY",
                    text(source.getAcceptedQuantity()), text(source.getMovementQuantity())));
        }
        if (source.getValuationLayerId() == null) {
            local.add(diff(runId, lineId, tenantId, now, "VALUATION_LAYER_MISSING", "INVENTORY",
                    "inventory valuation layer", "missing"));
        }
        if (valuationAmountDifference != null && valuationAmountDifference != 0L) {
            local.add(diff(runId, lineId, tenantId, now, "VALUATION_AMOUNT_MISMATCH", "INVENTORY",
                    text(source.getMovementCostAmountMinor()), text(source.getValuationAmountMinor())));
        }
        if (source.getJournalEntryId() == null) {
            local.add(diff(runId, lineId, tenantId, now, "FINANCE_JOURNAL_MISSING", "FINANCE",
                    "qualified receipt journal", "missing"));
        }
        if (journalAmountDifference != null && journalAmountDifference != 0L) {
            local.add(diff(runId, lineId, tenantId, now, "FINANCE_AMOUNT_MISMATCH", "FINANCE",
                    text(source.getMovementCostAmountMinor()), text(source.getJournalAmountMinor())));
        }
        LineOutcome outcome;
        if (source.getReceiptLineId() == null || source.getQualityDispositionId() == null) {
            outcome = new LineOutcome("UNCOMPARABLE", "PROCUREMENT_LINKAGE_MISSING", "PROCUREMENT", null, null);
        } else if (quantityDifference != null && quantityDifference.signum() != 0) {
            outcome = new LineOutcome("DIFFERENT", "QUANTITY_MISMATCH", "INVENTORY", quantityDifference, null);
        } else if (source.getValuationLayerId() == null) {
            outcome = new LineOutcome("MISSING", "VALUATION_LAYER_MISSING", "INVENTORY", zeroQuantity(quantityDifference), null);
        } else if (valuationAmountDifference != null && valuationAmountDifference != 0L) {
            outcome = new LineOutcome("DIFFERENT", "VALUATION_AMOUNT_MISMATCH", "INVENTORY", zeroQuantity(quantityDifference), valuationAmountDifference);
        } else if (source.getJournalEntryId() == null) {
            outcome = new LineOutcome("MISSING", "FINANCE_JOURNAL_MISSING", "FINANCE", zeroQuantity(quantityDifference), null);
        } else if (journalAmountDifference != null && journalAmountDifference != 0L) {
            outcome = new LineOutcome("DIFFERENT", "FINANCE_AMOUNT_MISMATCH", "FINANCE", zeroQuantity(quantityDifference), journalAmountDifference);
        } else {
            outcome = matchedOutcome(quantityDifference, journalAmountDifference);
        }
        lines.add(new ReconciliationLine()
                .setLineId(lineId)
                .setTenantId(tenantId)
                .setRunId(runId)
                .setLineType("QUALIFIED_RECEIPT")
                .setLineKey(lineKey)
                .setLegalEntityId(source.getLegalEntityId())
                .setCurrencyCode(source.getCurrencyCode())
                .setPurchaseOrderId(source.getPurchaseOrderId())
                .setPurchaseOrderItemId(source.getPurchaseOrderItemId())
                .setDeliveryScheduleId(source.getDeliveryScheduleId())
                .setReceiptLineId(source.getReceiptLineId())
                .setInventoryMovementId(source.getInventoryMovementId())
                .setProcurementVersion(max(source.getPurchaseOrderLineVersion(), source.getReceiptLineVersion()))
                .setInventoryVersion(max(source.getInventoryMovementVersion(), source.getValuationLayerVersion()))
                .setFinanceVersion(source.getJournalVersion())
                .setProcurementQuantity(source.getAcceptedQuantity())
                .setInventoryQuantity(source.getMovementQuantity())
                .setProcurementAmountMinor(null)
                .setInventoryAmountMinor(source.getMovementCostAmountMinor())
                .setFinanceAmountMinor(source.getJournalAmountMinor())
                .setQuantityDifference(outcome.quantityDifference())
                .setAmountDifferenceMinor(outcome.amountDifferenceMinor())
                .setPrimaryDifferenceCode(outcome.primaryDifferenceCode())
                .setResponsibilityDomain(outcome.responsibilityDomain())
                .setMatchStatus(outcome.matchStatus())
                .setDifferenceCount(local.size())
                .setCreatedAt(now));
        differences.addAll(local);
    }

    private void classifySupplierReturn(String runId, Long tenantId, LocalDateTime now,
                                        SupplierReturnSource source,
                                        List<ReconciliationLine> lines,
                                        List<ReconciliationDifference> differences) {
        String lineId = UUID.randomUUID().toString();
        String lineKey = source.getSupplierReturnLineId() + "|" + Objects.toString(source.getExecutionLineId(), "PENDING");
        List<ReconciliationDifference> local = new ArrayList<>();
        BigDecimal quantityDifference = quantityDifference(source.getReturnQuantity(), source.getDispatchedQuantity());
        if (source.getExecutionLineId() == null || source.getInventoryLedgerTransactionId() == null
                || source.getDispatchedQuantity() == null) {
            local.add(diff(runId, lineId, tenantId, now, "RETURN_DISPATCH_INCOMPLETE", "INVENTORY",
                    "dispatched inventory execution", "missing"));
        } else if (quantityDifference != null && quantityDifference.signum() != 0) {
            local.add(diff(runId, lineId, tenantId, now, "PARTIAL_RETURN_DISPATCH", "INVENTORY",
                    text(source.getReturnQuantity()), text(source.getDispatchedQuantity())));
        } else {
            local.add(diff(runId, lineId, tenantId, now, "RETURN_FINANCE_EFFECT_MISSING", "FINANCE",
                    "finance AP/journal for supplier return", "missing"));
        }
        LineOutcome outcome;
        if (source.getExecutionLineId() == null || source.getInventoryLedgerTransactionId() == null
                || source.getDispatchedQuantity() == null) {
            outcome = new LineOutcome("UNCOMPARABLE", "RETURN_DISPATCH_INCOMPLETE", "INVENTORY", null, null);
        } else if (quantityDifference != null && quantityDifference.signum() != 0) {
            outcome = new LineOutcome("UNCOMPARABLE", "PARTIAL_RETURN_DISPATCH", "INVENTORY", quantityDifference, null);
        } else {
            outcome = new LineOutcome("MISSING", "RETURN_FINANCE_EFFECT_MISSING", "FINANCE", zeroQuantity(quantityDifference), null);
        }
        lines.add(new ReconciliationLine()
                .setLineId(lineId)
                .setTenantId(tenantId)
                .setRunId(runId)
                .setLineType("SUPPLIER_RETURN")
                .setLineKey(lineKey)
                .setLegalEntityId(source.getLegalEntityId())
                .setCurrencyCode(source.getCurrencyCode())
                .setPurchaseOrderId(source.getPurchaseOrderId())
                .setPurchaseOrderItemId(source.getPurchaseOrderItemId())
                .setDeliveryScheduleId(source.getDeliveryScheduleId())
                .setReceiptLineId(source.getReceiptLineId())
                .setSupplierReturnId(source.getSupplierReturnId())
                .setSupplierReturnLineId(source.getSupplierReturnLineId())
                .setProcurementVersion(source.getReturnLineVersion())
                .setInventoryVersion(source.getInventoryAggregateVersion())
                .setProcurementQuantity(source.getReturnQuantity())
                .setInventoryQuantity(source.getDispatchedQuantity())
                .setProcurementAmountMinor(source.getMovementCostAmountMinor())
                .setInventoryAmountMinor(source.getMovementCostAmountMinor())
                .setQuantityDifference(outcome.quantityDifference())
                .setAmountDifferenceMinor(outcome.amountDifferenceMinor())
                .setPrimaryDifferenceCode(outcome.primaryDifferenceCode())
                .setResponsibilityDomain(outcome.responsibilityDomain())
                .setMatchStatus(outcome.matchStatus())
                .setDifferenceCount(local.size())
                .setCreatedAt(now));
        differences.addAll(local);
    }

    private void classifySupplierInvoice(String runId, Long tenantId, LocalDateTime now,
                                         SupplierInvoiceSource source,
                                         List<ReconciliationLine> lines,
                                         List<ReconciliationDifference> differences) {
        String lineId = UUID.randomUUID().toString();
        String lineKey = source.getSupplierInvoiceLineId();
        List<ReconciliationDifference> local = new ArrayList<>();
        BigDecimal quantityDifference = quantityDifference(source.getInvoiceQuantity(), source.getAllocatedQuantity());
        Long matchedInventoryAmountDifference = amountDifference(source.getInvoiceGrossAmountMinor(), source.getMatchedInventoryAmountMinor());
        Long apAmountDifference = amountDifference(source.getInvoiceGrossAmountMinor(), source.getApOpenAmountMinor());
        Long journalAmountDifference = amountDifference(source.getInvoiceGrossAmountMinor(), source.getJournalAmountMinor());
        if (!"MATCHED".equals(source.getMatchResultStatus()) || safeInt(source.getOpenExceptionCount()) > 0) {
            local.add(diff(runId, lineId, tenantId, now, "THREE_WAY_MATCH_EXCEPTION", "PROCUREMENT",
                    "MATCHED", source.getMatchResultStatus() + "/" + safeInt(source.getOpenExceptionCount())));
        }
        if (source.getAllocatedQuantity() == null || source.getAllocatedQuantity().signum() == 0) {
            local.add(diff(runId, lineId, tenantId, now, "INVENTORY_ALLOCATION_MISSING", "INVENTORY",
                    "allocated receipt quantity", "0"));
        }
        if (source.getApOpenItemId() == null || source.getJournalEntryId() == null) {
            local.add(diff(runId, lineId, tenantId, now, "AP_OR_JOURNAL_MISSING", "FINANCE",
                    "ap open item + posted journal", "missing"));
        }
        if (quantityDifference != null && quantityDifference.signum() != 0) {
            local.add(diff(runId, lineId, tenantId, now, "INVOICE_QUANTITY_MISMATCH", "INVENTORY",
                    text(source.getInvoiceQuantity()), text(source.getAllocatedQuantity())));
        }
        if (apAmountDifference != null && apAmountDifference != 0L) {
            local.add(diff(runId, lineId, tenantId, now, "AP_AMOUNT_MISMATCH", "FINANCE",
                    text(source.getInvoiceGrossAmountMinor()), text(source.getApOpenAmountMinor())));
        }
        if (journalAmountDifference != null && journalAmountDifference != 0L) {
            local.add(diff(runId, lineId, tenantId, now, "JOURNAL_AMOUNT_MISMATCH", "FINANCE",
                    text(source.getInvoiceGrossAmountMinor()), text(source.getJournalAmountMinor())));
        }
        LineOutcome outcome;
        if (source.getAllocatedQuantity() == null || source.getAllocatedQuantity().signum() == 0) {
            outcome = new LineOutcome("MISSING", "INVENTORY_ALLOCATION_MISSING", "INVENTORY", null, null);
        } else if (!"MATCHED".equals(source.getMatchResultStatus()) || safeInt(source.getOpenExceptionCount()) > 0) {
            outcome = new LineOutcome("DIFFERENT", "THREE_WAY_MATCH_EXCEPTION", "PROCUREMENT",
                    quantityDifferenceSignificant(quantityDifference) ? quantityDifference : null,
                    amountDifferenceSignificant(matchedInventoryAmountDifference) ? matchedInventoryAmountDifference : null);
        } else if (quantityDifference != null && quantityDifference.signum() != 0) {
            outcome = new LineOutcome("DIFFERENT", "INVOICE_QUANTITY_MISMATCH", "INVENTORY", quantityDifference, null);
        } else if (source.getApOpenItemId() == null || source.getJournalEntryId() == null) {
            outcome = new LineOutcome("MISSING", "AP_OR_JOURNAL_MISSING", "FINANCE", zeroQuantity(quantityDifference), null);
        } else if (apAmountDifference != null && apAmountDifference != 0L) {
            outcome = new LineOutcome("DIFFERENT", "AP_AMOUNT_MISMATCH", "FINANCE", zeroQuantity(quantityDifference), apAmountDifference);
        } else if (journalAmountDifference != null && journalAmountDifference != 0L) {
            outcome = new LineOutcome("DIFFERENT", "JOURNAL_AMOUNT_MISMATCH", "FINANCE", zeroQuantity(quantityDifference), journalAmountDifference);
        } else {
            outcome = matchedOutcome(quantityDifference, journalAmountDifference);
        }
        lines.add(new ReconciliationLine()
                .setLineId(lineId)
                .setTenantId(tenantId)
                .setRunId(runId)
                .setLineType("SUPPLIER_INVOICE")
                .setLineKey(lineKey)
                .setLegalEntityId(source.getLegalEntityId())
                .setCurrencyCode(source.getCurrencyCode())
                .setPurchaseOrderId(source.getPurchaseOrderId())
                .setPurchaseOrderItemId(source.getPurchaseOrderItemId())
                .setInventoryMovementId(source.getInventoryMovementId())
                .setSupplierInvoiceId(source.getSupplierInvoiceId())
                .setSupplierInvoiceLineId(source.getSupplierInvoiceLineId())
                .setApOpenItemId(source.getApOpenItemId())
                .setJournalEntryId(source.getJournalEntryId())
                .setProcurementVersion(source.getPurchaseOrderLineVersion())
                .setInventoryVersion(source.getInventoryMovementVersion())
                .setFinanceVersion(max(source.getApOpenItemVersion(), source.getJournalVersion()))
                .setProcurementQuantity(source.getInvoiceQuantity())
                .setInventoryQuantity(source.getAllocatedQuantity())
                .setFinanceQuantity(source.getInvoiceQuantity())
                .setProcurementAmountMinor(source.getInvoiceGrossAmountMinor())
                .setInventoryAmountMinor(source.getMatchedInventoryAmountMinor())
                .setFinanceAmountMinor(source.getInvoiceGrossAmountMinor())
                .setQuantityDifference(outcome.quantityDifference())
                .setAmountDifferenceMinor(outcome.amountDifferenceMinor())
                .setPrimaryDifferenceCode(outcome.primaryDifferenceCode())
                .setResponsibilityDomain(outcome.responsibilityDomain())
                .setMatchStatus(outcome.matchStatus())
                .setDifferenceCount(local.size())
                .setCreatedAt(now));
        differences.addAll(local);
    }

    private List<ReconciliationWatermark> buildWatermarks(Long tenantId, String runId, LocalDateTime now,
                                                          List<QualifiedReceiptSource> qualifiedReceipts,
                                                          List<SupplierReturnSource> supplierReturns,
                                                          List<SupplierInvoiceSource> supplierInvoices) {
        List<ReconciliationWatermark> values = new ArrayList<>();
        values.add(watermark(tenantId, runId, now, "PROCUREMENT_RECEIPT", "qualified_receipt",
                maxLong(qualifiedReceipts, QualifiedReceiptSource::getReceiptLineVersion), qualifiedReceipts.size()));
        values.add(watermark(tenantId, runId, now, "INVENTORY_MOVEMENT", "accepted_inventory_movement",
                maxLong(qualifiedReceipts, QualifiedReceiptSource::getInventoryMovementVersion), qualifiedReceipts.size()));
        values.add(watermark(tenantId, runId, now, "INVENTORY_VALUATION", "inventory_valuation_layer",
                maxLong(qualifiedReceipts, QualifiedReceiptSource::getValuationLayerVersion), countNonNull(qualifiedReceipts, QualifiedReceiptSource::getValuationLayerId)));
        values.add(watermark(tenantId, runId, now, "WAREHOUSE_SUPPLIER_RETURN", "supplier_return_line",
                maxLong(supplierReturns, SupplierReturnSource::getReturnLineVersion), supplierReturns.size()));
        values.add(watermark(tenantId, runId, now, "WAREHOUSE_SUPPLIER_RETURN_EXECUTION", "supplier_return_dispatch_line",
                maxLong(supplierReturns, SupplierReturnSource::getExecutionLineVersion), countNonNull(supplierReturns, SupplierReturnSource::getExecutionLineId)));
        values.add(watermark(tenantId, runId, now, "FINANCE_SUPPLIER_INVOICE", "supplier_invoice_line",
                maxLong(supplierInvoices, SupplierInvoiceSource::getInvoiceLineVersion), supplierInvoices.size()));
        values.add(watermark(tenantId, runId, now, "FINANCE_AP_JOURNAL", "ap_open_item_journal_entry",
                maxLong(supplierInvoices, value -> max(value.getApOpenItemVersion(), value.getJournalVersion())), supplierInvoices.size()));
        return values;
    }

    private static ReconciliationWatermark watermark(Long tenantId, String runId, LocalDateTime now,
                                                     String domainCode, String sourceTable,
                                                     Long maxVersion, int recordCount) {
        return new ReconciliationWatermark()
                .setWatermarkId(UUID.randomUUID().toString())
                .setTenantId(tenantId)
                .setRunId(runId)
                .setDomainCode(domainCode)
                .setSourceTable(sourceTable)
                .setMaxAggregateVersion(maxVersion)
                .setMaxObservedAt(null)
                .setRecordCount(recordCount)
                .setCreatedAt(now);
    }

    private static Map<String, Integer> countStatuses(List<ReconciliationLine> lines) {
        Map<String, Integer> counts = new HashMap<>();
        for (ReconciliationLine line : lines) {
            counts.merge(line.getMatchStatus(), 1, Integer::sum);
        }
        return counts;
    }

    private static ReconciliationDifference diff(String runId, String lineId, Long tenantId, LocalDateTime now,
                                                 String code, String domain, String expected, String actual) {
        return new ReconciliationDifference()
                .setDifferenceId(UUID.randomUUID().toString())
                .setTenantId(tenantId)
                .setRunId(runId)
                .setLineId(lineId)
                .setDifferenceCode(code)
                .setSourceDomain(domain)
                .setExpectedValue(expected)
                .setActualValue(actual)
                .setBlocking(Boolean.TRUE)
                .setCreatedAt(now);
    }

    private static void validate(CreateRunRequest request, String actorPrincipalId) {
        require(request != null, "reconciliation request is required");
        requireRef(actorPrincipalId, "actorPrincipalId");
        requireRef(request.getIdempotencyKey(), "idempotencyKey");
        requireRef(request.getRunId(), "runId");
        requireUuid(request.getCorrelationId(), "correlationId");
        require(request.getOccurredAt() != null, "occurredAt is required");
        requireRef(request.getLegalEntityId(), "legalEntityId");
        requireCurrency(request.getCurrencyCode());
    }

    private static void requireRef(String value, String field) {
        require(value != null && SAFE_REF.matcher(value).matches(), field + " must be a safe opaque reference");
    }

    private static void requireCurrency(String value) {
        require(value != null && upper(value).matches("[A-Z]{3}"), "currencyCode must be ISO-4217");
    }

    private static void requireUuid(String value, String field) {
        try {
            UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " must be a UUID", exception);
        }
    }

    private static Long max(Long left, Long right) {
        if (left == null) return right;
        if (right == null) return left;
        return Math.max(left, right);
    }

    private static <T> Long maxLong(List<T> values, java.util.function.Function<T, Long> getter) {
        Long max = null;
        for (T value : values) {
            max = max(max, getter.apply(value));
        }
        return max;
    }

    private static <T> int countNonNull(List<T> values, java.util.function.Function<T, Object> getter) {
        int count = 0;
        for (T value : values) {
            if (getter.apply(value) != null) {
                count++;
            }
        }
        return count;
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String text(Object value) {
        return value == null ? "null" : value.toString();
    }

    private static BigDecimal quantityDifference(BigDecimal expected, BigDecimal actual) {
        return expected == null || actual == null ? null : actual.subtract(expected);
    }

    private static Long amountDifference(Long expected, Long actual) {
        return expected == null || actual == null ? null : Math.subtractExact(actual, expected);
    }

    private static BigDecimal zeroQuantity(BigDecimal difference) {
        return difference == null ? BigDecimal.ZERO.setScale(6) : difference.subtract(difference);
    }

    private static boolean quantityDifferenceSignificant(BigDecimal value) {
        return value != null && value.signum() != 0;
    }

    private static boolean amountDifferenceSignificant(Long value) {
        return value != null && value != 0L;
    }

    private static LineOutcome matchedOutcome(BigDecimal quantityDifference, Long amountDifference) {
        return new LineOutcome("MATCHED", null, "NONE", zeroQuantity(quantityDifference), amountDifference == null ? 0L : amountDifference);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private record LineOutcome(String matchStatus, String primaryDifferenceCode, String responsibilityDomain,
                               BigDecimal quantityDifference, Long amountDifferenceMinor) {
    }
}
