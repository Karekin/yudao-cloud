package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;

@Data
public class LegacyTradeTargetReadinessItemSourceDO {
    private Long tenantId;
    private String candidateId;
    private Long legacyOrderId;
    private Long legacyOrderItemId;
    private String itemEvidenceId;
    private String legacyItemSnapshotHash;
    private Integer productIdentityQualificationCount;
    private String productIdentityQualificationId;
    private Long qualifiedLegacyOrderItemId;
    private Long historicalSpuId;
    private Long historicalSkuId;
    private String productIdentitySourceItemEvidenceHash;
    private String historicalProductSnapshotHash;
    private Boolean deleted;
    private Boolean orderDeleted;
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
    private Integer spuMappingCount;
    private String spuMappingId;
    private String canonicalSpuId;
    private Long spuMappingVersion;
    private Integer skuMappingCount;
    private String skuMappingId;
    private String canonicalSkuId;
    private String canonicalSkuSpuId;
    private Long skuMappingVersion;
    private Integer orderItemMappingCount;
    private String orderItemMappingPlanId;
    private String plannedOrderItemId;
    private String plannedOrderId;
    private Long orderItemMappingVersion;
    private String orderItemMappingSourceSnapshotHash;
}
