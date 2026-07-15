package cn.iocoder.yudao.module.cloudmold.merchant.api;

/** Fail-closed role authorization port. Callers must not infer authority from account IDs. */
public interface MerchantOperatorAuthorizationApi {
    MerchantOperatorAuthorizationView requireAuthorizedOperator(MerchantOperatorAuthorizationCommand command);
}
