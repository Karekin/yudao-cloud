package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LegacyTradeOrderAssessmentSourceDO {
    private Long tenantId;
    private Long legacyOrderId;
    private String legacyOrderNo;
    private LocalDateTime sourceCreatedAt;
    private LocalDateTime sourceUpdatedAt;
    private Long legacyBuyerId;
    private Integer legacyOrderStatus;
    private String buyerSourceIdentityId;
    private String buyerPrincipalId;
    private Long buyerIdentityVersion;
    private String buyerIdentityStatus;
    private Boolean deleted;
    private Integer headerQuantity;
    private Integer itemRowCount;
    private Integer itemQuantity;
    private Long headerGrossAmountMinor;
    private Long headerGenericDiscountAmountMinor;
    private Long headerCouponAmountMinor;
    private Long headerPointAmountMinor;
    private Long headerVipAmountMinor;
    private Long headerDeliveryAmountMinor;
    private Long headerAdjustAmountMinor;
    private Long headerPayAmountMinor;
    private Long itemGrossAmountMinor;
    private Long itemGenericDiscountAmountMinor;
    private Long itemCouponAmountMinor;
    private Long itemPointAmountMinor;
    private Long itemVipAmountMinor;
    private Long itemDeliveryAmountMinor;
    private Long itemAdjustAmountMinor;
    private Long itemPayAmountMinor;
    private Integer invalidItemMoneyCount;
    private Long legacyCouponId;
    private Integer legacyUsedPointQuantity;
    private Long legacySeckillActivityId;
    private Long legacyBargainActivityId;
    private Long legacyCombinationActivityId;
    private Long legacyPointActivityId;
}
