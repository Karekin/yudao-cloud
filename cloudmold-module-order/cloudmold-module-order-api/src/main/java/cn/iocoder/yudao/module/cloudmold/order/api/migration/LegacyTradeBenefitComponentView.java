package cn.iocoder.yudao.module.cloudmold.order.api.migration;

import lombok.Data;

@Data
public class LegacyTradeBenefitComponentView {
    private String componentId;
    private String migrationRunId;
    private String candidateId;
    private Long legacyOrderId;
    private String componentType;
    private Long componentAmountMinor;
    private String sourceReference;
    private String identityResolutionStatus;
    private String fundingResolutionStatus;
    private Boolean canonicalImportAllowed;
}
