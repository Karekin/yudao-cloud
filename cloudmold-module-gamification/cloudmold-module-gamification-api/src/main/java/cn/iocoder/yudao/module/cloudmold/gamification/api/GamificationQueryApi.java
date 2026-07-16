package cn.iocoder.yudao.module.cloudmold.gamification.api;

public interface GamificationQueryApi {
    GamificationView getGame(String gameId);
    GamificationView getPlayerAccount(String gameId, String principalId);
    GamificationView getSession(String sessionId);
    GamificationView getTaskProgress(String taskDefinitionId, Long taskVersion, String principalId);
    GamificationView getFragmentBalance(String gameId, String principalId, String fragmentCode);
}
