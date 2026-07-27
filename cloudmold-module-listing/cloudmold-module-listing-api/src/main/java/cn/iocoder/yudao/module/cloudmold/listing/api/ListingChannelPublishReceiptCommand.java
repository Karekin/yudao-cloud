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
public class ListingChannelPublishReceiptCommand {

    private String idempotencyKey;
    private String listingId;
    private Long expectedVersion;
    private ListingChannelPublishReceiptOutcome outcome;
    private String channelListingId;
    private String channelStatus;
    private Instant confirmedAt;
    private String evidenceRef;
    private String failureCode;
    private String failureMessage;
    private Boolean retryable;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
