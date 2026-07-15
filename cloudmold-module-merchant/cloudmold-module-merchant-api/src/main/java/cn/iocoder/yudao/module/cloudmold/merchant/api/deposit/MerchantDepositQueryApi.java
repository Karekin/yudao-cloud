package cn.iocoder.yudao.module.cloudmold.merchant.api.deposit;

public interface MerchantDepositQueryApi {
    MerchantDepositView requireCurrent(String merchantId, String currency);
}
