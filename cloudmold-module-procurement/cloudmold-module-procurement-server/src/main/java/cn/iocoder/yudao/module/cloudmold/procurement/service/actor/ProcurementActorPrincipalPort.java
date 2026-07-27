package cn.iocoder.yudao.module.cloudmold.procurement.service.actor;

public interface ProcurementActorPrincipalPort {
    String resolveSystemAdmin(Long loginUserId);

    void requireActive(String principalId);
}
