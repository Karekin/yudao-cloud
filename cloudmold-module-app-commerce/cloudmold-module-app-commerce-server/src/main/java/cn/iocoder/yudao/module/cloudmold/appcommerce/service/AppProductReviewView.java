package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppProductReviewView {
    private String reviewId;
    private String orderId;
    private String orderItemId;
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
    private String body;
    private String publicSummary;
    private String moderationStatus;
    private String moderationPolicy;
    private Boolean duplicate;
    private Instant createdAt;
    private Instant approvedAt;
}
