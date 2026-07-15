package cn.iocoder.yudao.module.cloudmold.engagement.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject.NotificationCampaignDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface NotificationCampaignMapper extends BaseMapperX<NotificationCampaignDO> {

    @Select("""
            SELECT campaign_id,tenant_id,campaign_code,campaign_name,channel,status,version,
                   source_system,source_type,source_id,created_at,updated_at
            FROM cloudmold_engagement_notification_campaign
            WHERE tenant_id=#{tenantId} AND campaign_id=#{campaignId}
            """)
    NotificationCampaignDO selectByTenantAndId(@Param("tenantId") Long tenantId,
                                               @Param("campaignId") String campaignId);

    @Select("""
            SELECT campaign_id,tenant_id,campaign_code,campaign_name,channel,status,version,
                   source_system,source_type,source_id,created_at,updated_at
            FROM cloudmold_engagement_notification_campaign
            WHERE tenant_id=#{tenantId} AND campaign_id=#{campaignId}
            FOR UPDATE
            """)
    NotificationCampaignDO selectForUpdate(@Param("tenantId") Long tenantId,
                                           @Param("campaignId") String campaignId);

    @Select("""
            SELECT campaign_id,tenant_id,campaign_code,campaign_name,channel,status,version,
                   source_system,source_type,source_id,created_at,updated_at
            FROM cloudmold_engagement_notification_campaign
            WHERE tenant_id=#{tenantId} AND campaign_code=#{campaignCode}
            """)
    NotificationCampaignDO selectByBusinessKey(@Param("tenantId") Long tenantId,
                                               @Param("campaignCode") String campaignCode);

    @Update("""
            UPDATE cloudmold_engagement_notification_campaign
            SET campaign_name=#{campaignName},status=#{status},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND campaign_id=#{campaignId} AND version=#{expectedVersion}
            """)
    int updateCampaign(@Param("tenantId") Long tenantId, @Param("campaignId") String campaignId,
                       @Param("expectedVersion") Long expectedVersion,
                       @Param("campaignName") String campaignName, @Param("status") String status,
                       @Param("now") LocalDateTime now);
}
