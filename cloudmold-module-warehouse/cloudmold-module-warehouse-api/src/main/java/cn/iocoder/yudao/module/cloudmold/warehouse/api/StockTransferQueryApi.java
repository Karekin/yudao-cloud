package cn.iocoder.yudao.module.cloudmold.warehouse.api;

public interface StockTransferQueryApi {
    StockTransferView requireByRequestId(String requestId);

    StockTransferView requireBySourceBusiness(String sourceBusinessType, String sourceBusinessRef);
}
