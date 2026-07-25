package cn.iocoder.yudao.module.cloudmold.appcommerce.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppCartItemDO;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AppCartItemMapper {

    @Select("""
            SELECT line_id,tenant_id,cart_id,buyer_principal_id,listing_id,listing_offer_id,
                   canonical_sku_id,quantity,selected,created_at,updated_at
            FROM cloudmold_app_cart_item
            WHERE tenant_id=#{tenantId} AND cart_id=#{cartId}
            ORDER BY updated_at DESC, line_id ASC
            """)
    List<AppCartItemDO> selectByCart(@Param("tenantId") Long tenantId,
                                     @Param("cartId") String cartId);

    @Select("""
            SELECT line_id,tenant_id,cart_id,buyer_principal_id,listing_id,listing_offer_id,
                   canonical_sku_id,quantity,selected,created_at,updated_at
            FROM cloudmold_app_cart_item
            WHERE tenant_id=#{tenantId} AND cart_id=#{cartId} AND line_id=#{lineId}
            """)
    AppCartItemDO selectOwnedLine(@Param("tenantId") Long tenantId,
                                  @Param("cartId") String cartId,
                                  @Param("lineId") String lineId);

    @Select("""
            SELECT line_id,tenant_id,cart_id,buyer_principal_id,listing_id,listing_offer_id,
                   canonical_sku_id,quantity,selected,created_at,updated_at
            FROM cloudmold_app_cart_item
            WHERE tenant_id=#{tenantId} AND cart_id=#{cartId}
              AND listing_id=#{listingId} AND listing_offer_id=#{listingOfferId}
              AND canonical_sku_id=#{canonicalSkuId}
            """)
    AppCartItemDO selectByIdentity(@Param("tenantId") Long tenantId,
                                   @Param("cartId") String cartId,
                                   @Param("listingId") String listingId,
                                   @Param("listingOfferId") String listingOfferId,
                                   @Param("canonicalSkuId") String canonicalSkuId);

    @Select("""
            SELECT COUNT(1)
            FROM cloudmold_app_cart_item
            WHERE tenant_id=#{tenantId} AND cart_id=#{cartId}
            """)
    long countByCart(@Param("tenantId") Long tenantId,
                     @Param("cartId") String cartId);

    @Insert("""
            INSERT INTO cloudmold_app_cart_item
              (line_id,tenant_id,cart_id,buyer_principal_id,listing_id,listing_offer_id,
               canonical_sku_id,quantity,selected,created_at,updated_at)
            VALUES
              (#{lineId},#{tenantId},#{cartId},#{buyerPrincipalId},#{listingId},#{listingOfferId},
               #{canonicalSkuId},#{quantity},#{selected},#{createdAt},#{updatedAt})
            """)
    int insert(AppCartItemDO item);

    @Update("""
            UPDATE cloudmold_app_cart_item
            SET quantity=#{quantity},selected=#{selected},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND cart_id=#{cartId} AND line_id=#{lineId}
            """)
    int updateQuantityAndSelection(@Param("tenantId") Long tenantId,
                                   @Param("cartId") String cartId,
                                   @Param("lineId") String lineId,
                                   @Param("quantity") BigDecimal quantity,
                                   @Param("selected") Boolean selected,
                                   @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_app_cart_item
            SET selected=#{selected},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND cart_id=#{cartId} AND line_id=#{lineId}
            """)
    int updateSelection(@Param("tenantId") Long tenantId,
                        @Param("cartId") String cartId,
                        @Param("lineId") String lineId,
                        @Param("selected") Boolean selected,
                        @Param("now") LocalDateTime now);

    @Delete("""
            DELETE FROM cloudmold_app_cart_item
            WHERE tenant_id=#{tenantId} AND cart_id=#{cartId} AND line_id=#{lineId}
            """)
    int deleteLine(@Param("tenantId") Long tenantId,
                   @Param("cartId") String cartId,
                   @Param("lineId") String lineId);

    @Delete("""
            DELETE FROM cloudmold_app_cart_item
            WHERE tenant_id=#{tenantId} AND cart_id=#{cartId}
            """)
    int deleteByCart(@Param("tenantId") Long tenantId,
                     @Param("cartId") String cartId);
}
