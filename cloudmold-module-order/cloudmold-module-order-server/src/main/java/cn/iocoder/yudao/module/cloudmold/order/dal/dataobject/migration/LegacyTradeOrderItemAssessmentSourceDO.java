package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LegacyTradeOrderItemAssessmentSourceDO {
    private Long tenantId;
    private Long legacyOrderItemId;
    private Long legacyOrderId;
    private Long legacyBuyerId;
    private LocalDateTime sourceCreatedAt;
    private LocalDateTime sourceUpdatedAt;
    private Boolean deleted;
    private Long legacySpuId;
    private String legacySpuName;
    private Long legacySkuId;
    private String legacySkuPropertiesJson;
    private String legacySkuPicUrl;
    private Integer itemQuantity;
    private Long unitPriceMinor;
    private Long grossAmountMinor;
    private Long genericDiscountAmountMinor;
    private Long couponAmountMinor;
    private Long pointAmountMinor;
    private Long vipAmountMinor;
    private Long deliveryAmountMinor;
    private Long adjustAmountMinor;
    private Long payAmountMinor;
    private Integer usedPointQuantity;
}
