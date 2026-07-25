package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

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
public class AppPublicProductReviewPageView {
    private Long total;
    private Integer pageNo;
    private Integer pageSize;
    private List<Item> list;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String reviewId;
        private String authorLabel;
        private String listingId;
        private String listingOfferId;
        private String canonicalSpuId;
        private String canonicalSkuId;
        private Integer productScore;
        private Integer serviceScore;
        private Integer logisticsScore;
        private Integer overallScore;
        private String body;
        private String publicSummary;
        private Instant approvedAt;
        private Instant createdAt;
    }
}
