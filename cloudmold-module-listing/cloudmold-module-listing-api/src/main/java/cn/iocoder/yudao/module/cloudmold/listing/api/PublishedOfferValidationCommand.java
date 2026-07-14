package cn.iocoder.yudao.module.cloudmold.listing.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishedOfferValidationCommand {
    private String listingId;
    private String listingOfferId;
    private String canonicalSkuId;
    private Long expectedPriceMinor;
    private String currencyCode;
}
