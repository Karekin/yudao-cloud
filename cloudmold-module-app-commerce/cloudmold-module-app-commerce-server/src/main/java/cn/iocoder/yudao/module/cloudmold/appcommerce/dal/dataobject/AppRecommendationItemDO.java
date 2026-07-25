package cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_app_recommendation_item")
public class AppRecommendationItemDO {
    @TableId(type = IdType.AUTO)
    private Long itemId;
    private Long tenantId;
    private String decisionId;
    private Integer rankNo;
    private String reasonCode;
    private String listingId;
    private String listingNo;
    private String listingTitle;
    private String primaryImageUrl;
    private String listingOfferId;
    private String merchantId;
    private String shopId;
    private String channelCode;
    private String canonicalSpuId;
    private String canonicalSkuId;
    private Long priceMinor;
    private String currencyCode;
    private Long inventoryVersion;
    private BigDecimal availableQuantity;
    private String qualityStatus;
    private LocalDateTime createdAt;
}
