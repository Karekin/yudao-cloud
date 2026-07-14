package cn.iocoder.yudao.module.cloudmold.listing.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingOfferView {
    private String listingOfferId;
    private String canonicalSkuId;
    private Integer revision;
    private Long priceMinor;
    private String currencyCode;
    private Boolean enabled;
    private String externalOfferId;
}
