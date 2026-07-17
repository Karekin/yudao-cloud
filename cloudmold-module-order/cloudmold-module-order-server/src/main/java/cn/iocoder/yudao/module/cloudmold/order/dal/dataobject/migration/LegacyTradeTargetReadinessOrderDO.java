package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LegacyTradeTargetReadinessOrderDO {
    private String orderReadinessId;
    private Long tenantId;
    private String targetReadinessRunId;
    private String sourceMigrationRunId;
    private String candidateId;
    private Long legacyOrderId;
    private String legacySnapshotHash;
    private String buyerIdentityStatus;
    private String buyerSourceIdentityId;
    private String buyerPrincipalId;
    private Long buyerIdentityVersion;
    private String orderMappingPlanId;
    private String plannedOrderId;
    private Long orderMappingVersion;
    private String orderMappingStatus;
    private String statusMappingId;
    private String canonicalOrderStatus;
    private Long lifecycleMappingVersion;
    private String lifecycleMappingStatus;
    private String moneyReconciliationStatus;
    private Integer activeItemCount;
    private Integer fullyMappedItemCount;
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
