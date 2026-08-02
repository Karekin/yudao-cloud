package cn.iocoder.yudao.module.cloudmold.warehouse.api;

public interface StockCountQueryApi {
    StockCountView requireByStockCountId(String stockCountId);
}
