package cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.dataobject;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class InventoryControlPolicyRecords {
    private InventoryControlPolicyRecords() {
    }

    @Data
    @Accessors(chain = true)
    public static class Operation {
        private Long operationId;
        private Long tenantId;
        private String idempotencyKey;
        private String commandType;
        private String requestHash;
        private String attemptToken;
        private Integer status;
        private String aggregateType;
        private String aggregateId;
        private String resultJson;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class SafetyStockPolicy {
        private String policyId;
        private Long tenantId;
        private String policyCode;
        private String ownerType;
        private String ownerId;
        private String canonicalSkuId;
        private String warehouseNetworkId;
        private LocalDate effectiveFrom;
        private LocalDate effectiveTo;
        private Integer targetServiceLevelBasisPoints;
        private BigDecimal safetyStockQuantity;
        private BigDecimal reorderPointQuantity;
        private BigDecimal maximumStockQuantity;
        private Integer replenishmentCycleDays;
        private Integer leadTimeDays;
        private String policyBasisCode;
        private String policySha256;
        private String evidenceRef;
        private String status;
        private Long currentVersion;
        /** Compare-and-set value used only by draft updates; not persisted. */
        private Long expectedVersion;
        private Long approvedVersion;
        private Long publishedVersion;
        private String activeVersionId;
        private String createdByPrincipalId;
        private String approvedByPrincipalId;
        private String publishedByPrincipalId;
        private String retiredByPrincipalId;
        private LocalDateTime approvedAt;
        private LocalDateTime publishedAt;
        private LocalDateTime retiredAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class SafetyStockPolicyVersion {
        private String policyVersionId;
        private Long tenantId;
        private String policyId;
        private String policyCode;
        private Long version;
        private String status;
        private String ownerType;
        private String ownerId;
        private String canonicalSkuId;
        private String warehouseNetworkId;
        private LocalDate effectiveFrom;
        private LocalDate effectiveTo;
        private Integer targetServiceLevelBasisPoints;
        private BigDecimal safetyStockQuantity;
        private BigDecimal reorderPointQuantity;
        private BigDecimal maximumStockQuantity;
        private Integer replenishmentCycleDays;
        private Integer leadTimeDays;
        private String policyBasisCode;
        private String policySha256;
        private String evidenceRef;
        private String actorPrincipalId;
        private Long sourceOperationId;
        private LocalDateTime createdAt;
    }

    @Data
    @Accessors(chain = true)
    public static class InventoryIssueReference {
        private String issueId;
        private Long tenantId;
        private String sourceBalanceId;
        private String issueType;
        private String severity;
        private String status;
    }

    @Data
    @Accessors(chain = true)
    public static class InventoryHealthSnapshot {
        private String snapshotId;
        private Long tenantId;
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
    }

    @Data
    @Accessors(chain = true)
    public static class InventoryHealthSnapshotIssueRef {
        private Long snapshotIssueRefId;
        private Long tenantId;
        private String snapshotId;
        private String issueId;
        private String issueType;
        private String severity;
        private String status;
        private String sourceBalanceId;
        private LocalDateTime createdAt;
    }
}
