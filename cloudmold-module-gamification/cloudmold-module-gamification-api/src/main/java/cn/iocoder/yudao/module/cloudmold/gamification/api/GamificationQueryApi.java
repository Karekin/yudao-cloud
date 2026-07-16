package cn.iocoder.yudao.module.cloudmold.gamification.api;

public interface GamificationQueryApi {
    GamificationView getGame(String gameId);
    GamificationView getPlayerAccount(String gameId, String principalId);
    GamificationView getSession(String sessionId);
    GamificationView getTaskProgress(String taskDefinitionId, Long taskVersion, String principalId);
    GamificationView getFragmentBalance(String gameId, String principalId, String fragmentCode);
    GamificationView getCollectibleOwnership(String gameId, String principalId, String collectibleDefinitionId,
                                              Long collectibleVersion);
    GamificationView getRewardClaim(String rewardClaimId);
    GamificationView getRedemption(String redemptionIntentId);
    GamificationView getSeasonSeries(String seasonSeriesId, Long seriesVersion);
    GamificationView getSeason(String seasonId, Long seasonVersion);
    GamificationView getCollectibleDefinition(String collectibleDefinitionId, Long collectibleVersion);
}
