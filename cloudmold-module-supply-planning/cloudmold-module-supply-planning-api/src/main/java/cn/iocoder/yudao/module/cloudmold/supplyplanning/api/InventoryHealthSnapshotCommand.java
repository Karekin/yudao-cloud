package cn.iocoder.yudao.module.cloudmold.supplyplanning.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryHealthSnapshotCommand {
    private String idempotencyKey;
    private String runId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    private SnapshotDefinition snapshot;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SnapshotDefinition {
        private String snapshotId;
        private String snapshotCode;
        private String policyId;
        private String policyVersionId;
        private String ledgerWatermarkRef;
        private Instant ledgerWatermarkOccurredAt;
        private Integer stockoutCount;
        private Integer lowStockCount;
        private Integer overstockCount;
        private Integer obsoleteCount;
        private Integer agedCount;
        private Integer shelfLifeRiskCount;
        private BigDecimal shortageQuantity;
        private BigDecimal excessQuantity;
        private BigDecimal atRiskQuantity;
        private String snapshotSha256;
        private List<IssueRefDefinition> issueRefs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IssueRefDefinition {
        private String issueId;
    }
}
