package cn.iocoder.yudao.module.cloudmold.tokenplatform.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.tokenplatform.dal.dataobject.InvocationUsageDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface InvocationUsageMapper extends BaseMapperX<InvocationUsageDO> {
    @Select("SELECT * FROM cloudmold_token_platform_invocation_usage WHERE tenant_id=#{tenantId} AND usage_id=#{id}")
    InvocationUsageDO selectOneById(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_token_platform_invocation_usage WHERE tenant_id=#{tenantId} AND request_id=#{requestId}")
    InvocationUsageDO selectByRequestId(@Param("tenantId") Long tenantId,
                                        @Param("requestId") String requestId);
}
