package cn.iocoder.yudao.module.cloudmold.supplyplanning.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryHealthSnapshotView {
    private String snapshotId;
    private String snapshotCode;
    private String policyId;
    private String policyCode;
    private String policyVersionId;
    private Long policyVersion;
    private String ledgerWatermarkRef;
    private LocalDateTime ledgerWatermarkOccurredAt;
    private Integer stockoutCount;
    private Integer lowStockCount;
    private Integer overstockCount;
    private Integer obsoleteCount;
    private Integer agedCount;
    private Integer shelfLifeRiskCount;
    private BigDecimal shortageQuantity;
    private BigDecimal excessQuantity;
    private BigDecimal atRiskQuantity;
    private Integer issueCount;
    private String snapshotSha256;
    private String status;
    private String createdByPrincipalId;
    private LocalDateTime createdAt;
    private List<InventoryHealthSnapshotIssueRefView> issues;
}
