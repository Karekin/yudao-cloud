package cn.iocoder.yudao.module.cloudmold.operationsintelligence.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.operationsintelligence.service.query.OperationsAlertPageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OperationsAlertPageMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_operations_alert a
            WHERE a.tenant_id = #{tenantId}
            <if test="alertId != null">AND a.alert_id = #{alertId}</if>
            <if test="alertCode != null">AND a.alert_code LIKE CONCAT('%', #{alertCode}, '%')</if>
            <if test="sourceType != null">AND a.source_type = #{sourceType}</if>
            <if test="severity != null">AND a.severity = #{severity}</if>
            <if test="category != null">AND a.category = #{category}</if>
            <if test="status != null">AND a.status = #{status}</if>
            <if test="currentActorPrincipalId != null">AND a.current_actor_principal_id = #{currentActorPrincipalId}</if>
            <if test="createdAtFrom != null">AND a.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND a.created_at &lt;= #{createdAtTo}</if>
            </script>
            """)
    long countPage(@Param("tenantId") Long tenantId,
                   @Param("alertId") String alertId,
                   @Param("alertCode") String alertCode,
                   @Param("sourceType") String sourceType,
                   @Param("severity") String severity,
                   @Param("category") String category,
                   @Param("status") String status,
                   @Param("currentActorPrincipalId") String currentActorPrincipalId,
                   @Param("createdAtFrom") LocalDateTime createdAtFrom,
                   @Param("createdAtTo") LocalDateTime createdAtTo);

    @Select("""
            <script>
            SELECT a.alert_id,
                   a.alert_code,
                   a.source_type,
                   a.source_ref,
                   a.severity,
                   a.category,
                   a.subcategory,
                   a.status,
                   a.current_actor_principal_id,
                   a.version AS aggregate_version,
                   a.opened_at,
                   a.terminal_at,
                   a.created_at,
                   a.updated_at
            FROM cloudmold_operations_alert a
            WHERE a.tenant_id = #{tenantId}
            <if test="alertId != null">AND a.alert_id = #{alertId}</if>
            <if test="alertCode != null">AND a.alert_code LIKE CONCAT('%', #{alertCode}, '%')</if>
            <if test="sourceType != null">AND a.source_type = #{sourceType}</if>
            <if test="severity != null">AND a.severity = #{severity}</if>
            <if test="category != null">AND a.category = #{category}</if>
            <if test="status != null">AND a.status = #{status}</if>
            <if test="currentActorPrincipalId != null">AND a.current_actor_principal_id = #{currentActorPrincipalId}</if>
            <if test="createdAtFrom != null">AND a.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND a.created_at &lt;= #{createdAtTo}</if>
            ORDER BY a.updated_at DESC, a.alert_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<OperationsAlertPageItem> selectPage(@Param("tenantId") Long tenantId,
                                             @Param("alertId") String alertId,
                                             @Param("alertCode") String alertCode,
                                             @Param("sourceType") String sourceType,
                                             @Param("severity") String severity,
                                             @Param("category") String category,
                                             @Param("status") String status,
                                             @Param("currentActorPrincipalId") String currentActorPrincipalId,
                                             @Param("createdAtFrom") LocalDateTime createdAtFrom,
                                             @Param("createdAtTo") LocalDateTime createdAtTo,
                                             @Param("offset") long offset,
                                             @Param("limit") int limit);
}
