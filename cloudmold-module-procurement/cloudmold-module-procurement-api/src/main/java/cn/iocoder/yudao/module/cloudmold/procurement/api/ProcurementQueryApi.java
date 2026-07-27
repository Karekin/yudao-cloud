package cn.iocoder.yudao.module.cloudmold.procurement.api;

public interface ProcurementQueryApi {
    ProcurementOrderView requireCurrent(String orderId);

    ProcurementOrderView requireBySourceBusiness(String sourceBusinessType, String sourceBusinessRef);
}
