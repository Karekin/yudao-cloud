package cn.iocoder.yudao.module.cloudmold.gamification.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.gamification.dal.dataobject.GamificationRecords.*;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface GamificationStoreMapper {

    @Insert("""
            INSERT INTO cloudmold_gamification_operation
              (tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES (#{tenantId},#{idempotencyKey},#{commandType},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolveOperation(@Param("tenantId") Long tenantId, @Param("idempotencyKey") String idempotencyKey,
                                 @Param("commandType") String commandType, @Param("requestHash") String requestHash,
                                 @Param("attemptToken") String attemptToken, @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()") Long selectLastInsertId();

    @Select("""
            SELECT operation_id,tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,
                   aggregate_id,result_json,created_at,updated_at
            FROM cloudmold_gamification_operation
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} FOR UPDATE
            """) Operation selectOperationForUpdate(@Param("operationId") Long operationId,
                                                       @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_gamification_operation SET status=10,aggregate_id=#{aggregateId},
              result_json=CAST(#{resultJson} AS JSON),updated_at=#{now}
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} AND status=0
            """)
    int markOperationSucceeded(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId,
                               @Param("aggregateId") String aggregateId, @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_gamification_game
              (game_id,tenant_id,game_code,game_name,status,current_version,created_at,updated_at)
            VALUES (#{gameId},#{tenantId},#{gameCode},#{gameName},#{status},#{currentVersion},#{createdAt},#{updatedAt})
            """) int insertGame(Game value);

    @Select("""
            SELECT game_id,tenant_id,game_code,game_name,status,current_version,virtual_currency_code,
                   assist_daily_limit,max_rounds_per_session,session_ttl_seconds,created_at,updated_at
            FROM cloudmold_gamification_game WHERE tenant_id=#{tenantId} AND game_id=#{gameId}
            """) Game selectGame(@Param("tenantId") Long tenantId, @Param("gameId") String gameId);

    @Select("""
            SELECT game_id,tenant_id,game_code,game_name,status,current_version,virtual_currency_code,
                   assist_daily_limit,max_rounds_per_session,session_ttl_seconds,created_at,updated_at
            FROM cloudmold_gamification_game WHERE tenant_id=#{tenantId} AND game_id=#{gameId} FOR UPDATE
            """) Game selectGameForUpdate(@Param("tenantId") Long tenantId, @Param("gameId") String gameId);

    @Insert("""
            INSERT INTO cloudmold_gamification_game_version
              (game_version_id,tenant_id,game_id,game_version,definition_sha256,virtual_currency_code,
               assist_daily_limit,max_rounds_per_session,session_ttl_seconds,published_at)
            VALUES (#{gameVersionId},#{tenantId},#{gameId},#{gameVersion},#{definitionSha256},#{virtualCurrencyCode},
                    #{assistDailyLimit},#{maxRoundsPerSession},#{sessionTtlSeconds},#{publishedAt})
            """) int insertGameVersion(GameVersion value);

    @Update("""
            UPDATE cloudmold_gamification_game SET status='PUBLISHED',current_version=current_version+1,
              virtual_currency_code=#{currencyCode},assist_daily_limit=#{assistLimit},
              max_rounds_per_session=#{maxRounds},session_ttl_seconds=#{ttlSeconds},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND game_id=#{gameId} AND current_version=#{expectedVersion}
            """)
    int publishGame(@Param("tenantId") Long tenantId, @Param("gameId") String gameId,
                    @Param("expectedVersion") Long expectedVersion, @Param("currencyCode") String currencyCode,
                    @Param("assistLimit") Integer assistLimit, @Param("maxRounds") Integer maxRounds,
                    @Param("ttlSeconds") Integer ttlSeconds, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_gamification_currency_account
              (account_id,tenant_id,game_id,owner_type,owner_ref,asset_class,currency_code,balance_microunits,
               status,version,created_at,updated_at)
            VALUES (#{accountId},#{tenantId},#{gameId},#{ownerType},#{ownerRef},#{assetClass},#{currencyCode},
                    #{balanceMicrounits},#{status},#{version},#{createdAt},#{updatedAt})
            """) int insertAccount(Account value);

    @Select("""
            SELECT account_id,tenant_id,game_id,owner_type,owner_ref,asset_class,currency_code,balance_microunits,
                   status,version,created_at,updated_at
            FROM cloudmold_gamification_currency_account
            WHERE tenant_id=#{tenantId} AND game_id=#{gameId} AND owner_type=#{ownerType} AND owner_ref=#{ownerRef}
            """)
    Account selectAccountByOwner(@Param("tenantId") Long tenantId, @Param("gameId") String gameId,
                                 @Param("ownerType") String ownerType, @Param("ownerRef") String ownerRef);

    @Select("""
            SELECT account_id,tenant_id,game_id,owner_type,owner_ref,asset_class,currency_code,balance_microunits,
                   status,version,created_at,updated_at
            FROM cloudmold_gamification_currency_account
            WHERE tenant_id=#{tenantId} AND account_id=#{accountId} FOR UPDATE
            """) Account selectAccountByIdForUpdate(@Param("tenantId") Long tenantId,
                                                        @Param("accountId") String accountId);

    @Update("""
            UPDATE cloudmold_gamification_currency_account
            SET balance_microunits=balance_microunits+#{delta},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND account_id=#{accountId} AND version=#{expectedVersion}
              AND (owner_type='TREASURY' OR balance_microunits+#{delta}>=0)
            """)
    int applyAccountDelta(@Param("tenantId") Long tenantId, @Param("accountId") String accountId,
                          @Param("expectedVersion") Long expectedVersion, @Param("delta") Long delta,
                          @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_gamification_currency_transaction
              (ledger_transaction_id,tenant_id,game_id,currency_code,business_type,business_id,
               amount_microunits,occurred_at,created_at)
            VALUES (#{ledgerTransactionId},#{tenantId},#{gameId},#{currencyCode},#{businessType},#{businessId},
                    #{amountMicrounits},#{occurredAt},#{createdAt})
            """) int insertCurrencyTransaction(CurrencyTransaction value);

    @Insert("""
            INSERT INTO cloudmold_gamification_currency_ledger_entry
              (ledger_entry_id,tenant_id,ledger_transaction_id,entry_sequence,account_id,delta_microunits,
               balance_after_microunits,created_at)
            VALUES (#{ledgerEntryId},#{tenantId},#{ledgerTransactionId},#{entrySequence},#{accountId},
                    #{deltaMicrounits},#{balanceAfterMicrounits},#{createdAt})
            """) int insertCurrencyEntry(CurrencyEntry value);

    @Insert("""
            INSERT INTO cloudmold_gamification_session
              (session_id,tenant_id,game_id,game_version,principal_id,status,round_count,version,
               expires_at,created_at,updated_at)
            VALUES (#{sessionId},#{tenantId},#{gameId},#{gameVersion},#{principalId},#{status},#{roundCount},
                    #{version},#{expiresAt},#{createdAt},#{updatedAt})
            """) int insertSession(Session value);

    @Select("""
            SELECT session_id,tenant_id,game_id,game_version,principal_id,status,round_count,version,
                   expires_at,created_at,updated_at
            FROM cloudmold_gamification_session WHERE tenant_id=#{tenantId} AND session_id=#{sessionId}
            """) Session selectSession(@Param("tenantId") Long tenantId, @Param("sessionId") String sessionId);

    @Select("""
            SELECT session_id,tenant_id,game_id,game_version,principal_id,status,round_count,version,
                   expires_at,created_at,updated_at
            FROM cloudmold_gamification_session
            WHERE tenant_id=#{tenantId} AND session_id=#{sessionId} FOR UPDATE
            """) Session selectSessionForUpdate(@Param("tenantId") Long tenantId, @Param("sessionId") String sessionId);

    @Update("""
            UPDATE cloudmold_gamification_session SET round_count=round_count+1,version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND session_id=#{sessionId} AND version=#{expectedVersion}
              AND status='OPEN' AND round_count<#{maxRounds} AND expires_at>#{now}
            """) int addSessionRound(@Param("tenantId") Long tenantId, @Param("sessionId") String sessionId,
                                      @Param("expectedVersion") Long expectedVersion,
                                      @Param("maxRounds") Integer maxRounds, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_gamification_round
              (round_id,tenant_id,session_id,game_id,game_version,principal_id,round_number,status,
               version,started_at,updated_at)
            VALUES (#{roundId},#{tenantId},#{sessionId},#{gameId},#{gameVersion},#{principalId},#{roundNumber},
                    #{status},#{version},#{startedAt},#{updatedAt})
            """) int insertRound(Round value);

    @Select("""
            SELECT round_id,tenant_id,session_id,game_id,game_version,principal_id,round_number,status,outcome,
                   score,version,started_at,completed_at,updated_at
            FROM cloudmold_gamification_round WHERE tenant_id=#{tenantId} AND round_id=#{roundId} FOR UPDATE
            """) Round selectRoundForUpdate(@Param("tenantId") Long tenantId, @Param("roundId") String roundId);

    @Update("""
            UPDATE cloudmold_gamification_round SET status='COMPLETED',outcome=#{outcome},score=#{score},
              version=version+1,completed_at=#{now},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND round_id=#{roundId} AND status='ACTIVE' AND version=#{expectedVersion}
            """) int completeRound(@Param("tenantId") Long tenantId, @Param("roundId") String roundId,
                                    @Param("expectedVersion") Long expectedVersion, @Param("outcome") String outcome,
                                    @Param("score") Long score, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_gamification_reward_definition
              (reward_definition_id,tenant_id,game_id,reward_code,reward_version,reward_kind,asset_class,
               asset_code,currency_amount_microunits,fragment_quantity,definition_sha256,published_at)
            VALUES (#{rewardDefinitionId},#{tenantId},#{gameId},#{rewardCode},#{rewardVersion},#{rewardKind},
                    #{assetClass},#{assetCode},#{currencyAmountMicrounits},#{fragmentQuantity},
                    #{definitionSha256},#{publishedAt})
            """) int insertRewardDefinition(RewardDefinition value);

    @Select("""
            SELECT reward_definition_id,tenant_id,game_id,reward_code,reward_version,reward_kind,asset_class,
                   asset_code,currency_amount_microunits,fragment_quantity,definition_sha256,published_at
            FROM cloudmold_gamification_reward_definition
            WHERE tenant_id=#{tenantId} AND reward_definition_id=#{rewardDefinitionId} AND reward_version=#{rewardVersion}
            """) RewardDefinition selectRewardDefinition(@Param("tenantId") Long tenantId,
                                                            @Param("rewardDefinitionId") String rewardDefinitionId,
                                                            @Param("rewardVersion") Long rewardVersion);

    @Insert("""
            INSERT INTO cloudmold_gamification_reward_grant
              (reward_grant_id,tenant_id,game_id,principal_id,reward_definition_id,reward_version,
               source_type,source_id,ledger_transaction_id,granted_currency_microunits,
               granted_fragment_quantity,occurred_at,created_at)
            VALUES (#{rewardGrantId},#{tenantId},#{gameId},#{principalId},#{rewardDefinitionId},#{rewardVersion},
                    #{sourceType},#{sourceId},#{ledgerTransactionId},#{grantedCurrencyMicrounits},
                    #{grantedFragmentQuantity},#{occurredAt},#{createdAt})
            """) int insertRewardGrant(RewardGrant value);

    @Update("""
            UPDATE cloudmold_gamification_reward_grant SET ledger_transaction_id=#{ledgerTransactionId}
            WHERE tenant_id=#{tenantId} AND reward_grant_id=#{rewardGrantId} AND ledger_transaction_id IS NULL
            """)
    int attachRewardGrantLedger(@Param("tenantId") Long tenantId, @Param("rewardGrantId") String rewardGrantId,
                                @Param("ledgerTransactionId") String ledgerTransactionId);

    @Insert("""
            INSERT INTO cloudmold_gamification_draw_pool
              (draw_pool_id,tenant_id,game_id,draw_pool_code,pool_version,status,price_microunits,
               total_weight,definition_sha256,published_at)
            VALUES (#{drawPoolId},#{tenantId},#{gameId},#{drawPoolCode},#{poolVersion},#{status},
                    #{priceMicrounits},#{totalWeight},#{definitionSha256},#{publishedAt})
            """) int insertDrawPool(DrawPool value);

    @Insert("""
            INSERT INTO cloudmold_gamification_draw_pool_item
              (draw_pool_item_id,tenant_id,draw_pool_id,pool_version,item_sequence,reward_definition_id,
               reward_version,weight,cumulative_weight)
            VALUES (#{drawPoolItemId},#{tenantId},#{drawPoolId},#{poolVersion},#{itemSequence},
                    #{rewardDefinitionId},#{rewardVersion},#{weight},#{cumulativeWeight})
            """) int insertDrawPoolItem(DrawPoolItem value);

    @Select("""
            SELECT draw_pool_id,tenant_id,game_id,draw_pool_code,pool_version,status,price_microunits,
                   total_weight,definition_sha256,published_at
            FROM cloudmold_gamification_draw_pool
            WHERE tenant_id=#{tenantId} AND draw_pool_id=#{drawPoolId} AND pool_version=#{poolVersion}
            """) DrawPool selectDrawPool(@Param("tenantId") Long tenantId, @Param("drawPoolId") String drawPoolId,
                                           @Param("poolVersion") Long poolVersion);

    @Select("""
            SELECT draw_pool_item_id,tenant_id,draw_pool_id,pool_version,item_sequence,reward_definition_id,
                   reward_version,weight,cumulative_weight
            FROM cloudmold_gamification_draw_pool_item
            WHERE tenant_id=#{tenantId} AND draw_pool_id=#{drawPoolId} AND pool_version=#{poolVersion}
            ORDER BY item_sequence
            """) List<DrawPoolItem> selectDrawPoolItems(@Param("tenantId") Long tenantId,
                                                          @Param("drawPoolId") String drawPoolId,
                                                          @Param("poolVersion") Long poolVersion);

    @Insert("""
            INSERT INTO cloudmold_gamification_draw_request
              (draw_request_id,tenant_id,game_id,principal_id,draw_pool_id,pool_version,charge_transaction_id,
               price_microunits,status,occurred_at,created_at)
            VALUES (#{drawRequestId},#{tenantId},#{gameId},#{principalId},#{drawPoolId},#{poolVersion},
                    #{chargeTransactionId},#{priceMicrounits},#{status},#{occurredAt},#{createdAt})
            """) int insertDrawRequest(DrawRequest value);

    @Insert("""
            INSERT INTO cloudmold_gamification_draw_result
              (draw_result_id,tenant_id,draw_request_id,selected_ticket,total_weight,entropy_sha256,
               draw_pool_item_id,reward_grant_id,created_at)
            VALUES (#{drawResultId},#{tenantId},#{drawRequestId},#{selectedTicket},#{totalWeight},#{entropySha256},
                    #{drawPoolItemId},#{rewardGrantId},#{createdAt})
            """) int insertDrawResult(DrawResult value);

    @Select("""
            SELECT assist_quota_id,tenant_id,game_id,beneficiary_principal_id,quota_date,assist_limit,
                   assists_used,version,created_at,updated_at
            FROM cloudmold_gamification_assist_quota
            WHERE tenant_id=#{tenantId} AND game_id=#{gameId} AND beneficiary_principal_id=#{principalId}
              AND quota_date=#{quotaDate} FOR UPDATE
            """) AssistQuota selectAssistQuotaForUpdate(@Param("tenantId") Long tenantId,
                                                           @Param("gameId") String gameId,
                                                           @Param("principalId") String principalId,
                                                           @Param("quotaDate") LocalDate quotaDate);

    @Insert("""
            INSERT INTO cloudmold_gamification_assist_quota
              (assist_quota_id,tenant_id,game_id,beneficiary_principal_id,quota_date,assist_limit,
               assists_used,version,created_at,updated_at)
            VALUES (#{assistQuotaId},#{tenantId},#{gameId},#{beneficiaryPrincipalId},#{quotaDate},#{assistLimit},
                    #{assistsUsed},#{version},#{createdAt},#{updatedAt})
            """) int insertAssistQuota(AssistQuota value);

    @Update("""
            UPDATE cloudmold_gamification_assist_quota
            SET assists_used=assists_used+1,version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND assist_quota_id=#{quotaId} AND version=#{expectedVersion}
              AND assists_used<assist_limit
            """) int consumeAssistQuota(@Param("tenantId") Long tenantId, @Param("quotaId") String quotaId,
                                          @Param("expectedVersion") Long expectedVersion,
                                          @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_gamification_assist_record
              (assist_record_id,tenant_id,game_id,helper_principal_id,beneficiary_principal_id,
               quota_date,ordinal,occurred_at,created_at)
            VALUES (#{assistRecordId},#{tenantId},#{gameId},#{helperPrincipalId},#{beneficiaryPrincipalId},
                    #{quotaDate},#{ordinal},#{occurredAt},#{createdAt})
            """) int insertAssistRecord(AssistRecord value);

    @Insert("""
            INSERT INTO cloudmold_gamification_task_definition
              (task_definition_id,tenant_id,game_id,task_code,task_version,target_units,reward_definition_id,
               reward_version,definition_sha256,published_at)
            VALUES (#{taskDefinitionId},#{tenantId},#{gameId},#{taskCode},#{taskVersion},#{targetUnits},
                    #{rewardDefinitionId},#{rewardVersion},#{definitionSha256},#{publishedAt})
            """) int insertTaskDefinition(TaskDefinition value);

    @Select("""
            SELECT task_definition_id,tenant_id,game_id,task_code,task_version,target_units,reward_definition_id,
                   reward_version,definition_sha256,published_at
            FROM cloudmold_gamification_task_definition
            WHERE tenant_id=#{tenantId} AND task_definition_id=#{taskDefinitionId} AND task_version=#{taskVersion}
            """) TaskDefinition selectTaskDefinition(@Param("tenantId") Long tenantId,
                                                        @Param("taskDefinitionId") String taskDefinitionId,
                                                        @Param("taskVersion") Long taskVersion);

    @Select("""
            SELECT task_progress_id,tenant_id,task_definition_id,task_version,game_id,principal_id,
                   completed_units,target_units,status,reward_grant_id,version,created_at,updated_at
            FROM cloudmold_gamification_task_progress
            WHERE tenant_id=#{tenantId} AND task_definition_id=#{taskDefinitionId}
              AND task_version=#{taskVersion} AND principal_id=#{principalId}
            """) TaskProgress selectTaskProgress(@Param("tenantId") Long tenantId,
                                                   @Param("taskDefinitionId") String taskDefinitionId,
                                                   @Param("taskVersion") Long taskVersion,
                                                   @Param("principalId") String principalId);

    @Select("""
            SELECT task_progress_id,tenant_id,task_definition_id,task_version,game_id,principal_id,
                   completed_units,target_units,status,reward_grant_id,version,created_at,updated_at
            FROM cloudmold_gamification_task_progress
            WHERE tenant_id=#{tenantId} AND task_definition_id=#{taskDefinitionId}
              AND task_version=#{taskVersion} AND principal_id=#{principalId} FOR UPDATE
            """) TaskProgress selectTaskProgressForUpdate(@Param("tenantId") Long tenantId,
                                                            @Param("taskDefinitionId") String taskDefinitionId,
                                                            @Param("taskVersion") Long taskVersion,
                                                            @Param("principalId") String principalId);

    @Insert("""
            INSERT INTO cloudmold_gamification_task_progress
              (task_progress_id,tenant_id,task_definition_id,task_version,game_id,principal_id,
               completed_units,target_units,status,reward_grant_id,version,created_at,updated_at)
            VALUES (#{taskProgressId},#{tenantId},#{taskDefinitionId},#{taskVersion},#{gameId},#{principalId},
                    #{completedUnits},#{targetUnits},#{status},#{rewardGrantId},#{version},#{createdAt},#{updatedAt})
            """) int insertTaskProgress(TaskProgress value);

    @Update("""
            UPDATE cloudmold_gamification_task_progress
            SET completed_units=#{completedUnits},status=#{status},reward_grant_id=#{rewardGrantId},
              version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND task_progress_id=#{progressId} AND version=#{expectedVersion}
              AND status='IN_PROGRESS'
            """) int updateTaskProgress(@Param("tenantId") Long tenantId, @Param("progressId") String progressId,
                                          @Param("expectedVersion") Long expectedVersion,
                                          @Param("completedUnits") Long completedUnits,
                                          @Param("status") String status,
                                          @Param("rewardGrantId") String rewardGrantId,
                                          @Param("now") LocalDateTime now);

    @Select("""
            SELECT fragment_balance_id,tenant_id,game_id,principal_id,asset_class,fragment_code,quantity,
                   version,created_at,updated_at
            FROM cloudmold_gamification_fragment_balance
            WHERE tenant_id=#{tenantId} AND game_id=#{gameId} AND principal_id=#{principalId}
              AND fragment_code=#{fragmentCode}
            """) FragmentBalance selectFragmentBalance(@Param("tenantId") Long tenantId,
                                                          @Param("gameId") String gameId,
                                                          @Param("principalId") String principalId,
                                                          @Param("fragmentCode") String fragmentCode);

    @Select("""
            SELECT fragment_balance_id,tenant_id,game_id,principal_id,asset_class,fragment_code,quantity,
                   version,created_at,updated_at
            FROM cloudmold_gamification_fragment_balance
            WHERE tenant_id=#{tenantId} AND game_id=#{gameId} AND principal_id=#{principalId}
              AND fragment_code=#{fragmentCode} FOR UPDATE
            """) FragmentBalance selectFragmentBalanceForUpdate(@Param("tenantId") Long tenantId,
                                                                   @Param("gameId") String gameId,
                                                                   @Param("principalId") String principalId,
                                                                   @Param("fragmentCode") String fragmentCode);

    @Insert("""
            INSERT INTO cloudmold_gamification_fragment_balance
              (fragment_balance_id,tenant_id,game_id,principal_id,asset_class,fragment_code,quantity,
               version,created_at,updated_at)
            VALUES (#{fragmentBalanceId},#{tenantId},#{gameId},#{principalId},#{assetClass},#{fragmentCode},
                    #{quantity},#{version},#{createdAt},#{updatedAt})
            """) int insertFragmentBalance(FragmentBalance value);

    @Update("""
            UPDATE cloudmold_gamification_fragment_balance
            SET quantity=quantity+#{delta},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND fragment_balance_id=#{balanceId} AND version=#{expectedVersion}
              AND quantity+#{delta}>=0
            """) int applyFragmentDelta(@Param("tenantId") Long tenantId, @Param("balanceId") String balanceId,
                                          @Param("expectedVersion") Long expectedVersion,
                                          @Param("delta") Long delta, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_gamification_fragment_ledger_entry
              (fragment_entry_id,tenant_id,fragment_balance_id,reward_grant_id,delta_quantity,
               balance_after_quantity,occurred_at,created_at)
            VALUES (#{fragmentEntryId},#{tenantId},#{fragmentBalanceId},#{rewardGrantId},#{deltaQuantity},
                    #{balanceAfterQuantity},#{occurredAt},#{createdAt})
            """) int insertFragmentEntry(FragmentEntry value);

    @Insert("""
            INSERT INTO cloudmold_gamification_gift_transfer
              (gift_transfer_id,tenant_id,game_id,currency_code,from_principal_id,to_principal_id,
               amount_microunits,ledger_transaction_id,reason_code,occurred_at,created_at)
            VALUES (#{giftTransferId},#{tenantId},#{gameId},#{currencyCode},#{fromPrincipalId},#{toPrincipalId},
                    #{amountMicrounits},#{ledgerTransactionId},#{reasonCode},#{occurredAt},#{createdAt})
            """) int insertGiftTransfer(GiftTransfer value);

    @Insert("""
            INSERT INTO cloudmold_gamification_status_history
              (tenant_id,aggregate_type,aggregate_id,aggregate_version,previous_status,current_status,
               operation_id,occurred_at,created_at)
            VALUES (#{tenantId},#{aggregateType},#{aggregateId},#{aggregateVersion},#{previousStatus},
                    #{currentStatus},#{operationId},#{occurredAt},#{createdAt})
            """) int insertStatusHistory(StatusHistory value);
}
