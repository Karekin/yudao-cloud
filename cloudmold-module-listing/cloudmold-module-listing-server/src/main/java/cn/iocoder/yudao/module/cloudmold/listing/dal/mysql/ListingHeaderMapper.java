package cn.iocoder.yudao.module.cloudmold.listing.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.listing.api.PublishedListingOfferView;
import cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.ListingHeaderDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ListingHeaderMapper extends BaseMapperX<ListingHeaderDO> {
    @Select("""
            SELECT listing_id,tenant_id,listing_no,run_id,merchant_id,channel_code,shop_id,canonical_spu_id,
                   revision,title,primary_image_url,category_ref,brand_ref,source_system,publisher_ref,currency_code,
                   publish_start_at,publish_end_at,status,completion_passed,business_approved,risk_approved,
                   version,created_at,updated_at
            FROM cloudmold_listing_header
            WHERE tenant_id=#{tenantId} AND listing_id=#{listingId}
            FOR UPDATE
            """)
    ListingHeaderDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("listingId") String listingId);

    @Select("""
            SELECT listing_id,tenant_id,listing_no,run_id,merchant_id,channel_code,shop_id,canonical_spu_id,
                   revision,title,primary_image_url,category_ref,brand_ref,source_system,publisher_ref,currency_code,
                   publish_start_at,publish_end_at,status,completion_passed,business_approved,risk_approved,
                   version,created_at,updated_at
            FROM cloudmold_listing_header
            WHERE tenant_id=#{tenantId} AND listing_id=#{listingId}
            """)
    ListingHeaderDO selectTenantListing(@Param("tenantId") Long tenantId, @Param("listingId") String listingId);

    @Select("""
            SELECT listing_id,tenant_id,listing_no,run_id,merchant_id,channel_code,shop_id,canonical_spu_id,
                   revision,title,primary_image_url,category_ref,brand_ref,source_system,publisher_ref,currency_code,
                   publish_start_at,publish_end_at,status,completion_passed,business_approved,risk_approved,
                   version,created_at,updated_at
            FROM cloudmold_listing_header
            WHERE tenant_id=#{tenantId} AND merchant_id=#{merchantId}
              AND (#{shopId} IS NULL OR shop_id=#{shopId}) AND status='PUBLISHED'
            ORDER BY listing_id
            FOR UPDATE
            """)
    List<ListingHeaderDO> selectPublishedForEligibilityEnforcement(@Param("tenantId") Long tenantId,
                                                                   @Param("merchantId") String merchantId,
                                                                   @Param("shopId") String shopId);

    @Update("""
            UPDATE cloudmold_listing_header
            SET revision=#{revision},status=#{nextStatus},completion_passed=#{completionPassed},
                business_approved=#{businessApproved},risk_approved=#{riskApproved},version=version+1,
                publisher_ref=COALESCE(#{publisherRef},publisher_ref),updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND listing_id=#{listingId}
              AND version=#{expectedVersion} AND status=#{expectedStatus}
            """)
    int transition(@Param("tenantId") Long tenantId, @Param("listingId") String listingId,
                   @Param("expectedVersion") Long expectedVersion, @Param("expectedStatus") String expectedStatus,
                   @Param("nextStatus") String nextStatus, @Param("revision") Integer revision,
                   @Param("completionPassed") Boolean completionPassed,
                   @Param("businessApproved") Boolean businessApproved,
                   @Param("riskApproved") Boolean riskApproved, @Param("publisherRef") String publisherRef,
                   @Param("now") LocalDateTime now);

    @Select("""
            SELECT h.listing_id,h.listing_no,o.listing_offer_id,h.merchant_id,h.channel_code,h.shop_id,h.canonical_spu_id,
                   o.canonical_sku_id,h.revision AS listing_revision,h.version AS listing_version,
                   o.price_minor,o.currency_code
            FROM cloudmold_listing_header h
            JOIN cloudmold_listing_offer o ON o.tenant_id=h.tenant_id AND o.listing_id=h.listing_id
                                         AND o.revision=h.revision
            WHERE h.tenant_id=#{tenantId} AND h.listing_id=#{listingId}
              AND o.listing_offer_id=#{listingOfferId} AND o.canonical_sku_id=#{canonicalSkuId}
              AND h.status='PUBLISHED' AND o.enabled=b'1'
              AND (h.publish_start_at IS NULL OR h.publish_start_at <= UTC_TIMESTAMP(6))
              AND (h.publish_end_at IS NULL OR h.publish_end_at > UTC_TIMESTAMP(6))
            """)
    PublishedListingOfferView selectPublishedOffer(@Param("tenantId") Long tenantId,
                                                    @Param("listingId") String listingId,
                                                    @Param("listingOfferId") String listingOfferId,
                                                    @Param("canonicalSkuId") String canonicalSkuId);

    @Select("""
            SELECT COUNT(*)
            FROM cloudmold_listing_header h
            WHERE h.tenant_id=#{tenantId} AND h.status='PUBLISHED'
              AND (h.publish_start_at IS NULL OR h.publish_start_at <= UTC_TIMESTAMP(6))
              AND (h.publish_end_at IS NULL OR h.publish_end_at > UTC_TIMESTAMP(6))
              AND (#{channelCode} IS NULL OR h.channel_code=#{channelCode})
              AND (#{keyword} IS NULL OR h.title LIKE CONCAT('%',#{keyword},'%')
                   OR h.listing_no LIKE CONCAT('%',#{keyword},'%'))
            """)
    long countCurrentPublished(@Param("tenantId") Long tenantId,
                               @Param("keyword") String keyword,
                               @Param("channelCode") String channelCode);

    @Select("""
            SELECT listing_id,tenant_id,listing_no,run_id,merchant_id,channel_code,shop_id,canonical_spu_id,
                   revision,title,primary_image_url,category_ref,brand_ref,source_system,publisher_ref,currency_code,
                   publish_start_at,publish_end_at,status,completion_passed,business_approved,risk_approved,
                   version,created_at,updated_at
            FROM cloudmold_listing_header h
            WHERE h.tenant_id=#{tenantId} AND h.status='PUBLISHED'
              AND (h.publish_start_at IS NULL OR h.publish_start_at <= UTC_TIMESTAMP(6))
              AND (h.publish_end_at IS NULL OR h.publish_end_at > UTC_TIMESTAMP(6))
              AND (#{channelCode} IS NULL OR h.channel_code=#{channelCode})
              AND (#{keyword} IS NULL OR h.title LIKE CONCAT('%',#{keyword},'%')
                   OR h.listing_no LIKE CONCAT('%',#{keyword},'%'))
            ORDER BY h.updated_at DESC,h.listing_id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<ListingHeaderDO> selectCurrentPublished(@Param("tenantId") Long tenantId,
                                                  @Param("keyword") String keyword,
                                                  @Param("channelCode") String channelCode,
                                                  @Param("offset") long offset,
                                                  @Param("limit") int limit);

    @Select("""
            SELECT listing_id,tenant_id,listing_no,run_id,merchant_id,channel_code,shop_id,canonical_spu_id,
                   revision,title,primary_image_url,category_ref,brand_ref,source_system,publisher_ref,currency_code,
                   publish_start_at,publish_end_at,status,completion_passed,business_approved,risk_approved,
                   version,created_at,updated_at
            FROM cloudmold_listing_header h
            WHERE h.tenant_id=#{tenantId} AND h.listing_id=#{listingId} AND h.status='PUBLISHED'
              AND (h.publish_start_at IS NULL OR h.publish_start_at <= UTC_TIMESTAMP(6))
              AND (h.publish_end_at IS NULL OR h.publish_end_at > UTC_TIMESTAMP(6))
            """)
    ListingHeaderDO selectCurrentPublishedById(@Param("tenantId") Long tenantId,
                                                @Param("listingId") String listingId);
}
