package cn.iocoder.yudao.module.cloudmold.order.api.migration;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class LegacyTradeBenefitCandidateView {
    private String candidateId;
    private String migrationRunId;
    private Long legacyOrderId;
    private String legacyOrderNo;
    private String legacySnapshotHash;
    private Long legacyBuyerId;
    private Integer legacyOrderStatus;
    private String buyerSourceIdentityId;
    private String buyerPrincipalId;
    private Long buyerIdentityVersion;
    private String buyerIdentityStatus;
    private Boolean deleted;
    private Integer headerQuantity;
    private Integer itemRowCount;
    private Integer itemQuantity;
    private Long headerBenefitAmountMinor;
    private Long itemBenefitAmountMinor;
    private Boolean negativeMoney;
    private Boolean headerMoneyMismatch;
    private Boolean headerItemMismatch;
    private Integer invalidItemMoneyCount;
    private String assessmentStatus;
    private List<String> reasonCodes;
    private Boolean canonicalImportAllowed;
    private Instant sourceCreatedAt;
    private Instant sourceUpdatedAt;
    private Instant assessedAt;
}
