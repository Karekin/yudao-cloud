package cn.iocoder.yudao.module.cloudmold.appcommerce.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppRecommendationDecisionDO;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppRecommendationItemDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AppRecommendationMapper extends BaseMapperX<AppRecommendationDecisionDO> {

    @Select("""
            SELECT decision_id,tenant_id,session_id,scene_code,request_hash,decision_token,result_set_token,
                   policy_version,status,ttl_seconds,item_count,generated_at,expires_at,created_at,updated_at
            FROM cloudmold_app_recommendation_decision
            WHERE tenant_id=#{tenantId}
              AND session_id=#{sessionId}
              AND scene_code=#{sceneCode}
              AND request_hash=#{requestHash}
              AND status='ACTIVE'
              AND expires_at > #{now}
            ORDER BY generated_at DESC
            LIMIT 1
            """)
    AppRecommendationDecisionDO selectActiveDecision(@Param("tenantId") Long tenantId,
                                                     @Param("sessionId") String sessionId,
                                                     @Param("sceneCode") String sceneCode,
                                                     @Param("requestHash") String requestHash,
                                                     @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_app_recommendation_decision
              (decision_id,tenant_id,session_id,scene_code,request_hash,decision_token,result_set_token,
               policy_version,status,ttl_seconds,item_count,generated_at,expires_at,created_at,updated_at)
            VALUES
              (#{decisionId},#{tenantId},#{sessionId},#{sceneCode},#{requestHash},#{decisionToken},#{resultSetToken},
               #{policyVersion},#{status},#{ttlSeconds},#{itemCount},#{generatedAt},#{expiresAt},#{createdAt},#{updatedAt})
            """)
    int insertDecision(AppRecommendationDecisionDO row);

    @Insert("""
            INSERT INTO cloudmold_app_recommendation_item
              (tenant_id,decision_id,rank_no,reason_code,listing_id,listing_no,listing_title,primary_image_url,
               listing_offer_id,merchant_id,shop_id,channel_code,canonical_spu_id,canonical_sku_id,price_minor,
               currency_code,inventory_version,available_quantity,quality_status,created_at)
            VALUES
              (#{tenantId},#{decisionId},#{rankNo},#{reasonCode},#{listingId},#{listingNo},#{listingTitle},
               #{primaryImageUrl},#{listingOfferId},#{merchantId},#{shopId},#{channelCode},#{canonicalSpuId},
               #{canonicalSkuId},#{priceMinor},#{currencyCode},#{inventoryVersion},#{availableQuantity},
               #{qualityStatus},#{createdAt})
            """)
    int insertItem(AppRecommendationItemDO row);

    @Select("""
            SELECT item_id,tenant_id,decision_id,rank_no,reason_code,listing_id,listing_no,listing_title,primary_image_url,
                   listing_offer_id,merchant_id,shop_id,channel_code,canonical_spu_id,canonical_sku_id,price_minor,
                   currency_code,inventory_version,available_quantity,quality_status,created_at
            FROM cloudmold_app_recommendation_item
            WHERE tenant_id=#{tenantId} AND decision_id=#{decisionId}
            ORDER BY rank_no ASC
            """)
    List<AppRecommendationItemDO> selectItems(@Param("tenantId") Long tenantId,
                                              @Param("decisionId") String decisionId);

    @Select("""
            SELECT i.item_id,i.tenant_id,i.decision_id,i.rank_no,i.reason_code,i.listing_id,i.listing_no,
                   i.listing_title,i.primary_image_url,i.listing_offer_id,i.merchant_id,i.shop_id,i.channel_code,
                   i.canonical_spu_id,i.canonical_sku_id,i.price_minor,i.currency_code,i.inventory_version,
                   i.available_quantity,i.quality_status,i.created_at
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
    AppRecommendationItemDO selectActiveItem(@Param("tenantId") Long tenantId,
                                             @Param("sessionId") String sessionId,
                                             @Param("decisionToken") String decisionToken,
                                             @Param("listingId") String listingId,
                                             @Param("rankNo") Integer rankNo,
                                             @Param("now") LocalDateTime now);
}
