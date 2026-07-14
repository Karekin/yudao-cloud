package cn.iocoder.yudao.module.cloudmold.payment.api;

public interface PaymentCommandApi {
    PaymentCommandResult execute(PaymentCommand command);
}
