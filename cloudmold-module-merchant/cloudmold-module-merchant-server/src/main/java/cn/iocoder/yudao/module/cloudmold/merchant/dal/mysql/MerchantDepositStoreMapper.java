package cn.iocoder.yudao.module.cloudmold.merchant.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.merchant.api.deposit.MerchantDepositView;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.*;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface MerchantDepositStoreMapper {

    @Insert("""
            INSERT INTO cloudmold_merchant_deposit_operation
              (tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES (#{tenantId},#{idempotencyKey},#{commandType},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolveOperation(@Param("tenantId") Long tenantId, @Param("idempotencyKey") String idempotencyKey,
                                 @Param("commandType") String commandType, @Param("requestHash") String requestHash,
                                 @Param("attemptToken") String attemptToken, @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("""
            SELECT operation_id,tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,
                   account_id,result_json,created_at,updated_at
            FROM cloudmold_merchant_deposit_operation
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId}
            FOR UPDATE
            """)
    MerchantDepositOperationDO selectOperationForUpdate(@Param("operationId") Long operationId,
                                                          @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_merchant_deposit_operation
            SET status=10,account_id=#{accountId},result_json=CAST(#{resultJson} AS JSON),updated_at=#{now}
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} AND status=0
            """)
    int markOperationSucceeded(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId,
                               @Param("accountId") String accountId, @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Select("""
            SELECT merchant_id,tenant_id,merchant_code,legal_entity_id,status,version,created_at,updated_at
            FROM cloudmold_merchant_account
            WHERE tenant_id=#{tenantId} AND merchant_id=#{merchantId}
            FOR UPDATE
            """)
    MerchantAccountDO selectMerchantForUpdate(@Param("tenantId") Long tenantId,
                                               @Param("merchantId") String merchantId);

    @Select("""
            SELECT account_id,tenant_id,merchant_id,currency,required_amount_minor,held_amount_minor,
                   frozen_amount_minor,paid_amount_minor,deducted_amount_minor,coverage_status,enforcement_status,
                   policy_version,version,created_at,updated_at
            FROM cloudmold_merchant_deposit_account
            WHERE tenant_id=#{tenantId} AND merchant_id=#{merchantId} AND currency=#{currency}
            FOR UPDATE
            """)
    MerchantDepositAccountDO selectAccountForUpdate(@Param("tenantId") Long tenantId,
                                                     @Param("merchantId") String merchantId,
                                                     @Param("currency") String currency);

    @Select("""
            SELECT account_id,merchant_id,currency,version AS account_version,required_amount_minor,
                   held_amount_minor,frozen_amount_minor,(held_amount_minor-frozen_amount_minor) AS available_amount_minor,
                   paid_amount_minor,deducted_amount_minor,coverage_status,enforcement_status,policy_version
            FROM cloudmold_merchant_deposit_account
            WHERE tenant_id=#{tenantId} AND merchant_id=#{merchantId} AND currency=#{currency}
            """)
    MerchantDepositView selectCurrent(@Param("tenantId") Long tenantId, @Param("merchantId") String merchantId,
                                      @Param("currency") String currency);

    @Insert("""
            INSERT INTO cloudmold_merchant_deposit_account
              (account_id,tenant_id,merchant_id,currency,required_amount_minor,held_amount_minor,frozen_amount_minor,
               paid_amount_minor,deducted_amount_minor,coverage_status,enforcement_status,policy_version,version,
               created_at,updated_at)
            VALUES (#{accountId},#{tenantId},#{merchantId},#{currency},#{requiredAmountMinor},#{heldAmountMinor},
                    #{frozenAmountMinor},#{paidAmountMinor},#{deductedAmountMinor},#{coverageStatus},
                    #{enforcementStatus},#{policyVersion},#{version},#{createdAt},#{updatedAt})
            """)
    int insertAccount(MerchantDepositAccountDO value);

    @Update("""
            UPDATE cloudmold_merchant_deposit_account
            SET required_amount_minor=#{requiredAmountMinor},held_amount_minor=#{heldAmountMinor},
                frozen_amount_minor=#{frozenAmountMinor},paid_amount_minor=#{paidAmountMinor},
                deducted_amount_minor=#{deductedAmountMinor},coverage_status=#{coverageStatus},
                enforcement_status=#{enforcementStatus},policy_version=#{policyVersion},version=version+1,
                updated_at=#{updatedAt}
            WHERE tenant_id=#{tenantId} AND account_id=#{accountId} AND version=#{expectedVersion}
            """)
    int updateAccount(@Param("tenantId") Long tenantId, @Param("accountId") String accountId,
                      @Param("expectedVersion") Long expectedVersion,
                      @Param("requiredAmountMinor") Long requiredAmountMinor,
                      @Param("heldAmountMinor") Long heldAmountMinor,
                      @Param("frozenAmountMinor") Long frozenAmountMinor,
                      @Param("paidAmountMinor") Long paidAmountMinor,
                      @Param("deductedAmountMinor") Long deductedAmountMinor,
                      @Param("coverageStatus") String coverageStatus,
                      @Param("enforcementStatus") String enforcementStatus,
                      @Param("policyVersion") String policyVersion,
                      @Param("updatedAt") LocalDateTime updatedAt);

    @Insert("""
            INSERT INTO cloudmold_merchant_deposit_ledger_entry
              (ledger_entry_id,tenant_id,account_id,merchant_id,currency,account_version,entry_type,amount_minor,
               held_delta_minor,frozen_delta_minor,held_before_minor,held_after_minor,frozen_before_minor,
               frozen_after_minor,required_before_minor,required_after_minor,paid_after_minor,deducted_after_minor,
               previous_coverage_status,current_coverage_status,previous_enforcement_status,
               current_enforcement_status,policy_version,business_reference,reason_code,evidence_ref,occurred_at,created_at)
            VALUES (#{ledgerEntryId},#{tenantId},#{accountId},#{merchantId},#{currency},#{accountVersion},#{entryType},
                    #{amountMinor},#{heldDeltaMinor},#{frozenDeltaMinor},#{heldBeforeMinor},#{heldAfterMinor},
                    #{frozenBeforeMinor},#{frozenAfterMinor},#{requiredBeforeMinor},#{requiredAfterMinor},
                    #{paidAfterMinor},#{deductedAfterMinor},#{previousCoverageStatus},#{currentCoverageStatus},
                    #{previousEnforcementStatus},#{currentEnforcementStatus},#{policyVersion},#{businessReference},
                    #{reasonCode},#{evidenceRef},#{occurredAt},#{createdAt})
            """)
    int insertLedgerEntry(MerchantDepositLedgerEntryDO value);
}
