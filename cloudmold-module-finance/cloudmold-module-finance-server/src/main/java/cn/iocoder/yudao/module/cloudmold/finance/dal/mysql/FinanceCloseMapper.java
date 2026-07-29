package cn.iocoder.yudao.module.cloudmold.finance.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.finance.api.ChannelStatementView;
import cn.iocoder.yudao.module.cloudmold.finance.api.FinanceCloseView;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.AccountingPeriod;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.ChannelStatement;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.JournalEntry;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.ReconciliationDifference;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.SettlementBatch;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface FinanceCloseMapper {
    @Insert("""
            INSERT INTO cloudmold_finance_operation
              (tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES (#{tenantId},#{idempotencyKey},#{commandType},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolveOperation(@Param("tenantId") Long tenantId,
                                 @Param("idempotencyKey") String idempotencyKey,
                                 @Param("commandType") String commandType,
                                 @Param("requestHash") String requestHash,
                                 @Param("attemptToken") String attemptToken,
                                 @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("""
            SELECT operation_id,tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,
                   aggregate_type,aggregate_id,result_json
            FROM cloudmold_finance_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} FOR UPDATE
            """)
    Operation selectOperationForUpdate(@Param("operationId") Long operationId,
                                       @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_finance_operation
            SET status=10,aggregate_type=#{aggregateType},aggregate_id=#{aggregateId},
                result_json=#{resultJson},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} AND status=0
            """)
    int markOperationSucceeded(@Param("operationId") Long operationId,
                               @Param("tenantId") Long tenantId,
                               @Param("aggregateType") String aggregateType,
                               @Param("aggregateId") String aggregateId,
                               @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_accounting_period
              (period_id,tenant_id,period_code,period_start,period_end,currency_code,status,
               opened_by_principal_id,reason_code,version,opened_at,created_at,updated_at)
            VALUES (#{periodId},#{tenantId},#{periodCode},#{periodStart},#{periodEnd},#{currencyCode},
                    #{status},#{openedByPrincipalId},#{reasonCode},#{version},#{openedAt},#{createdAt},#{updatedAt})
            """)
    int insertPeriod(AccountingPeriod value);

    @Select("""
            SELECT period_id,tenant_id,period_code,period_start,period_end,currency_code,status,
                   opened_by_principal_id,closed_by_principal_id,close_evidence_sha256,reason_code,
                   version,opened_at,closed_at,created_at,updated_at
            FROM cloudmold_finance_accounting_period
            WHERE tenant_id=#{tenantId} AND period_id=#{periodId} FOR UPDATE
            """)
    AccountingPeriod selectPeriodForUpdate(@Param("tenantId") Long tenantId,
                                            @Param("periodId") String periodId);

    @Update("""
            UPDATE cloudmold_finance_accounting_period
            SET status='CLOSED',closed_by_principal_id=#{actorPrincipalId},
                close_evidence_sha256=#{evidenceSha256},reason_code=#{reasonCode},
                closed_at=#{now},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND period_id=#{periodId}
              AND status='OPEN' AND version=#{expectedVersion}
            """)
    int closePeriod(@Param("tenantId") Long tenantId,
                    @Param("periodId") String periodId,
                    @Param("expectedVersion") Long expectedVersion,
                    @Param("actorPrincipalId") String actorPrincipalId,
                    @Param("evidenceSha256") String evidenceSha256,
                    @Param("reasonCode") String reasonCode,
                    @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_channel_statement
              (statement_id,tenant_id,statement_code,period_id,channel_code,statement_date,currency_code,
               gross_amount_minor,refund_amount_minor,fee_amount_minor,net_settlement_amount_minor,
               expected_business_net_amount_minor,difference_amount_minor,evidence_sha256,status,
               imported_by_principal_id,reason_code,version,imported_at,created_at,updated_at)
            VALUES (#{statementId},#{tenantId},#{statementCode},#{periodId},#{channelCode},#{statementDate},
                    #{currencyCode},#{grossAmountMinor},#{refundAmountMinor},#{feeAmountMinor},
                    #{netSettlementAmountMinor},#{expectedBusinessNetAmountMinor},#{differenceAmountMinor},
                    #{evidenceSha256},#{status},#{importedByPrincipalId},#{reasonCode},#{version},
                    #{importedAt},#{createdAt},#{updatedAt})
            """)
    int insertStatement(ChannelStatement value);

    @Select("""
            SELECT statement_id,tenant_id,statement_code,period_id,channel_code,statement_date,currency_code,
                   gross_amount_minor,refund_amount_minor,fee_amount_minor,net_settlement_amount_minor,
                   expected_business_net_amount_minor,difference_amount_minor,evidence_sha256,status,
                   imported_by_principal_id,reconciled_by_principal_id,reason_code,version,
                   imported_at,reconciled_at,created_at,updated_at
            FROM cloudmold_finance_channel_statement
            WHERE tenant_id=#{tenantId} AND statement_id=#{statementId} FOR UPDATE
            """)
    ChannelStatement selectStatementForUpdate(@Param("tenantId") Long tenantId,
                                              @Param("statementId") String statementId);

    @Update("""
            UPDATE cloudmold_finance_channel_statement
            SET status=#{status},difference_amount_minor=#{differenceAmountMinor},
                reconciled_by_principal_id=#{actorPrincipalId},
                reconciled_at=CASE WHEN #{status}='RECONCILED' THEN #{now} ELSE NULL END,
                reason_code=#{reasonCode},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND statement_id=#{statementId}
              AND status IN ('IMPORTED','EXCEPTION') AND version=#{expectedVersion}
            """)
    int markStatementReconciled(@Param("tenantId") Long tenantId,
                                @Param("statementId") String statementId,
                                @Param("expectedVersion") Long expectedVersion,
                                @Param("status") String status,
                                @Param("differenceAmountMinor") Long differenceAmountMinor,
                                @Param("actorPrincipalId") String actorPrincipalId,
                                @Param("reasonCode") String reasonCode,
                                @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_finance_channel_statement
            SET status='RECONCILED',expected_business_net_amount_minor=net_settlement_amount_minor,
                difference_amount_minor=0,reconciled_by_principal_id=#{actorPrincipalId},
                reconciled_at=#{now},reason_code=#{reasonCode},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND statement_id=#{statementId}
              AND status='EXCEPTION' AND version=#{expectedVersion}
            """)
    int resolveStatement(@Param("tenantId") Long tenantId,
                         @Param("statementId") String statementId,
                         @Param("expectedVersion") Long expectedVersion,
                         @Param("actorPrincipalId") String actorPrincipalId,
                         @Param("reasonCode") String reasonCode,
                         @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_reconciliation_difference
              (difference_id,tenant_id,period_id,statement_id,difference_amount_minor,status,
               opened_by_principal_id,reason_code,version,opened_at,created_at,updated_at)
            VALUES (#{differenceId},#{tenantId},#{periodId},#{statementId},#{differenceAmountMinor},
                    #{status},#{openedByPrincipalId},#{reasonCode},#{version},#{openedAt},#{createdAt},#{updatedAt})
            """)
    int insertDifference(ReconciliationDifference value);

    @Select("""
            SELECT difference_id,tenant_id,period_id,statement_id,difference_amount_minor,
                   adjustment_amount_minor,status,resolution_type,resolution_evidence_sha256,
                   opened_by_principal_id,resolved_by_principal_id,reason_code,version,
                   opened_at,resolved_at,created_at,updated_at
            FROM cloudmold_finance_reconciliation_difference
            WHERE tenant_id=#{tenantId} AND difference_id=#{differenceId} FOR UPDATE
            """)
    ReconciliationDifference selectDifferenceForUpdate(@Param("tenantId") Long tenantId,
                                                       @Param("differenceId") String differenceId);

    @Update("""
            UPDATE cloudmold_finance_reconciliation_difference
            SET adjustment_amount_minor=#{adjustmentAmountMinor},status='RESOLVED',
                resolution_type=#{resolutionType},resolution_evidence_sha256=#{evidenceSha256},
                resolved_by_principal_id=#{actorPrincipalId},reason_code=#{reasonCode},
                resolved_at=#{now},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND difference_id=#{differenceId}
              AND status='OPEN' AND version=#{expectedVersion}
            """)
    int resolveDifference(@Param("tenantId") Long tenantId,
                          @Param("differenceId") String differenceId,
                          @Param("expectedVersion") Long expectedVersion,
                          @Param("adjustmentAmountMinor") Long adjustmentAmountMinor,
                          @Param("resolutionType") String resolutionType,
                          @Param("evidenceSha256") String evidenceSha256,
                          @Param("actorPrincipalId") String actorPrincipalId,
                          @Param("reasonCode") String reasonCode,
                          @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_settlement_batch
              (settlement_batch_id,tenant_id,settlement_code,period_id,statement_id,currency_code,
               expected_amount_minor,status,prepared_by_principal_id,reason_code,version,
               prepared_at,created_at,updated_at)
            VALUES (#{settlementBatchId},#{tenantId},#{settlementCode},#{periodId},#{statementId},
                    #{currencyCode},#{expectedAmountMinor},#{status},#{preparedByPrincipalId},
                    #{reasonCode},#{version},#{preparedAt},#{createdAt},#{updatedAt})
            """)
    int insertSettlement(SettlementBatch value);

    @Select("""
            SELECT settlement_batch_id,tenant_id,settlement_code,period_id,statement_id,currency_code,
                   expected_amount_minor,settled_amount_minor,bank_reference,settlement_evidence_sha256,
                   status,prepared_by_principal_id,settled_by_principal_id,reason_code,version,
                   prepared_at,settled_at,created_at,updated_at
            FROM cloudmold_finance_settlement_batch
            WHERE tenant_id=#{tenantId} AND settlement_batch_id=#{settlementBatchId} FOR UPDATE
            """)
    SettlementBatch selectSettlementForUpdate(@Param("tenantId") Long tenantId,
                                              @Param("settlementBatchId") String settlementBatchId);

    @Update("""
            UPDATE cloudmold_finance_settlement_batch
            SET settled_amount_minor=#{settledAmountMinor},bank_reference=#{bankReference},
                settlement_evidence_sha256=#{evidenceSha256},status='SETTLED',
                settled_by_principal_id=#{actorPrincipalId},reason_code=#{reasonCode},
                settled_at=#{now},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND settlement_batch_id=#{settlementBatchId}
              AND status='PREPARED' AND version=#{expectedVersion}
            """)
    int settleBatch(@Param("tenantId") Long tenantId,
                    @Param("settlementBatchId") String settlementBatchId,
                    @Param("expectedVersion") Long expectedVersion,
                    @Param("settledAmountMinor") Long settledAmountMinor,
                    @Param("bankReference") String bankReference,
                    @Param("evidenceSha256") String evidenceSha256,
                    @Param("actorPrincipalId") String actorPrincipalId,
                    @Param("reasonCode") String reasonCode,
                    @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_journal_entry
              (journal_entry_id,tenant_id,journal_code,period_id,source_type,source_id,currency_code,
               debit_total_minor,credit_total_minor,evidence_sha256,status,prepared_by_principal_id,
               reason_code,version,prepared_at,created_at,updated_at)
            VALUES (#{journalEntryId},#{tenantId},#{journalCode},#{periodId},#{sourceType},#{sourceId},
                    #{currencyCode},#{debitTotalMinor},#{creditTotalMinor},#{evidenceSha256},#{status},
                    #{preparedByPrincipalId},#{reasonCode},#{version},#{preparedAt},#{createdAt},#{updatedAt})
            """)
    int insertJournalEntry(JournalEntry value);

    @Select("""
            SELECT journal_entry_id,tenant_id,journal_code,period_id,source_type,source_id,currency_code,
                   debit_total_minor,credit_total_minor,evidence_sha256,status,
                   prepared_by_principal_id,posted_by_principal_id,reason_code,version,
                   prepared_at,posted_at,created_at,updated_at
            FROM cloudmold_finance_journal_entry
            WHERE tenant_id=#{tenantId} AND journal_entry_id=#{journalEntryId} FOR UPDATE
            """)
    JournalEntry selectJournalEntryForUpdate(@Param("tenantId") Long tenantId,
                                             @Param("journalEntryId") String journalEntryId);

    @Update("""
            UPDATE cloudmold_finance_journal_entry
            SET status='POSTED',posted_by_principal_id=#{actorPrincipalId},reason_code=#{reasonCode},
                posted_at=#{now},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND journal_entry_id=#{journalEntryId}
              AND status='PREPARED' AND version=#{expectedVersion}
            """)
    int postJournalEntry(@Param("tenantId") Long tenantId,
                         @Param("journalEntryId") String journalEntryId,
                         @Param("expectedVersion") Long expectedVersion,
                         @Param("actorPrincipalId") String actorPrincipalId,
                         @Param("reasonCode") String reasonCode,
                         @Param("now") LocalDateTime now);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_finance_channel_statement
            WHERE tenant_id=#{tenantId} AND period_id=#{periodId}
            """)
    int countStatements(@Param("tenantId") Long tenantId, @Param("periodId") String periodId);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_finance_channel_statement
            WHERE tenant_id=#{tenantId} AND period_id=#{periodId} AND status<>'RECONCILED'
            """)
    int countUnreconciledStatements(@Param("tenantId") Long tenantId, @Param("periodId") String periodId);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_finance_reconciliation_difference
            WHERE tenant_id=#{tenantId} AND period_id=#{periodId} AND status='OPEN'
            """)
    int countOpenDifferences(@Param("tenantId") Long tenantId, @Param("periodId") String periodId);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_finance_settlement_batch
            WHERE tenant_id=#{tenantId} AND period_id=#{periodId}
            """)
    int countSettlementBatches(@Param("tenantId") Long tenantId, @Param("periodId") String periodId);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_finance_settlement_batch
            WHERE tenant_id=#{tenantId} AND period_id=#{periodId} AND status<>'SETTLED'
            """)
    int countUnsettledBatches(@Param("tenantId") Long tenantId, @Param("periodId") String periodId);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_finance_journal_entry
            WHERE tenant_id=#{tenantId} AND period_id=#{periodId}
            """)
    int countJournalEntries(@Param("tenantId") Long tenantId, @Param("periodId") String periodId);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_finance_journal_entry
            WHERE tenant_id=#{tenantId} AND period_id=#{periodId} AND status<>'POSTED'
            """)
    int countUnpostedJournalEntries(@Param("tenantId") Long tenantId, @Param("periodId") String periodId);

    @Select("""
            SELECT p.period_id periodId,p.period_code periodCode,p.period_start periodStart,
                   p.period_end periodEnd,p.currency_code currencyCode,p.status,
                   (SELECT COUNT(*) FROM cloudmold_finance_channel_statement s
                     WHERE s.tenant_id=p.tenant_id AND s.period_id=p.period_id) statementCount,
                   (SELECT COUNT(*) FROM cloudmold_finance_channel_statement s
                     WHERE s.tenant_id=p.tenant_id AND s.period_id=p.period_id
                       AND s.status='RECONCILED') reconciledStatementCount,
                   (SELECT COUNT(*) FROM cloudmold_finance_reconciliation_difference d
                     WHERE d.tenant_id=p.tenant_id AND d.period_id=p.period_id
                       AND d.status='OPEN') openDifferenceCount,
                   (SELECT COUNT(*) FROM cloudmold_finance_settlement_batch b
                     WHERE b.tenant_id=p.tenant_id AND b.period_id=p.period_id) settlementBatchCount,
                   (SELECT COUNT(*) FROM cloudmold_finance_settlement_batch b
                     WHERE b.tenant_id=p.tenant_id AND b.period_id=p.period_id
                       AND b.status='SETTLED') settledBatchCount,
                   (SELECT COUNT(*) FROM cloudmold_finance_journal_entry j
                     WHERE j.tenant_id=p.tenant_id AND j.period_id=p.period_id) journalEntryCount,
                   (SELECT COUNT(*) FROM cloudmold_finance_journal_entry j
                     WHERE j.tenant_id=p.tenant_id AND j.period_id=p.period_id
                       AND j.status='POSTED') postedJournalCount,
                   (SELECT COALESCE(SUM(s.net_settlement_amount_minor),0)
                      FROM cloudmold_finance_channel_statement s
                     WHERE s.tenant_id=p.tenant_id AND s.period_id=p.period_id) statementNetAmountMinor,
                   (SELECT COALESCE(SUM(b.settled_amount_minor),0)
                      FROM cloudmold_finance_settlement_batch b
                     WHERE b.tenant_id=p.tenant_id AND b.period_id=p.period_id
                       AND b.status='SETTLED') settledAmountMinor,
                   p.version,p.opened_by_principal_id openedByPrincipalId,
                   p.closed_by_principal_id closedByPrincipalId,p.opened_at openedAt,p.closed_at closedAt
            FROM cloudmold_finance_accounting_period p
            WHERE p.tenant_id=#{tenantId} AND p.period_id=#{periodId}
            """)
    FinanceCloseView selectPeriodView(@Param("tenantId") Long tenantId,
                                      @Param("periodId") String periodId);

    @Select("""
            SELECT s.statement_id statementId,s.statement_code statementCode,s.period_id periodId,
                   s.channel_code channelCode,s.statement_date statementDate,s.currency_code currencyCode,
                   s.gross_amount_minor grossAmountMinor,s.refund_amount_minor refundAmountMinor,
                   s.fee_amount_minor feeAmountMinor,s.net_settlement_amount_minor netSettlementAmountMinor,
                   s.expected_business_net_amount_minor expectedBusinessNetAmountMinor,
                   s.difference_amount_minor differenceAmountMinor,s.status,
                   d.difference_id differenceId,d.status differenceStatus,
                   b.settlement_batch_id settlementBatchId,b.status settlementStatus,
                   s.version,s.imported_at importedAt,s.reconciled_at reconciledAt
            FROM cloudmold_finance_channel_statement s
            LEFT JOIN cloudmold_finance_reconciliation_difference d
              ON d.tenant_id=s.tenant_id AND d.statement_id=s.statement_id
            LEFT JOIN cloudmold_finance_settlement_batch b
              ON b.tenant_id=s.tenant_id AND b.statement_id=s.statement_id
            WHERE s.tenant_id=#{tenantId} AND s.statement_id=#{statementId}
            """)
    ChannelStatementView selectStatementView(@Param("tenantId") Long tenantId,
                                             @Param("statementId") String statementId);
}
