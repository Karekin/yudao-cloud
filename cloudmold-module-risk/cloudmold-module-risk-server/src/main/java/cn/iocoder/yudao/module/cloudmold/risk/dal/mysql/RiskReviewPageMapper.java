package cn.iocoder.yudao.module.cloudmold.risk.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.risk.service.query.RiskReviewPageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RiskReviewPageMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_risk_review_case c
            WHERE c.tenant_id = #{tenantId}
            <if test="caseId != null">AND c.case_id = #{caseId}</if>
            <if test="clusterId != null">AND c.cluster_id = #{clusterId}</if>
            <if test="status != null">AND c.status = #{status}</if>
            <if test="reviewerPrincipalId != null">AND c.reviewer_principal_id = #{reviewerPrincipalId}</if>
            <if test="createdAtFrom != null">AND c.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND c.created_at &lt;= #{createdAtTo}</if>
            </script>
            """)
    long countPage(@Param("tenantId") Long tenantId,
                   @Param("caseId") String caseId,
                   @Param("clusterId") String clusterId,
                   @Param("status") String status,
                   @Param("reviewerPrincipalId") String reviewerPrincipalId,
                   @Param("createdAtFrom") LocalDateTime createdAtFrom,
                   @Param("createdAtTo") LocalDateTime createdAtTo);

    @Select("""
            <script>
            SELECT c.case_id,
                   c.cluster_id,
                   c.status,
                   c.reviewer_principal_id,
                   c.version AS aggregate_version,
                   c.created_at,
                   c.updated_at
            FROM cloudmold_risk_review_case c
            WHERE c.tenant_id = #{tenantId}
            <if test="caseId != null">AND c.case_id = #{caseId}</if>
            <if test="clusterId != null">AND c.cluster_id = #{clusterId}</if>
            <if test="status != null">AND c.status = #{status}</if>
            <if test="reviewerPrincipalId != null">AND c.reviewer_principal_id = #{reviewerPrincipalId}</if>
            <if test="createdAtFrom != null">AND c.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND c.created_at &lt;= #{createdAtTo}</if>
            ORDER BY c.updated_at DESC, c.case_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<RiskReviewPageItem> selectPage(@Param("tenantId") Long tenantId,
                                        @Param("caseId") String caseId,
                                        @Param("clusterId") String clusterId,
                                        @Param("status") String status,
                                        @Param("reviewerPrincipalId") String reviewerPrincipalId,
                                        @Param("createdAtFrom") LocalDateTime createdAtFrom,
                                        @Param("createdAtTo") LocalDateTime createdAtTo,
                                        @Param("offset") long offset,
                                        @Param("limit") int limit);
}
