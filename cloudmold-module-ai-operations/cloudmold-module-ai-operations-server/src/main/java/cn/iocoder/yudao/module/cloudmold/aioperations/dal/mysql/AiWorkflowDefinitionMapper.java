package cn.iocoder.yudao.module.cloudmold.aioperations.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.aioperations.dal.dataobject.AiWorkflowDefinitionDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface AiWorkflowDefinitionMapper extends BaseMapperX<AiWorkflowDefinitionDO> {
    @Select("SELECT * FROM cloudmold_ai_ops_workflow_definition WHERE tenant_id=#{tenantId} AND workflow_id=#{id}")
    AiWorkflowDefinitionDO selectOneById(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_ai_ops_workflow_definition WHERE tenant_id=#{tenantId} AND workflow_id=#{id} FOR UPDATE")
    AiWorkflowDefinitionDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_ai_ops_workflow_definition WHERE tenant_id=#{tenantId} AND application_id=#{applicationId} AND workflow_code=#{code}")
    AiWorkflowDefinitionDO selectByCode(@Param("tenantId") Long tenantId,
                                        @Param("applicationId") String applicationId,
                                        @Param("code") String code);

    @Update("UPDATE cloudmold_ai_ops_workflow_definition SET current_version=current_version+1,updated_at=#{now} WHERE tenant_id=#{tenantId} AND workflow_id=#{id} AND current_version=#{version}")
    int publishVersionCas(@Param("tenantId") Long tenantId, @Param("id") String id,
                          @Param("version") Long version, @Param("now") LocalDateTime now);
}
