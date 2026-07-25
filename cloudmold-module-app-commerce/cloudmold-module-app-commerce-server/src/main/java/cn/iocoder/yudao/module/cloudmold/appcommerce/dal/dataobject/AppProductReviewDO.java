package cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_app_product_review")
public class AppProductReviewDO {
    @TableId(type = IdType.INPUT)
    private String reviewId;
    private Long tenantId;
    private String buyerPrincipalId;
    private String orderId;
    private String orderItemId;
    private String paymentId;
    private String fulfillmentId;
    private String listingId;
    private String listingOfferId;
    private String merchantId;
    private String shopId;
    private String canonicalSpuId;
    private String canonicalSkuId;
    private Integer productScore;
    private Integer serviceScore;
    private Integer logisticsScore;
    private Integer overallScore;
    private String contentKeyId;
    private byte[] contentIv;
    private byte[] contentCiphertext;
    private String contentDigestSha256;
    private String publicSummary;
    private String moderationStatus;
    private String moderationPolicy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
