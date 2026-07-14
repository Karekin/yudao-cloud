package cn.iocoder.yudao.module.cloudmold.listing.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishedListingOfferView {
    private String listingId;
    private String listingNo;
    private String listingOfferId;
    private String channelCode;
    private String shopId;
    private String canonicalSpuId;
    private String canonicalSkuId;
    private Integer listingRevision;
    private Long listingVersion;
    private Long priceMinor;
    private String currencyCode;
}
