package cn.iocoder.yudao.module.cloudmold.listing.api.unpublish;

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
public class ListingUnpublishSagaView {
    private String sagaId;
    private String runId;
    private String sourceEventId;
    private String sourceEntityType;
    private Long sourceAggregateVersion;
    private String merchantId;
    private String shopId;
    private String status;
    private String activeStep;
    private Integer expectedListingCount;
    private Integer unpublishedListingCount;
    private Integer skippedListingCount;
    private Integer attemptCount;
    private Integer maxAttempts;
    private Long aggregateVersion;
    private String reason;
    private String lastErrorCode;
    private String lastErrorMessage;
    private Instant nextRetryAt;
    private Instant completedAt;
    private List<ListingUnpublishSagaItemView> items;
    private Boolean duplicate;
}
