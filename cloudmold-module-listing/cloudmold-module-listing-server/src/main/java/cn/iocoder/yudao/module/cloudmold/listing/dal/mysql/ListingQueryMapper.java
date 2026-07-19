package cn.iocoder.yudao.module.cloudmold.listing.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.listing.service.query.ListingPageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ListingQueryMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_listing_header h
            WHERE h.tenant_id = #{tenantId}
            <if test="listingId != null">AND h.listing_id = #{listingId}</if>
            <if test="listingNo != null">AND h.listing_no LIKE CONCAT('%', #{listingNo}, '%')</if>
            <if test="title != null">AND h.title LIKE CONCAT('%', #{title}, '%')</if>
            <if test="merchantId != null">AND h.merchant_id = #{merchantId}</if>
            <if test="shopId != null">AND h.shop_id = #{shopId}</if>
            <if test="channelCode != null">AND h.channel_code = #{channelCode}</if>
            <if test="canonicalSpuId != null">AND h.canonical_spu_id = #{canonicalSpuId}</if>
            <if test="status != null">AND h.status = #{status}</if>
            </script>
            """)
    long countListingPage(@Param("tenantId") Long tenantId,
                          @Param("listingId") String listingId,
                          @Param("listingNo") String listingNo,
                          @Param("title") String title,
                          @Param("merchantId") String merchantId,
                          @Param("shopId") String shopId,
                          @Param("channelCode") String channelCode,
                          @Param("canonicalSpuId") String canonicalSpuId,
                          @Param("status") String status);

    @Select("""
            <script>
            SELECT h.listing_id,
                   h.listing_no,
                   h.title,
                   h.merchant_id,
                   h.shop_id,
                   h.channel_code,
                   h.canonical_spu_id,
                   h.revision,
                   h.status,
                   h.completion_passed,
                   h.business_approved,
                   h.risk_approved,
                   COALESCE(s.offer_count, 0) AS offer_count,
                   COALESCE(s.enabled_offer_count, 0) AS enabled_offer_count,
                   s.min_price_minor,
                   s.max_price_minor,
                   h.currency_code,
                   h.publish_start_at,
                   h.publish_end_at,
                   h.version,
                   h.updated_at
            FROM cloudmold_listing_header h
            LEFT JOIN (
                SELECT o.tenant_id,
                       o.listing_id,
                       o.revision,
                       COUNT(*) AS offer_count,
                       SUM(CASE WHEN o.enabled = TRUE THEN 1 ELSE 0 END) AS enabled_offer_count,
                       MIN(CASE WHEN o.enabled = TRUE THEN o.price_minor END) AS min_price_minor,
                       MAX(CASE WHEN o.enabled = TRUE THEN o.price_minor END) AS max_price_minor
                FROM cloudmold_listing_offer o
                WHERE o.tenant_id = #{tenantId}
                GROUP BY o.tenant_id, o.listing_id, o.revision
            ) s
              ON s.tenant_id = h.tenant_id
             AND s.listing_id = h.listing_id
             AND s.revision = h.revision
            WHERE h.tenant_id = #{tenantId}
            <if test="listingId != null">AND h.listing_id = #{listingId}</if>
            <if test="listingNo != null">AND h.listing_no LIKE CONCAT('%', #{listingNo}, '%')</if>
            <if test="title != null">AND h.title LIKE CONCAT('%', #{title}, '%')</if>
            <if test="merchantId != null">AND h.merchant_id = #{merchantId}</if>
            <if test="shopId != null">AND h.shop_id = #{shopId}</if>
            <if test="channelCode != null">AND h.channel_code = #{channelCode}</if>
            <if test="canonicalSpuId != null">AND h.canonical_spu_id = #{canonicalSpuId}</if>
            <if test="status != null">AND h.status = #{status}</if>
            ORDER BY h.updated_at DESC, h.listing_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ListingPageItem> selectListingPage(@Param("tenantId") Long tenantId,
                                            @Param("listingId") String listingId,
                                            @Param("listingNo") String listingNo,
                                            @Param("title") String title,
                                            @Param("merchantId") String merchantId,
                                            @Param("shopId") String shopId,
                                            @Param("channelCode") String channelCode,
                                            @Param("canonicalSpuId") String canonicalSpuId,
                                            @Param("status") String status,
                                            @Param("offset") long offset,
                                            @Param("limit") int limit);
}
