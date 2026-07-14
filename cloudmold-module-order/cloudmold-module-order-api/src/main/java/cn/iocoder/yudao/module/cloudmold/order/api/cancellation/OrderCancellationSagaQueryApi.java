package cn.iocoder.yudao.module.cloudmold.order.api.cancellation;

public interface OrderCancellationSagaQueryApi {
    OrderCancellationSagaView get(String sagaId);
}
