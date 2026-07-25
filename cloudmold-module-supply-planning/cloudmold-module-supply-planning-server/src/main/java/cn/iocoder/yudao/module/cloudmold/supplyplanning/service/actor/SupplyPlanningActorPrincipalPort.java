package cn.iocoder.yudao.module.cloudmold.supplyplanning.service.actor;

public interface SupplyPlanningActorPrincipalPort {

    String resolveSystemAdmin(Long loginUserId);

    void requireActive(String principalId);
}
