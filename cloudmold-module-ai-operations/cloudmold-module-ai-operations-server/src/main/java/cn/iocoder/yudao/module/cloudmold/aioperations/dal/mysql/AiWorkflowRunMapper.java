package cn.iocoder.yudao.module.cloudmold.aioperations.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.aioperations.dal.dataobject.AiWorkflowRunDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface AiWorkflowRunMapper extends BaseMapperX<AiWorkflowRunDO> {
    @Select("SELECT * FROM cloudmold_ai_ops_workflow_run WHERE tenant_id=#{tenantId} AND run_id=#{id}")
    AiWorkflowRunDO selectOneById(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_ai_ops_workflow_run WHERE tenant_id=#{tenantId} AND run_id=#{id} FOR UPDATE")
    AiWorkflowRunDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("SELECT * FROM cloudmold_ai_ops_workflow_run WHERE tenant_id=#{tenantId} AND run_key=#{key}")
    AiWorkflowRunDO selectByRunKey(@Param("tenantId") Long tenantId, @Param("key") String key);

    @Update("""
        UPDATE cloudmold_ai_ops_workflow_run
        SET status=#{status},finished_at=#{finishedAt},error_code=#{errorCode},version=version+1,updated_at=#{now}
        WHERE tenant_id=#{tenantId} AND run_id=#{id} AND version=#{version} AND status='RUNNING'
        """)
    int updateStatusCas(@Param("tenantId") Long tenantId, @Param("id") String id,
                        @Param("version") Long version, @Param("status") String status,
                        @Param("finishedAt") LocalDateTime finishedAt, @Param("errorCode") String errorCode,
                        @Param("now") LocalDateTime now);
}
