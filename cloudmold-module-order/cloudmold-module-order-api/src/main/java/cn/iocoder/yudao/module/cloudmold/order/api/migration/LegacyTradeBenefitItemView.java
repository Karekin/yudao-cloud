package cn.iocoder.yudao.module.cloudmold.order.api.migration;

import lombok.Data;

import java.time.Instant;

@Data
public class LegacyTradeBenefitItemView {
    private String itemEvidenceId;
    private String migrationRunId;
    private String candidateId;
    private Long legacyOrderId;
    private Long legacyOrderItemId;
    private Long legacyBuyerId;
    private String legacyItemSnapshotHash;
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
    private Instant sourceUpdatedAt;
}
