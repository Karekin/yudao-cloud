package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.dataobject.WorkflowRegistryEvaluationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkflowRegistryEvaluationMapper extends BaseMapperX<WorkflowRegistryEvaluationDO> {

    @Select("""
            SELECT * FROM cloudmold_ai_ops_workflow_registry_evaluation
             WHERE tenant_id=#{tenantId} AND idempotency_key=#{idempotencyKey}
             LIMIT 1
            """)
    WorkflowRegistryEvaluationDO selectByIdempotencyKey(@Param("tenantId") Long tenantId,
                                                        @Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT * FROM cloudmold_ai_ops_workflow_registry_evaluation
             WHERE tenant_id=#{tenantId} AND evaluation_id=#{evaluationId}
             LIMIT 1
            """)
    WorkflowRegistryEvaluationDO selectByEvaluationId(@Param("tenantId") Long tenantId,
                                                      @Param("evaluationId") String evaluationId);

    @Select("""
            SELECT * FROM cloudmold_ai_ops_workflow_registry_evaluation
             WHERE tenant_id=#{tenantId} AND skill_id=#{skillId}
             ORDER BY created_at DESC, evaluation_id DESC
             LIMIT 1
            """)
    WorkflowRegistryEvaluationDO selectLatestBySkill(@Param("tenantId") Long tenantId,
                                                     @Param("skillId") String skillId);
}
