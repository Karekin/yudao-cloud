package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder
public class AppProductView {
    String listingId;
    String listingNo;
    String title;
    String primaryImageUrl;
    List<Media> media;
    String merchantId;
    String shopId;
    String canonicalSpuId;
    Integer listingRevision;
    Long listingVersion;
    QualitySummary qualitySummary;
    List<Sku> skus;

    @Value
    @Builder
    public static class Media {
        String type;
        String url;
        Integer sort;
    }

    @Value
    @Builder
    public static class QualitySummary {
        String status;
    }

    @Value
    @Builder
    public static class Sku {
        String canonicalSkuId;
        String skuCode;
        String colorCode;
        String colorName;
        String sizeCode;
        String sizeName;
        String barcode;
        String listingOfferId;
        Long priceMinor;
        String currencyCode;
        Boolean enabled;
        BigDecimal availableQuantity;
        Boolean inStock;
        Long inventoryVersion;
        String qualityStatus;
    }
}
