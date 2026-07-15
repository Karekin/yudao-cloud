package cn.iocoder.yudao.module.cloudmold.merchant.api;

/** Fail-closed Merchant owner validation port for Inventory and other owner-reference consumers. */
public interface MerchantOwnerValidationApi {
    MerchantOwnerView requireActiveMerchant(String merchantId);
}
