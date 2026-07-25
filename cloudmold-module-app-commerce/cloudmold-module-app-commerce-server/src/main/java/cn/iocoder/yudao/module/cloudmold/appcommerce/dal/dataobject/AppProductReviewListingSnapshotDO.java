package cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject;

import lombok.Data;

@Data
public class AppProductReviewListingSnapshotDO {
    private String listingId;
    private String listingNo;
    private String merchantId;
    private String shopId;
    private String canonicalSpuId;
    private String title;
    private String primaryImageUrl;
    private String listingOfferId;
    private String canonicalSkuId;
}
