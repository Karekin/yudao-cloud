package cn.iocoder.yudao.module.cloudmold.finance.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.*;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.AccountingPeriod;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureToPayRecords.*;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.SupplierReturnReversalRecords.*;
import cn.iocoder.yudao.module.cloudmold.finance.dal.mysql.SupplierReturnReversalMapper;
import cn.iocoder.yudao.module.cloudmold.finance.service.actor.FinanceActorPrincipalPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SupplierReturnReversalService implements SupplierReturnReversalCommandApi {

    private static final int OPERATION_SUCCEEDED = 10;
    private static final String SOURCE_SYSTEM = "cloudmold-finance";
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private final SupplierReturnReversalMapper mapper;
    private final OutboxAppender outboxAppender;
    private final FinanceActorPrincipalPort actorPrincipalPort;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult post(SupplierReturnReversalCommands.Post command, String actorPrincipalId) {
        FinanceCommandEnvelope envelope = requireEnvelope(command);
        validateEnvelope(envelope);
        requireRef(actorPrincipalId, "actorPrincipalId");
        actorPrincipalPort.requireActive(actorPrincipalId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.ofInstant(envelope.getOccurredAt(), ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(actorPrincipalId + "\n" + JsonUtils.toJsonString(command));
        String attemptToken = nextAttemptToken();
        mapper.insertOrResolveOperation(tenantId, envelope.getIdempotencyKey(), "POST_SUPPLIER_RETURN_REVERSAL",
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve supplier return reversal operation");
        Operation operation = nonNull(mapper.selectOperationForUpdate(tenantId, operationId),
                "supplier return reversal operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with different supplier return reversal payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing supplier return reversal operation is incomplete");
            ProcureToPayResult replay = JsonUtils.parseObject(operation.getResultJson(), ProcureToPayResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        Context context = new Context(tenantId, actorPrincipalId, now);
        PostedResult posted = postAdjustment(context, command);
        appendEvent(context, envelope, posted);
        ProcureToPayResult result = ProcureToPayResult.builder()
                .operationId(operationId)
                .duplicate(false)
                .aggregateType("finance_supplier_debit_adjustment")
                .aggregateId(posted.adjustmentId())
                .aggregateVersion(1L)
                .status("POSTED")
                .journalEntryId(posted.journalEntryId())
                .build();
        require(mapper.markOperationSucceeded(tenantId, operationId, posted.adjustmentId(),
                        JsonUtils.toJsonString(result), now) == 1,
                "supplier return reversal operation completion conflict");
        return result;
    }

    private PostedResult postAdjustment(Context context, SupplierReturnReversalCommands.Post command) {
        requireRef(command.getSupplierReturnId(), "supplierReturnId");
        require(command.getExpectedSupplierReturnVersion() != null && command.getExpectedSupplierReturnVersion() > 0,
                "expectedSupplierReturnVersion must be positive");
        requireRef(command.getLedgerId(), "ledgerId");
        requireRef(command.getAccountingPeriodId(), "accountingPeriodId");
        require(command.getAccountingDate() != null, "accountingDate is required");
        requireRef(command.getPostingRuleId(), "postingRuleId");
        require(command.getPostingRuleVersion() != null && command.getPostingRuleVersion() > 0,
                "postingRuleVersion must be positive");
        requireSha256(command.getReversalEvidenceSha256(), "reversalEvidenceSha256");
        requireRef(command.getReasonCode(), "reasonCode");

        WarehouseReturn supplierReturn = nonNull(mapper.selectWarehouseReturn(context.tenantId(), command.getSupplierReturnId()),
                "supplier return not found");
        require(supplierReturn.getVersion().equals(command.getExpectedSupplierReturnVersion()),
                "supplier return version mismatch");
        require(Set.of("DISPATCHED", "COMPLETED").contains(supplierReturn.getStatus()),
                "supplier return must be dispatched or completed");
        List<WarehouseReturnLine> lines = mapper.selectWarehouseReturnLines(context.tenantId(), supplierReturn.getReturnId());
        require(!lines.isEmpty(), "supplier return has no lines");
        lines.forEach(line -> require("ACCEPTED".equals(line.getSourceDisposition()),
                "only ACCEPTED supplier return lines are supported in finance reversal"));

        AccountingPeriod period = requireOpenPeriod(context.tenantId(), command.getAccountingPeriodId(), command.getAccountingDate());
        Map<String, PostingAccount> accounts = postingAccounts(context.tenantId(), command.getPostingRuleId(),
                command.getPostingRuleVersion(), command.getLedgerId(), "SUPPLIER_RETURN",
                Set.of("INVENTORY", "INPUT_TAX", "PURCHASE_PRICE_VARIANCE", "AP"));

        String adjustmentId = UUID.randomUUID().toString();
        String adjustmentCode = "SDA-" + UUID.randomUUID();
        String journalId = UUID.randomUUID().toString();

        List<SupplierDebitAdjustmentLine> adjustmentLines = new ArrayList<>();
        List<SupplierReturnApReversal> apReversals = new ArrayList<>();
        List<JournalLine> journalLines = new ArrayList<>();
        long totalGross = 0L;
        long totalTax = 0L;
        long totalValuation = 0L;
        String legalEntityId = null;
        String currencyCode = null;

        int nextJournalLine = 1;
        int nextAdjustmentLine = 10;
        for (WarehouseReturnLine line : lines) {
            PurchaseOrderLineEvidence po = nonNull(mapper.selectLatestPoEvidence(context.tenantId(), line.getPurchaseOrderItemId()),
                    "purchase order evidence not found");
            ReceiptLineEvidence receipt = nonNull(mapper.selectLatestReceiptEvidence(context.tenantId(), line.getReceiptLineId()),
                    "receipt evidence not found");
            QualityDispositionEvidence quality = nonNull(mapper.selectQualityEvidence(context.tenantId(),
                    line.getQualityDecisionId(), line.getDecisionVersion()), "quality disposition evidence not found");
            require(Objects.equals(supplierReturn.getPurchaseOrderId(), po.getPurchaseOrderId()),
                    "supplier return purchase order does not match finance purchase order evidence");
            require(Objects.equals(supplierReturn.getReceiptId(), receipt.getReceiptId()),
                    "supplier return receipt does not match finance receipt evidence");
            require(Objects.equals(line.getReceiptLineId(), quality.getReceiptLineId()),
                    "supplier return line receipt does not match finance quality evidence");
            require(Objects.equals(line.getPurchaseOrderItemId(), po.getPurchaseOrderItemId())
                            && Objects.equals(line.getPurchaseOrderItemId(), quality.getPurchaseOrderItemId()),
                    "supplier return line purchase item does not match finance evidence");
            require(Objects.equals(line.getCurrencyCode(), po.getCurrencyCode())
                            && Objects.equals(line.getCurrencyCode(), period.getCurrencyCode()),
                    "supplier return currency must equal purchase order and accounting period currency");
            if (legalEntityId == null) {
                legalEntityId = po.getLegalEntityId();
            } else {
                require(Objects.equals(legalEntityId, po.getLegalEntityId()),
                        "supplier return crosses multiple legal entities");
            }
            if (currencyCode == null) {
                currencyCode = line.getCurrencyCode();
            } else {
                require(Objects.equals(currencyCode, line.getCurrencyCode()),
                        "supplier return crosses multiple currencies");
            }

            InventoryValuationLayer layer = nonNull(mapper.selectValuationLayerForUpdate(context.tenantId(),
                    command.getLedgerId(), line.getReceiptLineId(), line.getQualityDecisionId(), line.getPurchaseOrderItemId()),
                    "valuation layer not found for supplier return line");
            require(Objects.equals(layer.getCurrencyCode(), line.getCurrencyCode()),
                    "valuation layer currency does not match supplier return line");
            require(Objects.equals(layer.getValuationPolicyId(), line.getValuationPolicyId())
                            && Objects.equals(layer.getValuationPolicyVersion(), line.getValuationPolicyVersion()),
                    "valuation layer snapshot does not match supplier return line");
            require(scale(layer.getRemainingQuantity()).compareTo(scale(line.getDispatchedQuantity())) >= 0,
                    "valuation layer remaining quantity is insufficient");

            BigDecimal reversalQuantity = scale(line.getDispatchedQuantity());
            long valuationAmount = proportional(layer.getTotalCostAmountMinor(), reversalQuantity, layer.getQuantity());
            require(layer.getRemainingCostAmountMinor() >= valuationAmount,
                    "valuation layer remaining cost is insufficient");

            List<SupplierReturnAllocationCandidate> candidates = mapper.selectActiveAllocationCandidates(
                    context.tenantId(), line.getReceiptLineId(), line.getQualityDecisionId());
            require(!candidates.isEmpty(), "no active matched posted supplier invoice allocation found");
            BigDecimal remaining = reversalQuantity;
            long lineGross = 0L;
            long lineTax = 0L;
            for (SupplierReturnAllocationCandidate candidate : candidates) {
                if (remaining.signum() == 0) {
                    break;
                }
                require(Objects.equals(candidate.getSupplierId(), supplierReturn.getSupplierId()),
                        "supplier return supplier does not match supplier invoice allocation");
                require(Objects.equals(candidate.getCurrencyCode(), line.getCurrencyCode()),
                        "supplier return currency does not match supplier invoice allocation");
                BigDecimal allocatable = min(remaining, candidate.getAllocatedQuantity());
                if (allocatable.signum() == 0) {
                    continue;
                }
                long grossShare = proportional(candidate.getInvoiceLineGrossAmountMinor(), allocatable, candidate.getInvoiceLineQuantity());
                long taxShare = proportional(candidate.getInvoiceLineTaxAmountMinor(), allocatable, candidate.getInvoiceLineQuantity());
                long netShare = grossShare - taxShare;
                require(candidate.getAvailableOpenAmountMinor() >= grossShare,
                        "supplier invoice open AP is insufficient for supplier return reversal");
                ApOpenItem ap = nonNull(mapper.selectApForUpdate(context.tenantId(), candidate.getApOpenItemId()),
                        "ap open item not found");
                require(ap.getOpenAmountMinor() >= grossShare, "ap open item is insufficient for supplier return reversal");
                applyInstallments(context, ap.getApOpenItemId(), grossShare);
                require(mapper.applyApSettlement(context.tenantId(), ap.getApOpenItemId(), ap.getVersion(), grossShare, context.now()) == 1,
                        "ap open item settlement conflict");
                require(mapper.updateInvoiceSettlementFromAp(context.tenantId(), ap.getApOpenItemId(), context.now()) >= 1,
                        "failed to update supplier invoice settlement state");
                SupplierReturnApReversal reversal = new SupplierReturnApReversal()
                        .setApReversalId(UUID.randomUUID().toString())
                        .setTenantId(context.tenantId())
                        .setSupplierDebitAdjustmentId(adjustmentId)
                        .setSupplierReturnLineId(line.getReturnLineId())
                        .setSupplierInvoiceId(candidate.getSupplierInvoiceId())
                        .setInvoiceLineId(candidate.getInvoiceLineId())
                        .setApOpenItemId(candidate.getApOpenItemId())
                        .setReversalQuantity(allocatable)
                        .setUnitOfMeasure(candidate.getUnitOfMeasure())
                        .setNetReversalAmountMinor(netShare)
                        .setTaxReversalAmountMinor(taxShare)
                        .setGrossReversalAmountMinor(grossShare)
                        .setJournalEntryVersion(1L)
                        .setCreatedAt(context.now());
                require(mapper.insertSupplierReturnApReversal(reversal) == 1,
                        "failed to persist supplier return ap reversal");
                require(mapper.insertSupplierReturnApApplication(UUID.randomUUID().toString(), context.tenantId(),
                                candidate.getApOpenItemId(), adjustmentId, grossShare, journalId, context.now()) == 1,
                        "failed to persist supplier return ap application");
                apReversals.add(reversal);
                lineGross = Math.addExact(lineGross, grossShare);
                lineTax = Math.addExact(lineTax, taxShare);
                totalGross = Math.addExact(totalGross, grossShare);
                totalTax = Math.addExact(totalTax, taxShare);
                remaining = remaining.subtract(allocatable);

                journalLines.add(journalLine(context, journalId, nextJournalLine++, accounts.get("AP"),
                        grossShare, 0L, line.getCurrencyCode(), supplierReturn.getSupplierId(),
                        candidate.getSupplierInvoiceId(), candidate.getApOpenItemId(),
                        supplierReturn.getPurchaseOrderId(), line.getPurchaseOrderItemId(),
                        line.getReceiptLineId(), layer.getInventoryMovementId()));
            }
            require(remaining.signum() == 0, "supplier return quantity exceeds finance matched allocation quantity");

            require(mapper.applyValuationReturn(context.tenantId(), layer.getValuationLayerId(), layer.getVersion(),
                            reversalQuantity, valuationAmount, context.now()) == 1,
                    "valuation layer reversal conflict");
            require(mapper.insertSupplierReturnValuationEffect(UUID.randomUUID().toString(), context.tenantId(),
                            layer.getValuationLayerId(), layer.getInventoryMovementId(), layer.getInventoryMovementVersion(),
                            reversalQuantity, valuationAmount, line.getCurrencyCode(), journalId, context.now()) == 1,
                    "failed to persist supplier return valuation effect");
            totalValuation = Math.addExact(totalValuation, valuationAmount);

            journalLines.add(journalLine(context, journalId, nextJournalLine++, accounts.get("INVENTORY"),
                    0L, valuationAmount, line.getCurrencyCode(), supplierReturn.getSupplierId(),
                    null, null, supplierReturn.getPurchaseOrderId(), line.getPurchaseOrderItemId(),
                    line.getReceiptLineId(), layer.getInventoryMovementId()));

            long lineVariance = Math.subtractExact(lineGross - lineTax, valuationAmount);
            adjustmentLines.add(new SupplierDebitAdjustmentLine()
                    .setAdjustmentLineId(UUID.randomUUID().toString())
                    .setTenantId(context.tenantId())
                    .setSupplierDebitAdjustmentId(adjustmentId)
                    .setSupplierReturnLineId(line.getReturnLineId())
                    .setLineNumber(nextAdjustmentLine)
                    .setReceiptLineId(line.getReceiptLineId())
                    .setQualityDispositionId(line.getQualityDecisionId())
                    .setQualityDispositionVersion(line.getDecisionVersion())
                    .setPurchaseOrderId(supplierReturn.getPurchaseOrderId())
                    .setPurchaseOrderItemId(line.getPurchaseOrderItemId())
                    .setPurchaseOrderScheduleId(line.getPurchaseOrderScheduleId())
                    .setValuationLayerId(layer.getValuationLayerId())
                    .setCanonicalSkuId(line.getCanonicalSkuId())
                    .setReversalQuantity(reversalQuantity)
                    .setUnitOfMeasure(line.getUomCode())
                    .setUnitCostAmountMinor(line.getUnitCostAmountMinor())
                    .setValuationReversalAmountMinor(valuationAmount)
                    .setNetReversalAmountMinor(lineGross - lineTax)
                    .setTaxReversalAmountMinor(lineTax)
                    .setGrossReversalAmountMinor(lineGross)
                    .setPurchasePriceVarianceAmountMinor(lineVariance)
                    .setCurrencyCode(line.getCurrencyCode())
                    .setCreatedAt(context.now()));
            nextAdjustmentLine += 10;
        }

        if (totalTax > 0) {
            journalLines.add(journalLine(context, journalId, nextJournalLine++, accounts.get("INPUT_TAX"),
                    0L, totalTax, currencyCode, supplierReturn.getSupplierId(),
                    null, null, supplierReturn.getPurchaseOrderId(), null, null, null));
        }
        long variance = Math.subtractExact(totalGross - totalTax, totalValuation);
        if (variance > 0) {
            journalLines.add(journalLine(context, journalId, nextJournalLine++, accounts.get("PURCHASE_PRICE_VARIANCE"),
                    0L, variance, currencyCode, supplierReturn.getSupplierId(),
                    null, null, supplierReturn.getPurchaseOrderId(), null, null, null));
        } else if (variance < 0) {
            journalLines.add(journalLine(context, journalId, nextJournalLine++, accounts.get("PURCHASE_PRICE_VARIANCE"),
                    Math.negateExact(variance), 0L, currencyCode, supplierReturn.getSupplierId(),
                    null, null, supplierReturn.getPurchaseOrderId(), null, null, null));
        }

        long debit = journalLines.stream().mapToLong(JournalLine::getDebitAmountMinor).sum();
        long credit = journalLines.stream().mapToLong(JournalLine::getCreditAmountMinor).sum();
        require(debit == credit && debit == totalGross, "supplier return posted journal is not balanced");

        JournalEntry journal = new JournalEntry().setJournalEntryId(journalId).setTenantId(context.tenantId())
                .setJournalCode("SRR-" + adjustmentCode).setLegalEntityId(legalEntityId)
                .setLedgerId(command.getLedgerId()).setPeriodId(command.getAccountingPeriodId())
                .setAccountingDate(command.getAccountingDate()).setSourceType("SUPPLIER_RETURN")
                .setSourceId(adjustmentId).setCurrencyCode(currencyCode)
                .setDocumentCurrencyCode(currencyCode).setDebitTotalMinor(totalGross)
                .setCreditTotalMinor(totalGross).setEvidenceSha256(command.getReversalEvidenceSha256())
                .setStatus("POSTED").setPreparedByPrincipalId(context.actor()).setPostedByPrincipalId(context.actor())
                .setReasonCode(command.getReasonCode()).setVersion(1L).setPreparedAt(context.now())
                .setPostedAt(context.now()).setCreatedAt(context.now()).setUpdatedAt(context.now());
        require(mapper.insertJournalEntry(journal) == 1, "failed to persist posted journal header");
        validateDimensions(context, command.getJournalDimensions());
        for (JournalLine line : journalLines) {
            require(mapper.insertJournalLine(line) == 1, "failed to persist posted journal line");
            insertDimensions(context, line.getJournalLineId(), command.getJournalDimensions());
        }
        require(mapper.insertJournalSourceEffect(UUID.randomUUID().toString(), context.tenantId(),
                        "SUPPLIER_RETURN", adjustmentId, "SUPPLIER_RETURN", journalId, context.now()) == 1,
                "failed to persist supplier return journal source effect");
        require(mapper.insertJournalHistory(context.tenantId(), journalId, null, "POSTED", context.actor(),
                        command.getReasonCode(), 1L, context.now()) == 1,
                "failed to persist posted journal history");

        SupplierDebitAdjustment header = new SupplierDebitAdjustment()
                .setSupplierDebitAdjustmentId(adjustmentId)
                .setTenantId(context.tenantId())
                .setAdjustmentCode(adjustmentCode)
                .setSupplierReturnId(supplierReturn.getReturnId())
                .setSupplierReturnVersion(supplierReturn.getVersion())
                .setLegalEntityId(legalEntityId)
                .setLedgerId(command.getLedgerId())
                .setAccountingPeriodId(command.getAccountingPeriodId())
                .setAccountingDate(command.getAccountingDate())
                .setSupplierId(supplierReturn.getSupplierId())
                .setCurrencyCode(currencyCode)
                .setApReversalAmountMinor(totalGross)
                .setTaxReversalAmountMinor(totalTax)
                .setValuationReversalAmountMinor(totalValuation)
                .setPurchasePriceVarianceAmountMinor(variance)
                .setPostingRuleId(command.getPostingRuleId())
                .setPostingRuleVersion(command.getPostingRuleVersion())
                .setJournalEntryId(journalId)
                .setReversalEvidenceSha256(command.getReversalEvidenceSha256())
                .setReasonCode(command.getReasonCode())
                .setStatus("POSTED")
                .setPostedByPrincipalId(context.actor())
                .setPostedAt(context.now())
                .setVersion(1L)
                .setCreatedAt(context.now())
                .setUpdatedAt(context.now());
        require(mapper.insertSupplierDebitAdjustment(header) == 1, "failed to persist supplier debit adjustment");
        for (SupplierDebitAdjustmentLine line : adjustmentLines) {
            require(mapper.insertSupplierDebitAdjustmentLine(line) == 1,
                    "failed to persist supplier debit adjustment line");
        }
        return new PostedResult(adjustmentId, journalId);
    }

    private void applyInstallments(Context context, String apOpenItemId, long amountMinor) {
        long remaining = amountMinor;
        for (ApInstallment installment : mapper.selectOpenInstallmentsForUpdate(context.tenantId(), apOpenItemId)) {
            if (remaining == 0) {
                break;
            }
            long available = installment.getAmountMinor() - installment.getSettledAmountMinor();
            long applied = Math.min(available, remaining);
            if (applied == 0) {
                continue;
            }
            require(mapper.applyInstallmentSettlement(context.tenantId(), installment.getApInstallmentId(),
                            installment.getVersion(), applied, context.now()) == 1,
                    "ap installment settlement conflict");
            remaining -= applied;
        }
        require(remaining == 0, "ap installments are insufficient for supplier return reversal");
    }

    private static BigDecimal min(BigDecimal left, BigDecimal right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private JournalLine journalLine(Context context, String journalId, int lineNumber, PostingAccount account,
                                    long debit, long credit, String currency, String supplierId,
                                    String supplierInvoiceId, String apOpenItemId, String purchaseOrderId,
                                    String purchaseOrderItemId, String receiptLineId, String inventoryMovementId) {
        require((debit > 0 && credit == 0) || (credit > 0 && debit == 0), "supplier return journal line side is invalid");
        require(account != null, "supplier return posting rule account is missing");
        return new JournalLine().setJournalLineId(UUID.randomUUID().toString()).setTenantId(context.tenantId())
                .setJournalEntryId(journalId).setLedgerId(account.getLedgerId()).setLineNumber(lineNumber)
                .setAccountId(account.getAccountId()).setAccountCode(account.getAccountCode())
                .setDebitAmountMinor(debit).setCreditAmountMinor(credit).setTransactionCurrencyCode(currency)
                .setTransactionAmountMinor(Math.max(debit, credit)).setSupplierId(supplierId)
                .setSupplierInvoiceId(supplierInvoiceId).setApOpenItemId(apOpenItemId).setPurchaseOrderId(purchaseOrderId)
                .setPurchaseOrderItemId(purchaseOrderItemId).setReceiptLineId(receiptLineId)
                .setInventoryMovementId(inventoryMovementId).setCreatedAt(context.now());
    }

    private Map<String, PostingAccount> postingAccounts(Long tenantId, String ruleId, Long ruleVersion,
                                                        String ledgerId, String sourceType, Set<String> requiredRoles) {
        Map<String, PostingAccount> accounts = new HashMap<>();
        for (PostingAccount account : mapper.selectPostingAccounts(tenantId, ruleId, ruleVersion, ledgerId, sourceType)) {
            require(accounts.put(account.getAccountRole(), account) == null,
                    "supplier return posting rule contains duplicate account role");
        }
        require(accounts.keySet().containsAll(requiredRoles),
                "supplier return posting rule is missing required account roles: " + requiredRoles);
        return accounts;
    }

    private AccountingPeriod requireOpenPeriod(Long tenantId, String periodId, java.time.LocalDate accountingDate) {
        AccountingPeriod period = nonNull(mapper.selectPeriodForUpdate(tenantId, periodId),
                "accounting period not found");
        require("OPEN".equals(period.getStatus()), "accounting period is not open");
        require(!accountingDate.isBefore(period.getPeriodStart()) && !accountingDate.isAfter(period.getPeriodEnd()),
                "accounting date is outside accounting period");
        return period;
    }

    private void validateDimensions(Context context, List<JournalDimensionAssignment> dimensions) {
        if (dimensions == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (JournalDimensionAssignment dimension : dimensions) {
            require(dimension != null && seen.add(dimension.getDimensionTypeId()),
                    "supplier return journal dimension type must be unique");
            require(mapper.countActiveDimensionValue(context.tenantId(), dimension.getDimensionTypeId(),
                    dimension.getDimensionValueId()) == 1,
                    "supplier return journal dimension value is not active for type");
        }
    }

    private void insertDimensions(Context context, String lineId, List<JournalDimensionAssignment> dimensions) {
        if (dimensions == null) {
            return;
        }
        for (JournalDimensionAssignment dimension : dimensions) {
            require(mapper.insertJournalLineDimension(UUID.randomUUID().toString(), context.tenantId(), lineId,
                            dimension.getDimensionTypeId(), dimension.getDimensionValueId(), context.now()) == 1,
                    "failed to persist supplier return journal dimension");
        }
    }

    private void appendEvent(Context context, FinanceCommandEnvelope envelope, PostedResult posted) {
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("finance.supplier_return_reversal.posted")
                .schemaVersion(1)
                .sourceSystem(SOURCE_SYSTEM)
                .tenantId(context.tenantId())
                .aggregateType("finance_supplier_debit_adjustment")
                .aggregateId(posted.adjustmentId())
                .aggregateVersion(1L)
                .eventSequence((short) 1)
                .occurredAt(envelope.getOccurredAt())
                .traceId(envelope.getRunId())
                .correlationId(envelope.getCorrelationId())
                .causationId(envelope.getCausationId())
                .idempotencyKey(envelope.getIdempotencyKey() + ":1")
                .payload(Map.of(
                        "supplier_debit_adjustment_id", posted.adjustmentId(),
                        "journal_entry_id", posted.journalEntryId(),
                        "status", "POSTED"))
                .headers(Map.of("run_id", envelope.getRunId() == null ? "" : envelope.getRunId()))
                .destination("lakehouse")
                .build());
    }

    String nextAttemptToken() {
        return UUID.randomUUID().toString();
    }

    private FinanceCommandEnvelope requireEnvelope(SupplierReturnReversalCommands.Post command) {
        require(command != null, "supplier return reversal command is required");
        return nonNull(command.getEnvelope(), "finance command envelope is required");
    }

    private static void validateEnvelope(FinanceCommandEnvelope envelope) {
        require(envelope.getCorrelationId() != null && !envelope.getCorrelationId().isBlank(),
                "correlationId is required");
        requireRef(envelope.getIdempotencyKey(), "idempotencyKey");
        require(envelope.getOccurredAt() != null, "occurredAt is required");
        if (envelope.getRunId() != null) {
            requireRef(envelope.getRunId(), "runId");
        }
        if (envelope.getCausationId() != null) {
            requireRef(envelope.getCausationId(), "causationId");
        }
    }

    private static long proportional(long total, BigDecimal part, BigDecimal whole) {
        return BigDecimal.valueOf(total).multiply(part).divide(whole, 0, RoundingMode.HALF_UP).longValueExact();
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(8) : value.setScale(8);
    }

    private static void requireRef(String value, String field) {
        require(value != null && SAFE_REF.matcher(value).matches(), field + " is invalid");
    }

    private static void requireSha256(String value, String field) {
        require(value != null && SHA256.matcher(value).matches(), field + " must be lowercase SHA-256");
    }

    private static <T> T nonNull(T value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record Context(Long tenantId, String actor, LocalDateTime now) {
    }

    private record PostedResult(String adjustmentId, String journalEntryId) {
    }
}
