package cn.iocoder.yudao.module.cloudmold.inventory.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Continuous read-only shadow comparison for an admitted production pilot.
 * VERIFIED means the evidence window is terminal; verificationResult carries the actual conclusion.
 * No result authorizes qualification, opening, execution, or cutover.
 */
public interface InventoryMigrationShadowApi {

    ShadowWindowResult start(String batchId, StartShadowWindowCommand command, Long collectorId);

    ShadowWindowResult recordRound(String windowId, RecordShadowRoundCommand command, Long collectorId);

    ShadowWindowResult finalizeWindow(String windowId, FinalizeShadowWindowCommand command, Long verifierId);

    ShadowWindowResult requireWindow(String windowId);

    @Data
    public static class StartShadowWindowCommand {
        private String idempotencyKey;
        private String sourceEventId;
        private Long expectedBatchVersion;
        private String evidenceRef;
        private String correlationId;
        private String causationId;
        private Instant occurredAt;
    }

    @Data
    public static class RecordShadowRoundCommand {
        private String idempotencyKey;
        private String sourceEventId;
        private Long expectedWindowVersion;
        private Instant observedAt;
        private String sourceWatermarkKind;
        private String sourceWatermarkValue;
        private Instant sourceWatermarkCapturedAt;
        private String targetWatermarkKind;
        private String targetWatermarkValue;
        private Instant targetWatermarkAppliedAt;
        private String watermarkValidationEvidenceRef;
        private String evidenceRef;
        private List<ShadowTargetObservation> observations;
        private String correlationId;
        private String causationId;
        private Instant occurredAt;
    }

    @Data
    public static class ShadowTargetObservation {
        private String pilotItemId;
        private String expectedItemScopeHash;
        /** False means the allowlisted projection was missing or unreadable, never that an item may be omitted. */
        private Boolean available;
        private Long targetRecordVersion;
        private String targetCanonicalGrainHash;
        private BigDecimal targetOnHandQuantity;
        private BigDecimal targetReservedQuantity;
        private BigDecimal targetInTransitQuantity;
        private String targetProjectionHash;
        private String targetEvidenceRef;
        /** Typed collector failure input; the server derives final comparison reason codes. */
        private String unavailabilityCode;
    }

    @Data
    public static class FinalizeShadowWindowCommand {
        private String idempotencyKey;
        private String sourceEventId;
        private Long expectedWindowVersion;
        private String evidenceRef;
        private String correlationId;
        private String causationId;
        private Instant occurredAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShadowWindowResult {
        private Long operationId;
        private String windowId;
        private String pilotBatchId;
        private String migrationRunId;
        private String status;
        private String verificationResult;
        private String manifestHash;
        private String expectedItemSetHash;
        private String admissionCheckpointId;
        private String admissionCheckpointHash;
        private String admissionEventId;
        private String policyHash;
        private String targetProjectionKind;
        private Integer targetProjectionVersion;
        private Integer expectedItemCount;
        private Integer requiredRoundCount;
        private Integer minimumDurationSeconds;
        private Integer maxRoundIntervalSeconds;
        private Integer maxWatermarkLagSeconds;
        private Integer observedRoundCount;
        private Integer totalMatchCount;
        private Integer totalDifferentCount;
        private Integer totalUncomparableCount;
        private Long collectorId;
        private Long verifierId;
        private Long version;
        private Long aggregateVersion;
        private Instant startedAt;
        private Instant lastObservedAt;
        private Instant finalizedAt;
        private List<ShadowRoundResult> rounds;
        private boolean shadowMatchVerified;
        private boolean targetMaterialized;
        private boolean executionAvailable;
        private boolean cutoverReady;
        private boolean duplicate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShadowRoundResult {
        private String roundId;
        private Integer roundNumber;
        private String denominatorHash;
        private Integer expectedItemCount;
        private Integer matchCount;
        private Integer differentCount;
        private Integer uncomparableCount;
        private String previousSourceWatermarkHash;
        private String sourceWatermarkValue;
        private String sourceWatermarkHash;
        private boolean sourceMonotonic;
        private String previousTargetWatermarkHash;
        private String targetWatermarkValue;
        private String targetWatermarkHash;
        private boolean targetMonotonic;
        private boolean targetContainsSource;
        private String watermarkValidator;
        private Integer watermarkLagSeconds;
        private Integer roundGapSeconds;
        private Instant observedAt;
    }
}
