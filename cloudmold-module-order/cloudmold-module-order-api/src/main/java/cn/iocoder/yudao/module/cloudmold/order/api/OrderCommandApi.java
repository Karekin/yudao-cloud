package cn.iocoder.yudao.module.cloudmold.order.api;

public interface OrderCommandApi {
    OrderCommandResult execute(OrderCommand command);
}
