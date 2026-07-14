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
public class ListingCommandResult {
    private Long operationId;
    private String listingId;
    private String listingNo;
    private String previousStatus;
    private String currentStatus;
    private Integer revision;
    private Long aggregateVersion;
    private Boolean completionPassed;
    private Boolean businessApproved;
    private Boolean riskApproved;
    private List<ListingOfferView> offers;
    private Boolean duplicate;
}
