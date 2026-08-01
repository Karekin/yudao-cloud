package cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal.dataobject.WorkflowEvidenceOperationDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface WorkflowEvidenceOperationMapper extends BaseMapperX<WorkflowEvidenceOperationDO> {

    @Insert("""
            INSERT INTO cloudmold_ai_ops_workflow_evidence_operation
              (tenant_id,operation_type,idempotency_key,request_hash,attempt_token,status,created_by,created_at,updated_at)
            VALUES
              (#{tenantId},#{operationType},#{idempotencyKey},#{requestHash},#{attemptToken},0,#{createdBy},#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolve(@Param("tenantId") Long tenantId,
                        @Param("operationType") String operationType,
                        @Param("idempotencyKey") String idempotencyKey,
                        @Param("requestHash") String requestHash,
                        @Param("attemptToken") String attemptToken,
                        @Param("createdBy") String createdBy,
                        @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("""
            SELECT * FROM cloudmold_ai_ops_workflow_evidence_operation
             WHERE operation_id=#{operationId} AND tenant_id=#{tenantId}
             FOR UPDATE
            """)
    WorkflowEvidenceOperationDO selectForUpdate(@Param("operationId") Long operationId,
                                                @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_ai_ops_workflow_evidence_operation
               SET status=10,aggregate_type=#{aggregateType},aggregate_id=#{aggregateId},result_json=#{resultJson},
                   updated_at=#{now}
             WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} AND status=0
            """)
    int markSucceeded(@Param("operationId") Long operationId,
                      @Param("tenantId") Long tenantId,
                      @Param("aggregateType") String aggregateType,
                      @Param("aggregateId") String aggregateId,
                      @Param("resultJson") String resultJson,
                      @Param("now") LocalDateTime now);
}
