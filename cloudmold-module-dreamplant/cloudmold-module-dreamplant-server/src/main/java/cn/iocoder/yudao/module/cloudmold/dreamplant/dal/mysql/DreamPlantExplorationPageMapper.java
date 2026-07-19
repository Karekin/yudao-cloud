package cn.iocoder.yudao.module.cloudmold.dreamplant.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.dreamplant.service.query.DreamPlantExplorationPageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DreamPlantExplorationPageMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_dreamplant_exploration e
            WHERE e.tenant_id = #{tenantId}
            <if test="explorationRunId != null">AND e.exploration_run_id = #{explorationRunId}</if>
            <if test="mapKey != null">AND e.map_key = #{mapKey}</if>
            <if test="status != null">AND e.status = #{status}</if>
            <if test="requestedByPrincipalId != null">AND e.requested_by_principal_id = #{requestedByPrincipalId}</if>
            <if test="createdAtFrom != null">AND e.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND e.created_at &lt;= #{createdAtTo}</if>
            </script>
            """)
    long countPage(@Param("tenantId") Long tenantId,
                   @Param("explorationRunId") String explorationRunId,
                   @Param("mapKey") String mapKey,
                   @Param("status") String status,
                   @Param("requestedByPrincipalId") String requestedByPrincipalId,
                   @Param("createdAtFrom") LocalDateTime createdAtFrom,
                   @Param("createdAtTo") LocalDateTime createdAtTo);

    @Select("""
            <script>
            SELECT e.exploration_run_id,
                   e.map_key,
                   e.intent,
                   e.requested_by_principal_id,
                   e.status,
                   e.version AS aggregate_version,
                   e.started_at,
                   e.completed_at,
                   e.created_at,
                   e.updated_at
            FROM cloudmold_dreamplant_exploration e
            WHERE e.tenant_id = #{tenantId}
            <if test="explorationRunId != null">AND e.exploration_run_id = #{explorationRunId}</if>
            <if test="mapKey != null">AND e.map_key = #{mapKey}</if>
            <if test="status != null">AND e.status = #{status}</if>
            <if test="requestedByPrincipalId != null">AND e.requested_by_principal_id = #{requestedByPrincipalId}</if>
            <if test="createdAtFrom != null">AND e.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND e.created_at &lt;= #{createdAtTo}</if>
            ORDER BY e.updated_at DESC, e.exploration_run_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<DreamPlantExplorationPageItem> selectPage(@Param("tenantId") Long tenantId,
                                                    @Param("explorationRunId") String explorationRunId,
                                                    @Param("mapKey") String mapKey,
                                                    @Param("status") String status,
                                                    @Param("requestedByPrincipalId") String requestedByPrincipalId,
                                                    @Param("createdAtFrom") LocalDateTime createdAtFrom,
                                                    @Param("createdAtTo") LocalDateTime createdAtTo,
                                                    @Param("offset") long offset,
                                                    @Param("limit") int limit);
}
