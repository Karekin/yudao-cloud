package cn.iocoder.yudao.module.cloudmold.aioperations.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.aioperations.dal.dataobject.AiInvocationAttemptDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AiInvocationAttemptMapper extends BaseMapperX<AiInvocationAttemptDO> {
    @Select("SELECT * FROM cloudmold_ai_ops_invocation_attempt WHERE tenant_id=#{tenantId} AND attempt_id=#{id}")
    AiInvocationAttemptDO selectOneById(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_ai_ops_invocation_attempt WHERE tenant_id=#{tenantId} AND attempt_key=#{key}")
    AiInvocationAttemptDO selectByAttemptKey(@Param("tenantId") Long tenantId, @Param("key") String key);

    @Select("SELECT COUNT(*) FROM cloudmold_ai_ops_invocation_attempt WHERE tenant_id=#{tenantId} AND run_id=#{runId}")
    int countByRun(@Param("tenantId") Long tenantId, @Param("runId") String runId);
}
