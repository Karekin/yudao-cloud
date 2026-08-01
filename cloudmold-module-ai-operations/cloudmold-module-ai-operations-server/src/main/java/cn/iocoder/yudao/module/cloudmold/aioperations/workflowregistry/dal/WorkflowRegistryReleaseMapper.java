package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.dataobject.WorkflowRegistryReleaseDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkflowRegistryReleaseMapper extends BaseMapperX<WorkflowRegistryReleaseDO> {

    @Select("""
            SELECT * FROM cloudmold_ai_ops_workflow_registry_release
             WHERE tenant_id=#{tenantId} AND skill_id=#{skillId}
             ORDER BY created_at DESC, release_id DESC
             LIMIT 1
            """)
    WorkflowRegistryReleaseDO selectLatestBySkill(@Param("tenantId") Long tenantId,
                                                  @Param("skillId") String skillId);

    @Select("""
            SELECT * FROM cloudmold_ai_ops_workflow_registry_release
             WHERE tenant_id=#{tenantId} AND source_idempotency_key=#{idempotencyKey}
               AND release_reason=#{releaseReason}
             ORDER BY created_at DESC, release_id DESC
             LIMIT 1
            """)
    WorkflowRegistryReleaseDO selectBySourceKeyAndReason(@Param("tenantId") Long tenantId,
                                                         @Param("idempotencyKey") String idempotencyKey,
                                                         @Param("releaseReason") String releaseReason);
}
