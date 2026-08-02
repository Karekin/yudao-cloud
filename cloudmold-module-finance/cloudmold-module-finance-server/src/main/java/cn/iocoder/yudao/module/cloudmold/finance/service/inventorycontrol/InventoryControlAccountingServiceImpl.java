package cn.iocoder.yudao.module.cloudmold.finance.service.inventorycontrol;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.finance.api.inventorycontrol.InventoryControlAccountingCommandApi;
import cn.iocoder.yudao.module.cloudmold.finance.api.inventorycontrol.InventoryControlAccountingCommands;
import cn.iocoder.yudao.module.cloudmold.finance.api.inventorycontrol.InventoryControlAccountingResult;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.FinanceCommandEnvelope;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.JournalDimensionAssignment;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.AccountingPeriod;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.InventoryControlFinanceRecords.*;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureToPayRecords.JournalEntry;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureToPayRecords.JournalLine;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureToPayRecords.PostingAccount;
import cn.iocoder.yudao.module.cloudmold.finance.dal.mysql.InventoryControlFinanceMapper;
import cn.iocoder.yudao.module.cloudmold.finance.service.actor.FinanceActorPrincipalPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class InventoryControlAccountingServiceImpl implements InventoryControlAccountingCommandApi {

    private static final int OPERATION_SUCCEEDED = 10;
    private static final String SOURCE_SYSTEM = "cloudmold-finance";
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private final InventoryControlFinanceMapper mapper;
    private final OutboxAppender outboxAppender;
    private final FinanceActorPrincipalPort actorPrincipalPort;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryControlAccountingResult submitStockCountGainBasis(
            InventoryControlAccountingCommands.SubmitStockCountGainBasis command, String actorPrincipalId) {
        return execute(command.getEnvelope(), "SUBMIT_STOCK_COUNT_GAIN_BASIS", actorPrincipalId, command, context -> {
            requireRef(command.getStockCountId(), "stockCountId");
            requireRef(command.getStockCountLineId(), "stockCountLineId");
            requireRef(command.getValuationPolicyId(), "valuationPolicyId");
            requireRef(command.getValuationPolicyVersion(), "valuationPolicyVersion");
            requireSha256(command.getValuationPolicyHash(), "valuationPolicyHash");
            requireRef(command.getUnitOfMeasure(), "unitOfMeasure");
            require(command.getUnitCostAmountMinor() != null && command.getUnitCostAmountMinor() >= 0,
                    "unitCostAmountMinor must be nonnegative");
            requireCurrency(command.getCurrencyCode());
            requireSha256(command.getEvidenceSha256(), "evidenceSha256");
            StockCountSource source = nonNull(mapper.selectStockCountSourceForUpdate(context.tenantId(),
                    command.getStockCountId(), command.getStockCountLineId()), "stock count line not found");
            requireExpectedVersion(command.getExpectedStockCountVersion(), source.getStockCountVersion());
            requireExpectedVersion(command.getExpectedLineVersion(), source.getLineVersion());
            require(source.getDifferenceQuantity() != null && source.getDifferenceQuantity().signum() > 0,
                    "stock count gain basis requires positive difference quantity");
            require("ADJUSTED".equals(source.getLineStatus()), "stock count line must be ADJUSTED before gain basis submission");
            require(mapper.selectGainBasisByLineForUpdate(context.tenantId(), source.getLineId(), source.getLineVersion()) == null,
                    "stock count gain basis already exists for this exact line version");
            long totalCost = BigDecimal.valueOf(command.getUnitCostAmountMinor()).multiply(source.getDifferenceQuantity())
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
            String basisId = valueOrUuid(command.getStockCountGainBasisId());
            String basisCode = command.getBasisCode() == null || command.getBasisCode().isBlank()
                    ? "SCGB-" + source.getStockCountCode() + "-" + source.getLineNumber()
                    : command.getBasisCode().trim();
            require(mapper.insertGainBasis(new StockCountGainBasis()
                            .setStockCountGainBasisId(basisId).setTenantId(context.tenantId()).setBasisCode(basisCode)
                            .setStockCountId(source.getStockCountId()).setStockCountLineId(source.getLineId())
                            .setStockCountVersion(source.getStockCountVersion()).setStockCountLineVersion(source.getLineVersion())
                            .setValuationPolicyId(command.getValuationPolicyId()).setValuationPolicyVersion(command.getValuationPolicyVersion())
                            .setValuationPolicyHash(command.getValuationPolicyHash()).setGainQuantity(scale(source.getDifferenceQuantity()))
                            .setUnitOfMeasure(command.getUnitOfMeasure()).setUnitCostAmountMinor(command.getUnitCostAmountMinor())
                            .setTotalCostAmountMinor(totalCost).setCurrencyCode(command.getCurrencyCode())
                            .setEvidenceSha256(command.getEvidenceSha256()).setStatus("SUBMITTED")
                            .setSubmittedByPrincipalId(context.actor()).setVersion(1L).setCreatedAt(context.now()).setUpdatedAt(context.now())) == 1,
                    "failed to persist stock count gain basis");
            require(mapper.insertGainBasisHistory(UUID.randomUUID().toString(), context.tenantId(), basisId, null,
                            "SUBMITTED", context.actor(), null, 1L, context.now()) == 1,
                    "failed to persist stock count gain basis history");
            return outcome("finance.inventory_control.stock_count.gain_basis_submitted", basisId, "STOCK_COUNT_GAIN_BASIS",
                    source.getStockCountId(), source.getLineId(), basisId, 1L, "SUBMITTED", null, null, null,
                    command.getCurrencyCode(), totalCost);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryControlAccountingResult approveStockCountGainBasis(
            InventoryControlAccountingCommands.ApproveStockCountGainBasis command, String actorPrincipalId) {
        return execute(command.getEnvelope(), "APPROVE_STOCK_COUNT_GAIN_BASIS", actorPrincipalId, command, context -> {
            requireRef(command.getStockCountGainBasisId(), "stockCountGainBasisId");
            StockCountGainBasis basis = nonNull(mapper.selectGainBasis(context.tenantId(), command.getStockCountGainBasisId()),
                    "stock count gain basis not found");
            requireExpectedVersion(command.getExpectedVersion(), basis.getVersion());
            require("SUBMITTED".equals(basis.getStatus()), "stock count gain basis is not in SUBMITTED status");
            require(!context.actor().equals(basis.getSubmittedByPrincipalId()),
                    "stock count gain basis approver must be independent from submitter");
            require(mapper.approveGainBasis(context.tenantId(), basis.getStockCountGainBasisId(), basis.getVersion(),
                            basis.getVersion() + 1, context.actor(), context.now()) == 1,
                    "stock count gain basis approval conflict");
            require(mapper.insertGainBasisHistory(UUID.randomUUID().toString(), context.tenantId(),
                            basis.getStockCountGainBasisId(), "SUBMITTED", "APPROVED", context.actor(), command.getNote(),
                            basis.getVersion() + 1, context.now()) == 1,
                    "failed to persist stock count gain basis approval history");
            return outcome("finance.inventory_control.stock_count.gain_basis_approved", basis.getStockCountGainBasisId(),
                    "STOCK_COUNT_GAIN_BASIS", basis.getStockCountId(), basis.getStockCountLineId(),
                    basis.getStockCountGainBasisId(), basis.getVersion() + 1, "APPROVED", null, null, null,
                    basis.getCurrencyCode(), basis.getTotalCostAmountMinor());
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryControlAccountingResult postStockCountAdjustment(
            InventoryControlAccountingCommands.PostStockCountAdjustment command, String actorPrincipalId) {
        return execute(command.getEnvelope(), "POST_STOCK_COUNT_ADJUSTMENT", actorPrincipalId, command, context -> {
            requireRef(command.getStockCountId(), "stockCountId");
            requireRef(command.getStockCountLineId(), "stockCountLineId");
            requireRef(command.getLedgerId(), "ledgerId");
            requireRef(command.getAccountingPeriodId(), "accountingPeriodId");
            require(command.getAccountingDate() != null, "accountingDate is required");
            requireRef(command.getPostingRuleId(), "postingRuleId");
            require(command.getPostingRuleVersion() != null && command.getPostingRuleVersion() > 0,
                    "postingRuleVersion must be positive");
            requireSha256(command.getPostingEvidenceSha256(), "postingEvidenceSha256");

            StockCountSource source = nonNull(mapper.selectStockCountSourceForUpdate(context.tenantId(),
                    command.getStockCountId(), command.getStockCountLineId()), "stock count line not found");
            requireExpectedVersion(command.getExpectedStockCountVersion(), source.getStockCountVersion());
            requireExpectedVersion(command.getExpectedLineVersion(), source.getLineVersion());
            require(Set.of("ADJUSTED", "COMPLETED").contains(source.getStockCountStatus()),
                    "stock count must be ADJUSTED or COMPLETED before finance posting");
            require("ADJUSTED".equals(source.getLineStatus()), "stock count line must be ADJUSTED before finance posting");
            require(source.getAdjustmentId() != null && source.getAdjustmentLedgerTransactionId() != null,
                    "stock count line has no exact inventory adjustment evidence");
            require(source.getDifferenceQuantity() != null && source.getDifferenceQuantity().signum() != 0,
                    "stock count difference quantity must be non-zero for finance posting");
            return source.getDifferenceQuantity().signum() > 0
                    ? postStockCountGain(context, command, source)
                    : postStockCountShrinkage(context, command, source);
        });
    }

    private Outcome postStockCountShrinkage(Context context, InventoryControlAccountingCommands.PostStockCountAdjustment command,
                                            StockCountSource source) {
        InventoryControlPosting existing = mapper.selectPostingBySourceForUpdate(context.tenantId(),
                "STOCK_COUNT_ADJUSTMENT", source.getAdjustmentId());
        if (existing != null) return duplicateOutcome(existing);
        Ledger ledger = requireLedger(context.tenantId(), command.getLedgerId());
        AccountingPeriod period = requireOpenPeriod(context.tenantId(), command.getAccountingPeriodId(), command.getAccountingDate());
        require(Objects.equals(ledger.getFunctionalCurrencyCode(), period.getCurrencyCode()),
                "ledger currency must equal accounting period currency");
        List<LayerAllocation> allocations = allocateLayers(context.tenantId(), command.getLedgerId(), source.getOwnerType(),
                source.getOwnerId(), source.getCanonicalSkuId(), source.getLotId(), source.getDifferenceQuantity().abs(),
                ledger.getFunctionalCurrencyCode());
        long totalAmount = allocations.stream().mapToLong(LayerAllocation::allocatedCostAmountMinor).sum();
        require(totalAmount > 0, "stock count shrinkage valuation amount must be positive");
        Map<String, PostingAccount> accounts = postingAccounts(context.tenantId(), command.getPostingRuleId(),
                command.getPostingRuleVersion(), command.getLedgerId(), "STOCK_COUNT_ADJUSTMENT",
                Set.of("INVENTORY", "INVENTORY_LOSS"));
        String postingId = UUID.randomUUID().toString();
        String journalId = valueOrUuid(command.getJournalEntryId());
        String journalCode = command.getJournalCode() == null || command.getJournalCode().isBlank()
                ? "STK-" + source.getStockCountCode() + "-" + source.getLineNumber()
                : command.getJournalCode().trim();
        postJournal(context, ledger.getLegalEntityId(), command.getLedgerId(), command.getAccountingPeriodId(),
                command.getAccountingDate(), "STOCK_COUNT_ADJUSTMENT", source.getAdjustmentId(), ledger.getFunctionalCurrencyCode(),
                totalAmount, command.getPostingEvidenceSha256(), journalId, journalCode,
                journalLines(context, journalId, accounts.get("INVENTORY_LOSS"), accounts.get("INVENTORY"), totalAmount,
                        ledger.getFunctionalCurrencyCode()), "STOCK_COUNT_POSTING", command.getJournalDimensions());
        applyAllocations(context, allocations);
        require(mapper.insertPosting(new InventoryControlPosting()
                        .setInventoryControlPostingId(postingId).setTenantId(context.tenantId())
                        .setSourceType("STOCK_COUNT_ADJUSTMENT").setSourceDocumentId(source.getStockCountId())
                        .setSourceLineId(source.getLineId()).setSourceReferenceId(source.getAdjustmentId())
                        .setSourceDocumentVersion(source.getStockCountVersion()).setSourceLineVersion(source.getLineVersion())
                        .setInventoryLedgerTransactionId(source.getAdjustmentLedgerTransactionId()).setLegalEntityId(ledger.getLegalEntityId())
                        .setLedgerId(command.getLedgerId()).setAccountingPeriodId(command.getAccountingPeriodId())
                        .setAccountingDate(command.getAccountingDate()).setPostingRuleId(command.getPostingRuleId())
                        .setPostingRuleVersion(command.getPostingRuleVersion()).setQuantity(source.getDifferenceQuantity().abs())
                        .setUnitOfMeasure(source.getBaseUomCode()).setCurrencyCode(ledger.getFunctionalCurrencyCode())
                        .setTotalAmountMinor(totalAmount).setJournalEntryId(journalId).setJournalCode(journalCode)
                        .setPostingEvidenceSha256(command.getPostingEvidenceSha256()).setStatus("POSTED")
                        .setCreatedByPrincipalId(context.actor()).setVersion(1L).setCreatedAt(context.now()).setUpdatedAt(context.now())) == 1,
                "failed to persist stock count finance posting");
        insertAllocationRows(context, postingId, allocations);
        return outcome("finance.inventory_control.stock_count.posted", postingId, "STOCK_COUNT_ADJUSTMENT",
                source.getStockCountId(), source.getLineId(), source.getAdjustmentId(), 1L, "POSTED",
                journalId, journalCode, null, ledger.getFunctionalCurrencyCode(), totalAmount);
    }

    private Outcome postStockCountGain(Context context, InventoryControlAccountingCommands.PostStockCountAdjustment command,
                                       StockCountSource source) {
        StockCountGainBasis basis = nonNull(mapper.selectGainBasisByLineForUpdate(context.tenantId(), source.getLineId(),
                source.getLineVersion()), "approved stock count gain basis not found");
        require("APPROVED".equals(basis.getStatus()), "stock count gain basis must be APPROVED before posting");
        require(scale(basis.getGainQuantity()).compareTo(scale(source.getDifferenceQuantity())) == 0,
                "stock count gain basis quantity does not match exact stock count difference");
        InventoryControlPosting existing = mapper.selectPostingBySourceForUpdate(context.tenantId(),
                "STOCK_COUNT_ADJUSTMENT", basis.getStockCountGainBasisId());
        if (existing != null) return duplicateOutcome(existing);
        Ledger ledger = requireLedger(context.tenantId(), command.getLedgerId());
        AccountingPeriod period = requireOpenPeriod(context.tenantId(), command.getAccountingPeriodId(), command.getAccountingDate());
        require(Objects.equals(ledger.getFunctionalCurrencyCode(), period.getCurrencyCode())
                        && Objects.equals(ledger.getFunctionalCurrencyCode(), basis.getCurrencyCode()),
                "ledger currency must equal gain basis and accounting period currency");
        Map<String, PostingAccount> accounts = postingAccounts(context.tenantId(), command.getPostingRuleId(),
                command.getPostingRuleVersion(), command.getLedgerId(), "STOCK_COUNT_ADJUSTMENT",
                Set.of("INVENTORY", "INVENTORY_GAIN"));
        String postingId = UUID.randomUUID().toString();
        String journalId = valueOrUuid(command.getJournalEntryId());
        String journalCode = command.getJournalCode() == null || command.getJournalCode().isBlank()
                ? "STKG-" + source.getStockCountCode() + "-" + source.getLineNumber()
                : command.getJournalCode().trim();
        postJournal(context, ledger.getLegalEntityId(), command.getLedgerId(), command.getAccountingPeriodId(),
                command.getAccountingDate(), "STOCK_COUNT_ADJUSTMENT", basis.getStockCountGainBasisId(),
                basis.getCurrencyCode(), basis.getTotalCostAmountMinor(), command.getPostingEvidenceSha256(), journalId,
                journalCode, journalLines(context, journalId, accounts.get("INVENTORY"), accounts.get("INVENTORY_GAIN"),
                        basis.getTotalCostAmountMinor(), basis.getCurrencyCode()), "STOCK_COUNT_GAIN_POSTING",
                command.getJournalDimensions());
        require(mapper.insertControlValuationLayer(new InventoryControlValuationLayer()
                        .setValuationLayerId(UUID.randomUUID().toString()).setTenantId(context.tenantId())
                        .setValuationLayerSourceType("STOCK_COUNT_GAIN").setLedgerId(command.getLedgerId())
                        .setSourceDocumentId(source.getStockCountId()).setSourceLineId(source.getLineId())
                        .setSourceReferenceId(basis.getStockCountGainBasisId())
                        .setInventoryLedgerTransactionId(source.getAdjustmentLedgerTransactionId()).setOwnerType(source.getOwnerType())
                        .setOwnerId(source.getOwnerId()).setCanonicalSkuId(source.getCanonicalSkuId()).setLotId(source.getLotId())
                        .setValuationPolicyId(basis.getValuationPolicyId()).setValuationPolicyVersion(basis.getValuationPolicyVersion())
                        .setValuationPolicyHash(basis.getValuationPolicyHash()).setQuantity(scale(basis.getGainQuantity()))
                        .setUnitOfMeasure(basis.getUnitOfMeasure()).setUnitCostAmountMinor(basis.getUnitCostAmountMinor())
                        .setTotalCostAmountMinor(basis.getTotalCostAmountMinor()).setCurrencyCode(basis.getCurrencyCode())
                        .setRemainingQuantity(scale(basis.getGainQuantity())).setRemainingCostAmountMinor(basis.getTotalCostAmountMinor())
                        .setStatus("OPEN").setVersion(1L).setCreatedAt(context.now()).setUpdatedAt(context.now())) == 1,
                "failed to persist stock count gain valuation layer");
        require(mapper.insertPosting(new InventoryControlPosting()
                        .setInventoryControlPostingId(postingId).setTenantId(context.tenantId())
                        .setSourceType("STOCK_COUNT_ADJUSTMENT").setSourceDocumentId(source.getStockCountId())
                        .setSourceLineId(source.getLineId()).setSourceReferenceId(basis.getStockCountGainBasisId())
                        .setSourceDocumentVersion(source.getStockCountVersion()).setSourceLineVersion(source.getLineVersion())
                        .setInventoryLedgerTransactionId(source.getAdjustmentLedgerTransactionId()).setLegalEntityId(ledger.getLegalEntityId())
                        .setLedgerId(command.getLedgerId()).setAccountingPeriodId(command.getAccountingPeriodId())
                        .setAccountingDate(command.getAccountingDate()).setPostingRuleId(command.getPostingRuleId())
                        .setPostingRuleVersion(command.getPostingRuleVersion()).setQuantity(scale(basis.getGainQuantity()))
                        .setUnitOfMeasure(basis.getUnitOfMeasure()).setCurrencyCode(basis.getCurrencyCode())
                        .setTotalAmountMinor(basis.getTotalCostAmountMinor()).setJournalEntryId(journalId).setJournalCode(journalCode)
                        .setPostingEvidenceSha256(command.getPostingEvidenceSha256()).setStatus("POSTED")
                        .setCreatedByPrincipalId(context.actor()).setVersion(1L).setCreatedAt(context.now()).setUpdatedAt(context.now())) == 1,
                "failed to persist stock count gain finance posting");
        return outcome("finance.inventory_control.stock_count.gain_posted", postingId, "STOCK_COUNT_ADJUSTMENT",
                source.getStockCountId(), source.getLineId(), basis.getStockCountGainBasisId(), 1L, "POSTED",
                journalId, journalCode, null, basis.getCurrencyCode(), basis.getTotalCostAmountMinor());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryControlAccountingResult postInventoryScrap(
            InventoryControlAccountingCommands.PostInventoryScrap command, String actorPrincipalId) {
        return execute(command.getEnvelope(), "POST_INVENTORY_SCRAP", actorPrincipalId, command, context -> {
            requireRef(command.getScrapId(), "scrapId");
            requireRef(command.getScrapLineId(), "scrapLineId");
            requireRef(command.getDispositionLineId(), "dispositionLineId");
            requireRef(command.getLedgerId(), "ledgerId");
            requireRef(command.getAccountingPeriodId(), "accountingPeriodId");
            require(command.getAccountingDate() != null, "accountingDate is required");
            requireRef(command.getPostingRuleId(), "postingRuleId");
            require(command.getPostingRuleVersion() != null && command.getPostingRuleVersion() > 0,
                    "postingRuleVersion must be positive");
            requireSha256(command.getPostingEvidenceSha256(), "postingEvidenceSha256");

            InventoryScrapSource source = nonNull(mapper.selectInventoryScrapSourceForUpdate(context.tenantId(),
                    command.getScrapId(), command.getScrapLineId(), command.getDispositionLineId()),
                    "inventory scrap disposition line not found");
            requireExpectedVersion(command.getExpectedScrapVersion(), source.getScrapVersion());
            requireExpectedVersion(command.getExpectedLineVersion(), source.getLineVersion());
            require(Set.of("PARTIALLY_DISPOSED", "DISPOSED", "COMPLETED").contains(source.getScrapStatus()),
                    "inventory scrap must be disposed before finance posting");
            require(Set.of("PARTIALLY_DISPOSED", "DISPOSED", "COMPLETED").contains(source.getLineStatus()),
                    "inventory scrap line must be disposed before finance posting");
            require(source.getInventoryLedgerTransactionId() != null, "inventory scrap line has no exact inventory disposition evidence");

            InventoryControlPosting existing = mapper.selectPostingBySourceForUpdate(context.tenantId(),
                    "INVENTORY_SCRAP", source.getDispositionLineId());
            if (existing != null) {
                return duplicateOutcome(existing);
            }

            Ledger ledger = requireLedger(context.tenantId(), command.getLedgerId());
            AccountingPeriod period = requireOpenPeriod(context.tenantId(), command.getAccountingPeriodId(),
                    command.getAccountingDate());
            require(Objects.equals(ledger.getFunctionalCurrencyCode(), period.getCurrencyCode()),
                    "ledger currency must equal accounting period currency");
            List<LayerAllocation> allocations = allocateLayers(context.tenantId(), command.getLedgerId(),
                    source.getOwnerType(), source.getOwnerId(), source.getCanonicalSkuId(), source.getLotId(),
                    source.getDisposedQuantity(), ledger.getFunctionalCurrencyCode());
            long totalAmount = allocations.stream().mapToLong(LayerAllocation::allocatedCostAmountMinor).sum();
            require(totalAmount > 0, "inventory scrap valuation amount must be positive");
            Map<String, PostingAccount> accounts = postingAccounts(context.tenantId(), command.getPostingRuleId(),
                    command.getPostingRuleVersion(), command.getLedgerId(), "INVENTORY_SCRAP",
                    Set.of("INVENTORY", "SCRAP_EXPENSE"));
            String postingId = UUID.randomUUID().toString();
            String journalId = valueOrUuid(command.getJournalEntryId());
            String journalCode = command.getJournalCode() == null || command.getJournalCode().isBlank()
                    ? "SCR-" + source.getScrapCode() + "-" + source.getLineNumber()
                    : command.getJournalCode().trim();
            postJournal(context, ledger.getLegalEntityId(), command.getLedgerId(), command.getAccountingPeriodId(),
                    command.getAccountingDate(), "INVENTORY_SCRAP", source.getDispositionLineId(),
                    ledger.getFunctionalCurrencyCode(), totalAmount, command.getPostingEvidenceSha256(),
                    journalId, journalCode, journalLines(context, journalId, accounts.get("SCRAP_EXPENSE"),
                            accounts.get("INVENTORY"), totalAmount, ledger.getFunctionalCurrencyCode()),
                    "INVENTORY_SCRAP_POSTING", command.getJournalDimensions());
            applyAllocations(context, allocations);
            InventoryControlPosting posting = new InventoryControlPosting()
                    .setInventoryControlPostingId(postingId)
                    .setTenantId(context.tenantId())
                    .setSourceType("INVENTORY_SCRAP")
                    .setSourceDocumentId(source.getScrapId())
                    .setSourceLineId(source.getLineId())
                    .setSourceReferenceId(source.getDispositionLineId())
                    .setSourceDocumentVersion(source.getScrapVersion())
                    .setSourceLineVersion(source.getLineVersion())
                    .setInventoryLedgerTransactionId(source.getInventoryLedgerTransactionId())
                    .setLegalEntityId(ledger.getLegalEntityId())
                    .setLedgerId(command.getLedgerId())
                    .setAccountingPeriodId(command.getAccountingPeriodId())
                    .setAccountingDate(command.getAccountingDate())
                    .setPostingRuleId(command.getPostingRuleId())
                    .setPostingRuleVersion(command.getPostingRuleVersion())
                    .setQuantity(source.getDisposedQuantity())
                    .setUnitOfMeasure(source.getBaseUomCode())
                    .setCurrencyCode(ledger.getFunctionalCurrencyCode())
                    .setTotalAmountMinor(totalAmount)
                    .setJournalEntryId(journalId)
                    .setJournalCode(journalCode)
                    .setPostingEvidenceSha256(command.getPostingEvidenceSha256())
                    .setStatus("POSTED")
                    .setCreatedByPrincipalId(context.actor())
                    .setVersion(1L)
                    .setCreatedAt(context.now())
                    .setUpdatedAt(context.now());
            require(mapper.insertPosting(posting) == 1, "failed to persist inventory scrap finance posting");
            insertAllocationRows(context, postingId, allocations);
            return outcome("finance.inventory_control.scrap.posted", postingId, "INVENTORY_SCRAP",
                    source.getScrapId(), source.getLineId(), source.getDispositionLineId(), 1L, "POSTED",
                    journalId, journalCode, null, ledger.getFunctionalCurrencyCode(), totalAmount);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryControlAccountingResult reverse(InventoryControlAccountingCommands.Reverse command,
                                                    String actorPrincipalId) {
        return execute(command.getEnvelope(), "REVERSE_INVENTORY_CONTROL_JOURNAL", actorPrincipalId, command, context -> {
            requireRef(command.getInventoryControlPostingId(), "inventoryControlPostingId");
            requireRef(command.getReversalJournalCode(), "reversalJournalCode");
            requireRef(command.getAccountingPeriodId(), "accountingPeriodId");
            require(command.getAccountingDate() != null, "accountingDate is required");
            requireSha256(command.getReversalEvidenceSha256(), "reversalEvidenceSha256");
            InventoryControlPosting posting = nonNull(mapper.selectPostingForUpdate(context.tenantId(),
                    command.getInventoryControlPostingId()), "inventory control finance posting not found");
            requireExpectedVersion(command.getExpectedVersion(), posting.getVersion());
            require("POSTED".equals(posting.getStatus()), "only POSTED inventory control finance entry can be reversed");
            require(posting.getReversalJournalEntryId() == null, "inventory control finance posting already reversed");

            JournalEntry original = nonNull(mapper.selectJournalForUpdate(context.tenantId(), posting.getJournalEntryId()),
                    "posted finance journal entry not found");
            require("POSTED".equals(original.getStatus()), "only posted journal entry can be reversed");
            require(!context.actor().equals(original.getPostedByPrincipalId()),
                    "journal reverser must be independent from original poster");
            require(mapper.countJournalReversal(context.tenantId(), original.getJournalEntryId()) == 0,
                    "posted journal entry already has a reversal");
            requireOpenPeriod(context.tenantId(), command.getAccountingPeriodId(), command.getAccountingDate());

            List<JournalLine> originalLines = mapper.selectJournalLines(context.tenantId(), original.getJournalEntryId());
            require(!originalLines.isEmpty(), "journal entry has no immutable lines");
            String reversalId = valueOrUuid(command.getReversalJournalEntryId());
            List<JournalLine> reversalLines = new ArrayList<>();
            for (JournalLine line : originalLines) {
                reversalLines.add(new JournalLine().setJournalLineId(UUID.randomUUID().toString())
                        .setTenantId(context.tenantId()).setJournalEntryId(reversalId).setLineNumber(line.getLineNumber())
                        .setLedgerId(line.getLedgerId()).setAccountId(line.getAccountId()).setAccountCode(line.getAccountCode())
                        .setDebitAmountMinor(line.getCreditAmountMinor()).setCreditAmountMinor(line.getDebitAmountMinor())
                        .setTransactionCurrencyCode(line.getTransactionCurrencyCode())
                        .setTransactionAmountMinor(line.getTransactionAmountMinor()).setSupplierId(line.getSupplierId())
                        .setSupplierInvoiceId(line.getSupplierInvoiceId()).setApOpenItemId(line.getApOpenItemId())
                        .setPurchaseOrderId(line.getPurchaseOrderId()).setPurchaseOrderItemId(line.getPurchaseOrderItemId())
                        .setReceiptLineId(line.getReceiptLineId()).setInventoryMovementId(line.getInventoryMovementId())
                        .setCreatedAt(context.now()));
            }
            postJournal(context, original.getLegalEntityId(), original.getLedgerId(), command.getAccountingPeriodId(),
                    command.getAccountingDate(), "JOURNAL_REVERSAL", original.getJournalEntryId(),
                    original.getCurrencyCode(), original.getDebitTotalMinor(), command.getReversalEvidenceSha256(),
                    reversalId, command.getReversalJournalCode(), reversalLines, "REVERSAL", List.of());
            for (int i = 0; i < originalLines.size(); i++) {
                for (JournalDimensionAssignment dimension : mapper.selectJournalLineDimensions(context.tenantId(),
                        originalLines.get(i).getJournalLineId())) {
                    require(mapper.insertJournalLineDimension(UUID.randomUUID().toString(), context.tenantId(),
                                    reversalLines.get(i).getJournalLineId(), dimension.getDimensionTypeId(),
                                    dimension.getDimensionValueId(), context.now()) == 1,
                            "failed to copy immutable journal dimension to reversal");
                }
            }
            require(mapper.insertJournalReversalLink(UUID.randomUUID().toString(), context.tenantId(),
                    original.getJournalEntryId(), reversalId, command.getReversalEvidenceSha256(), context.actor(),
                    context.now()) == 1, "failed to persist journal reversal link");

            for (InventoryControlPostingAllocation allocation : mapper.selectPostingAllocations(context.tenantId(),
                    posting.getInventoryControlPostingId())) {
                ValuationLayerCandidate layer = nonNull(
                        "INVENTORY_CONTROL_GAIN".equals(allocation.getValuationLayerSourceType())
                                ? mapper.selectControlValuationLayerForUpdate(context.tenantId(), allocation.getValuationLayerId())
                                : mapper.selectValuationLayerForUpdate(context.tenantId(), allocation.getValuationLayerId()),
                        "valuation layer referenced by posting disappeared");
                BigDecimal restoredQuantity = layer.getRemainingQuantity().add(allocation.getAllocatedQuantity());
                long restoredCost = Math.addExact(layer.getRemainingCostAmountMinor(), allocation.getAllocatedCostAmountMinor());
                int updated = "INVENTORY_CONTROL_GAIN".equals(allocation.getValuationLayerSourceType())
                        ? mapper.updateControlValuationLayerRemainingCas(context.tenantId(), layer.getValuationLayerId(),
                        layer.getVersion(), layer.getVersion() + 1, restoredQuantity, restoredCost, "OPEN", context.now())
                        : mapper.updateValuationLayerRemainingCas(context.tenantId(), layer.getValuationLayerId(),
                        layer.getVersion(), layer.getVersion() + 1, restoredQuantity, restoredCost, "OPEN", context.now());
                require(updated == 1,
                        "valuation layer restore conflict");
            }
            require(mapper.markPostingReversed(context.tenantId(), posting.getInventoryControlPostingId(),
                            posting.getVersion(), posting.getVersion() + 1, reversalId, context.actor(), context.now()) == 1,
                    "inventory control finance reversal conflict");
            return outcome("finance.inventory_control.journal.reversed", posting.getInventoryControlPostingId(),
                    posting.getSourceType(), posting.getSourceDocumentId(), posting.getSourceLineId(),
                    posting.getSourceReferenceId(), posting.getVersion() + 1, "REVERSED",
                    posting.getJournalEntryId(), posting.getJournalCode(), reversalId, posting.getCurrencyCode(),
                    posting.getTotalAmountMinor());
        });
    }

    private void applyAllocations(Context context, List<LayerAllocation> allocations) {
        for (LayerAllocation allocation : allocations) {
            int updated = "INVENTORY_CONTROL_GAIN".equals(allocation.layer().getValuationLayerSourceType())
                    ? mapper.updateControlValuationLayerRemainingCas(context.tenantId(), allocation.layer().getValuationLayerId(),
                    allocation.layer().getVersion(), allocation.layer().getVersion() + 1, allocation.remainingQuantity(),
                    allocation.remainingCostAmountMinor(), allocation.remainingQuantity().signum() == 0 ? "CONSUMED" : "OPEN",
                    context.now())
                    : mapper.updateValuationLayerRemainingCas(context.tenantId(), allocation.layer().getValuationLayerId(),
                    allocation.layer().getVersion(), allocation.layer().getVersion() + 1, allocation.remainingQuantity(),
                    allocation.remainingCostAmountMinor(), allocation.remainingQuantity().signum() == 0 ? "CONSUMED" : "OPEN",
                    context.now());
            require(updated == 1,
                    "inventory valuation layer consumption conflict");
        }
    }

    private void insertAllocationRows(Context context, String postingId, List<LayerAllocation> allocations) {
        int sequence = 1;
        for (LayerAllocation allocation : allocations) {
            require(mapper.insertPostingAllocation(new InventoryControlPostingAllocation()
                    .setAllocationId(UUID.randomUUID().toString())
                    .setTenantId(context.tenantId())
                    .setInventoryControlPostingId(postingId)
                    .setSequenceNo(sequence++)
                    .setValuationLayerSourceType(allocation.layer().getValuationLayerSourceType())
                    .setValuationLayerId(allocation.layer().getValuationLayerId())
                    .setAllocatedQuantity(allocation.allocatedQuantity())
                    .setAllocatedCostAmountMinor(allocation.allocatedCostAmountMinor())
                            .setCreatedAt(context.now())) == 1,
                    "failed to persist inventory valuation allocation");
        }
    }

    private List<LayerAllocation> allocateLayers(Long tenantId, String ledgerId, String ownerType, String ownerId,
                                                 String canonicalSkuId, String lotId, BigDecimal quantity,
                                                 String expectedCurrency) {
        List<ValuationLayerCandidate> layers = new ArrayList<>();
        layers.addAll(mapper.selectOpenValuationLayersForSource(tenantId, ledgerId, ownerType, ownerId, canonicalSkuId, lotId));
        layers.addAll(mapper.selectOpenControlValuationLayersForSource(tenantId, ledgerId, ownerType, ownerId, canonicalSkuId, lotId));
        layers.sort(Comparator.comparing(ValuationLayerCandidate::getCreatedAt).thenComparing(ValuationLayerCandidate::getValuationLayerId));
        require(!layers.isEmpty(), "no open finance valuation layer matches the inventory control source");
        BigDecimal remaining = scale(quantity);
        List<LayerAllocation> allocations = new ArrayList<>();
        for (ValuationLayerCandidate layer : layers) {
            if (remaining.signum() == 0) {
                break;
            }
            require(expectedCurrency.equals(layer.getCurrencyCode()),
                    "valuation layer currency must equal ledger functional currency");
            BigDecimal layerRemaining = scale(layer.getRemainingQuantity());
            if (layerRemaining.signum() <= 0) {
                continue;
            }
            BigDecimal allocatedQuantity = layerRemaining.min(remaining);
            long allocatedCost = allocatedCost(layerRemaining, layer.getRemainingCostAmountMinor(), allocatedQuantity);
            allocations.add(new LayerAllocation(layer, allocatedQuantity, allocatedCost,
                    layerRemaining.subtract(allocatedQuantity).setScale(8, RoundingMode.HALF_UP),
                    Math.subtractExact(layer.getRemainingCostAmountMinor(), allocatedCost)));
            remaining = remaining.subtract(allocatedQuantity).setScale(8, RoundingMode.HALF_UP);
        }
        require(remaining.signum() == 0, "open finance valuation layers cannot absorb the requested inventory quantity");
        return allocations;
    }

    private Map<String, PostingAccount> postingAccounts(Long tenantId, String ruleId, Long ruleVersion,
                                                        String ledgerId, String sourceType,
                                                        Set<String> requiredRoles) {
        Map<String, PostingAccount> accounts = new HashMap<>();
        for (PostingAccount account : mapper.selectPostingAccounts(tenantId, ruleId, ruleVersion, ledgerId, sourceType)) {
            require(accounts.put(account.getAccountRole(), account) == null,
                    "posting rule contains duplicate account role");
        }
        require(accounts.keySet().containsAll(requiredRoles),
                "posting rule is missing required account roles: " + requiredRoles);
        return accounts;
    }

    private AccountingPeriod requireOpenPeriod(Long tenantId, String periodId, java.time.LocalDate accountingDate) {
        AccountingPeriod period = nonNull(mapper.selectPeriodForUpdate(tenantId, periodId), "accounting period not found");
        require("OPEN".equals(period.getStatus()), "accounting period is not open");
        require(!accountingDate.isBefore(period.getPeriodStart()) && !accountingDate.isAfter(period.getPeriodEnd()),
                "accounting date is outside accounting period");
        return period;
    }

    private Ledger requireLedger(Long tenantId, String ledgerId) {
        Ledger ledger = nonNull(mapper.selectLedgerForUpdate(tenantId, ledgerId), "ledger not found");
        require("ACTIVE".equals(ledger.getStatus()), "ledger is not active");
        requireCurrency(ledger.getFunctionalCurrencyCode());
        return ledger;
    }

    private List<JournalLine> journalLines(Context context, String journalId, PostingAccount debit, PostingAccount credit,
                                           long amountMinor, String currency) {
        return List.of(
                journalLine(context, journalId, 1, debit, amountMinor, 0L, currency),
                journalLine(context, journalId, 2, credit, 0L, amountMinor, currency));
    }

    private JournalLine journalLine(Context context, String journalId, int lineNumber, PostingAccount account,
                                    long debitAmountMinor, long creditAmountMinor, String currency) {
        return new JournalLine().setJournalLineId(UUID.randomUUID().toString()).setTenantId(context.tenantId())
                .setJournalEntryId(journalId).setLineNumber(lineNumber).setLedgerId(account.getLedgerId())
                .setAccountId(account.getAccountId()).setAccountCode(account.getAccountCode())
                .setDebitAmountMinor(debitAmountMinor).setCreditAmountMinor(creditAmountMinor)
                .setTransactionCurrencyCode(currency)
                .setTransactionAmountMinor(Math.max(debitAmountMinor, creditAmountMinor))
                .setCreatedAt(context.now());
    }

    private void postJournal(Context context, String legalEntityId, String ledgerId, String periodId,
                             java.time.LocalDate accountingDate, String sourceType, String sourceId, String currency,
                             long total, String evidence, String journalId, String journalCode, List<JournalLine> lines,
                             String effectType, List<JournalDimensionAssignment> dimensions) {
        long debit = lines.stream().mapToLong(JournalLine::getDebitAmountMinor).sum();
        long credit = lines.stream().mapToLong(JournalLine::getCreditAmountMinor).sum();
        require(total > 0 && debit == total && credit == total, "posted journal lines must balance to source total");
        JournalEntry journal = new JournalEntry().setJournalEntryId(journalId).setTenantId(context.tenantId())
                .setJournalCode(journalCode).setLegalEntityId(legalEntityId).setLedgerId(ledgerId).setPeriodId(periodId)
                .setAccountingDate(accountingDate).setSourceType(sourceType).setSourceId(sourceId).setCurrencyCode(currency)
                .setDocumentCurrencyCode(currency).setDebitTotalMinor(total).setCreditTotalMinor(total)
                .setEvidenceSha256(evidence).setStatus("POSTED").setPreparedByPrincipalId(context.actor())
                .setPostedByPrincipalId(context.actor()).setVersion(1L).setPreparedAt(context.now()).setPostedAt(context.now())
                .setCreatedAt(context.now()).setUpdatedAt(context.now());
        require(mapper.insertJournalEntry(journal) == 1, "failed to persist posted journal header");
        List<JournalDimensionAssignment> safeDimensions = dimensions == null ? List.of() : dimensions;
        Set<String> dimensionTypes = new HashSet<>();
        for (JournalDimensionAssignment dimension : safeDimensions) {
            require(dimension != null && dimensionTypes.add(dimension.getDimensionTypeId()),
                    "journal dimension type must be unique");
            require(mapper.countActiveDimensionValue(context.tenantId(), dimension.getDimensionTypeId(),
                    dimension.getDimensionValueId()) == 1, "journal dimension value is not active for type");
        }
        for (JournalLine line : lines) {
            require(mapper.insertJournalLine(line) == 1, "failed to persist immutable journal line");
            for (JournalDimensionAssignment dimension : safeDimensions) {
                require(mapper.insertJournalLineDimension(UUID.randomUUID().toString(), context.tenantId(),
                                line.getJournalLineId(), dimension.getDimensionTypeId(), dimension.getDimensionValueId(),
                                context.now()) == 1,
                        "failed to persist journal dimension");
            }
        }
        require(mapper.insertJournalSourceEffect(UUID.randomUUID().toString(), context.tenantId(), sourceType, sourceId,
                        effectType, journalId, context.now()) == 1,
                "failed to persist journal source effect");
        require(mapper.insertJournalHistory(UUID.randomUUID().toString(), context.tenantId(), journalId, null, "POSTED",
                        context.actor(), null, 1L, context.now()) == 1,
                "failed to persist journal history");
    }

    private InventoryControlAccountingResult execute(FinanceCommandEnvelope envelope, String commandType,
                                                     String actorPrincipalId, Object command,
                                                     Function<Context, Outcome> action) {
        validateEnvelope(envelope);
        requireRef(actorPrincipalId, "actorPrincipalId");
        actorPrincipalPort.requireActive(actorPrincipalId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.ofInstant(envelope.getOccurredAt(), ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(actorPrincipalId + "\n" + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, envelope.getIdempotencyKey(), commandType, requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve finance operation");
        Operation operation = nonNull(mapper.selectOperationForUpdate(operationId, tenantId), "finance operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with different finance payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing finance operation is incomplete");
            InventoryControlAccountingResult replay = JsonUtils.parseObject(operation.getResultJson(),
                    InventoryControlAccountingResult.class);
            replay.setDuplicate(true);
            return replay;
        }
        Outcome outcome = action.apply(new Context(tenantId, actorPrincipalId, now));
        appendEvent(tenantId, envelope, outcome);
        InventoryControlAccountingResult result = InventoryControlAccountingResult.builder()
                .operationId(operationId)
                .duplicate(outcome.duplicate())
                .inventoryControlPostingId(outcome.inventoryControlPostingId())
                .sourceType(outcome.sourceType())
                .sourceDocumentId(outcome.sourceDocumentId())
                .sourceLineId(outcome.sourceLineId())
                .sourceReferenceId(outcome.sourceReferenceId())
                .aggregateVersion(outcome.version())
                .status(outcome.status())
                .journalEntryId(outcome.journalEntryId())
                .journalCode(outcome.journalCode())
                .reversalJournalEntryId(outcome.reversalJournalEntryId())
                .currencyCode(outcome.currencyCode())
                .totalAmountMinor(outcome.totalAmountMinor())
                .build();
        require(mapper.markOperationSucceeded(operationId, tenantId, "finance_inventory_control_posting",
                        outcome.inventoryControlPostingId(), JsonUtils.toJsonString(result), now) == 1,
                "finance operation completion conflict");
        return result;
    }

    private void appendEvent(Long tenantId, FinanceCommandEnvelope envelope, Outcome outcome) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inventory_control_posting_id", outcome.inventoryControlPostingId());
        payload.put("source_type", outcome.sourceType());
        payload.put("source_document_id", outcome.sourceDocumentId());
        payload.put("source_line_id", outcome.sourceLineId());
        payload.put("source_reference_id", outcome.sourceReferenceId());
        payload.put("journal_entry_id", outcome.journalEntryId());
        payload.put("reversal_journal_entry_id", outcome.reversalJournalEntryId());
        payload.put("status", outcome.status());
        payload.put("currency_code", outcome.currencyCode());
        payload.put("total_amount_minor", outcome.totalAmountMinor());
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("run_id", envelope.getRunId());
        headers.put("status", outcome.status());
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(outcome.eventType())
                .schemaVersion(1)
                .sourceSystem(SOURCE_SYSTEM)
                .tenantId(tenantId)
                .aggregateType("finance_inventory_control_posting")
                .aggregateId(outcome.inventoryControlPostingId())
                .aggregateVersion(outcome.version())
                .eventSequence((short) 1)
                .occurredAt(envelope.getOccurredAt())
                .traceId(envelope.getRunId())
                .correlationId(envelope.getCorrelationId())
                .causationId(envelope.getCausationId())
                .idempotencyKey(envelope.getIdempotencyKey() + ":" + outcome.version())
                .payload(payload)
                .headers(headers)
                .destination("lakehouse")
                .build());
    }

    private static InventoryControlAccountingResult duplicate(InventoryControlPosting posting) {
        return InventoryControlAccountingResult.builder()
                .duplicate(true)
                .inventoryControlPostingId(posting.getInventoryControlPostingId())
                .sourceType(posting.getSourceType())
                .sourceDocumentId(posting.getSourceDocumentId())
                .sourceLineId(posting.getSourceLineId())
                .sourceReferenceId(posting.getSourceReferenceId())
                .aggregateVersion(posting.getVersion())
                .status(posting.getStatus())
                .journalEntryId(posting.getJournalEntryId())
                .journalCode(posting.getJournalCode())
                .reversalJournalEntryId(posting.getReversalJournalEntryId())
                .currencyCode(posting.getCurrencyCode())
                .totalAmountMinor(posting.getTotalAmountMinor())
                .build();
    }

    private static Outcome duplicateOutcome(InventoryControlPosting posting) {
        return new Outcome("finance.inventory_control.duplicate", posting.getInventoryControlPostingId(),
                posting.getSourceType(), posting.getSourceDocumentId(), posting.getSourceLineId(),
                posting.getSourceReferenceId(), posting.getVersion(), posting.getStatus(), posting.getJournalEntryId(),
                posting.getJournalCode(), posting.getReversalJournalEntryId(), posting.getCurrencyCode(),
                posting.getTotalAmountMinor(), true);
    }

    private static Outcome outcome(String eventType, String postingId, String sourceType, String sourceDocumentId,
                                   String sourceLineId, String sourceReferenceId, Long version, String status,
                                   String journalEntryId, String journalCode, String reversalJournalEntryId,
                                   String currencyCode, Long totalAmountMinor) {
        return new Outcome(eventType, postingId, sourceType, sourceDocumentId, sourceLineId, sourceReferenceId,
                version, status, journalEntryId, journalCode, reversalJournalEntryId, currencyCode, totalAmountMinor, false);
    }

    private static void validateEnvelope(FinanceCommandEnvelope envelope) {
        require(envelope != null, "finance command envelope is required");
        requireUuid(envelope.getCorrelationId(), "correlationId");
        if (envelope.getCausationId() != null) {
            requireUuid(envelope.getCausationId(), "causationId");
        }
        requireRef(envelope.getIdempotencyKey(), "idempotencyKey");
        require(envelope.getOccurredAt() != null, "occurredAt is required");
    }

    private static void requireExpectedVersion(Long expected, Long actual) {
        require(expected == null || Objects.equals(expected, actual), "aggregate version changed");
    }

    private static long allocatedCost(BigDecimal remainingQuantity, Long remainingCost, BigDecimal allocatedQuantity) {
        if (allocatedQuantity.compareTo(remainingQuantity) == 0) {
            return remainingCost;
        }
        return BigDecimal.valueOf(remainingCost).multiply(allocatedQuantity)
                .divide(remainingQuantity, 0, RoundingMode.HALF_UP).longValueExact();
    }

    private static BigDecimal scale(BigDecimal value) {
        require(value != null, "quantity is required");
        return value.setScale(8, RoundingMode.UNNECESSARY);
    }

    private static String valueOrUuid(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
    }

    private static void requireCurrency(String value) {
        require(value != null && value.matches("^[A-Z]{3}$"), "currency code is invalid");
    }

    private static void requireSha256(String value, String field) {
        require(value != null && SHA256.matcher(value).matches(), field + " must be a lowercase sha256 hex");
    }

    private static void requireUuid(String value, String field) {
        try {
            UUID.fromString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException(field + " must be a UUID", ex);
        }
    }

    private static void requireRef(String value, String field) {
        require(value != null && SAFE_REF.matcher(value.trim()).matches(), field + " is invalid");
    }

    private static <T> T nonNull(T value, String message) {
        require(value != null, message);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record Context(Long tenantId, String actor, LocalDateTime now) {
    }

    private record Outcome(String eventType, String inventoryControlPostingId, String sourceType,
                           String sourceDocumentId, String sourceLineId, String sourceReferenceId,
                           Long version, String status, String journalEntryId, String journalCode,
                           String reversalJournalEntryId, String currencyCode, Long totalAmountMinor,
                           boolean duplicate) {
    }

    private record LayerAllocation(ValuationLayerCandidate layer, BigDecimal allocatedQuantity,
                                   long allocatedCostAmountMinor, BigDecimal remainingQuantity,
                                   long remainingCostAmountMinor) {
    }
}
