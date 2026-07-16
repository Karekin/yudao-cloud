package cn.iocoder.yudao.module.cloudmold.gamification.service;

import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.gamification.api.GamificationCommand;
import cn.iocoder.yudao.module.cloudmold.gamification.dal.dataobject.GamificationRecords.*;
import cn.iocoder.yudao.module.cloudmold.gamification.dal.mysql.GamificationStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GamificationEventService {
    static final String SOURCE_SYSTEM = "cloudmold-gamification";

    private final GamificationStoreMapper mapper;
    private final OutboxAppender outboxAppender;

    public void appendGameCreated(Long operationId, Game game, GamificationCommand command,
                                  Instant occurredAt, LocalDateTime now) {
        history(operationId, game.getTenantId(), "GAME", game.getGameId(), 0L, null,
                game.getStatus(), occurredAt, now);
        append("gamification.game.created", "gamification_game_lifecycle", game.getGameId(), 1L,
                game.getTenantId(), command, occurredAt,
                payload("game_id", game.getGameId(), "game_code", game.getGameCode(),
                        "game_name", game.getGameName(), "definition_version", game.getCurrentVersion(),
                        "current_status", game.getStatus()));
    }

    public void appendAccountOpened(Account account, GamificationCommand command, Instant occurredAt) {
        append("gamification.virtual_currency.account_opened", "gamification_currency_account",
                account.getAccountId(), account.getVersion(), account.getTenantId(), command, occurredAt,
                payload("account_id", account.getAccountId(), "game_id", account.getGameId(),
                        "owner_type", account.getOwnerType(), "owner_ref", account.getOwnerRef(),
                        "asset_class", account.getAssetClass(), "currency_code", account.getCurrencyCode(),
                        "balance_microunits", account.getBalanceMicrounits(), "status", account.getStatus()));
    }

    public void appendRewardDefinition(RewardDefinition definition, GamificationCommand command,
                                       Instant occurredAt) {
        append("gamification.reward.definition_published", "gamification_reward_definition",
                definition.getRewardDefinitionId(), definition.getRewardVersion(), definition.getTenantId(),
                command, occurredAt,
                payload("reward_definition_id", definition.getRewardDefinitionId(),
                        "game_id", definition.getGameId(), "reward_code", definition.getRewardCode(),
                        "reward_version", definition.getRewardVersion(), "reward_kind", definition.getRewardKind(),
                        "asset_class", definition.getAssetClass(), "asset_code", definition.getAssetCode(),
                        "currency_amount_microunits", definition.getCurrencyAmountMicrounits(),
                        "fragment_quantity", definition.getFragmentQuantity(),
                        "definition_sha256", definition.getDefinitionSha256()));
    }

    public void appendDrawPool(DrawPool pool, GamificationCommand command, Instant occurredAt) {
        append("gamification.draw_pool.version_published", "gamification_draw_pool", pool.getDrawPoolId(),
                pool.getPoolVersion(), pool.getTenantId(), command, occurredAt,
                payload("draw_pool_id", pool.getDrawPoolId(), "game_id", pool.getGameId(),
                        "draw_pool_code", pool.getDrawPoolCode(), "pool_version", pool.getPoolVersion(),
                        "status", pool.getStatus(), "price_microunits", pool.getPriceMicrounits(),
                        "total_weight", pool.getTotalWeight(), "definition_sha256", pool.getDefinitionSha256()));
    }

    public void appendTaskDefinition(TaskDefinition definition, GamificationCommand command, Instant occurredAt) {
        append("gamification.task.definition_published", "gamification_task_definition",
                definition.getTaskDefinitionId(), definition.getTaskVersion(), definition.getTenantId(), command,
                occurredAt, payload("task_definition_id", definition.getTaskDefinitionId(),
                        "game_id", definition.getGameId(), "task_code", definition.getTaskCode(),
                        "task_version", definition.getTaskVersion(), "target_units", definition.getTargetUnits(),
                        "reward_definition_id", definition.getRewardDefinitionId(),
                        "reward_version", definition.getRewardVersion(),
                        "definition_sha256", definition.getDefinitionSha256()));
    }

    public void appendGame(Long operationId, Game game, GameVersion version, String previousStatus,
                           GamificationCommand command, Instant occurredAt, LocalDateTime now) {
        history(operationId, game.getTenantId(), "GAME", game.getGameId(), game.getCurrentVersion(), previousStatus,
                game.getStatus(), occurredAt, now);
        Map<String, Object> payload = payload("game_id", game.getGameId(), "game_code", game.getGameCode(),
                "game_version", game.getCurrentVersion(), "definition_sha256", version.getDefinitionSha256(),
                "previous_status", previousStatus, "current_status", game.getStatus(),
                "virtual_currency_code", game.getVirtualCurrencyCode(), "assist_daily_limit", game.getAssistDailyLimit(),
                "max_rounds_per_session", game.getMaxRoundsPerSession(),
                "session_ttl_seconds", game.getSessionTtlSeconds());
        append("gamification.game.definition_published", "gamification_game", game.getGameId(),
                game.getCurrentVersion(), game.getTenantId(), command, occurredAt, payload);
    }

    public void appendSession(Long operationId, Session session, String previousStatus, GamificationCommand command,
                              Instant occurredAt, LocalDateTime now) {
        history(operationId, session.getTenantId(), "SESSION", session.getSessionId(), session.getVersion(),
                previousStatus, session.getStatus(), occurredAt, now);
        append("gamification.session.status_changed", "gamification_session", session.getSessionId(),
                session.getVersion(), session.getTenantId(), command, occurredAt,
                payload("session_id", session.getSessionId(), "game_id", session.getGameId(),
                        "game_version", session.getGameVersion(), "principal_id", session.getPrincipalId(),
                        "previous_status", previousStatus, "current_status", session.getStatus(),
                        "round_count", session.getRoundCount(), "expires_at",
                        session.getExpiresAt().toInstant(ZoneOffset.UTC).toString()));
    }

    public void appendRound(Long operationId, Round round, String previousStatus, GamificationCommand command,
                            Instant occurredAt, LocalDateTime now) {
        history(operationId, round.getTenantId(), "ROUND", round.getRoundId(), round.getVersion(), previousStatus,
                round.getStatus(), occurredAt, now);
        append("gamification.round.status_changed", "gamification_round", round.getRoundId(), round.getVersion(),
                round.getTenantId(), command, occurredAt,
                payload("round_id", round.getRoundId(), "session_id", round.getSessionId(),
                        "game_id", round.getGameId(), "game_version", round.getGameVersion(),
                        "principal_id", round.getPrincipalId(), "round_number", round.getRoundNumber(),
                        "previous_status", previousStatus, "current_status", round.getStatus(),
                        "outcome", round.getOutcome(), "score", round.getScore()));
    }

    public void appendCurrencyLedger(CurrencyTransaction transaction, Account debit, Account credit,
                                     GamificationCommand command, Instant occurredAt) {
        append("gamification.virtual_currency.ledger_posted", "gamification_currency_transaction",
                transaction.getLedgerTransactionId(), 1L, transaction.getTenantId(), command, occurredAt,
                payload("ledger_transaction_id", transaction.getLedgerTransactionId(), "game_id", transaction.getGameId(),
                        "asset_class", "GAME_VIRTUAL_CURRENCY", "currency_code", transaction.getCurrencyCode(),
                        "business_type", transaction.getBusinessType(), "business_id", transaction.getBusinessId(),
                        "amount_microunits", transaction.getAmountMicrounits(), "entry_count", 2,
                        "entry_delta_sum_microunits", 0, "debit_account_id", debit.getAccountId(),
                        "debit_balance_after_microunits", debit.getBalanceMicrounits(),
                        "credit_account_id", credit.getAccountId(),
                        "credit_balance_after_microunits", credit.getBalanceMicrounits()));
    }

    public void appendReward(RewardGrant grant, RewardDefinition definition, GamificationCommand command,
                             Instant occurredAt) {
        append("gamification.reward.granted", "gamification_reward_grant", grant.getRewardGrantId(), 1L,
                grant.getTenantId(), command, occurredAt,
                payload("reward_grant_id", grant.getRewardGrantId(), "game_id", grant.getGameId(),
                        "principal_id", grant.getPrincipalId(), "reward_definition_id", grant.getRewardDefinitionId(),
                        "reward_version", grant.getRewardVersion(), "reward_kind", definition.getRewardKind(),
                        "asset_class", definition.getAssetClass(), "asset_code", definition.getAssetCode(),
                        "currency_amount_microunits", grant.getGrantedCurrencyMicrounits(),
                        "fragment_quantity", grant.getGrantedFragmentQuantity(), "source_type", grant.getSourceType(),
                        "source_id", grant.getSourceId(), "ledger_transaction_id", grant.getLedgerTransactionId()));
    }

    public void appendDraw(DrawRequest request, DrawResult result, DrawPoolItem selected,
                           GamificationCommand command, Instant occurredAt) {
        append("gamification.draw.completed", "gamification_draw_request", request.getDrawRequestId(), 1L,
                request.getTenantId(), command, occurredAt,
                payload("draw_request_id", request.getDrawRequestId(), "draw_result_id", result.getDrawResultId(),
                        "game_id", request.getGameId(), "principal_id", request.getPrincipalId(),
                        "draw_pool_id", request.getDrawPoolId(), "draw_pool_version", request.getPoolVersion(),
                        "price_microunits", request.getPriceMicrounits(), "selected_ticket", result.getSelectedTicket(),
                        "total_weight", result.getTotalWeight(), "entropy_sha256", result.getEntropySha256(),
                        "reward_definition_id", selected.getRewardDefinitionId(),
                        "reward_version", selected.getRewardVersion(), "reward_grant_id", result.getRewardGrantId()));
    }

    public void appendAssist(AssistRecord record, AssistQuota quota, GamificationCommand command, Instant occurredAt) {
        append("gamification.assist.recorded", "gamification_assist_record", record.getAssistRecordId(), 1L,
                record.getTenantId(), command, occurredAt,
                payload("assist_record_id", record.getAssistRecordId(), "game_id", record.getGameId(),
                        "helper_principal_id", record.getHelperPrincipalId(),
                        "beneficiary_principal_id", record.getBeneficiaryPrincipalId(),
                        "quota_date", record.getQuotaDate().toString(), "ordinal", record.getOrdinal(),
                        "assist_limit", quota.getAssistLimit(), "assists_used", quota.getAssistsUsed()));
    }

    public void appendTask(Long operationId, TaskProgress progress, String previousStatus,
                           GamificationCommand command, Instant occurredAt, LocalDateTime now) {
        history(operationId, progress.getTenantId(), "TASK_PROGRESS", progress.getTaskProgressId(),
                progress.getVersion(), previousStatus, progress.getStatus(), occurredAt, now);
        append("gamification.task.progress_changed", "gamification_task_progress", progress.getTaskProgressId(),
                progress.getVersion(), progress.getTenantId(), command, occurredAt,
                payload("task_progress_id", progress.getTaskProgressId(), "task_definition_id",
                        progress.getTaskDefinitionId(), "task_version", progress.getTaskVersion(),
                        "game_id", progress.getGameId(), "principal_id", progress.getPrincipalId(),
                        "completed_units", progress.getCompletedUnits(), "target_units", progress.getTargetUnits(),
                        "previous_status", previousStatus, "current_status", progress.getStatus(),
                        "reward_grant_id", progress.getRewardGrantId()));
    }

    public void appendFragment(FragmentEntry entry, FragmentBalance balance, GamificationCommand command,
                               Instant occurredAt) {
        append("gamification.fragment.ledger_posted", "gamification_fragment_balance",
                balance.getFragmentBalanceId(), balance.getVersion(), balance.getTenantId(), command, occurredAt,
                payload("fragment_entry_id", entry.getFragmentEntryId(), "fragment_balance_id",
                        balance.getFragmentBalanceId(), "game_id", balance.getGameId(),
                        "principal_id", balance.getPrincipalId(), "asset_class", balance.getAssetClass(),
                        "fragment_code", balance.getFragmentCode(), "delta_quantity", entry.getDeltaQuantity(),
                        "balance_after_quantity", entry.getBalanceAfterQuantity(),
                        "reward_grant_id", entry.getRewardGrantId()));
    }

    public void appendGift(GiftTransfer gift, GamificationCommand command, Instant occurredAt) {
        append("gamification.gift.transferred", "gamification_gift_transfer", gift.getGiftTransferId(), 1L,
                gift.getTenantId(), command, occurredAt,
                payload("gift_transfer_id", gift.getGiftTransferId(), "game_id", gift.getGameId(),
                        "asset_class", "GAME_VIRTUAL_CURRENCY", "currency_code", gift.getCurrencyCode(),
                        "from_principal_id", gift.getFromPrincipalId(), "to_principal_id", gift.getToPrincipalId(),
                        "amount_microunits", gift.getAmountMicrounits(),
                        "ledger_transaction_id", gift.getLedgerTransactionId(), "reason_code", gift.getReasonCode()));
    }

    public void appendSeasonSeries(SeasonSeries value, GamificationCommand command, Instant occurredAt) {
        append("gamification.season_series.version_published", "gamification_season_series",
                value.getSeasonSeriesId(), value.getSeriesVersion(), value.getTenantId(), command, occurredAt,
                payload("season_series_id", value.getSeasonSeriesId(), "series_version", value.getSeriesVersion(),
                        "game_id", value.getGameId(), "series_code", value.getSeriesCode(),
                        "series_name", value.getSeriesName(),
                        "definition_sha256", value.getDefinitionSha256()));
    }

    public void appendSeason(Season value, GamificationCommand command, Instant occurredAt) {
        append("gamification.season.version_published", "gamification_season", value.getSeasonId(),
                value.getSeasonVersion(), value.getTenantId(), command, occurredAt,
                payload("season_id", value.getSeasonId(), "season_version", value.getSeasonVersion(),
                        "season_series_id", value.getSeasonSeriesId(), "series_version", value.getSeriesVersion(),
                        "game_id", value.getGameId(), "season_code", value.getSeasonCode(),
                        "season_name", value.getSeasonName(), "status", value.getStatus(),
                        "starts_at", value.getStartsAt().toInstant(ZoneOffset.UTC).toString(),
                        "ends_at", value.getEndsAt().toInstant(ZoneOffset.UTC).toString(),
                        "definition_sha256", value.getDefinitionSha256()));
    }

    public void appendCollectibleDefinition(CollectibleDefinition value, GamificationCommand command,
                                            Instant occurredAt) {
        append("gamification.collectible.version_published", "gamification_collectible_definition",
                value.getCollectibleDefinitionId(), value.getCollectibleVersion(), value.getTenantId(), command,
                occurredAt, payload("collectible_definition_id", value.getCollectibleDefinitionId(),
                        "collectible_version", value.getCollectibleVersion(), "game_id", value.getGameId(),
                        "collectible_code", value.getCollectibleCode(), "collectible_kind", value.getCollectibleKind(),
                        "collectible_name", value.getCollectibleName(),
                        "definition_sha256", value.getDefinitionSha256()));
    }

    public void appendCollectible(CollectibleLedgerEntry entry, CollectibleOwnership ownership,
                                  GamificationCommand command, Instant occurredAt) {
        append("gamification.collectible.ownership_changed", "gamification_collectible_ownership",
                ownership.getOwnershipId(), ownership.getVersion(), ownership.getTenantId(), command, occurredAt,
                payload("collectible_entry_id", entry.getCollectibleEntryId(), "ownership_id", ownership.getOwnershipId(),
                        "game_id", ownership.getGameId(), "principal_id", ownership.getPrincipalId(),
                        "collectible_definition_id", ownership.getCollectibleDefinitionId(),
                        "collectible_version", ownership.getCollectibleVersion(), "delta_quantity", entry.getDeltaQuantity(),
                        "quantity", ownership.getQuantity(), "source_type", entry.getSourceType(),
                        "source_id", entry.getSourceId()));
    }

    public void appendRedemption(Long operationId, RedemptionIntent value, String previousStatus,
                                 GamificationCommand command, Instant occurredAt, LocalDateTime now) {
        history(operationId, value.getTenantId(), "REDEMPTION", value.getRedemptionIntentId(), value.getVersion(),
                previousStatus, value.getStatus(), occurredAt, now);
        append("gamification.redemption.status_changed", "gamification_redemption_intent",
                value.getRedemptionIntentId(), value.getVersion(), value.getTenantId(), command, occurredAt,
                payload("redemption_intent_id", value.getRedemptionIntentId(), "game_id", value.getGameId(),
                        "principal_id", value.getPrincipalId(), "adapter_code", value.getAdapterCode(),
                        "source_asset_class", value.getSourceAssetClass(),
                        "collectible_definition_id", value.getCollectibleDefinitionId(),
                        "collectible_version", value.getCollectibleVersion(), "quantity", value.getQuantity(),
                        "currency_code", value.getCurrencyCode(), "amount_microunits", value.getAmountMicrounits(),
                        "external_intent_ref", value.getExternalIntentRef(),
                        "external_result_ref", value.getExternalResultRef(), "previous_status", previousStatus,
                        "current_status", value.getStatus(), "source_ledger_transaction_id",
                        value.getSourceLedgerTransactionId(), "contains_external_balance", false));
    }

    public void appendClaim(Long operationId, RewardClaim value, String previousStatus,
                            GamificationCommand command, Instant occurredAt, LocalDateTime now) {
        history(operationId, value.getTenantId(), "REWARD_CLAIM", value.getRewardClaimId(), value.getVersion(),
                previousStatus, value.getStatus(), occurredAt, now);
        append("gamification.reward_claim.status_changed", "gamification_reward_claim", value.getRewardClaimId(),
                value.getVersion(), value.getTenantId(), command, occurredAt,
                payload("reward_claim_id", value.getRewardClaimId(), "game_id", value.getGameId(),
                        "principal_id", value.getPrincipalId(), "reward_definition_id", value.getRewardDefinitionId(),
                        "reward_version", value.getRewardVersion(), "previous_status", previousStatus,
                        "current_status", value.getStatus(), "reward_grant_id", value.getRewardGrantId(),
                        "source_type", value.getSourceType(), "source_id", value.getSourceId(),
                        "claimed_at", value.getClaimedAt() == null ? null
                                : value.getClaimedAt().toInstant(ZoneOffset.UTC).toString(),
                        "claim_expires_at", value.getClaimExpiresAt().toInstant(ZoneOffset.UTC).toString()));
    }

    private void history(Long operationId, Long tenantId, String type, String id, Long version, String previous,
                         String current, Instant occurredAt, LocalDateTime now) {
        mapper.insertStatusHistory(new StatusHistory().setTenantId(tenantId).setAggregateType(type).setAggregateId(id)
                .setAggregateVersion(version).setPreviousStatus(previous).setCurrentStatus(current)
                .setOperationId(operationId).setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
                .setCreatedAt(now));
    }

    private void append(String eventType, String aggregateType, String aggregateId, Long version, Long tenantId,
                        GamificationCommand command, Instant occurredAt, Map<String, Object> payload) {
        outboxAppender.append(AppendDomainEventCommand.builder().eventType(eventType).schemaVersion(1)
                .sourceSystem(SOURCE_SYSTEM).tenantId(tenantId).aggregateType(aggregateType)
                .aggregateId(aggregateId).aggregateVersion(version).eventSequence((short) 1)
                .occurredAt(occurredAt).traceId(command.getRunId()).correlationId(command.getCorrelationId())
                .causationId(command.getCausationId())
                .idempotencyKey(aggregateType + ":" + aggregateId + ":event:" + version)
                .payload(payload).headers(Map.of("pii_safe", true, "asset_boundary", "GAME_ONLY"))
                .destination("lakehouse").build());
    }

    private static Map<String, Object> payload(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            result.put((String) values[i], values[i + 1]);
        }
        return result;
    }
}
