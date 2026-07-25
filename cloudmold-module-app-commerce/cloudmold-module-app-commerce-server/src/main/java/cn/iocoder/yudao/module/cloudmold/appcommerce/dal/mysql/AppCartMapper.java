package cn.iocoder.yudao.module.cloudmold.appcommerce.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppCartDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface AppCartMapper {

    @Insert("""
            INSERT IGNORE INTO cloudmold_app_cart
              (cart_id,tenant_id,buyer_principal_id,version,status,created_at,updated_at)
            VALUES
              (#{cartId},#{tenantId},#{buyerPrincipalId},0,'ACTIVE',#{now},#{now})
            """)
    int insertIgnore(@Param("cartId") String cartId,
                     @Param("tenantId") Long tenantId,
                     @Param("buyerPrincipalId") String buyerPrincipalId,
                     @Param("now") LocalDateTime now);

    @Select("""
            SELECT cart_id,tenant_id,buyer_principal_id,version,status,created_at,updated_at
            FROM cloudmold_app_cart
            WHERE tenant_id=#{tenantId} AND buyer_principal_id=#{buyerPrincipalId}
            """)
    AppCartDO selectOwned(@Param("tenantId") Long tenantId,
                          @Param("buyerPrincipalId") String buyerPrincipalId);

    @Select("""
            SELECT cart_id,tenant_id,buyer_principal_id,version,status,created_at,updated_at
            FROM cloudmold_app_cart
            WHERE tenant_id=#{tenantId} AND buyer_principal_id=#{buyerPrincipalId}
            FOR UPDATE
            """)
    AppCartDO selectOwnedForUpdate(@Param("tenantId") Long tenantId,
                                   @Param("buyerPrincipalId") String buyerPrincipalId);

    @Update("""
            UPDATE cloudmold_app_cart
            SET version=#{nextVersion},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND cart_id=#{cartId} AND version=#{expectedVersion}
            """)
    int updateVersion(@Param("tenantId") Long tenantId,
                      @Param("cartId") String cartId,
                      @Param("expectedVersion") Long expectedVersion,
                      @Param("nextVersion") Long nextVersion,
                      @Param("now") LocalDateTime now);
}
