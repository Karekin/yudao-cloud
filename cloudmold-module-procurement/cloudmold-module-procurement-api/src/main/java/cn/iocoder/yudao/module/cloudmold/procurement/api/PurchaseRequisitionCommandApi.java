package cn.iocoder.yudao.module.cloudmold.procurement.api;

public interface PurchaseRequisitionCommandApi {
    PurchaseRequisitionResult createApproved(PurchaseRequisitionCommand command, String actorPrincipalId);
}
