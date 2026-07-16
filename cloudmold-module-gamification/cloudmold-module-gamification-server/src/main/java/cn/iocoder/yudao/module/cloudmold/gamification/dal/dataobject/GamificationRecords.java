package cn.iocoder.yudao.module.cloudmold.gamification.dal.dataobject;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class GamificationRecords {
    private GamificationRecords() {
    }

    @Data @Accessors(chain = true)
    public static class Operation {
        private Long operationId; private Long tenantId; private String idempotencyKey; private String commandType;
        private String requestHash; private String attemptToken; private Integer status; private String aggregateId;
        private String resultJson; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class Game {
        private String gameId; private Long tenantId; private String gameCode; private String gameName;
        private String status; private Long currentVersion; private String virtualCurrencyCode;
        private Integer assistDailyLimit; private Integer maxRoundsPerSession; private Integer sessionTtlSeconds;
        private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class GameVersion {
        private String gameVersionId; private Long tenantId; private String gameId; private Long gameVersion;
        private String definitionSha256; private String virtualCurrencyCode; private Integer assistDailyLimit;
        private Integer maxRoundsPerSession; private Integer sessionTtlSeconds; private LocalDateTime publishedAt;
    }

    @Data @Accessors(chain = true)
    public static class Account {
        private String accountId; private Long tenantId; private String gameId; private String ownerType;
        private String ownerRef; private String assetClass; private String currencyCode; private Long balanceMicrounits;
        private String status; private Long version; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class CurrencyTransaction {
        private String ledgerTransactionId; private Long tenantId; private String gameId; private String currencyCode;
        private String businessType; private String businessId; private Long amountMicrounits;
        private LocalDateTime occurredAt; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class CurrencyEntry {
        private String ledgerEntryId; private Long tenantId; private String ledgerTransactionId; private Integer entrySequence;
        private String accountId; private Long deltaMicrounits; private Long balanceAfterMicrounits;
        private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class Session {
        private String sessionId; private Long tenantId; private String gameId; private Long gameVersion;
        private String principalId; private String status; private Integer roundCount; private Long version;
        private LocalDateTime expiresAt; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class Round {
        private String roundId; private Long tenantId; private String sessionId; private String gameId;
        private Long gameVersion; private String principalId; private Integer roundNumber; private String status;
        private String outcome; private Long score; private Long version; private LocalDateTime startedAt;
        private LocalDateTime completedAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class RewardDefinition {
        private String rewardDefinitionId; private Long tenantId; private String gameId; private String rewardCode;
        private Long rewardVersion; private String rewardKind; private String assetClass; private String assetCode;
        private Long currencyAmountMicrounits; private Long fragmentQuantity; private String definitionSha256;
        private LocalDateTime publishedAt;
    }

    @Data @Accessors(chain = true)
    public static class RewardGrant {
        private String rewardGrantId; private Long tenantId; private String gameId; private String principalId;
        private String rewardDefinitionId; private Long rewardVersion; private String sourceType; private String sourceId;
        private String ledgerTransactionId; private Long grantedCurrencyMicrounits; private Long grantedFragmentQuantity;
        private LocalDateTime occurredAt; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class DrawPool {
        private String drawPoolId; private Long tenantId; private String gameId; private String drawPoolCode;
        private Long poolVersion; private String status; private Long priceMicrounits; private Integer totalWeight;
        private String definitionSha256; private LocalDateTime publishedAt;
    }

    @Data @Accessors(chain = true)
    public static class DrawPoolItem {
        private String drawPoolItemId; private Long tenantId; private String drawPoolId; private Long poolVersion;
        private Integer itemSequence; private String rewardDefinitionId; private Long rewardVersion;
        private Integer weight; private Integer cumulativeWeight;
    }

    @Data @Accessors(chain = true)
    public static class DrawRequest {
        private String drawRequestId; private Long tenantId; private String gameId; private String principalId;
        private String drawPoolId; private Long poolVersion; private String chargeTransactionId;
        private Long priceMicrounits; private String status; private LocalDateTime occurredAt; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class DrawResult {
        private String drawResultId; private Long tenantId; private String drawRequestId; private Integer selectedTicket;
        private Integer totalWeight; private String entropySha256; private String drawPoolItemId;
        private String rewardGrantId; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class AssistQuota {
        private String assistQuotaId; private Long tenantId; private String gameId; private String beneficiaryPrincipalId;
        private LocalDate quotaDate; private Integer assistLimit; private Integer assistsUsed; private Long version;
        private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class AssistRecord {
        private String assistRecordId; private Long tenantId; private String gameId; private String helperPrincipalId;
        private String beneficiaryPrincipalId; private LocalDate quotaDate; private Integer ordinal;
        private LocalDateTime occurredAt; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class TaskDefinition {
        private String taskDefinitionId; private Long tenantId; private String gameId; private String taskCode;
        private Long taskVersion; private Long targetUnits; private String rewardDefinitionId; private Long rewardVersion;
        private String definitionSha256; private LocalDateTime publishedAt;
    }

    @Data @Accessors(chain = true)
    public static class TaskProgress {
        private String taskProgressId; private Long tenantId; private String taskDefinitionId; private Long taskVersion;
        private String gameId; private String principalId; private Long completedUnits; private Long targetUnits;
        private String status; private String rewardGrantId; private Long version; private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class FragmentBalance {
        private String fragmentBalanceId; private Long tenantId; private String gameId; private String principalId;
        private String assetClass; private String fragmentCode; private Long quantity; private Long version;
        private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class FragmentEntry {
        private String fragmentEntryId; private Long tenantId; private String fragmentBalanceId;
        private String rewardGrantId; private Long deltaQuantity; private Long balanceAfterQuantity;
        private LocalDateTime occurredAt; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class GiftTransfer {
        private String giftTransferId; private Long tenantId; private String gameId; private String currencyCode;
        private String fromPrincipalId; private String toPrincipalId; private Long amountMicrounits;
        private String ledgerTransactionId; private String reasonCode; private LocalDateTime occurredAt;
        private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class StatusHistory {
        private Long tenantId; private String aggregateType; private String aggregateId; private Long aggregateVersion;
        private String previousStatus; private String currentStatus; private Long operationId;
        private LocalDateTime occurredAt; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class SeasonSeries {
        private String seasonSeriesId; private Long tenantId; private String gameId; private String seriesCode;
        private Long seriesVersion; private String seriesName; private String definitionSha256;
        private LocalDateTime publishedAt;
    }

    @Data @Accessors(chain = true)
    public static class Season {
        private String seasonId; private Long tenantId; private String gameId; private String seasonSeriesId;
        private Long seriesVersion; private String seasonCode; private Long seasonVersion; private String seasonName;
        private String status; private LocalDateTime startsAt; private LocalDateTime endsAt;
        private String definitionSha256; private LocalDateTime publishedAt;
    }

    @Data @Accessors(chain = true)
    public static class CollectibleDefinition {
        private String collectibleDefinitionId; private Long tenantId; private String gameId;
        private String collectibleCode; private Long collectibleVersion; private String collectibleKind;
        private String collectibleName; private String definitionSha256; private LocalDateTime publishedAt;
    }

    @Data @Accessors(chain = true)
    public static class CollectibleOwnership {
        private String ownershipId; private Long tenantId; private String gameId; private String principalId;
        private String collectibleDefinitionId; private Long collectibleVersion; private Long quantity;
        private Long version; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class CollectibleLedgerEntry {
        private String collectibleEntryId; private Long tenantId; private String ownershipId; private String sourceType;
        private String sourceId; private Long deltaQuantity; private Long balanceAfterQuantity;
        private LocalDateTime occurredAt; private LocalDateTime createdAt;
    }

    @Data @Accessors(chain = true)
    public static class RedemptionIntent {
        private String redemptionIntentId; private Long tenantId; private String gameId; private String principalId;
        private String sourceAssetClass; private String collectibleDefinitionId; private Long collectibleVersion;
        private Long quantity; private String currencyCode; private Long amountMicrounits;
        private String adapterCode; private String externalIntentRef; private String externalResultRef;
        private String sourceLedgerTransactionId; private String status; private Long version; private LocalDateTime occurredAt;
        private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class RewardClaim {
        private String rewardClaimId; private Long tenantId; private String gameId; private String principalId;
        private String rewardDefinitionId; private Long rewardVersion; private String sourceType; private String sourceId;
        private String status; private String rewardGrantId; private Long version; private LocalDateTime claimExpiresAt;
        private LocalDateTime claimedAt; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }
}
