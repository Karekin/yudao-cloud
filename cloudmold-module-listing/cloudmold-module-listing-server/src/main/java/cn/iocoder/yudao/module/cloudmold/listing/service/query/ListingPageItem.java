package cn.iocoder.yudao.module.cloudmold.listing.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold Listing 分页项")
@Data
public class ListingPageItem {

    private String listingId;
    private String listingNo;
    private String title;
    private String merchantId;
    private String shopId;
    private String channelCode;
    private String canonicalSpuId;
    private Integer revision;
    private String status;
    private Boolean completionPassed;
    private Boolean businessApproved;
    private Boolean riskApproved;
    private Long offerCount;
    private Long enabledOfferCount;
    private Long minPriceMinor;
    private Long maxPriceMinor;
    private String currencyCode;
    private LocalDateTime publishStartAt;
    private LocalDateTime publishEndAt;
    private Long version;
    private LocalDateTime updatedAt;
}
