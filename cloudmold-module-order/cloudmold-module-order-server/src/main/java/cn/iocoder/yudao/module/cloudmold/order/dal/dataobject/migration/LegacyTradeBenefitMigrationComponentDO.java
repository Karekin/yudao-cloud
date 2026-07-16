package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LegacyTradeBenefitMigrationComponentDO {
    private String componentId;
    private Long tenantId;
    private String migrationRunId;
    private String candidateId;
    private Long legacyOrderId;
    private String componentType;
    private Long componentAmountMinor;
    private String sourceReference;
    private String identityResolutionStatus;
    private String fundingResolutionStatus;
    private Boolean canonicalImportAllowed;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
