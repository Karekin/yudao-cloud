package cn.iocoder.yudao.module.cloudmold.promotion.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject.CouponEntitlementDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

@Mapper
public interface CouponEntitlementMapper extends BaseMapperX<CouponEntitlementDO> {
    @Select("SELECT * FROM cloudmold_promotion_coupon_entitlement WHERE tenant_id=#{tenantId} AND entitlement_id=#{id}")
    CouponEntitlementDO selectOneById(@Param("tenantId") Long tenantId, @Param("id") String id);
    @Select("SELECT * FROM cloudmold_promotion_coupon_entitlement WHERE tenant_id=#{tenantId} AND entitlement_id=#{id} FOR UPDATE")
    CouponEntitlementDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") String id);
    @Select("SELECT * FROM cloudmold_promotion_coupon_entitlement WHERE tenant_id=#{tenantId} AND entitlement_code=#{code}")
    CouponEntitlementDO selectByCode(@Param("tenantId") Long tenantId, @Param("code") String code);
    @Update("UPDATE cloudmold_promotion_coupon_entitlement SET status=#{status},order_ref=#{orderRef},version=version+1,updated_at=#{now} WHERE tenant_id=#{tenantId} AND entitlement_id=#{id} AND version=#{version}")
    int updateStatusCas(@Param("tenantId") Long tenantId, @Param("id") String id, @Param("version") Long version,
                        @Param("status") String status, @Param("orderRef") String orderRef,
                        @Param("now") LocalDateTime now);
}
