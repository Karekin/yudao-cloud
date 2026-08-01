package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.dataobject.WorkflowRegistryPointerDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface WorkflowRegistryPointerMapper extends BaseMapperX<WorkflowRegistryPointerDO> {

    @Select("SELECT * FROM cloudmold_ai_ops_workflow_registry_pointer WHERE tenant_id=#{tenantId} AND skill_id=#{skillId}")
    WorkflowRegistryPointerDO selectBySkillId(@Param("tenantId") Long tenantId, @Param("skillId") String skillId);

    @Select("SELECT * FROM cloudmold_ai_ops_workflow_registry_pointer WHERE tenant_id=#{tenantId} AND skill_id=#{skillId} FOR UPDATE")
    WorkflowRegistryPointerDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("skillId") String skillId);

    @Update("""
            UPDATE cloudmold_ai_ops_workflow_registry_pointer
               SET candidate_version_id=#{candidateVersionId}, pointer_version=pointer_version+1,
                   updated_by=#{updatedBy}, updated_at=#{updatedAt}
             WHERE tenant_id=#{tenantId} AND skill_id=#{skillId}
               AND pointer_version=#{expectedPointerVersion} AND candidate_version_id IS NULL
            """)
    int setCandidateCas(@Param("tenantId") Long tenantId,
                        @Param("skillId") String skillId,
                        @Param("candidateVersionId") String candidateVersionId,
                        @Param("expectedPointerVersion") Long expectedPointerVersion,
                        @Param("updatedBy") String updatedBy,
                        @Param("updatedAt") LocalDateTime updatedAt);
}
