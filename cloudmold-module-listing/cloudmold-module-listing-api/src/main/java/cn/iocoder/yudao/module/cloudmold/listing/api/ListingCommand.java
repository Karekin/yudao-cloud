package cn.iocoder.yudao.module.cloudmold.listing.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingCommand {
    private ListingOperation operation;
    private String idempotencyKey;
    private String runId;
    private String listingId;
    private Long expectedVersion;
    private String merchantId;
    private String channelCode;
    private String shopId;
    private String canonicalSpuId;
    private String title;
    private String primaryImageUrl;
    private String categoryRef;
    private String brandRef;
    private String sourceSystem;
    private String publisherRef;
    private Instant publishStartAt;
    private Instant publishEndAt;
    private List<ListingOfferCommand> offers;
    private String reason;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
