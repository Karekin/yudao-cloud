package cn.iocoder.yudao.module.cloudmold.order.api.migration;

import lombok.Data;

@Data
public class LegacyTradeBenefitComponentReconciliationView {
    private String reconciliationId;
    private String migrationRunId;
    private String candidateId;
    private Long legacyOrderId;
    private String legacyOrderNo;
    private String componentType;
    private Integer sourceItemComponentRowCount;
    private Long sourceItemComponentAmountMinor;
    private Integer itemComponentRowCount;
    private Long itemComponentAmountMinor;
    private Integer excludedItemComponentRowCount;
    private Long excludedItemComponentAmountMinor;
    private Integer headerComponentCount;
    private Long headerComponentAmountMinor;
    private Long amountGapMinor;
    private String orderAssessmentStatus;
    private String reconciliationStatus;
    private String reconciliationHash;
    private Boolean canonicalImportAllowed;
}
