package cn.iocoder.yudao.module.cloudmold.commercebehavior.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.commercebehavior.service.query.BehaviorEventPageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CommerceBehaviorEventPageMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_commerce_behavior_event e
            WHERE e.tenant_id = #{tenantId}
            <if test="behaviorId != null">AND e.behavior_id = #{behaviorId}</if>
            <if test="sessionId != null">AND e.session_id = #{sessionId}</if>
            <if test="principalId != null">AND e.principal_id = #{principalId}</if>
            <if test="behaviorType != null">AND e.behavior_type = #{behaviorType}</if>
            <if test="canonicalSpuId != null">AND e.canonical_spu_id = #{canonicalSpuId}</if>
            <if test="shopId != null">AND e.shop_id = #{shopId}</if>
            <if test="merchantId != null">AND e.merchant_id = #{merchantId}</if>
            <if test="channelCode != null">AND e.channel_code = #{channelCode}</if>
            <if test="occurredAtFrom != null">AND e.occurred_at &gt;= #{occurredAtFrom}</if>
            <if test="occurredAtTo != null">AND e.occurred_at &lt;= #{occurredAtTo}</if>
            </script>
            """)
    long countPage(@Param("tenantId") Long tenantId,
                   @Param("behaviorId") String behaviorId,
                   @Param("sessionId") String sessionId,
                   @Param("principalId") String principalId,
                   @Param("behaviorType") String behaviorType,
                   @Param("canonicalSpuId") String canonicalSpuId,
                   @Param("shopId") String shopId,
                   @Param("merchantId") String merchantId,
                   @Param("channelCode") String channelCode,
                   @Param("occurredAtFrom") LocalDateTime occurredAtFrom,
                   @Param("occurredAtTo") LocalDateTime occurredAtTo);

    @Select("""
            <script>
            SELECT e.behavior_id,
                   e.session_id,
                   e.behavior_type,
                   e.principal_id,
                   e.canonical_spu_id,
                   e.listing_id,
                   e.merchant_id,
                   e.shop_id,
                   e.channel_code,
                   e.quantity,
                   e.checkout_token,
                   e.source_system,
                   e.source_type,
                   e.source_id,
                   e.occurred_at,
                   e.created_at
            FROM cloudmold_commerce_behavior_event e
            WHERE e.tenant_id = #{tenantId}
            <if test="behaviorId != null">AND e.behavior_id = #{behaviorId}</if>
            <if test="sessionId != null">AND e.session_id = #{sessionId}</if>
            <if test="principalId != null">AND e.principal_id = #{principalId}</if>
            <if test="behaviorType != null">AND e.behavior_type = #{behaviorType}</if>
            <if test="canonicalSpuId != null">AND e.canonical_spu_id = #{canonicalSpuId}</if>
            <if test="shopId != null">AND e.shop_id = #{shopId}</if>
            <if test="merchantId != null">AND e.merchant_id = #{merchantId}</if>
            <if test="channelCode != null">AND e.channel_code = #{channelCode}</if>
            <if test="occurredAtFrom != null">AND e.occurred_at &gt;= #{occurredAtFrom}</if>
            <if test="occurredAtTo != null">AND e.occurred_at &lt;= #{occurredAtTo}</if>
            ORDER BY e.occurred_at DESC, e.behavior_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<BehaviorEventPageItem> selectPage(@Param("tenantId") Long tenantId,
                                           @Param("behaviorId") String behaviorId,
                                           @Param("sessionId") String sessionId,
                                           @Param("principalId") String principalId,
                                           @Param("behaviorType") String behaviorType,
                                           @Param("canonicalSpuId") String canonicalSpuId,
                                           @Param("shopId") String shopId,
                                           @Param("merchantId") String merchantId,
                                           @Param("channelCode") String channelCode,
                                           @Param("occurredAtFrom") LocalDateTime occurredAtFrom,
                                           @Param("occurredAtTo") LocalDateTime occurredAtTo,
                                           @Param("offset") long offset,
                                           @Param("limit") int limit);
}
