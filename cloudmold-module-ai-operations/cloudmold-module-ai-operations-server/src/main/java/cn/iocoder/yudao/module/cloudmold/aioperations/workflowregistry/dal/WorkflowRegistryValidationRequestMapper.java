package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.dataobject.WorkflowRegistryValidationRequestDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface WorkflowRegistryValidationRequestMapper extends BaseMapperX<WorkflowRegistryValidationRequestDO> {

    @Select("""
            SELECT * FROM cloudmold_ai_ops_workflow_registry_validation_request
             WHERE tenant_id=#{tenantId} AND idempotency_key=#{idempotencyKey}
             LIMIT 1
            """)
    WorkflowRegistryValidationRequestDO selectByIdempotencyKey(@Param("tenantId") Long tenantId,
                                                                @Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT * FROM cloudmold_ai_ops_workflow_registry_validation_request
             WHERE tenant_id=#{tenantId} AND validation_request_id=#{validationRequestId}
             FOR UPDATE
            """)
    WorkflowRegistryValidationRequestDO selectForUpdate(@Param("tenantId") Long tenantId,
                                                         @Param("validationRequestId") String validationRequestId);

    @Select("""
            SELECT * FROM cloudmold_ai_ops_workflow_registry_validation_request
             WHERE tenant_id=#{tenantId} AND skill_id=#{skillId}
             ORDER BY created_at DESC, validation_request_id DESC
             LIMIT 1
            """)
    WorkflowRegistryValidationRequestDO selectLatestBySkill(@Param("tenantId") Long tenantId,
                                                             @Param("skillId") String skillId);

    @Update("""
            UPDATE cloudmold_ai_ops_workflow_registry_validation_request
               SET request_status='COMPLETED', consumed_at=#{consumedAt}
             WHERE tenant_id=#{tenantId} AND validation_request_id=#{validationRequestId}
               AND request_status='REQUESTED' AND expires_at>=#{consumedAt}
            """)
    int markCompleted(@Param("tenantId") Long tenantId,
                      @Param("validationRequestId") String validationRequestId,
                      @Param("consumedAt") LocalDateTime consumedAt);
}
