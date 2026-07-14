package cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_listing_header")
public class ListingHeaderDO {
    @TableId(type = IdType.INPUT)
    private String listingId;
    private Long tenantId;
    private String listingNo;
    private String runId;
    private String merchantId;
    private String channelCode;
    private String shopId;
    private String canonicalSpuId;
    private Integer revision;
    private String title;
    private String primaryImageUrl;
    private String categoryRef;
    private String brandRef;
    private String sourceSystem;
    private String publisherRef;
    private String currencyCode;
    private LocalDateTime publishStartAt;
    private LocalDateTime publishEndAt;
    private String status;
    private Boolean completionPassed;
    private Boolean businessApproved;
    private Boolean riskApproved;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
