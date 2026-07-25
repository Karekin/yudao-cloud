package cn.iocoder.yudao.module.cloudmold.commercebehavior.dal.dataobject;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AppRecommendationSnapshotDO {
    private String decisionId;
    private String decisionToken;
    private String sessionId;
    private String listingId;
    private String listingOfferId;
    private String merchantId;
    private String shopId;
    private String channelCode;
    private String canonicalSpuId;
    private String canonicalSkuId;
    private Long priceMinor;
    private String currencyCode;
    private Integer rankNo;
    private BigDecimal availableQuantity;
    private String qualityStatus;
}
