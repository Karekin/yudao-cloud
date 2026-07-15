package cn.iocoder.yudao.module.cloudmold.promotion.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject.AdvertisingInteractionDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AdvertisingInteractionMapper {
    @Insert("""
        INSERT INTO cloudmold_promotion_advertising_interaction
          (interaction_id,tenant_id,deduplication_key,interaction_type,placement_id,campaign_id,principal_id,
           session_id,source_interaction_id,order_ref,attribution_amount_minor,currency_code,occurred_at,created_at)
        VALUES
          (#{interactionId},#{tenantId},#{deduplicationKey},#{interactionType},#{placementId},#{campaignId},
           #{principalId},#{sessionId},#{sourceInteractionId},#{orderRef},#{attributionAmountMinor},#{currencyCode},
           #{occurredAt},#{createdAt})
        """)
    int insert(AdvertisingInteractionDO row);

    @Select("SELECT * FROM cloudmold_promotion_advertising_interaction WHERE tenant_id=#{tenantId} AND interaction_id=#{id}")
    AdvertisingInteractionDO selectById(@Param("tenantId") Long tenantId, @Param("id") String id);
    @Select("SELECT * FROM cloudmold_promotion_advertising_interaction WHERE tenant_id=#{tenantId} AND deduplication_key=#{key}")
    AdvertisingInteractionDO selectByDeduplicationKey(@Param("tenantId") Long tenantId, @Param("key") String key);
}
