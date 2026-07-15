package cn.iocoder.yudao.module.cloudmold.merchant.api;

/** Fail-closed validation port for Listing, Order, Fulfillment, and other merchant-reference consumers. */
public interface MerchantReferenceValidationApi {
    MerchantReferenceView requireActiveReference(MerchantReferenceValidationCommand command);
}
