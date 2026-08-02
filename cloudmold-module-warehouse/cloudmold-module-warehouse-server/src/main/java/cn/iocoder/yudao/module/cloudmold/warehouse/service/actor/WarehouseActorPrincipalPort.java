package cn.iocoder.yudao.module.cloudmold.warehouse.service.actor;

public interface WarehouseActorPrincipalPort {
    String resolveSystemAdmin(Long loginUserId);

    void requireActive(String principalId);
}
