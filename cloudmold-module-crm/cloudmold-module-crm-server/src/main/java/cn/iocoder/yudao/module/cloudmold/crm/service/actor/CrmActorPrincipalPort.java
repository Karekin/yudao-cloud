package cn.iocoder.yudao.module.cloudmold.crm.service.actor;

public interface CrmActorPrincipalPort {

    String resolveSystemAdmin(Long loginUserId);

    void requireActive(String principalId);
}
