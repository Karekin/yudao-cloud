package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.dataobject.WorkflowRegistryApprovalDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkflowRegistryApprovalMapper extends BaseMapperX<WorkflowRegistryApprovalDO> {

    @Select("""
            SELECT * FROM cloudmold_ai_ops_workflow_registry_approval
             WHERE tenant_id=#{tenantId} AND idempotency_key=#{idempotencyKey}
             LIMIT 1
            """)
    WorkflowRegistryApprovalDO selectByIdempotencyKey(@Param("tenantId") Long tenantId,
                                                      @Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT * FROM cloudmold_ai_ops_workflow_registry_approval
             WHERE tenant_id=#{tenantId} AND skill_id=#{skillId}
             ORDER BY created_at DESC, approval_id DESC
             LIMIT 1
            """)
    WorkflowRegistryApprovalDO selectLatestBySkill(@Param("tenantId") Long tenantId,
                                                   @Param("skillId") String skillId);
}
