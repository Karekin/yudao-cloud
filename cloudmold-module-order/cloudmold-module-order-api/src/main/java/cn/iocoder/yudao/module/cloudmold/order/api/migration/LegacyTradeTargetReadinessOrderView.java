package cn.iocoder.yudao.module.cloudmold.order.api.migration;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class LegacyTradeTargetReadinessOrderView {
    private String orderReadinessId;
    private String targetReadinessRunId;
    private String sourceMigrationRunId;
    private String candidateId;
    private Long legacyOrderId;
    private String buyerIdentityStatus;
    private String buyerPrincipalId;
    private String orderMappingPlanId;
    private String plannedOrderId;
    private String orderMappingStatus;
    private String canonicalOrderStatus;
    private String lifecycleMappingStatus;
    private String moneyReconciliationStatus;
    private Integer activeItemCount;
    private Integer fullyMappedItemCount;
    private String mappingReadinessStatus;
    private List<String> blockerCodes;
    private Boolean mappingAdmissionAllowed;
    private Boolean canonicalImportAllowed;
    private String evidenceHash;
    private Instant assessedAt;
}
