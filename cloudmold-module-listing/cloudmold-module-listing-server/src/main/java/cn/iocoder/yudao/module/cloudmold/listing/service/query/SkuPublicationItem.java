package cn.iocoder.yudao.module.cloudmold.listing.service.query;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class SkuPublicationItem {
    private String listingId;
    private String listingNo;
    private String channelCode;
    private String listingStatus;
    private Boolean offerEnabled;
    private Boolean currentlyPublished;
    private LocalDateTime publishStartAt;
    private LocalDateTime publishEndAt;
    private Long priceMinor;
    private String currencyCode;
}
