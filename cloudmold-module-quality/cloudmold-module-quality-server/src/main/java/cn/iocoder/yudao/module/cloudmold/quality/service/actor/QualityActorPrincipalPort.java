package cn.iocoder.yudao.module.cloudmold.quality.service.actor;

public interface QualityActorPrincipalPort {

    String resolveSystemAdmin(Long loginUserId);

    void requireActive(String principalId);
}
