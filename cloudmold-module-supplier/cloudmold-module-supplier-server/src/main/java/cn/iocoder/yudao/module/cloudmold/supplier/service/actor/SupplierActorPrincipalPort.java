package cn.iocoder.yudao.module.cloudmold.supplier.service.actor;

public interface SupplierActorPrincipalPort {
    String resolveSystemAdmin(Long loginUserId);

    void requireActive(String principalId);
}
