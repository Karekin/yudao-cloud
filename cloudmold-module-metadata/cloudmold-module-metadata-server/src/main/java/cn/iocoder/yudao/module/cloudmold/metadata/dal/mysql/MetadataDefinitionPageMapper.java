package cn.iocoder.yudao.module.cloudmold.metadata.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.metadata.service.query.MetadataDefinitionPageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MetadataDefinitionPageMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_metadata_definition d
            WHERE d.tenant_id = #{tenantId}
            <if test="definitionId != null">AND d.definition_id = #{definitionId}</if>
            <if test="definitionKind != null">AND d.definition_kind = #{definitionKind}</if>
            <if test="definitionCode != null">AND d.definition_code LIKE CONCAT('%', #{definitionCode}, '%')</if>
            <if test="displayName != null">AND d.display_name LIKE CONCAT('%', #{displayName}, '%')</if>
            <if test="status != null">AND d.status = #{status}</if>
            <if test="ownerPrincipalId != null">AND d.owner_principal_id = #{ownerPrincipalId}</if>
            <if test="createdAtFrom != null">AND d.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND d.created_at &lt;= #{createdAtTo}</if>
            </script>
            """)
    long countPage(@Param("tenantId") Long tenantId,
                   @Param("definitionId") String definitionId,
                   @Param("definitionKind") String definitionKind,
                   @Param("definitionCode") String definitionCode,
                   @Param("displayName") String displayName,
                   @Param("status") String status,
                   @Param("ownerPrincipalId") String ownerPrincipalId,
                   @Param("createdAtFrom") LocalDateTime createdAtFrom,
                   @Param("createdAtTo") LocalDateTime createdAtTo);

    @Select("""
            <script>
            SELECT d.definition_id,
                   d.definition_kind,
                   d.definition_code,
                   d.display_name,
                   d.status,
                   d.current_version,
                   d.owner_principal_id,
                   d.created_at,
                   d.updated_at
            FROM cloudmold_metadata_definition d
            WHERE d.tenant_id = #{tenantId}
            <if test="definitionId != null">AND d.definition_id = #{definitionId}</if>
            <if test="definitionKind != null">AND d.definition_kind = #{definitionKind}</if>
            <if test="definitionCode != null">AND d.definition_code LIKE CONCAT('%', #{definitionCode}, '%')</if>
            <if test="displayName != null">AND d.display_name LIKE CONCAT('%', #{displayName}, '%')</if>
            <if test="status != null">AND d.status = #{status}</if>
            <if test="ownerPrincipalId != null">AND d.owner_principal_id = #{ownerPrincipalId}</if>
            <if test="createdAtFrom != null">AND d.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND d.created_at &lt;= #{createdAtTo}</if>
            ORDER BY d.updated_at DESC, d.definition_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<MetadataDefinitionPageItem> selectPage(@Param("tenantId") Long tenantId,
                                                @Param("definitionId") String definitionId,
                                                @Param("definitionKind") String definitionKind,
                                                @Param("definitionCode") String definitionCode,
                                                @Param("displayName") String displayName,
                                                @Param("status") String status,
                                                @Param("ownerPrincipalId") String ownerPrincipalId,
                                                @Param("createdAtFrom") LocalDateTime createdAtFrom,
                                                @Param("createdAtTo") LocalDateTime createdAtTo,
                                                @Param("offset") long offset,
                                                @Param("limit") int limit);
}
