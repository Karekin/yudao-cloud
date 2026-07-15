package cn.iocoder.yudao.module.cloudmold.merchant.api.deposit;

public interface MerchantDepositCommandApi {
    MerchantDepositResult execute(MerchantDepositCommand command);
}
