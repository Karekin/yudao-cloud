package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;

@Data
public class LegacyTradeTargetReadinessOrderSourceDO {
    private Long tenantId;
    private String candidateId;
    private Long legacyOrderId;
    private Integer legacyOrderStatus;
    private String legacySnapshotHash;
    private Boolean deleted;
    private String buyerIdentityStatus;
    private String buyerSourceIdentityId;
    private String buyerPrincipalId;
    private Long buyerIdentityVersion;
    private Boolean negativeMoney;
    private Boolean headerMoneyMismatch;
    private Boolean headerItemMismatch;
    private Integer invalidItemMoneyCount;
    private Integer orderMappingCount;
    private String orderMappingPlanId;
    private String plannedOrderId;
    private Long orderMappingVersion;
    private String orderMappingSourceSnapshotHash;
    private Integer lifecycleMappingCount;
    private String statusMappingId;
    private String canonicalOrderStatus;
    private Long lifecycleMappingVersion;
}
