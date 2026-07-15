package cn.iocoder.yudao.module.cloudmold.promotion.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject.PromotionCampaignDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

@Mapper
public interface PromotionCampaignMapper extends BaseMapperX<PromotionCampaignDO> {
    @Select("SELECT * FROM cloudmold_promotion_campaign WHERE tenant_id=#{tenantId} AND campaign_id=#{id}")
    PromotionCampaignDO selectOneById(@Param("tenantId") Long tenantId, @Param("id") String id);
    @Select("SELECT * FROM cloudmold_promotion_campaign WHERE tenant_id=#{tenantId} AND campaign_id=#{id} FOR UPDATE")
    PromotionCampaignDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("id") String id);
    @Select("SELECT * FROM cloudmold_promotion_campaign WHERE tenant_id=#{tenantId} AND campaign_code=#{code}")
    PromotionCampaignDO selectByCode(@Param("tenantId") Long tenantId, @Param("code") String code);
    @Update("UPDATE cloudmold_promotion_campaign SET status=#{status},version=version+1,updated_at=#{now} WHERE tenant_id=#{tenantId} AND campaign_id=#{id} AND version=#{version}")
    int updateStatusCas(@Param("tenantId") Long tenantId, @Param("id") String id, @Param("version") Long version,
                        @Param("status") String status, @Param("now") LocalDateTime now);
}
