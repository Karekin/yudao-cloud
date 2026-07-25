package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppProductReviewEligibilityView {
    private Boolean eligible;
    private String reasonCode;
    private String existingReviewId;
    private String existingModerationStatus;
    private String orderId;
    private String orderItemId;
    private String listingId;
    private String listingOfferId;
    private String merchantId;
    private String shopId;
    private String canonicalSpuId;
    private String canonicalSkuId;
}
