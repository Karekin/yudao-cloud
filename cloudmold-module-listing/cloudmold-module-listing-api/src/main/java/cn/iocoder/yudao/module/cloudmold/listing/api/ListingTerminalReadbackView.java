package cn.iocoder.yudao.module.cloudmold.listing.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingTerminalReadbackView {

    private String listingId;
    private String listingNo;
    private String merchantId;
    private String shopId;
    private String channelCode;
    private String canonicalSpuId;
    private String currentStatus;
    private Integer revision;
    private Long aggregateVersion;
    private Integer offerCount;
    private Integer enabledOfferCount;
    private Boolean channelFactPresent;
    private String channelPublicationStatus;
    private String overallResultCode;
    private String channelListingId;
    private String channelStatus;
    private String failureCode;
    private String failureMessage;
    private Boolean retryable;
    private Instant confirmedAt;
    private String evidenceRef;
    private String evidenceSource;
    private String summary;
}
