package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LegacyTradeBenefitMigrationItemDO {
    private String itemEvidenceId;
    private Long tenantId;
    private String migrationRunId;
    private String candidateId;
    private Long legacyOrderId;
    private Long legacyOrderItemId;
    private String legacyItemSnapshotHash;
    private LocalDateTime sourceUpdatedAt;
    private Boolean deleted;
    private Long legacySpuId;
    private Long legacySkuId;
    private String sourceProductIdentityStatus;
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
    private Boolean canonicalImportAllowed;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public long benefitAmountMinor() {
        return genericDiscountAmountMinor + couponAmountMinor + pointAmountMinor + vipAmountMinor;
    }
}
