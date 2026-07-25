package cn.iocoder.yudao.module.cloudmold.appcommerce.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppCheckoutDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface AppCheckoutMapper {

    @Insert("""
            INSERT IGNORE INTO cloudmold_app_checkout
              (checkout_token,tenant_id,buyer_principal_id,idempotency_key,request_hash,
               listing_id,listing_offer_id,canonical_spu_id,canonical_sku_id,quantity,
               unit_price_minor,product_amount_minor,shipping_amount_minor,discount_amount_minor,
               payable_amount_minor,currency_code,status,version,expires_at,created_at,updated_at)
            VALUES
              (#{checkoutToken},#{tenantId},#{buyerPrincipalId},#{idempotencyKey},#{requestHash},
               #{listingId},#{listingOfferId},#{canonicalSpuId},#{canonicalSkuId},#{quantity},
               #{unitPriceMinor},#{productAmountMinor},#{shippingAmountMinor},#{discountAmountMinor},
               #{payableAmountMinor},#{currencyCode},#{status},#{version},#{expiresAt},#{createdAt},#{updatedAt})
            """)
    int insertIgnore(AppCheckoutDO checkout);

    @Select("""
            SELECT * FROM cloudmold_app_checkout
            WHERE tenant_id=#{tenantId} AND buyer_principal_id=#{buyerPrincipalId}
              AND idempotency_key=#{idempotencyKey}
            """)
    AppCheckoutDO selectByIdempotency(@Param("tenantId") Long tenantId,
                                      @Param("buyerPrincipalId") String buyerPrincipalId,
                                      @Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT * FROM cloudmold_app_checkout
            WHERE tenant_id=#{tenantId} AND checkout_token=#{checkoutToken}
            FOR UPDATE
            """)
    AppCheckoutDO selectForUpdate(@Param("tenantId") Long tenantId,
                                  @Param("checkoutToken") String checkoutToken);

    @Update("""
            UPDATE cloudmold_app_checkout
            SET status='ORDERED',order_id=#{orderId},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND checkout_token=#{checkoutToken}
              AND buyer_principal_id=#{buyerPrincipalId} AND status='PREVIEWED' AND version=#{expectedVersion}
            """)
    int markOrdered(@Param("tenantId") Long tenantId,
                    @Param("checkoutToken") String checkoutToken,
                    @Param("buyerPrincipalId") String buyerPrincipalId,
                    @Param("expectedVersion") Long expectedVersion,
                    @Param("orderId") String orderId,
                    @Param("now") LocalDateTime now);
}
