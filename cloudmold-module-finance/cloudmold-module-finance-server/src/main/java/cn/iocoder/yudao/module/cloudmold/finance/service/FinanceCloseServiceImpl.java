package cn.iocoder.yudao.module.cloudmold.finance.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.finance.api.*;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.AccountingPeriod;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.ChannelStatement;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.JournalEntry;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.ReconciliationDifference;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.SettlementBatch;
import cn.iocoder.yudao.module.cloudmold.finance.dal.mysql.FinanceCloseMapper;
import cn.iocoder.yudao.module.cloudmold.finance.service.actor.FinanceActorPrincipalPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FinanceCloseServiceImpl implements FinanceCloseCommandApi {
    private static final int OPERATION_SUCCEEDED = 10;
    private static final String SOURCE_SYSTEM = "cloudmold-finance";
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> RESOLUTION_TYPES =
            Set.of("CHANNEL_ADJUSTMENT", "BUSINESS_ADJUSTMENT", "MANUAL_EVIDENCE");

    private final FinanceCloseMapper mapper;
    private final OutboxAppender outboxAppender;
    private final FinanceActorPrincipalPort actorPrincipalPort;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FinanceCloseResult execute(FinanceCloseCommand command, String actorPrincipalId) {
        validateEnvelope(command);
        requireRef(actorPrincipalId, "actorPrincipalId", 128);
        actorPrincipalPort.requireActive(actorPrincipalId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(actorPrincipalId + "\n" + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve finance operation");
        Operation operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "finance operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with different finance payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing finance operation is incomplete");
            FinanceCloseResult replay = JsonUtils.parseObject(operation.getResultJson(), FinanceCloseResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        Outcome outcome = switch (command.getOperation()) {
            case OPEN_ACCOUNTING_PERIOD -> openPeriod(tenantId, command, actorPrincipalId, now);
            case IMPORT_CHANNEL_STATEMENT -> importStatement(tenantId, command, actorPrincipalId, now);
            case RECONCILE_CHANNEL_STATEMENT ->
                    reconcileStatement(tenantId, command, actorPrincipalId, now);
            case RESOLVE_RECONCILIATION_DIFFERENCE ->
                    resolveDifference(tenantId, command, actorPrincipalId, now);
            case CREATE_SETTLEMENT_BATCH -> createSettlement(tenantId, command, actorPrincipalId, now);
            case CONFIRM_SETTLEMENT -> confirmSettlement(tenantId, command, actorPrincipalId, now);
            case PREPARE_JOURNAL_ENTRY -> prepareJournal(tenantId, command, actorPrincipalId, now);
            case POST_JOURNAL_ENTRY -> postJournal(tenantId, command, actorPrincipalId, now);
            case CLOSE_ACCOUNTING_PERIOD -> closePeriod(tenantId, command, actorPrincipalId, now);
        };
        appendEvent(tenantId, command, outcome);
        FinanceCloseResult result = FinanceCloseResult.builder()
                .operationId(operationId).duplicate(false)
                .aggregateType(outcome.aggregateType()).aggregateId(outcome.aggregateId())
                .aggregateVersion(outcome.version()).status(outcome.status())
                .periodId(text(outcome.payload().get("period_id")))
                .statementId(text(outcome.payload().get("statement_id")))
                .differenceId(text(outcome.payload().get("difference_id")))
                .settlementBatchId(text(outcome.payload().get("settlement_batch_id")))
                .journalEntryId(text(outcome.payload().get("journal_entry_id")))
                .build();
        require(mapper.markOperationSucceeded(operationId, tenantId, outcome.aggregateType(),
                outcome.aggregateId(), JsonUtils.toJsonString(result), now) == 1,
                "finance operation completion conflict");
        return result;
    }

    private Outcome openPeriod(Long tenantId, FinanceCloseCommand command,
                               String actorPrincipalId, LocalDateTime now) {
        FinanceCloseCommand.PeriodDefinition input = nonNull(command.getPeriod(), "period is required");
        String periodId = valueOrUuid(input.getPeriodId());
        requireRef(input.getPeriodCode(), "periodCode", 64);
        require(input.getPeriodStart() != null && input.getPeriodEnd() != null
                        && !input.getPeriodEnd().isBefore(input.getPeriodStart()),
                "periodStart and periodEnd are invalid");
        requireCurrency(input.getCurrencyCode());
        AccountingPeriod row = new AccountingPeriod()
                .setPeriodId(periodId).setTenantId(tenantId).setPeriodCode(input.getPeriodCode())
                .setPeriodStart(input.getPeriodStart()).setPeriodEnd(input.getPeriodEnd())
                .setCurrencyCode(upper(input.getCurrencyCode())).setStatus("OPEN")
                .setOpenedByPrincipalId(actorPrincipalId)
                .setReasonCode(reason(input.getReasonCode())).setVersion(1L)
                .setOpenedAt(now).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertPeriod(row) == 1, "failed to persist accounting period");
        return outcome("finance.accounting_period.opened", "finance_accounting_period",
                periodId, 1L, "OPEN",
                payload("period_id", periodId, "period_code", row.getPeriodCode(),
                        "period_start", row.getPeriodStart(), "period_end", row.getPeriodEnd(),
                        "currency_code", row.getCurrencyCode(), "current_status", "OPEN"));
    }

    private Outcome importStatement(Long tenantId, FinanceCloseCommand command,
                                    String actorPrincipalId, LocalDateTime now) {
        FinanceCloseCommand.StatementDefinition input =
                nonNull(command.getStatement(), "statement is required");
        String statementId = valueOrUuid(input.getStatementId());
        requireRef(input.getStatementCode(), "statementCode", 64);
        requireRef(input.getPeriodId(), "periodId", 128);
        requireCode(input.getChannelCode(), "channelCode");
        require(input.getStatementDate() != null, "statementDate is required");
        requireCurrency(input.getCurrencyCode());
        requireNonNegative(input.getGrossAmountMinor(), "grossAmountMinor");
        requireNonNegative(input.getRefundAmountMinor(), "refundAmountMinor");
        requireNonNegative(input.getFeeAmountMinor(), "feeAmountMinor");
        requireNonNegative(input.getNetSettlementAmountMinor(), "netSettlementAmountMinor");
        requireNonNegative(input.getExpectedBusinessNetAmountMinor(), "expectedBusinessNetAmountMinor");
        requireSha256(input.getEvidenceSha256(), "evidenceSha256");
        long calculatedNet = Math.subtractExact(
                Math.subtractExact(input.getGrossAmountMinor(), input.getRefundAmountMinor()),
                input.getFeeAmountMinor());
        require(calculatedNet == input.getNetSettlementAmountMinor(),
                "net settlement must equal gross minus refunds and fees");
        AccountingPeriod period = requireOpenPeriod(tenantId, input.getPeriodId());
        require(period.getCurrencyCode().equals(upper(input.getCurrencyCode())),
                "statement currency does not match accounting period");
        require(!input.getStatementDate().isBefore(period.getPeriodStart())
                        && !input.getStatementDate().isAfter(period.getPeriodEnd()),
                "statement date is outside accounting period");
        long difference = Math.subtractExact(
                input.getNetSettlementAmountMinor(), input.getExpectedBusinessNetAmountMinor());
        ChannelStatement row = new ChannelStatement()
                .setStatementId(statementId).setTenantId(tenantId)
                .setStatementCode(input.getStatementCode()).setPeriodId(period.getPeriodId())
                .setChannelCode(upper(input.getChannelCode())).setStatementDate(input.getStatementDate())
                .setCurrencyCode(period.getCurrencyCode()).setGrossAmountMinor(input.getGrossAmountMinor())
                .setRefundAmountMinor(input.getRefundAmountMinor()).setFeeAmountMinor(input.getFeeAmountMinor())
                .setNetSettlementAmountMinor(input.getNetSettlementAmountMinor())
                .setExpectedBusinessNetAmountMinor(input.getExpectedBusinessNetAmountMinor())
                .setDifferenceAmountMinor(difference).setEvidenceSha256(input.getEvidenceSha256())
                .setStatus("IMPORTED").setImportedByPrincipalId(actorPrincipalId)
                .setReasonCode(reason(input.getReasonCode())).setVersion(1L)
                .setImportedAt(now).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertStatement(row) == 1, "failed to persist channel statement");
        return outcome("finance.channel_statement.imported", "finance_channel_statement",
                statementId, 1L, "IMPORTED",
                payload("period_id", period.getPeriodId(), "statement_id", statementId,
                        "statement_code", row.getStatementCode(), "channel_code", row.getChannelCode(),
                        "net_settlement_amount_minor", row.getNetSettlementAmountMinor(),
                        "expected_business_net_amount_minor", row.getExpectedBusinessNetAmountMinor(),
                        "difference_amount_minor", difference, "current_status", "IMPORTED"));
    }

    private Outcome reconcileStatement(Long tenantId, FinanceCloseCommand command,
                                       String actorPrincipalId, LocalDateTime now) {
        FinanceCloseCommand.StatementDefinition input =
                nonNull(command.getStatement(), "statement is required");
        requireRef(input.getStatementId(), "statementId", 128);
        ChannelStatement row = nonNull(mapper.selectStatementForUpdate(tenantId, input.getStatementId()),
                "channel statement not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        requireOpenPeriod(tenantId, row.getPeriodId());
        require(Set.of("IMPORTED", "EXCEPTION").contains(row.getStatus()),
                "only imported or exception statements can be reconciled");
        long difference = Math.subtractExact(
                row.getNetSettlementAmountMinor(), row.getExpectedBusinessNetAmountMinor());
        String reasonCode = reason(input.getReasonCode());
        if (difference == 0) {
            require(mapper.markStatementReconciled(tenantId, row.getStatementId(), row.getVersion(),
                    "RECONCILED", 0L, actorPrincipalId, reasonCode, now) == 1,
                    "statement reconciliation conflict");
            return outcome("finance.channel_statement.reconciled", "finance_channel_statement",
                    row.getStatementId(), row.getVersion() + 1, "RECONCILED",
                    payload("period_id", row.getPeriodId(), "statement_id", row.getStatementId(),
                            "difference_amount_minor", 0L, "current_status", "RECONCILED"));
        }
        require(!"EXCEPTION".equals(row.getStatus()),
                "statement already has an unresolved reconciliation difference");
        String differenceId = UUID.randomUUID().toString();
        ReconciliationDifference differenceRow = new ReconciliationDifference()
                .setDifferenceId(differenceId).setTenantId(tenantId).setPeriodId(row.getPeriodId())
                .setStatementId(row.getStatementId()).setDifferenceAmountMinor(difference)
                .setStatus("OPEN").setOpenedByPrincipalId(actorPrincipalId)
                .setReasonCode(reasonCode).setVersion(1L).setOpenedAt(now)
                .setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertDifference(differenceRow) == 1, "failed to persist reconciliation difference");
        require(mapper.markStatementReconciled(tenantId, row.getStatementId(), row.getVersion(),
                "EXCEPTION", difference, actorPrincipalId, reasonCode, now) == 1,
                "statement exception transition conflict");
        return outcome("finance.reconciliation_difference.opened",
                "finance_reconciliation_difference", differenceId, 1L, "OPEN",
                payload("period_id", row.getPeriodId(), "statement_id", row.getStatementId(),
                        "difference_id", differenceId, "difference_amount_minor", difference,
                        "current_status", "OPEN"));
    }

    private Outcome resolveDifference(Long tenantId, FinanceCloseCommand command,
                                      String actorPrincipalId, LocalDateTime now) {
        FinanceCloseCommand.DifferenceResolutionDefinition input = nonNull(
                command.getDifferenceResolution(), "differenceResolution is required");
        requireRef(input.getDifferenceId(), "differenceId", 128);
        require(input.getAdjustmentAmountMinor() != null, "adjustmentAmountMinor is required");
        String resolutionType = upper(input.getResolutionType());
        require(RESOLUTION_TYPES.contains(resolutionType), "resolutionType is invalid");
        requireSha256(input.getResolutionEvidenceSha256(), "resolutionEvidenceSha256");
        ReconciliationDifference row = nonNull(
                mapper.selectDifferenceForUpdate(tenantId, input.getDifferenceId()),
                "reconciliation difference not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("OPEN".equals(row.getStatus()), "only an open difference can be resolved");
        require(!actorPrincipalId.equals(row.getOpenedByPrincipalId()),
                "difference resolver must be independent from difference opener");
        require(Objects.equals(input.getAdjustmentAmountMinor(), row.getDifferenceAmountMinor()),
                "adjustment amount must exactly resolve the recorded difference");
        requireOpenPeriod(tenantId, row.getPeriodId());
        ChannelStatement statement = nonNull(
                mapper.selectStatementForUpdate(tenantId, row.getStatementId()),
                "difference statement not found");
        require("EXCEPTION".equals(statement.getStatus()),
                "difference statement is not in exception state");
        String reasonCode = reason(input.getReasonCode());
        require(mapper.resolveDifference(tenantId, row.getDifferenceId(), row.getVersion(),
                input.getAdjustmentAmountMinor(), resolutionType, input.getResolutionEvidenceSha256(),
                actorPrincipalId, reasonCode, now) == 1, "difference resolution conflict");
        require(mapper.resolveStatement(tenantId, statement.getStatementId(), statement.getVersion(),
                actorPrincipalId, reasonCode, now) == 1, "statement resolution conflict");
        return outcome("finance.reconciliation_difference.resolved",
                "finance_reconciliation_difference", row.getDifferenceId(), row.getVersion() + 1,
                "RESOLVED", payload("period_id", row.getPeriodId(),
                        "statement_id", statement.getStatementId(), "difference_id", row.getDifferenceId(),
                        "adjustment_amount_minor", input.getAdjustmentAmountMinor(),
                        "resolution_type", resolutionType, "current_status", "RESOLVED"));
    }

    private Outcome createSettlement(Long tenantId, FinanceCloseCommand command,
                                     String actorPrincipalId, LocalDateTime now) {
        FinanceCloseCommand.SettlementDefinition input =
                nonNull(command.getSettlement(), "settlement is required");
        String batchId = valueOrUuid(input.getSettlementBatchId());
        requireRef(input.getSettlementCode(), "settlementCode", 64);
        requireRef(input.getPeriodId(), "periodId", 128);
        requireRef(input.getStatementId(), "statementId", 128);
        requireNonNegative(input.getExpectedAmountMinor(), "expectedAmountMinor");
        AccountingPeriod period = requireOpenPeriod(tenantId, input.getPeriodId());
        ChannelStatement statement = nonNull(
                mapper.selectStatementForUpdate(tenantId, input.getStatementId()),
                "channel statement not found");
        require(Objects.equals(statement.getPeriodId(), period.getPeriodId())
                        && "RECONCILED".equals(statement.getStatus()),
                "settlement requires a reconciled statement in the same period");
        require(Objects.equals(input.getExpectedAmountMinor(), statement.getNetSettlementAmountMinor()),
                "settlement expected amount must equal reconciled statement net");
        SettlementBatch row = new SettlementBatch()
                .setSettlementBatchId(batchId).setTenantId(tenantId)
                .setSettlementCode(input.getSettlementCode()).setPeriodId(period.getPeriodId())
                .setStatementId(statement.getStatementId()).setCurrencyCode(period.getCurrencyCode())
                .setExpectedAmountMinor(input.getExpectedAmountMinor()).setStatus("PREPARED")
                .setPreparedByPrincipalId(actorPrincipalId).setReasonCode(reason(input.getReasonCode()))
                .setVersion(1L).setPreparedAt(now).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertSettlement(row) == 1, "failed to persist settlement batch");
        return outcome("finance.settlement_batch.created", "finance_settlement_batch",
                batchId, 1L, "PREPARED",
                payload("period_id", period.getPeriodId(), "statement_id", statement.getStatementId(),
                        "settlement_batch_id", batchId, "settlement_code", row.getSettlementCode(),
                        "expected_amount_minor", row.getExpectedAmountMinor(), "current_status", "PREPARED"));
    }

    private Outcome confirmSettlement(Long tenantId, FinanceCloseCommand command,
                                      String actorPrincipalId, LocalDateTime now) {
        FinanceCloseCommand.SettlementDefinition input =
                nonNull(command.getSettlement(), "settlement is required");
        requireRef(input.getSettlementBatchId(), "settlementBatchId", 128);
        requireNonNegative(input.getSettledAmountMinor(), "settledAmountMinor");
        requireRef(input.getBankReference(), "bankReference", 128);
        requireSha256(input.getSettlementEvidenceSha256(), "settlementEvidenceSha256");
        SettlementBatch row = nonNull(
                mapper.selectSettlementForUpdate(tenantId, input.getSettlementBatchId()),
                "settlement batch not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("PREPARED".equals(row.getStatus()), "only a prepared settlement can be confirmed");
        require(!actorPrincipalId.equals(row.getPreparedByPrincipalId()),
                "settlement confirmer must be independent from settlement preparer");
        require(Objects.equals(input.getSettledAmountMinor(), row.getExpectedAmountMinor()),
                "settled amount must equal reconciled expected amount");
        requireOpenPeriod(tenantId, row.getPeriodId());
        String reasonCode = reason(input.getReasonCode());
        require(mapper.settleBatch(tenantId, row.getSettlementBatchId(), row.getVersion(),
                input.getSettledAmountMinor(), input.getBankReference(),
                input.getSettlementEvidenceSha256(), actorPrincipalId, reasonCode, now) == 1,
                "settlement confirmation conflict");
        return outcome("finance.settlement_batch.settled", "finance_settlement_batch",
                row.getSettlementBatchId(), row.getVersion() + 1, "SETTLED",
                payload("period_id", row.getPeriodId(), "statement_id", row.getStatementId(),
                        "settlement_batch_id", row.getSettlementBatchId(),
                        "settled_amount_minor", input.getSettledAmountMinor(),
                        "bank_reference", input.getBankReference(), "current_status", "SETTLED"));
    }

    private Outcome prepareJournal(Long tenantId, FinanceCloseCommand command,
                                   String actorPrincipalId, LocalDateTime now) {
        FinanceCloseCommand.JournalEntryDefinition input =
                nonNull(command.getJournalEntry(), "journalEntry is required");
        String journalId = valueOrUuid(input.getJournalEntryId());
        requireRef(input.getJournalCode(), "journalCode", 64);
        requireRef(input.getPeriodId(), "periodId", 128);
        require("SETTLEMENT_BATCH".equals(upper(input.getSourceType())),
                "only settled batches may prepare canonical journal entries");
        requireRef(input.getSourceId(), "sourceId", 128);
        requirePositive(input.getDebitTotalMinor(), "debitTotalMinor");
        requirePositive(input.getCreditTotalMinor(), "creditTotalMinor");
        require(Objects.equals(input.getDebitTotalMinor(), input.getCreditTotalMinor()),
                "journal entry must be balanced");
        requireSha256(input.getEvidenceSha256(), "evidenceSha256");
        AccountingPeriod period = requireOpenPeriod(tenantId, input.getPeriodId());
        SettlementBatch batch = nonNull(
                mapper.selectSettlementForUpdate(tenantId, input.getSourceId()),
                "journal source settlement batch not found");
        require(Objects.equals(batch.getPeriodId(), period.getPeriodId())
                        && "SETTLED".equals(batch.getStatus()),
                "journal entry requires a settled batch in the same period");
        require(Objects.equals(input.getDebitTotalMinor(), batch.getSettledAmountMinor()),
                "journal total must equal settled batch amount");
        JournalEntry row = new JournalEntry()
                .setJournalEntryId(journalId).setTenantId(tenantId).setJournalCode(input.getJournalCode())
                .setPeriodId(period.getPeriodId()).setSourceType("SETTLEMENT_BATCH")
                .setSourceId(batch.getSettlementBatchId()).setCurrencyCode(period.getCurrencyCode())
                .setDebitTotalMinor(input.getDebitTotalMinor()).setCreditTotalMinor(input.getCreditTotalMinor())
                .setEvidenceSha256(input.getEvidenceSha256()).setStatus("PREPARED")
                .setPreparedByPrincipalId(actorPrincipalId).setReasonCode(reason(input.getReasonCode()))
                .setVersion(1L).setPreparedAt(now).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertJournalEntry(row) == 1, "failed to persist journal entry");
        return outcome("finance.journal_entry.prepared", "finance_journal_entry",
                journalId, 1L, "PREPARED",
                payload("period_id", period.getPeriodId(),
                        "settlement_batch_id", batch.getSettlementBatchId(),
                        "journal_entry_id", journalId, "journal_code", row.getJournalCode(),
                        "debit_total_minor", row.getDebitTotalMinor(),
                        "credit_total_minor", row.getCreditTotalMinor(), "current_status", "PREPARED"));
    }

    private Outcome postJournal(Long tenantId, FinanceCloseCommand command,
                                String actorPrincipalId, LocalDateTime now) {
        FinanceCloseCommand.JournalEntryDefinition input =
                nonNull(command.getJournalEntry(), "journalEntry is required");
        requireRef(input.getJournalEntryId(), "journalEntryId", 128);
        JournalEntry row = nonNull(
                mapper.selectJournalEntryForUpdate(tenantId, input.getJournalEntryId()),
                "journal entry not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("PREPARED".equals(row.getStatus()), "only a prepared journal entry can be posted");
        require(Objects.equals(row.getDebitTotalMinor(), row.getCreditTotalMinor())
                        && row.getDebitTotalMinor() > 0,
                "journal entry is not balanced");
        require(!actorPrincipalId.equals(row.getPreparedByPrincipalId()),
                "journal poster must be independent from journal preparer");
        requireOpenPeriod(tenantId, row.getPeriodId());
        String reasonCode = reason(input.getReasonCode());
        require(mapper.postJournalEntry(tenantId, row.getJournalEntryId(), row.getVersion(),
                actorPrincipalId, reasonCode, now) == 1, "journal posting conflict");
        return outcome("finance.journal_entry.posted", "finance_journal_entry",
                row.getJournalEntryId(), row.getVersion() + 1, "POSTED",
                payload("period_id", row.getPeriodId(), "settlement_batch_id", row.getSourceId(),
                        "journal_entry_id", row.getJournalEntryId(),
                        "debit_total_minor", row.getDebitTotalMinor(),
                        "credit_total_minor", row.getCreditTotalMinor(), "current_status", "POSTED"));
    }

    private Outcome closePeriod(Long tenantId, FinanceCloseCommand command,
                                String actorPrincipalId, LocalDateTime now) {
        FinanceCloseCommand.PeriodDefinition input = nonNull(command.getPeriod(), "period is required");
        requireRef(input.getPeriodId(), "periodId", 128);
        requireSha256(input.getEvidenceSha256(), "evidenceSha256");
        AccountingPeriod period = requireOpenPeriod(tenantId, input.getPeriodId());
        requireExpectedVersion(input.getExpectedVersion(), period.getVersion());
        require(!actorPrincipalId.equals(period.getOpenedByPrincipalId()),
                "period closer must be independent from period opener");
        require(mapper.countStatements(tenantId, period.getPeriodId()) > 0,
                "accounting period has no channel statements");
        require(mapper.countUnreconciledStatements(tenantId, period.getPeriodId()) == 0,
                "accounting period has unreconciled statements");
        require(mapper.countOpenDifferences(tenantId, period.getPeriodId()) == 0,
                "accounting period has unresolved reconciliation differences");
        require(mapper.countSettlementBatches(tenantId, period.getPeriodId()) > 0,
                "accounting period has no settlement batches");
        require(mapper.countUnsettledBatches(tenantId, period.getPeriodId()) == 0,
                "accounting period has unsettled batches");
        require(mapper.countJournalEntries(tenantId, period.getPeriodId()) > 0,
                "accounting period has no journal entries");
        require(mapper.countUnpostedJournalEntries(tenantId, period.getPeriodId()) == 0,
                "accounting period has unposted journal entries");
        String reasonCode = reason(input.getReasonCode());
        require(mapper.closePeriod(tenantId, period.getPeriodId(), period.getVersion(),
                actorPrincipalId, input.getEvidenceSha256(), reasonCode, now) == 1,
                "accounting period close conflict");
        return outcome("finance.accounting_period.closed", "finance_accounting_period",
                period.getPeriodId(), period.getVersion() + 1, "CLOSED",
                payload("period_id", period.getPeriodId(), "period_code", period.getPeriodCode(),
                        "statement_count", mapper.countStatements(tenantId, period.getPeriodId()),
                        "settlement_batch_count", mapper.countSettlementBatches(tenantId, period.getPeriodId()),
                        "journal_entry_count", mapper.countJournalEntries(tenantId, period.getPeriodId()),
                        "close_evidence_sha256", input.getEvidenceSha256(), "current_status", "CLOSED"));
    }

    private AccountingPeriod requireOpenPeriod(Long tenantId, String periodId) {
        AccountingPeriod period = nonNull(mapper.selectPeriodForUpdate(tenantId, periodId),
                "accounting period not found");
        require("OPEN".equals(period.getStatus()), "accounting period is not open");
        return period;
    }

    private void appendEvent(Long tenantId, FinanceCloseCommand command, Outcome outcome) {
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(UUID.randomUUID().toString()).eventType(outcome.eventType())
                .schemaVersion(1).sourceSystem(SOURCE_SYSTEM).tenantId(tenantId)
                .aggregateType(outcome.aggregateType()).aggregateId(outcome.aggregateId())
                .aggregateVersion(outcome.version()).eventSequence((short) 1)
                .occurredAt(command.getOccurredAt()).traceId(command.getRunId())
                .correlationId(command.getCorrelationId()).causationId(command.getCausationId())
                .idempotencyKey(command.getIdempotencyKey() + ":" + outcome.version())
                .payload(outcome.payload()).headers(payload("run_id", command.getRunId(),
                        "status", outcome.status())).destination("lakehouse").build());
    }

    private static void validateEnvelope(FinanceCloseCommand command) {
        require(command != null, "finance command is required");
        require(command.getOperation() != null, "operation is required");
        requireUuid(command.getCorrelationId(), "correlationId");
        require(command.getOccurredAt() != null, "occurredAt is required");
        requireRef(command.getIdempotencyKey(), "idempotencyKey", 128);
        if (command.getRunId() != null) {
            requireRef(command.getRunId(), "runId", 128);
        }
        if (command.getCausationId() != null) {
            requireUuid(command.getCausationId(), "causationId");
        }
    }

    private static String valueOrUuid(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    private static void requireExpectedVersion(Long expected, Long actual) {
        require(expected != null && Objects.equals(expected, actual),
                "expectedVersion does not match current version");
    }

    private static void requireNonNegative(Long value, String field) {
        require(value != null && value >= 0, field + " must not be negative");
    }

    private static void requirePositive(Long value, String field) {
        require(value != null && value > 0, field + " must be positive");
    }

    private static void requireCurrency(String value) {
        require(value != null && value.matches("[A-Za-z]{3}"),
                "currencyCode must be ISO alpha-3");
    }

    private static void requireCode(String value, String field) {
        require(value != null && SAFE_CODE.matcher(upper(value)).matches(), field + " is invalid");
    }

    private static void requireRef(String value, String field, int maxLength) {
        require(value != null && value.length() <= maxLength && SAFE_REF.matcher(value).matches(),
                field + " is invalid");
    }

    private static void requireSha256(String value, String field) {
        require(value != null && SHA256.matcher(value).matches(),
                field + " must be lowercase SHA-256");
    }

    private static void requireUuid(String value, String field) {
        try {
            UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " must be a UUID", exception);
        }
    }

    private static String reason(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = upper(value);
        requireCode(normalized, "reasonCode");
        return normalized;
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static <T> T nonNull(T value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private static Map<String, Object> payload(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            if (values[index + 1] != null) {
                result.put(values[index].toString(), values[index + 1]);
            }
        }
        return result;
    }

    private static Outcome outcome(String eventType, String aggregateType, String aggregateId,
                                   Long version, String status, Map<String, Object> payload) {
        return new Outcome(eventType, aggregateType, aggregateId, version, status, payload);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record Outcome(String eventType, String aggregateType, String aggregateId,
                           Long version, String status, Map<String, Object> payload) {
    }
}
