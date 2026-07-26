package cn.iocoder.yudao.module.cloudmold.listing.service.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkuSellabilityView {
    private String canonicalSkuId;
    private boolean sellable;
    private List<String> blockingReasonCodes;
    private Quality quality;
    private Inventory inventory;
    private Listing listing;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Quality {
        private String status;
        private String inspectionTaskId;
        private String inspectionStatus;
        private String decision;
        private LocalDateTime inspectedAt;
        private LocalDateTime completedAt;
        private String standardId;
        private String standardCode;
        private Long standardVersion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Inventory {
        private BigDecimal allocatableQuantity;
        private String baseUomCode;
        private Long inventoryVersion;
        private Integer balanceCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Listing {
        private Integer publishedListingCount;
        private Integer enabledOfferCount;
        private List<String> channels;
        private List<SkuPublicationItem> publications;
    }
}
