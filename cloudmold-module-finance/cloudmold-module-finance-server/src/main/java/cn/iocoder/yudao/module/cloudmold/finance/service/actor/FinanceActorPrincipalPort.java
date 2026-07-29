package cn.iocoder.yudao.module.cloudmold.finance.service.actor;

public interface FinanceActorPrincipalPort {
    String resolveSystemAdmin(Long loginUserId);

    void requireActive(String principalId);
}
