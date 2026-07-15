package cn.iocoder.yudao.module.cloudmold.customerservice.api;

public interface CustomerServiceCommandApi {
    CustomerServiceView execute(CustomerServiceCommand command);
}
