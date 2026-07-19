package cn.iocoder.yudao.module.cloudmold.promotion.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.promotion.service.query.PromotionCampaignPageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PromotionCampaignPageMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_promotion_campaign c
            WHERE c.tenant_id = #{tenantId}
            <if test="campaignId != null">AND c.campaign_id = #{campaignId}</if>
            <if test="campaignCode != null">AND c.campaign_code LIKE CONCAT('%', #{campaignCode}, '%')</if>
            <if test="name != null">AND c.name LIKE CONCAT('%', #{name}, '%')</if>
            <if test="status != null">AND c.status = #{status}</if>
            <if test="campaignKind != null">AND c.campaign_kind = #{campaignKind}</if>
            <if test="createdAtFrom != null">AND c.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND c.created_at &lt;= #{createdAtTo}</if>
            </script>
            """)
    long countPage(@Param("tenantId") Long tenantId,
                   @Param("campaignId") String campaignId,
                   @Param("campaignCode") String campaignCode,
                   @Param("name") String name,
                   @Param("status") String status,
                   @Param("campaignKind") String campaignKind,
                   @Param("createdAtFrom") LocalDateTime createdAtFrom,
                   @Param("createdAtTo") LocalDateTime createdAtTo);

    @Select("""
            <script>
            SELECT c.campaign_id,
                   c.campaign_code,
                   c.campaign_kind,
                   c.name,
                   c.status,
                   c.starts_at,
                   c.ends_at,
                   c.version AS aggregate_version,
                   c.created_at,
                   c.updated_at
            FROM cloudmold_promotion_campaign c
            WHERE c.tenant_id = #{tenantId}
            <if test="campaignId != null">AND c.campaign_id = #{campaignId}</if>
            <if test="campaignCode != null">AND c.campaign_code LIKE CONCAT('%', #{campaignCode}, '%')</if>
            <if test="name != null">AND c.name LIKE CONCAT('%', #{name}, '%')</if>
            <if test="status != null">AND c.status = #{status}</if>
            <if test="campaignKind != null">AND c.campaign_kind = #{campaignKind}</if>
            <if test="createdAtFrom != null">AND c.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND c.created_at &lt;= #{createdAtTo}</if>
            ORDER BY c.updated_at DESC, c.campaign_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<PromotionCampaignPageItem> selectPage(@Param("tenantId") Long tenantId,
                                               @Param("campaignId") String campaignId,
                                               @Param("campaignCode") String campaignCode,
                                               @Param("name") String name,
                                               @Param("status") String status,
                                               @Param("campaignKind") String campaignKind,
                                               @Param("createdAtFrom") LocalDateTime createdAtFrom,
                                               @Param("createdAtTo") LocalDateTime createdAtTo,
                                               @Param("offset") long offset,
                                               @Param("limit") int limit);
}
