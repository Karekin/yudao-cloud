package cn.iocoder.yudao.module.cloudmold.engagement.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.engagement.service.query.NotificationCampaignPageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NotificationCampaignPageMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_engagement_notification_campaign c
            WHERE c.tenant_id = #{tenantId}
            <if test="campaignId != null">AND c.campaign_id = #{campaignId}</if>
            <if test="campaignCode != null">AND c.campaign_code LIKE CONCAT('%', #{campaignCode}, '%')</if>
            <if test="campaignName != null">AND c.campaign_name LIKE CONCAT('%', #{campaignName}, '%')</if>
            <if test="channel != null">AND c.channel = #{channel}</if>
            <if test="status != null">AND c.status = #{status}</if>
            <if test="createdAtFrom != null">AND c.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND c.created_at &lt;= #{createdAtTo}</if>
            </script>
            """)
    long countPage(@Param("tenantId") Long tenantId,
                   @Param("campaignId") String campaignId,
                   @Param("campaignCode") String campaignCode,
                   @Param("campaignName") String campaignName,
                   @Param("channel") String channel,
                   @Param("status") String status,
                   @Param("createdAtFrom") LocalDateTime createdAtFrom,
                   @Param("createdAtTo") LocalDateTime createdAtTo);

    @Select("""
            <script>
            SELECT c.campaign_id,
                   c.campaign_code,
                   c.campaign_name,
                   c.channel,
                   c.status,
                   c.source_system,
                   c.source_type,
                   c.source_id,
                   c.version AS aggregate_version,
                   c.created_at,
                   c.updated_at
            FROM cloudmold_engagement_notification_campaign c
            WHERE c.tenant_id = #{tenantId}
            <if test="campaignId != null">AND c.campaign_id = #{campaignId}</if>
            <if test="campaignCode != null">AND c.campaign_code LIKE CONCAT('%', #{campaignCode}, '%')</if>
            <if test="campaignName != null">AND c.campaign_name LIKE CONCAT('%', #{campaignName}, '%')</if>
            <if test="channel != null">AND c.channel = #{channel}</if>
            <if test="status != null">AND c.status = #{status}</if>
            <if test="createdAtFrom != null">AND c.created_at &gt;= #{createdAtFrom}</if>
            <if test="createdAtTo != null">AND c.created_at &lt;= #{createdAtTo}</if>
            ORDER BY c.updated_at DESC, c.campaign_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<NotificationCampaignPageItem> selectPage(@Param("tenantId") Long tenantId,
                                                  @Param("campaignId") String campaignId,
                                                  @Param("campaignCode") String campaignCode,
                                                  @Param("campaignName") String campaignName,
                                                  @Param("channel") String channel,
                                                  @Param("status") String status,
                                                  @Param("createdAtFrom") LocalDateTime createdAtFrom,
                                                  @Param("createdAtTo") LocalDateTime createdAtTo,
                                                  @Param("offset") long offset,
                                                  @Param("limit") int limit);
}
