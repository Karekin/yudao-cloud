package cn.iocoder.yudao.module.cloudmold.skilltask.definition.dynamic;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkflowRegistryPointerReadMapper {

    @Select("""
            SELECT pointer_id,
                   tenant_id,
                   skill_id,
                   stable_version_id,
                   candidate_version_id,
                   pointer_version,
                   COALESCE(kill_switch_enabled, 0) AS kill_switch_enabled
              FROM cloudmold_ai_ops_workflow_registry_pointer
             WHERE tenant_id=#{tenantId}
               AND skill_id=#{skillId}
            """)
    WorkflowRegistryPointerRecord selectBySkillId(@Param("tenantId") Long tenantId,
                                                  @Param("skillId") String skillId);
}
