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
public class ListingChannelPublishReceiptResult {

    private Long operationId;
    private String listingId;
    private String listingNo;
    private Long aggregateVersion;
    private ListingChannelPublishReceiptOutcome outcome;
    private String channelListingId;
    private String channelStatus;
    private Instant confirmedAt;
    private String evidenceRef;
    private String failureCode;
    private String failureMessage;
    private Boolean retryable;
    private Boolean duplicate;
}
