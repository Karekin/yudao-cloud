package cn.iocoder.yudao.module.cloudmold.warehouse.api;

public interface StockTransferCommandApi {
    StockTransferResult execute(StockTransferCommand command);
}
