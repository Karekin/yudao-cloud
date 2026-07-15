package cn.iocoder.yudao.module.cloudmold.tokenplatform.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.tokenplatform.dal.dataobject.PricingVersionDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PricingVersionMapper extends BaseMapperX<PricingVersionDO> {
    @Select("SELECT * FROM cloudmold_token_platform_pricing_version WHERE tenant_id=#{tenantId} AND pricing_version_id=#{id}")
    PricingVersionDO selectOneById(@Param("tenantId") Long tenantId, @Param("id") String id);
}
