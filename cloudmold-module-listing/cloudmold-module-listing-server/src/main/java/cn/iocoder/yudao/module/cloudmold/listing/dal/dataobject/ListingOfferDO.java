package cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_listing_offer")
public class ListingOfferDO {
    @TableId(type = IdType.INPUT)
    private String listingOfferId;
    private Long tenantId;
    private String listingId;
    private Integer revision;
    private String canonicalSkuId;
    private Long priceMinor;
    private String currencyCode;
    private Boolean enabled;
    private String externalOfferId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
