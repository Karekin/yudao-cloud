package cn.iocoder.yudao.module.cloudmold.listing.api.unpublish;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingUnpublishSagaItemView {
    private String sagaItemId;
    private String listingId;
    private Long listingVersionAtRequest;
    private String status;
    private Integer attemptCount;
    private Long listingOperationId;
    private String finalListingStatus;
    private Long finalListingVersion;
}
