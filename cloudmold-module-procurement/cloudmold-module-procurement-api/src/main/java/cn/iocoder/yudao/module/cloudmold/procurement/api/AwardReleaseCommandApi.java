package cn.iocoder.yudao.module.cloudmold.procurement.api;

public interface AwardReleaseCommandApi {
    AwardReleaseResult releaseApprovedAward(AwardReleaseCommand command, String actorPrincipalId);
}
