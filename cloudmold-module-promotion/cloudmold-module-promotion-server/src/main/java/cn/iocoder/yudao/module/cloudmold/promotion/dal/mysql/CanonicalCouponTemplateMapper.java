package cn.iocoder.yudao.module.cloudmold.promotion.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject.CouponTemplateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * Canonical CloudMold coupon template persistence.
 *
 * <p>The explicit canonical name prevents a Spring bean-name collision with the upstream Mall
 * Promotion {@code CouponTemplateMapper} when both modules are assembled in {@code yudao-server}.</p>
 */
@Mapper
public interface CanonicalCouponTemplateMapper extends BaseMapperX<CouponTemplateDO> {

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
