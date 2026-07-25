package cn.iocoder.yudao.module.cloudmold.listing.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishedListingView {
    private String listingId;
    private String listingNo;
    private String title;
    private String primaryImageUrl;
    private String merchantId;
    private String shopId;
    private String channelCode;
    private String canonicalSpuId;
    private Integer listingRevision;
    private Long listingVersion;
    private List<ListingOfferView> offers;
}
