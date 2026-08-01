package cn.iocoder.yudao.module.cloudmold.skilltask.definition.dynamic;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SkillTaskRegistryLineageMapper {

    @Select("""
            SELECT lineage_id,
                   tenant_id,
                   skill_id,
                   skill_version,
                   registry_version_id,
                   pointer_version,
                   source_kind,
                   canonical_definition_json,
                   registry_payload_sha256,
                   definition_sha256,
                   definition_closure_sha256,
                   created_at,
                   updated_at
              FROM cloudmold_skill_task_registry_lineage
             WHERE tenant_id=#{tenantId}
               AND skill_id=#{skillId}
               AND skill_version=#{skillVersion}
            """)
    SkillTaskRegistryLineageRecord selectBySkillVersion(@Param("tenantId") Long tenantId,
                                                        @Param("skillId") String skillId,
                                                        @Param("skillVersion") String skillVersion);

    @Insert("""
            INSERT INTO cloudmold_skill_task_registry_lineage (
                lineage_id,
                tenant_id,
                skill_id,
                skill_version,
                registry_version_id,
                pointer_version,
                source_kind,
                canonical_definition_json,
                registry_payload_sha256,
                definition_sha256,
                definition_closure_sha256,
                created_at,
                updated_at
            ) VALUES (
                #{lineage.lineageId},
                #{lineage.tenantId},
                #{lineage.skillId},
                #{lineage.skillVersion},
                #{lineage.registryVersionId},
                #{lineage.pointerVersion},
                #{lineage.sourceKind},
                #{lineage.canonicalDefinitionJson},
                #{lineage.registryPayloadSha256},
                #{lineage.definitionSha256},
                #{lineage.definitionClosureSha256},
                #{lineage.createdAt},
                #{lineage.updatedAt}
            )
            ON DUPLICATE KEY UPDATE
                registry_version_id=VALUES(registry_version_id),
                pointer_version=VALUES(pointer_version),
                source_kind=VALUES(source_kind),
                canonical_definition_json=VALUES(canonical_definition_json),
                registry_payload_sha256=VALUES(registry_payload_sha256),
                definition_sha256=VALUES(definition_sha256),
                definition_closure_sha256=VALUES(definition_closure_sha256),
                updated_at=VALUES(updated_at)
            """)
    int upsert(@Param("lineage") SkillTaskRegistryLineageRecord lineage);
}
