package cn.iocoder.yudao.module.cloudmold.crm.service.contract;

public interface SalesContractActorPrincipalPort {

    String resolveSystemAdmin(Long loginUserId);

    void requireActive(String principalId);
}
