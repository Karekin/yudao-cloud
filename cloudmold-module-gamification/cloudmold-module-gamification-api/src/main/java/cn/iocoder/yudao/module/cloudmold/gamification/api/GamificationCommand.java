package cn.iocoder.yudao.module.cloudmold.gamification.api;

import lombok.*;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class GamificationCommand {
    private GamificationOperation operation;
    private String idempotencyKey;
    private String runId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    private Long expectedVersion;

    private String gameId;
    private String gameCode;
    private String gameName;
    private String virtualCurrencyCode;
    private Integer assistDailyLimit;
    private Integer maxRoundsPerSession;
    private Integer sessionTtlSeconds;

    private String principalId;
    private String sessionId;
    private String roundId;
    private Long score;
    private String roundOutcome;

    private String rewardDefinitionId;
    private Long rewardVersion;
    private String rewardCode;
    private String rewardKind;
    private String assetCode;
    private Long assetAmountMicrounits;
    private Long rewardFragmentQuantity;
    private String rewardSourceType;
    private String rewardSourceId;
    private String evidenceRef;

    private String drawPoolId;
    private String drawPoolCode;
    private Long drawPoolVersion;
    private Long drawPriceMicrounits;
    private List<DrawPoolItem> drawItems;

    private String helperPrincipalId;
    private String beneficiaryPrincipalId;

    private String taskDefinitionId;
    private String taskCode;
    private Long taskVersion;
    private Long targetUnits;
    private Long progressDelta;

    private String fragmentCode;
    private String fromPrincipalId;
    private String toPrincipalId;
    private Long giftAmountMicrounits;
    private String reasonCode;

    private String seasonSeriesId;
    private String seasonSeriesCode;
    private Long seasonSeriesVersion;
    private String seasonSeriesName;
    private String seasonId;
    private String seasonCode;
    private Long seasonVersion;
    private String seasonName;
    private Instant seasonStartsAt;
    private Instant seasonEndsAt;

    private String collectibleDefinitionId;
    private String collectibleCode;
    private Long collectibleVersion;
    private String collectibleKind;
    private String collectibleName;
    private Long collectibleQuantity;

    private String redemptionIntentId;
    private String adapterCode;
    private String externalIntentRef;
    private String externalResultRef;
    private String redemptionOutcome;

    private String rewardClaimId;
    private Instant claimExpiresAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DrawPoolItem {
        private String rewardDefinitionId;
        private Long rewardVersion;
        private Integer weight;
    }
}
