package cn.iocoder.yudao.module.cloudmold.inventory.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Production-pilot admission control. Passing admission never opens v3 stock.
 */
public interface InventoryMigrationPilotApi {

    PilotBatchResult freeze(FreezePilotBatchCommand command, Long requesterId);

    PilotBatchResult approve(String batchId, ApprovePilotBatchCommand command, Long approverId);

    PilotBatchResult admit(String batchId, AdmitPilotBatchCommand command, Long executorId);

    PilotBatchResult requireBatch(String batchId);

    @Data
    public static class FreezePilotBatchCommand {
        private String idempotencyKey;
        private String sourceEventId;
        private String migrationRunId;
        private String policyVersion;
        private String changeTicket;
        private String purpose;
        private String sourceWatermarkKind;
        private String sourceWatermarkValue;
        private Instant sourceWatermarkCapturedAt;
        private String targetWatermarkKind;
        private String targetWatermarkValue;
        private Instant targetWatermarkAppliedAt;
        private Integer maxLagSeconds;
        private Instant executionWindowStart;
        private Instant executionWindowEnd;
        private String correlationId;
        private String causationId;
        private Instant occurredAt;
        private List<PilotItemEvidence> items;
    }

    @Data
    public static class PilotItemEvidence {
        private String candidateId;
        private String expectedSourceSnapshotHash;
        private String authoritativeRecordRef;
        private String quantityEvidenceRef;
        private String sourceCdcPosition;
        private Instant sourceExtractedAt;
        private String ownerSourceSystem;
        private String ownerSourceType;
        private String ownerSourceId;
        private String skuMappingId;
        private Long skuMappingVersion;
        private String skuMappingEvidenceRef;
        private String sourceUomCode;
        private BigDecimal uomConversionRatio;
        private String uomEvidenceRef;
        private String warehouseSourceSystem;
        private String warehouseSourceType;
        private String warehouseSourceId;
        private String warehouseMappingEvidenceRef;
        private String locationSourceSystem;
        private String locationSourceType;
        private String locationSourceId;
        private String locationMappingEvidenceRef;
        private String lotTrackingPolicy;
        private String lotId;
        private String lotMappingId;
        private Long lotMappingVersion;
        private String lotEvidenceRef;
    }

    @Data
    public static class ApprovePilotBatchCommand {
        private String idempotencyKey;
        private String sourceEventId;
        private String approvalRole;
        private Long expectedVersion;
        private String evidenceRef;
        private Instant expiresAt;
        private String correlationId;
        private String causationId;
        private Instant occurredAt;
    }

    @Data
    public static class AdmitPilotBatchCommand {
        private String idempotencyKey;
        private String sourceEventId;
        private Long expectedVersion;
        private String evidenceRef;
        private String correlationId;
        private String causationId;
        private Instant occurredAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PilotBatchResult {
        private Long operationId;
        private String batchId;
        private String migrationRunId;
        private String environment;
        private String sourceClassification;
        private String status;
        private String manifestHash;
        private String policyHash;
        private Integer expectedItemCount;
        private BigDecimal expectedOnHandQuantity;
        private Integer approvalCount;
        private Long requesterId;
        private Long executorId;
        private Long version;
        private Instant executionWindowStart;
        private Instant executionWindowEnd;
        private List<PilotItemResult> items;
        private List<PilotApprovalResult> approvals;
        private boolean duplicate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PilotItemResult {
        private String itemId;
        private Integer ordinal;
        private String candidateId;
        private String legacyBalanceId;
        private String sourceSnapshotHash;
        private String ownerId;
        private String canonicalSkuId;
        private String warehouseId;
        private String locationId;
        private String lotTrackingPolicy;
        private String lotId;
        private String baseUomCode;
        private BigDecimal sourceOnHandQuantity;
        private String itemScopeHash;
        private String status;
        private Long version;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PilotApprovalResult {
        private String approvalId;
        private String approvalRole;
        private Long approverId;
        private String evidenceRef;
        private Instant expiresAt;
        private String status;
    }
}
