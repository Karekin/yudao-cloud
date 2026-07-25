package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppCartView {
    private String cartId;
    private String principalId;
    private Long aggregateVersion;
    private Integer lineCount;
    private Integer selectedLineCount;
    private Boolean duplicate;
    private List<Line> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        private String lineId;
        private String listingId;
        private String listingOfferId;
        private String canonicalSkuId;
        private String title;
        private String primaryImageUrl;
        private BigDecimal quantity;
        private Boolean selected;
        private Boolean valid;
        private String invalidReasonCode;
        private Long currentUnitPriceMinor;
        private Long currentProductAmountMinor;
        private String currencyCode;
        private BigDecimal availableQuantity;
        private String qualityStatus;
    }
}
