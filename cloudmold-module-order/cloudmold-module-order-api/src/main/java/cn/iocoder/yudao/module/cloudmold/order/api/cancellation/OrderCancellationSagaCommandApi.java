package cn.iocoder.yudao.module.cloudmold.order.api.cancellation;

public interface OrderCancellationSagaCommandApi {
    OrderCancellationSagaView execute(OrderCancellationSagaCommand command);
}
