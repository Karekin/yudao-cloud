package cn.iocoder.yudao.module.cloudmold.commercebehavior.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.commercebehavior.dal.dataobject.AppRecommendationSnapshotDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface AppRecommendationReadMapper {

    @Select("""
            SELECT d.decision_id,d.decision_token,d.session_id,
                   i.listing_id,i.listing_offer_id,i.merchant_id,i.shop_id,i.channel_code,
                   i.canonical_spu_id,i.canonical_sku_id,i.price_minor,i.currency_code,i.rank_no,
                   i.available_quantity,i.quality_status
            FROM cloudmold_app_recommendation_decision d
            JOIN cloudmold_app_recommendation_item i
              ON i.tenant_id=d.tenant_id AND i.decision_id=d.decision_id
            WHERE d.tenant_id=#{tenantId}
              AND d.session_id=#{sessionId}
              AND d.decision_token=#{decisionToken}
              AND d.status='ACTIVE'
              AND d.expires_at > #{now}
              AND i.listing_id=#{listingId}
              AND i.rank_no=#{rankNo}
            LIMIT 1
            """)
    AppRecommendationSnapshotDO selectSnapshot(@Param("tenantId") Long tenantId,
                                               @Param("sessionId") String sessionId,
                                               @Param("decisionToken") String decisionToken,
                                               @Param("listingId") String listingId,
                                               @Param("rankNo") Integer rankNo,
                                               @Param("now") LocalDateTime now);
}
