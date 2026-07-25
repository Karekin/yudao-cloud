package cn.iocoder.yudao.module.cloudmold.appcommerce.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppProductReviewDO;
import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppProductReviewListingSnapshotDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AppProductReviewMapper {

    @Select("""
            SELECT review_id,tenant_id,buyer_principal_id,order_id,order_item_id,payment_id,fulfillment_id,
                   listing_id,listing_offer_id,merchant_id,shop_id,canonical_spu_id,canonical_sku_id,
                   product_score,service_score,logistics_score,overall_score,
                   content_key_id,content_iv,content_ciphertext,content_digest_sha256,public_summary,
                   moderation_status,moderation_policy,approved_at,created_at,updated_at
            FROM cloudmold_app_product_review
            WHERE tenant_id=#{tenantId} AND order_item_id=#{orderItemId}
            LIMIT 1
            """)
    AppProductReviewDO selectByOrderItem(@Param("tenantId") Long tenantId,
                                         @Param("orderItemId") String orderItemId);

    @Select("""
            SELECT review_id,tenant_id,buyer_principal_id,order_id,order_item_id,payment_id,fulfillment_id,
                   listing_id,listing_offer_id,merchant_id,shop_id,canonical_spu_id,canonical_sku_id,
                   product_score,service_score,logistics_score,overall_score,
                   content_key_id,content_iv,content_ciphertext,content_digest_sha256,public_summary,
                   moderation_status,moderation_policy,approved_at,created_at,updated_at
            FROM cloudmold_app_product_review
            WHERE tenant_id=#{tenantId} AND review_id=#{reviewId}
            LIMIT 1
            """)
    AppProductReviewDO selectById(@Param("tenantId") Long tenantId,
                                  @Param("reviewId") String reviewId);

    @Insert("""
            INSERT INTO cloudmold_app_product_review
              (review_id,tenant_id,buyer_principal_id,order_id,order_item_id,payment_id,fulfillment_id,
               listing_id,listing_offer_id,merchant_id,shop_id,canonical_spu_id,canonical_sku_id,
               product_score,service_score,logistics_score,overall_score,
               content_key_id,content_iv,content_ciphertext,content_digest_sha256,public_summary,
               moderation_status,moderation_policy,approved_at,created_at,updated_at)
            VALUES
              (#{reviewId},#{tenantId},#{buyerPrincipalId},#{orderId},#{orderItemId},#{paymentId},#{fulfillmentId},
               #{listingId},#{listingOfferId},#{merchantId},#{shopId},#{canonicalSpuId},#{canonicalSkuId},
               #{productScore},#{serviceScore},#{logisticsScore},#{overallScore},
               #{contentKeyId},#{contentIv},#{contentCiphertext},#{contentDigestSha256},#{publicSummary},
               #{moderationStatus},#{moderationPolicy},#{approvedAt},#{createdAt},#{updatedAt})
            """)
    int insert(AppProductReviewDO row);

    @Select("""
            SELECT review_id,tenant_id,buyer_principal_id,order_id,order_item_id,payment_id,fulfillment_id,
                   listing_id,listing_offer_id,merchant_id,shop_id,canonical_spu_id,canonical_sku_id,
                   product_score,service_score,logistics_score,overall_score,
                   content_key_id,content_iv,content_ciphertext,content_digest_sha256,public_summary,
                   moderation_status,moderation_policy,approved_at,created_at,updated_at
            FROM cloudmold_app_product_review
            WHERE tenant_id=#{tenantId}
              AND listing_id=#{listingId}
              AND moderation_status='APPROVED'
            ORDER BY approved_at DESC, created_at DESC, review_id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<AppProductReviewDO> selectApprovedByListing(@Param("tenantId") Long tenantId,
                                                     @Param("listingId") String listingId,
                                                     @Param("offset") long offset,
                                                     @Param("limit") int limit);

    @Select("""
            SELECT review_id,tenant_id,buyer_principal_id,order_id,order_item_id,payment_id,fulfillment_id,
                   listing_id,listing_offer_id,merchant_id,shop_id,canonical_spu_id,canonical_sku_id,
                   product_score,service_score,logistics_score,overall_score,
                   content_key_id,content_iv,content_ciphertext,content_digest_sha256,public_summary,
                   moderation_status,moderation_policy,approved_at,created_at,updated_at
            FROM cloudmold_app_product_review
            WHERE tenant_id=#{tenantId}
              AND canonical_spu_id=#{canonicalSpuId}
              AND moderation_status='APPROVED'
            ORDER BY approved_at DESC, created_at DESC, review_id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<AppProductReviewDO> selectApprovedByProduct(@Param("tenantId") Long tenantId,
                                                     @Param("canonicalSpuId") String canonicalSpuId,
                                                     @Param("offset") long offset,
                                                     @Param("limit") int limit);

    @Select("""
            SELECT COUNT(1)
            FROM cloudmold_app_product_review
            WHERE tenant_id=#{tenantId}
              AND listing_id=#{listingId}
              AND moderation_status='APPROVED'
            """)
    long countApprovedByListing(@Param("tenantId") Long tenantId,
                                @Param("listingId") String listingId);

    @Select("""
            SELECT COUNT(1)
            FROM cloudmold_app_product_review
            WHERE tenant_id=#{tenantId}
              AND canonical_spu_id=#{canonicalSpuId}
              AND moderation_status='APPROVED'
            """)
    long countApprovedByProduct(@Param("tenantId") Long tenantId,
                                @Param("canonicalSpuId") String canonicalSpuId);

    @Select("""
            SELECT h.listing_id,h.listing_no,h.merchant_id,h.shop_id,h.canonical_spu_id,h.title,h.primary_image_url,
                   o.listing_offer_id,o.canonical_sku_id
            FROM cloudmold_listing_header h
            JOIN cloudmold_listing_offer o
              ON o.tenant_id=h.tenant_id
             AND o.listing_id=h.listing_id
             AND o.revision=h.revision
            WHERE h.tenant_id=#{tenantId}
              AND h.listing_id=#{listingId}
              AND h.revision=#{revision}
              AND o.listing_offer_id=#{listingOfferId}
              AND o.canonical_sku_id=#{canonicalSkuId}
            LIMIT 1
            """)
    AppProductReviewListingSnapshotDO selectListingSnapshot(@Param("tenantId") Long tenantId,
                                                            @Param("listingId") String listingId,
                                                            @Param("revision") Integer revision,
                                                            @Param("listingOfferId") String listingOfferId,
                                                            @Param("canonicalSkuId") String canonicalSkuId);
}
