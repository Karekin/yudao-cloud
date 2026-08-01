package cn.iocoder.yudao.module.cloudmold.skilltask.definition.dynamic;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkflowRegistryVersionReadMapper {

    @Select("""
            SELECT registry_version_id,
                   tenant_id,
                   skill_id,
                   skill_semantic_version,
                   definition_sha256,
                   canonical_definition_json,
                   registry_status
              FROM cloudmold_ai_ops_workflow_registry_version
             WHERE tenant_id=#{tenantId}
               AND registry_version_id=#{registryVersionId}
            """)
    WorkflowRegistryVersionRecord selectByRegistryVersionId(@Param("tenantId") Long tenantId,
                                                            @Param("registryVersionId") String registryVersionId);
}
