package cn.iocoder.yudao.module.cloudmold.procurement.api;

public interface PurchaseRequisitionQueryApi {
    PurchaseRequisitionView requireBySourceBusiness(String sourceBusinessType, String sourceBusinessRef);
}
