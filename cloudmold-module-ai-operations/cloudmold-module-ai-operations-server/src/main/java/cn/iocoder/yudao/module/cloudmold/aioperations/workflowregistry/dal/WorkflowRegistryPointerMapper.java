package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.dataobject.WorkflowRegistryPointerDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WorkflowRegistryPointerMapper extends BaseMapperX<WorkflowRegistryPointerDO> {

    @Select("SELECT * FROM cloudmold_ai_ops_workflow_registry_pointer WHERE tenant_id=#{tenantId} AND skill_id=#{skillId}")
    WorkflowRegistryPointerDO selectBySkillId(@Param("tenantId") Long tenantId, @Param("skillId") String skillId);

    @Select("SELECT * FROM cloudmold_ai_ops_workflow_registry_pointer WHERE tenant_id=#{tenantId} AND skill_id=#{skillId} FOR UPDATE")
    WorkflowRegistryPointerDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("skillId") String skillId);

    @Select("""
            SELECT * FROM cloudmold_ai_ops_workflow_registry_pointer
             WHERE tenant_id=#{tenantId}
             ORDER BY skill_id
             LIMIT #{limit}
            """)
    List<WorkflowRegistryPointerDO> selectByTenant(@Param("tenantId") Long tenantId,
                                                   @Param("limit") int limit);

    @Update("""
            UPDATE cloudmold_ai_ops_workflow_registry_pointer
               SET candidate_version_id=#{candidateVersionId}, pointer_version=pointer_version+1,
                   updated_by=#{updatedBy}, updated_at=#{updatedAt}
             WHERE tenant_id=#{tenantId} AND skill_id=#{skillId}
               AND pointer_version=#{expectedPointerVersion} AND candidate_version_id IS NULL
               AND COALESCE(kill_switch_enabled, 0)=0
            """)
    int setCandidateCas(@Param("tenantId") Long tenantId,
                        @Param("skillId") String skillId,
                        @Param("candidateVersionId") String candidateVersionId,
                        @Param("expectedPointerVersion") Long expectedPointerVersion,
                        @Param("updatedBy") String updatedBy,
                        @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE cloudmold_ai_ops_workflow_registry_pointer
               SET stable_version_id=#{candidateVersionId}, candidate_version_id=NULL,
                   pointer_version=pointer_version+1, updated_by=#{updatedBy}, updated_at=#{updatedAt}
             WHERE tenant_id=#{tenantId} AND skill_id=#{skillId}
               AND candidate_version_id=#{candidateVersionId}
               AND pointer_version=#{expectedPointerVersion}
               AND COALESCE(kill_switch_enabled, 0)=0
            """)
    int promoteCandidateToStableCas(@Param("tenantId") Long tenantId,
                                    @Param("skillId") String skillId,
                                    @Param("candidateVersionId") String candidateVersionId,
                                    @Param("expectedPointerVersion") Long expectedPointerVersion,
                                    @Param("updatedBy") String updatedBy,
                                    @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE cloudmold_ai_ops_workflow_registry_pointer
               SET candidate_version_id=NULL, pointer_version=pointer_version+1,
                   updated_by=#{updatedBy}, updated_at=#{updatedAt}
             WHERE tenant_id=#{tenantId} AND skill_id=#{skillId}
               AND candidate_version_id=#{candidateVersionId}
               AND pointer_version=#{expectedPointerVersion}
            """)
    int clearCandidateCas(@Param("tenantId") Long tenantId,
                          @Param("skillId") String skillId,
                          @Param("candidateVersionId") String candidateVersionId,
                          @Param("expectedPointerVersion") Long expectedPointerVersion,
                          @Param("updatedBy") String updatedBy,
                          @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE cloudmold_ai_ops_workflow_registry_pointer
               SET stable_version_id=#{previousStableVersionId}, candidate_version_id=NULL,
                   pointer_version=pointer_version+1, updated_by=#{updatedBy}, updated_at=#{updatedAt}
             WHERE tenant_id=#{tenantId} AND skill_id=#{skillId}
               AND stable_version_id=#{currentStableVersionId}
               AND candidate_version_id IS NULL
               AND pointer_version=#{expectedPointerVersion}
               AND COALESCE(kill_switch_enabled, 0)=0
            """)
    int rollbackStableCas(@Param("tenantId") Long tenantId,
                          @Param("skillId") String skillId,
                          @Param("currentStableVersionId") String currentStableVersionId,
                          @Param("previousStableVersionId") String previousStableVersionId,
                          @Param("expectedPointerVersion") Long expectedPointerVersion,
                          @Param("updatedBy") String updatedBy,
                          @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE cloudmold_ai_ops_workflow_registry_pointer
               SET kill_switch_enabled=1, pointer_version=pointer_version+1,
                   updated_by=#{updatedBy}, updated_at=#{updatedAt}
             WHERE tenant_id=#{tenantId} AND skill_id=#{skillId}
               AND stable_version_id=#{stableVersionId}
               AND pointer_version=#{expectedPointerVersion}
               AND COALESCE(kill_switch_enabled, 0)=0
            """)
    int armKillSwitchCas(@Param("tenantId") Long tenantId,
                         @Param("skillId") String skillId,
                         @Param("stableVersionId") String stableVersionId,
                         @Param("expectedPointerVersion") Long expectedPointerVersion,
                         @Param("updatedBy") String updatedBy,
                         @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE cloudmold_ai_ops_workflow_registry_pointer
               SET kill_switch_enabled=0, pointer_version=pointer_version+1,
                   updated_by=#{updatedBy}, updated_at=#{updatedAt}
             WHERE tenant_id=#{tenantId} AND skill_id=#{skillId}
               AND stable_version_id=#{stableVersionId}
               AND pointer_version=#{expectedPointerVersion}
               AND kill_switch_enabled=1
            """)
    int disarmKillSwitchCas(@Param("tenantId") Long tenantId,
                            @Param("skillId") String skillId,
                            @Param("stableVersionId") String stableVersionId,
                            @Param("expectedPointerVersion") Long expectedPointerVersion,
                            @Param("updatedBy") String updatedBy,
                            @Param("updatedAt") LocalDateTime updatedAt);
}
