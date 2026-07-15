package cn.iocoder.yudao.module.cloudmold.aioperations.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.aioperations.dal.dataobject.AiWorkflowVersionDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AiWorkflowVersionMapper extends BaseMapperX<AiWorkflowVersionDO> {
    @Select("SELECT * FROM cloudmold_ai_ops_workflow_version WHERE tenant_id=#{tenantId} AND workflow_id=#{workflowId} AND workflow_version=#{version}")
    AiWorkflowVersionDO selectByVersion(@Param("tenantId") Long tenantId,
                                        @Param("workflowId") String workflowId,
                                        @Param("version") Long version);
}
