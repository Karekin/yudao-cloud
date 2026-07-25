package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Value
@Builder
public class AppRecommendationDecisionView {
    String sceneCode;
    String policyVersion;
    String decisionToken;
    String resultSetToken;
    Integer ttlSeconds;
    Instant expiresAt;
    List<Item> items;

    @Value
    @Builder
    public static class Item {
        Integer rank;
        String reasonCode;
        String listingId;
        String listingNo;
        String title;
        String primaryImageUrl;
        String merchantId;
        String shopId;
        String canonicalSpuId;
        String canonicalSkuId;
        String listingOfferId;
        Long priceMinor;
        String currencyCode;
        Long inventoryVersion;
        BigDecimal availableQuantity;
        String qualityStatus;
    }
}
