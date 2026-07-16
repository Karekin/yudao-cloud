package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LegacyTradeBenefitMigrationCandidateDO {
    private String candidateId;
    private Long tenantId;
    private String migrationRunId;
    private Long legacyOrderId;
    private String legacyOrderNo;
    private String legacySnapshotHash;
    private LocalDateTime sourceUpdatedAt;
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
    private Boolean negativeMoney;
    private Boolean headerMoneyMismatch;
    private Boolean headerItemMismatch;
    private String assessmentStatus;
    private String reasonCodes;
    private Boolean canonicalImportAllowed;
    private Long version;
    private LocalDateTime assessedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public long headerBenefitAmountMinor() {
        return headerGenericDiscountAmountMinor + headerCouponAmountMinor
                + headerPointAmountMinor + headerVipAmountMinor;
    }

    public long itemBenefitAmountMinor() {
        return itemGenericDiscountAmountMinor + itemCouponAmountMinor
                + itemPointAmountMinor + itemVipAmountMinor;
    }
}
