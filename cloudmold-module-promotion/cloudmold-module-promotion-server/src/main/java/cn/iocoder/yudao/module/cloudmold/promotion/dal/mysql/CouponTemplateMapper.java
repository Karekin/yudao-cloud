package cn.iocoder.yudao.module.cloudmold.promotion.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject.CouponTemplateDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

@Mapper
public interface CouponTemplateMapper extends BaseMapperX<CouponTemplateDO> {
    @Select("SELECT * FROM cloudmold_promotion_coupon_template WHERE tenant_id=#{tenantId} AND template_id=#{id}")
    CouponTemplateDO selectOneById(@Param("tenantId") Long tenantId, @Param("id") String id);
    @Select("SELECT * FROM cloudmold_promotion_coupon_template WHERE tenant_id=#{tenantId} AND template_id=#{id} FOR UPDATE")
    CouponTemplateDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") String id);
    @Select("SELECT * FROM cloudmold_promotion_coupon_template WHERE tenant_id=#{tenantId} AND template_code=#{code}")
    CouponTemplateDO selectByCode(@Param("tenantId") Long tenantId, @Param("code") String code);
    @Update("UPDATE cloudmold_promotion_coupon_template SET status=#{status},version=version+1,updated_at=#{now} WHERE tenant_id=#{tenantId} AND template_id=#{id} AND version=#{version}")
    int updateStatusCas(@Param("tenantId") Long tenantId, @Param("id") String id, @Param("version") Long version,
                        @Param("status") String status, @Param("now") LocalDateTime now);
}
