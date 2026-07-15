package cn.iocoder.yudao.module.cloudmold.tokenplatform.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.tokenplatform.dal.dataobject.ModelOfferingDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface ModelOfferingMapper extends BaseMapperX<ModelOfferingDO> {
    @Select("SELECT * FROM cloudmold_token_platform_model_offering WHERE tenant_id=#{tenantId} AND offering_id=#{id}")
    ModelOfferingDO selectOneById(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_token_platform_model_offering WHERE tenant_id=#{tenantId} AND offering_id=#{id} FOR UPDATE")
    ModelOfferingDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_token_platform_model_offering WHERE tenant_id=#{tenantId} AND offering_code=#{code}")
    ModelOfferingDO selectByCode(@Param("tenantId") Long tenantId, @Param("code") String code);

    @Update("""
        UPDATE cloudmold_token_platform_model_offering
        SET status=#{status},version=version+1,updated_at=#{now}
        WHERE tenant_id=#{tenantId} AND offering_id=#{id} AND version=#{version}
        """)
    int updateStatusCas(@Param("tenantId") Long tenantId, @Param("id") String id,
                        @Param("version") Long version, @Param("status") String status,
                        @Param("now") LocalDateTime now);

    @Update("""
        UPDATE cloudmold_token_platform_model_offering
        SET current_pricing_version_id=#{pricingVersionId},version=version+1,updated_at=#{now}
        WHERE tenant_id=#{tenantId} AND offering_id=#{id} AND version=#{version}
        """)
    int updatePricingCas(@Param("tenantId") Long tenantId, @Param("id") String id,
                         @Param("version") Long version, @Param("pricingVersionId") String pricingVersionId,
                         @Param("now") LocalDateTime now);
}
