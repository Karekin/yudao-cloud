package cn.iocoder.yudao.module.cloudmold.order.api.migration;

import lombok.Data;

import java.util.List;

@Data
public class LegacyTradeTargetReadinessItemView {
    private String itemReadinessId;
    private String targetReadinessRunId;
    private String orderReadinessId;
    private Long legacyOrderId;
    private Long legacyOrderItemId;
    private String itemEvidenceId;
    private String productIdentityQualificationId;
    private String historicalProductIdentityStatus;
    private String spuMappingStatus;
    private String canonicalSpuId;
    private String skuMappingStatus;
    private String canonicalSkuId;
    private String orderItemMappingStatus;
    private String plannedOrderItemId;
    private String plannedOrderId;
    private String moneyReconciliationStatus;
    private String mappingReadinessStatus;
    private List<String> blockerCodes;
    private Boolean mappingAdmissionAllowed;
    private Boolean canonicalImportAllowed;
    private String evidenceHash;
}
