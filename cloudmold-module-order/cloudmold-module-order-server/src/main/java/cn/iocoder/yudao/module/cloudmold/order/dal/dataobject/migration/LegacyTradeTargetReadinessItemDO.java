package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LegacyTradeTargetReadinessItemDO {
    private String itemReadinessId;
    private Long tenantId;
    private String targetReadinessRunId;
    private String orderReadinessId;
    private Long legacyOrderId;
    private Long legacyOrderItemId;
    private String itemEvidenceId;
    private String legacyItemSnapshotHash;
    private String spuMappingId;
    private String canonicalSpuId;
    private Long spuMappingVersion;
    private String spuMappingStatus;
    private String skuMappingId;
    private String canonicalSkuId;
    private Long skuMappingVersion;
    private String skuMappingStatus;
    private String orderItemMappingPlanId;
    private String plannedOrderItemId;
    private String plannedOrderId;
    private Long orderItemMappingVersion;
    private String orderItemMappingStatus;
    private String moneyReconciliationStatus;
    private String mappingReadinessStatus;
    private String blockerCodes;
    private Boolean mappingAdmissionAllowed;
    private Boolean canonicalImportAllowed;
    private String evidenceHash;
    private Long version;
    private LocalDateTime assessedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
