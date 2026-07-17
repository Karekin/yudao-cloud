package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LegacyTradeBenefitGovernanceComponentSourceDO {
    private Long tenantId;
    private String sourceMigrationRunId;
    private String componentId;
    private String candidateId;
    private Long legacyOrderId;
    private String componentType;
    private Long componentAmountMinor;
    private String sourceReference;
    private String observedSourceTable;
    private Long observedSourceId;
    private LocalDateTime observedSourceCreatedAt;
    private LocalDateTime observedSourceUpdatedAt;
    private String observedSourceStatus;
    private Boolean observedSourceDeleted;
    private Long observedSourceSpuId;
    private Integer identityQualificationCount;
    private String identityQualificationId;
    private String identitySourceComponentEvidenceHash;
    private Integer fundingShareCount;
    private Long fundingAmountMinor;
    private Integer fundingSourceEvidenceHashCount;
    private String fundingSourceComponentEvidenceHash;
}
