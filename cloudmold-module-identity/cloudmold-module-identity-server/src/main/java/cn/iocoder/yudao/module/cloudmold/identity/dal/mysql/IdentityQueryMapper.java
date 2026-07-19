package cn.iocoder.yudao.module.cloudmold.identity.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.identity.service.query.IdentityOperationPageItem;
import cn.iocoder.yudao.module.cloudmold.identity.service.query.IdentityPrincipalPageItem;
import cn.iocoder.yudao.module.cloudmold.identity.service.query.IdentitySourcePageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * CloudMold 规范身份只读查询 Mapper（Query 侧，与 Command 侧 PrincipalMapper /
 * SourceIdentityMapper / IdentityOperationMapper 分离）。
 * 走纯注解 SQL + script 动态条件 + LIMIT/OFFSET 手动分页，不继承 MyBatis-Plus BaseMapper。
 * cloudmold 规范表无逻辑删除列，不过滤 deleted。
 */
@Mapper
public interface IdentityQueryMapper {

    // ===== Principal（无 JOIN，主数据自含） =====
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_identity_principal p
            WHERE p.tenant_id = #{tenantId}
            <if test="principalType != null">AND p.principal_type = #{principalType}</if>
            <if test="status != null">AND p.status = #{status}</if>
            </script>
            """)
    long countPrincipalPage(@Param("tenantId") Long tenantId,
                            @Param("principalType") String principalType,
                            @Param("status") String status);

    @Select("""
            <script>
            SELECT p.principal_id, p.tenant_id, p.principal_type, p.status, p.version,
                   p.created_at, p.updated_at
            FROM cloudmold_identity_principal p
            WHERE p.tenant_id = #{tenantId}
            <if test="principalType != null">AND p.principal_type = #{principalType}</if>
            <if test="status != null">AND p.status = #{status}</if>
            ORDER BY p.updated_at DESC, p.principal_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<IdentityPrincipalPageItem> selectPrincipalPage(@Param("tenantId") Long tenantId,
                                                        @Param("principalType") String principalType,
                                                        @Param("status") String status,
                                                        @Param("offset") long offset,
                                                        @Param("limit") int limit);

    // ===== Source identity（JOIN principal 取 principalType） =====
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_identity_source_identity s
            WHERE s.tenant_id = #{tenantId}
            <if test="principalId != null">AND s.principal_id = #{principalId}</if>
            <if test="sourceSystem != null">AND s.source_system = #{sourceSystem}</if>
            <if test="sourceType != null">AND s.source_type = #{sourceType}</if>
            <if test="status != null">AND s.status = #{status}</if>
            </script>
            """)
    long countSourcePage(@Param("tenantId") Long tenantId,
                         @Param("principalId") String principalId,
                         @Param("sourceSystem") String sourceSystem,
                         @Param("sourceType") String sourceType,
                         @Param("status") String status);

    @Select("""
            <script>
            SELECT s.source_identity_id, s.principal_id, p.principal_type,
                   s.source_system, s.source_type, s.source_id, s.status, s.version,
                   s.valid_from, s.valid_to, s.updated_at
            FROM cloudmold_identity_source_identity s
            LEFT JOIN cloudmold_identity_principal p
              ON p.tenant_id = s.tenant_id AND p.principal_id = s.principal_id
            WHERE s.tenant_id = #{tenantId}
            <if test="principalId != null">AND s.principal_id = #{principalId}</if>
            <if test="sourceSystem != null">AND s.source_system = #{sourceSystem}</if>
            <if test="sourceType != null">AND s.source_type = #{sourceType}</if>
            <if test="status != null">AND s.status = #{status}</if>
            ORDER BY s.updated_at DESC, s.source_identity_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<IdentitySourcePageItem> selectSourcePage(@Param("tenantId") Long tenantId,
                                                  @Param("principalId") String principalId,
                                                  @Param("sourceSystem") String sourceSystem,
                                                  @Param("sourceType") String sourceType,
                                                  @Param("status") String status,
                                                  @Param("offset") long offset,
                                                  @Param("limit") int limit);

    // ===== Operation（独立，principal_id 可空，不 JOIN） =====
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_identity_operation o
            WHERE o.tenant_id = #{tenantId}
            <if test="principalId != null">AND o.principal_id = #{principalId}</if>
            <if test="commandType != null">AND o.command_type = #{commandType}</if>
            <if test="status != null">AND o.status = #{status}</if>
            </script>
            """)
    long countOperationPage(@Param("tenantId") Long tenantId,
                            @Param("principalId") String principalId,
                            @Param("commandType") String commandType,
                            @Param("status") Integer status);

    @Select("""
            <script>
            SELECT o.operation_id, o.idempotency_key, o.command_type, o.status, o.principal_id,
                   o.created_at, o.updated_at
            FROM cloudmold_identity_operation o
            WHERE o.tenant_id = #{tenantId}
            <if test="principalId != null">AND o.principal_id = #{principalId}</if>
            <if test="commandType != null">AND o.command_type = #{commandType}</if>
            <if test="status != null">AND o.status = #{status}</if>
            ORDER BY o.updated_at DESC, o.operation_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<IdentityOperationPageItem> selectOperationPage(@Param("tenantId") Long tenantId,
                                                        @Param("principalId") String principalId,
                                                        @Param("commandType") String commandType,
                                                        @Param("status") Integer status,
                                                        @Param("offset") long offset,
                                                        @Param("limit") int limit);
}
