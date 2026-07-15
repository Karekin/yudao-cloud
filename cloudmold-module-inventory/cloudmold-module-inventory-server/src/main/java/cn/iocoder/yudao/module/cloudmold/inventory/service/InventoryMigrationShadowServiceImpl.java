package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryMigrationShadowApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryMigrationShadowApi.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryMigrationPilotMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryMigrationShadowMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryMigrationStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Evidence-only continuous shadow for an admitted production pilot.
 *
 * <p>The collector supplies a read-only target projection and watermark evidence. This service owns the
 * denominator, rereads and hashes the authoritative v1 source under the transaction, derives every result,
 * and never calls a qualification, opening, bridge, or v3 balance mutation path.</p>
 */
@Service
@RequiredArgsConstructor
public class InventoryMigrationShadowServiceImpl implements InventoryMigrationShadowApi {

    static final String WINDOW_EVENT = "inventory.migration.shadow_window_status_changed";
    static final String ROUND_EVENT = "inventory.migration.shadow_round_completed";
    static final String ITEM_EVENT = "inventory.migration.shadow_item_compared";
    static final String TARGET_PROJECTION = "CANONICAL_INVENTORY_V3_SHADOW_PROJECTION";
    static final String WATERMARK_KIND = "MYSQL_GTID_SET";
    static final String GTID_VALIDATOR = "MYSQL_GTID_SET_CONTAINS_V1";
    private static final BigDecimal ZERO = new BigDecimal("0.000000");

    private final InventoryMigrationStoreMapper migrationMapper;
    private final InventoryMigrationPilotMapper pilotMapper;
    private final InventoryMigrationShadowMapper shadowMapper;
    private final OutboxAppender outboxAppender;
    private final InventoryMigrationPilotProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShadowWindowResult start(String rawBatchId, StartShadowWindowCommand rawCommand, Long collectorId) {
        String batchId = requireUuid(rawBatchId, "batchId");
        StartShadowWindowCommand command = normalizeStart(rawCommand);
        requireShadowEnvironment();
        requireAllowlisted(collectorId, properties.getTrustedShadowCollectorIds(), "collector");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = utcNow();
        OperationAttempt attempt = beginOperation(tenantId, "START_SHADOW_V1", command.getIdempotencyKey(),
                command.getSourceEventId(), requestHash(tenantId, "START_SHADOW_V1", collectorId,
                        Map.of("batch_id", batchId, "command", command)), now);
        if (attempt.replay() != null) return attempt.replay();

        validatePolicyBounds();
        InventoryMigrationPilotBatchDO batch = pilotMapper.selectBatchForUpdate(tenantId, batchId);
        require(batch != null, "Inventory migration pilot batch does not exist");
        require("PRODUCTION".equals(batch.getEnvironment()) && "PRODUCTION_HISTORY".equals(batch.getSourceClassification()),
                "shadow window requires a production-history pilot");
        require("ADMISSION_PASSED".equals(batch.getStatus()) && Objects.equals(batch.getVersion(), 4L),
                "shadow window requires an ADMISSION_PASSED v4 pilot batch");
        require(Objects.equals(command.getExpectedBatchVersion(), batch.getVersion()),
                "pilot batch version changed before shadow start");
        require(WATERMARK_KIND.equals(upper(batch.getSourceWatermarkKind()))
                        && WATERMARK_KIND.equals(upper(batch.getTargetWatermarkKind())),
                "shadow window requires a MYSQL_GTID_SET admission baseline");
        requireGtid(batch.getSourceWatermarkValue(), "pilot source watermark");
        requireGtid(batch.getTargetWatermarkValue(), "pilot target watermark");
        require(shadowMapper.selectActiveWindowForBatch(tenantId, batchId) == null,
                "pilot batch already has an active shadow window");

        List<InventoryMigrationPilotApprovalDO> approvals = pilotMapper.selectApprovalsForUpdate(tenantId, batchId);
        require(approvals.size() == 2, "shadow window requires the complete pilot approval set");
        requireActorSeparated(collectorId, batch, approvals, "collector");
        List<InventoryMigrationPilotItemDO> items = pilotMapper.selectItemsForUpdate(tenantId, batchId);
        validateAdmittedDenominator(batch, items);
        InventoryMigrationPilotCheckpointDO checkpoint = pilotMapper.selectAdmissionCheckpoint(tenantId, batchId);
        require(checkpoint != null && "ADMISSION_PASSED".equals(checkpoint.getCheckpointType())
                        && Objects.equals(checkpoint.getBatchVersion(), 4L)
                        && Objects.equals(checkpoint.getScopeHash(), batch.getManifestHash())
                        && Objects.equals(checkpoint.getPolicyHash(), batch.getPolicyHash())
                        && Objects.equals(checkpoint.getItemCount(), batch.getExpectedItemCount()),
                "shadow window admission checkpoint does not bind the admitted denominator");
        String admissionEventId = shadowMapper.selectAdmissionEventId(tenantId, batchId);
        requireUuid(admissionEventId, "admissionEventId");
        require(Objects.equals(properties.getEnvironmentFingerprint(),
                        shadowMapper.selectAdmissionEnvironmentFingerprint(tenantId, admissionEventId)),
                "trusted environment fingerprint no longer matches the immutable admission event");

        String expectedItemSetHash = expectedItemSetHash(items);
        InventoryMigrationShadowWindowDO window = new InventoryMigrationShadowWindowDO()
                .setWindowId(UUID.randomUUID().toString()).setTenantId(tenantId).setBatchId(batchId)
                .setMigrationRunId(batch.getMigrationRunId()).setEnvironment("PRODUCTION")
                .setEnvironmentFingerprint(properties.getEnvironmentFingerprint())
                .setManifestHash(batch.getManifestHash()).setExpectedItemSetHash(expectedItemSetHash)
                .setAdmissionCheckpointId(checkpoint.getCheckpointId())
                .setAdmissionCheckpointHash(admissionCheckpointHash(checkpoint)).setAdmissionEventId(admissionEventId)
                .setAdmissionBatchVersion(4L).setPolicyVersion(batch.getPolicyVersion()).setPolicyHash(batch.getPolicyHash())
                .setTargetProjectionKind(TARGET_PROJECTION).setTargetProjectionVersion(1).setTargetMaterialized(false)
                .setExpectedItemCount(items.size()).setRequiredRoundCount(properties.getShadowRequiredRoundCount())
                .setMinimumDurationSeconds(properties.getShadowMinimumDurationSeconds())
                .setMaxRoundIntervalSeconds(properties.getShadowMaxRoundIntervalSeconds())
                .setMaxWatermarkLagSeconds(properties.getShadowMaxWatermarkLagSeconds())
                .setCollectorId(collectorId).setStatus("OPEN").setVerificationResult("PENDING")
                .setVersion(1L).setAggregateVersion(1L).setObservedRoundCount(0)
                .setTotalMatchCount(0).setTotalDifferentCount(0).setTotalUncomparableCount(0)
                .setStartedAt(now).setCreatedAt(now).setUpdatedAt(now);
        require(shadowMapper.insertWindow(window) == 1, "failed to persist the shadow window");
        appendWindowEvent(window, command.getOccurredAt(), command.getCorrelationId(), command.getCausationId(),
                command.getIdempotencyKey());
        ShadowWindowResult result = toResult(attempt.operationId(), window, List.of());
        completeOperation(tenantId, attempt.operationId(), batch.getMigrationRunId(), result, now);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShadowWindowResult recordRound(String rawWindowId, RecordShadowRoundCommand rawCommand, Long collectorId) {
        String windowId = requireUuid(rawWindowId, "windowId");
        RecordShadowRoundCommand command = normalizeRound(rawCommand);
        requireShadowEnvironment();
        requireAllowlisted(collectorId, properties.getTrustedShadowCollectorIds(), "collector");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = utcNow();
        OperationAttempt attempt = beginOperation(tenantId, "RECORD_SHADOW_ROUND_V1", command.getIdempotencyKey(),
                command.getSourceEventId(), requestHash(tenantId, "RECORD_SHADOW_ROUND_V1", collectorId,
                        Map.of("window_id", windowId, "command", command)), now);
        if (attempt.replay() != null) return attempt.replay();

        InventoryMigrationShadowWindowDO window = shadowMapper.selectWindowForUpdate(tenantId, windowId);
        require(window != null, "Inventory migration shadow window does not exist");
        require(Set.of("OPEN", "OBSERVING").contains(window.getStatus())
                        && "PENDING".equals(window.getVerificationResult()),
                "shadow window is not accepting rounds");
        require(Objects.equals(command.getExpectedWindowVersion(), window.getVersion()),
                "shadow window version changed before round collection");
        require(Objects.equals(window.getCollectorId(), collectorId),
                "only the collector bound to the shadow window may record a round");
        InventoryMigrationPilotBatchDO batch = pilotMapper.selectBatchForUpdate(tenantId, window.getBatchId());
        require(batch != null && "ADMISSION_PASSED".equals(batch.getStatus()) && Objects.equals(batch.getVersion(), 4L),
                "shadow round lost its admitted pilot binding");
        List<InventoryMigrationPilotApprovalDO> approvals = pilotMapper.selectApprovalsForUpdate(tenantId, batch.getBatchId());
        requireActorSeparated(collectorId, batch, approvals, "collector");
        List<InventoryMigrationPilotItemDO> items = pilotMapper.selectItemsForUpdate(tenantId, batch.getBatchId());
        validateAdmittedDenominator(batch, items);
        require(expectedItemSetHash(items).equals(window.getExpectedItemSetHash()),
                "shadow pilot item denominator changed after window start");
        Map<String, ShadowTargetObservation> observations = validateObservationDenominator(command.getObservations(), items);

        List<InventoryMigrationShadowRoundDO> existingRounds = shadowMapper.selectRounds(tenantId, windowId);
        require(existingRounds.size() == window.getObservedRoundCount(), "shadow round denominator is inconsistent");
        InventoryMigrationShadowRoundDO previousRound = existingRounds.isEmpty() ? null
                : existingRounds.get(existingRounds.size() - 1);
        String previousSource = previousRound == null ? batch.getSourceWatermarkValue()
                : previousRound.getSourceWatermarkValue();
        String previousTarget = previousRound == null ? batch.getTargetWatermarkValue()
                : previousRound.getTargetWatermarkValue();
        // Stable ordinal locking closes the race between observing @@GLOBAL.gtid_executed and rereading v1 facts.
        Map<String, SourceSnapshot> sources = new LinkedHashMap<>();
        for (InventoryMigrationPilotItemDO item : items) {
            sources.put(item.getItemId(), readSourceForShadow(tenantId, item));
        }
        LocalDateTime previousSourceAt = previousRound == null ? batch.getSourceWatermarkCapturedAt()
                : previousRound.getSourceWatermarkCapturedAt();
        LocalDateTime previousTargetAt = previousRound == null ? batch.getTargetWatermarkAppliedAt()
                : previousRound.getTargetWatermarkAppliedAt();
        WatermarkEvidence watermark = validateWatermarks(command, previousSource, previousTarget,
                previousSourceAt, previousTargetAt, now);
        int roundNumber = window.getObservedRoundCount() + 1;
        int roundGapSeconds = safeSeconds(Duration.between(
                window.getLastObservedAt() == null ? window.getStartedAt() : window.getLastObservedAt(), now),
                "round interval");
        boolean roundPolicyComparable = watermark.targetContainsSource()
                && watermark.lagSeconds() <= window.getMaxWatermarkLagSeconds()
                && roundGapSeconds <= window.getMaxRoundIntervalSeconds();
        List<String> policyReasons = new ArrayList<>();
        if (!watermark.targetContainsSource()) policyReasons.add("TARGET_WATERMARK_NOT_COVERED");
        if (watermark.lagSeconds() > window.getMaxWatermarkLagSeconds()) policyReasons.add("WATERMARK_LAG_EXCEEDED");
        if (roundGapSeconds > window.getMaxRoundIntervalSeconds()) policyReasons.add("ROUND_GAP_EXCEEDED");

        Map<String, InventoryMigrationShadowComparisonDO> previousComparisons = previousRound == null ? Map.of()
                : shadowMapper.selectComparisons(tenantId, previousRound.getRoundId()).stream()
                .collect(Collectors.toMap(InventoryMigrationShadowComparisonDO::getPilotItemId, Function.identity()));
        String roundId = UUID.randomUUID().toString();
        List<InventoryMigrationShadowComparisonDO> comparisons = new ArrayList<>();
        for (InventoryMigrationPilotItemDO item : items) {
            SourceSnapshot source = sources.get(item.getItemId());
            validateSourceProgression(source, previousComparisons.get(item.getItemId()));
            ShadowTargetObservation observation = observations.get(item.getItemId());
            comparisons.add(compare(window, roundId, roundNumber, item, source, observation,
                    watermark, roundPolicyComparable, policyReasons, now));
        }
        int matchCount = countResult(comparisons, "MATCH");
        int differentCount = countResult(comparisons, "DIFFERENT");
        int uncomparableCount = countResult(comparisons, "UNCOMPARABLE");
        require(matchCount + differentCount + uncomparableCount == items.size(),
                "shadow comparison denominator was not fully classified");
        if (!roundPolicyComparable) require(uncomparableCount == items.size(),
                "shadow policy failure must classify the complete round as UNCOMPARABLE");

        InventoryMigrationShadowRoundDO round = new InventoryMigrationShadowRoundDO()
                .setRoundId(roundId).setTenantId(tenantId).setWindowId(windowId).setRoundNumber(roundNumber)
                .setObservedAt(now).setPreviousSourceWatermarkValue(previousSource)
                .setPreviousSourceWatermarkHash(sha256(previousSource)).setSourceWatermarkKind(WATERMARK_KIND)
                .setSourceWatermarkValue(watermark.sourceValue()).setSourceWatermarkHash(watermark.sourceHash())
                .setSourceWatermarkCapturedAt(watermark.sourceCapturedAt()).setSourceMonotonic(true)
                .setPreviousTargetWatermarkValue(previousTarget).setPreviousTargetWatermarkHash(sha256(previousTarget))
                .setTargetWatermarkKind(WATERMARK_KIND).setTargetWatermarkValue(watermark.targetValue())
                .setTargetWatermarkHash(watermark.targetHash()).setTargetWatermarkAppliedAt(watermark.targetAppliedAt())
                .setTargetMonotonic(true).setTargetContainsSource(watermark.targetContainsSource())
                .setWatermarkValidator(GTID_VALIDATOR)
                .setWatermarkValidationEvidenceRef(command.getWatermarkValidationEvidenceRef())
                .setWatermarkValid(watermark.targetContainsSource()).setWatermarkLagSeconds(watermark.lagSeconds())
                .setRoundGapSeconds(roundGapSeconds).setDenominatorHash(denominatorHash(window, roundNumber, items))
                .setExpectedItemCount(items.size()).setMatchCount(matchCount).setDifferentCount(differentCount)
                .setUncomparableCount(uncomparableCount).setCollectorId(collectorId).setEvidenceRef(command.getEvidenceRef())
                .setStatus("COMPLETED").setAggregateVersion(1L).setCreatedAt(now);
        require(shadowMapper.insertRound(round) == 1, "failed to persist the complete shadow round");
        for (InventoryMigrationShadowComparisonDO comparison : comparisons) {
            require(shadowMapper.insertComparison(comparison) == 1,
                    "failed to persist the complete shadow comparison denominator");
        }
        boolean firstRound = "OPEN".equals(window.getStatus());
        require(shadowMapper.advanceWindow(tenantId, windowId, window.getVersion(), matchCount, differentCount,
                uncomparableCount, watermark.sourceValue(), watermark.sourceHash(), watermark.sourceCapturedAt(),
                watermark.targetValue(), watermark.targetHash(), watermark.targetAppliedAt(), now, now) == 1,
                "shadow window changed concurrently while recording the round");
        advanceInMemory(window, round, now);
        appendRoundEvent(window, round, command.getOccurredAt(), command.getCorrelationId(),
                command.getCausationId(), command.getIdempotencyKey());
        for (InventoryMigrationShadowComparisonDO comparison : comparisons) {
            appendItemEvent(window, round, comparison, command.getOccurredAt(), command.getCorrelationId(),
                    command.getCausationId(), command.getIdempotencyKey());
        }
        if (firstRound) appendWindowEvent(window, command.getOccurredAt(), command.getCorrelationId(),
                command.getCausationId(), command.getIdempotencyKey());
        List<InventoryMigrationShadowRoundDO> allRounds = new ArrayList<>(existingRounds);
        allRounds.add(round);
        ShadowWindowResult result = toResult(attempt.operationId(), window, allRounds);
        completeOperation(tenantId, attempt.operationId(), window.getMigrationRunId(), result, now);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShadowWindowResult finalizeWindow(String rawWindowId, FinalizeShadowWindowCommand rawCommand, Long verifierId) {
        String windowId = requireUuid(rawWindowId, "windowId");
        FinalizeShadowWindowCommand command = normalizeFinalize(rawCommand);
        requireShadowEnvironment();
        requireAllowlisted(verifierId, properties.getTrustedShadowVerifierIds(), "verifier");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = utcNow();
        OperationAttempt attempt = beginOperation(tenantId, "FINALIZE_SHADOW_V1", command.getIdempotencyKey(),
                command.getSourceEventId(), requestHash(tenantId, "FINALIZE_SHADOW_V1", verifierId,
                        Map.of("window_id", windowId, "command", command)), now);
        if (attempt.replay() != null) return attempt.replay();

        InventoryMigrationShadowWindowDO window = shadowMapper.selectWindowForUpdate(tenantId, windowId);
        require(window != null, "Inventory migration shadow window does not exist");
        require("OBSERVING".equals(window.getStatus()) && "PENDING".equals(window.getVerificationResult()),
                "shadow window is not ready for verification");
        require(Objects.equals(command.getExpectedWindowVersion(), window.getVersion()),
                "shadow window version changed before verification");
        InventoryMigrationPilotBatchDO batch = pilotMapper.selectBatchForUpdate(tenantId, window.getBatchId());
        require(batch != null && "ADMISSION_PASSED".equals(batch.getStatus()) && Objects.equals(batch.getVersion(), 4L),
                "verified shadow window must remain bound to its admitted pilot");
        List<InventoryMigrationPilotApprovalDO> approvals = pilotMapper.selectApprovalsForUpdate(tenantId, batch.getBatchId());
        requireActorSeparated(verifierId, batch, approvals, "verifier");
        require(!Objects.equals(window.getCollectorId(), verifierId),
                "shadow collector cannot verify the same evidence window");
        require(window.getObservedRoundCount() >= window.getRequiredRoundCount(),
                "shadow window has not reached the required round count");
        require(Duration.between(window.getStartedAt(), now).getSeconds() >= window.getMinimumDurationSeconds(),
                "shadow window has not reached the required duration");
        require(window.getLastObservedAt() != null
                        && Duration.between(window.getLastObservedAt(), now).getSeconds()
                        <= window.getMaxRoundIntervalSeconds(),
                "shadow window verification exceeded the final observation gap");
        List<InventoryMigrationShadowRoundDO> rounds = shadowMapper.selectRounds(tenantId, windowId);
        require(rounds.size() == window.getObservedRoundCount(), "shadow round denominator is inconsistent at verification");
        String verificationResult = window.getTotalUncomparableCount() > 0 ? "UNCOMPARABLE"
                : window.getTotalDifferentCount() > 0 ? "DIFFERENT" : "MATCH";
        require(shadowMapper.finalizeWindow(tenantId, windowId, window.getVersion(), verificationResult,
                verifierId, now) == 1, "shadow window changed concurrently during verification");
        window.setStatus("VERIFIED").setVerificationResult(verificationResult).setVerifierId(verifierId)
                .setVersion(window.getVersion() + 1).setAggregateVersion(3L).setFinalizedAt(now).setUpdatedAt(now);
        appendWindowEvent(window, command.getOccurredAt(), command.getCorrelationId(), command.getCausationId(),
                command.getIdempotencyKey());
        ShadowWindowResult result = toResult(attempt.operationId(), window, rounds);
        completeOperation(tenantId, attempt.operationId(), window.getMigrationRunId(), result, now);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public ShadowWindowResult requireWindow(String rawWindowId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String windowId = requireUuid(rawWindowId, "windowId");
        InventoryMigrationShadowWindowDO window = shadowMapper.selectWindow(tenantId, windowId);
        require(window != null, "Inventory migration shadow window does not exist");
        return toResult(null, window, shadowMapper.selectRounds(tenantId, windowId));
    }

    private SourceSnapshot readSourceForShadow(Long tenantId, InventoryMigrationPilotItemDO item) {
        InventoryBalanceDO balance = migrationMapper.selectLegacyBalanceForUpdate(tenantId, item.getLegacyBalanceId());
        require(balance != null, "shadow source balance disappeared");
        InventoryLegacySourceFactDO fact = migrationMapper.selectInitialSourceFact(tenantId, balance.getBalanceId());
        int reservationCount = migrationMapper.countActiveReservations(tenantId, balance.getBalanceId());
        BigDecimal reservationQuantity = scaled(migrationMapper.sumActiveReservationQuantity(tenantId, balance.getBalanceId()));
        String snapshotHash = InventoryMigrationAssessmentServiceImpl.snapshotHash(
                tenantId, balance, fact, reservationCount, reservationQuantity);
        return new SourceSnapshot(balance, reservationCount, reservationQuantity, snapshotHash);
    }

    private InventoryMigrationShadowComparisonDO compare(InventoryMigrationShadowWindowDO window, String roundId,
                                                          int roundNumber, InventoryMigrationPilotItemDO item,
                                                          SourceSnapshot source, ShadowTargetObservation observation,
                                                          WatermarkEvidence watermark, boolean policyComparable,
                                                          List<String> policyReasons, LocalDateTime now) {
        String grainHash = canonicalGrainHash(item);
        boolean targetAvailable = Boolean.TRUE.equals(observation.getAvailable());
        List<String> differences = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        if (!policyComparable) {
            reasons.addAll(policyReasons);
        } else if (!targetAvailable) {
            reasons.add("TARGET_MISSING".equals(observation.getUnavailabilityCode())
                    ? "TARGET_MISSING" : "TARGET_READ_ERROR");
        } else {
            if (!grainHash.equals(observation.getTargetCanonicalGrainHash())) {
                differences.add("GRAIN");
                reasons.add("GRAIN_DIFFERENCE");
            }
            if (scaled(source.balance().getOnHandQuantity()).compareTo(observation.getTargetOnHandQuantity()) != 0)
                differences.add("ON_HAND");
            if (scaled(source.balance().getReservedQuantity()).compareTo(observation.getTargetReservedQuantity()) != 0)
                differences.add("RESERVED");
            if (scaled(source.balance().getInTransitQuantity()).compareTo(observation.getTargetInTransitQuantity()) != 0)
                differences.add("IN_TRANSIT");
            if (differences.stream().anyMatch(value -> !"GRAIN".equals(value))) reasons.add("VALUE_DIFFERENCE");
        }
        boolean comparable = policyComparable && targetAvailable;
        String result = !comparable ? "UNCOMPARABLE" : differences.isEmpty() ? "MATCH" : "DIFFERENT";
        return new InventoryMigrationShadowComparisonDO()
                .setComparisonId(deterministicUuid(window.getWindowId() + "|" + roundNumber + "|" + item.getItemId()))
                .setTenantId(window.getTenantId()).setWindowId(window.getWindowId()).setRoundId(roundId)
                .setRoundNumber(roundNumber).setPilotItemId(item.getItemId()).setManifestOrdinal(item.getOrdinal())
                .setItemScopeHash(item.getItemScopeHash()).setCanonicalGrainHash(grainHash)
                .setSourceWatermarkHash(watermark.sourceHash()).setTargetWatermarkHash(watermark.targetHash())
                .setSourceId(source.balance().getBalanceId()).setSourceVersion(source.balance().getVersion())
                .setSourceUpdatedAt(source.balance().getUpdatedAt()).setSourceSnapshotHash(source.snapshotHash())
                .setSourceOnHandQuantity(scaled(source.balance().getOnHandQuantity()))
                .setSourceReservedQuantity(scaled(source.balance().getReservedQuantity()))
                .setSourceInTransitQuantity(scaled(source.balance().getInTransitQuantity()))
                .setActiveReservationCount(source.activeReservationCount())
                .setActiveReservationQuantity(source.activeReservationQuantity())
                .setSourceEvidenceRef(item.getQuantityEvidenceRef()).setTargetAvailable(targetAvailable)
                .setTargetRecordVersion(targetAvailable ? observation.getTargetRecordVersion() : null)
                .setTargetCanonicalGrainHash(targetAvailable ? observation.getTargetCanonicalGrainHash() : null)
                .setTargetOnHandQuantity(targetAvailable ? observation.getTargetOnHandQuantity() : null)
                .setTargetReservedQuantity(targetAvailable ? observation.getTargetReservedQuantity() : null)
                .setTargetInTransitQuantity(targetAvailable ? observation.getTargetInTransitQuantity() : null)
                .setTargetProjectionHash(targetAvailable ? observation.getTargetProjectionHash() : null)
                .setTargetEvidenceRef(observation.getTargetEvidenceRef()).setComparable(comparable)
                .setComparisonResult(result).setDifferenceFields(JsonUtils.toJsonString(differences))
                .setReasonCodes(JsonUtils.toJsonString(reasons)).setAggregateVersion(1L).setCreatedAt(now);
    }

    private WatermarkEvidence validateWatermarks(RecordShadowRoundCommand command, String previousSource,
                                                  String previousTarget, LocalDateTime previousSourceAt,
                                                  LocalDateTime previousTargetAt, LocalDateTime now) {
        require(WATERMARK_KIND.equals(command.getSourceWatermarkKind())
                        && WATERMARK_KIND.equals(command.getTargetWatermarkKind()),
                "shadow round supports only MYSQL_GTID_SET watermarks");
        String source = command.getSourceWatermarkValue().trim();
        String target = command.getTargetWatermarkValue().trim();
        requireGtid(source, "source watermark");
        requireGtid(target, "target watermark");
        requireGtid(previousSource, "previous source watermark");
        requireGtid(previousTarget, "previous target watermark");
        String current = shadowMapper.selectCurrentGtidSet();
        requireGtid(current, "server @@GLOBAL.gtid_executed");
        require(isSubset(source, current) && isSubset(current, source),
                "source command GTID must exactly match server @@GLOBAL.gtid_executed");
        require(isSubset(previousSource, source), "source GTID must not regress");
        require(isSubset(previousTarget, target), "target GTID must not regress");
        boolean targetContainsSource = isSubset(source, target);
        LocalDateTime sourceAt = toDateTime(command.getSourceWatermarkCapturedAt());
        LocalDateTime targetAt = toDateTime(command.getTargetWatermarkAppliedAt());
        require(previousSourceAt != null && previousTargetAt != null
                        && !sourceAt.isBefore(previousSourceAt) && !targetAt.isBefore(previousTargetAt),
                "shadow watermark timestamps must not regress");
        require(!sourceAt.isAfter(now) && !targetAt.isAfter(now) && !targetAt.isBefore(sourceAt),
                "shadow watermark timestamps are invalid or in the future");
        int lag = safeSeconds(Duration.between(sourceAt, targetAt), "watermark lag");
        return new WatermarkEvidence(source, sha256(source), sourceAt, target, sha256(target), targetAt,
                targetContainsSource, lag);
    }

    private void validateSourceProgression(SourceSnapshot source, InventoryMigrationShadowComparisonDO previous) {
        if (previous == null) return;
        require(source.balance().getVersion() >= previous.getSourceVersion(), "shadow source version regressed");
        require(source.balance().getVersion() > previous.getSourceVersion()
                        || source.snapshotHash().equals(previous.getSourceSnapshotHash()),
                "shadow source hash changed without a source version advance");
    }

    private Map<String, ShadowTargetObservation> validateObservationDenominator(
            List<ShadowTargetObservation> observations, List<InventoryMigrationPilotItemDO> items) {
        require(observations != null && observations.size() == items.size(),
                "shadow observations must cover the complete pilot denominator");
        Map<String, InventoryMigrationPilotItemDO> expected = items.stream()
                .collect(Collectors.toMap(InventoryMigrationPilotItemDO::getItemId, Function.identity()));
        Map<String, ShadowTargetObservation> values = new HashMap<>();
        for (ShadowTargetObservation observation : observations) {
            require(observation != null, "shadow observation is required");
            String itemId = requireUuid(observation.getPilotItemId(), "pilotItemId");
            InventoryMigrationPilotItemDO item = expected.get(itemId);
            require(item != null, "shadow observation contains an item outside the pilot denominator");
            require(values.put(itemId, observation) == null, "shadow observation contains a duplicate pilot item");
            require(Objects.equals(item.getItemScopeHash(), requireHash(observation.getExpectedItemScopeHash(),
                    "expectedItemScopeHash")), "shadow observation item scope changed");
            require(observation.getAvailable() != null, "target available flag is required");
            requireEvidence(observation.getTargetEvidenceRef(), "targetEvidenceRef");
            if (Boolean.TRUE.equals(observation.getAvailable())) {
                require(observation.getTargetRecordVersion() != null && observation.getTargetRecordVersion() >= 1,
                        "available target projection version is required");
                requireHash(observation.getTargetCanonicalGrainHash(), "targetCanonicalGrainHash");
                observation.setTargetOnHandQuantity(nonNegative(observation.getTargetOnHandQuantity(), "targetOnHandQuantity"));
                observation.setTargetReservedQuantity(nonNegative(observation.getTargetReservedQuantity(), "targetReservedQuantity"));
                observation.setTargetInTransitQuantity(nonNegative(observation.getTargetInTransitQuantity(), "targetInTransitQuantity"));
                require(observation.getTargetReservedQuantity().compareTo(observation.getTargetOnHandQuantity()) <= 0,
                        "target reserved quantity exceeds on-hand quantity");
                String suppliedHash = requireHash(observation.getTargetProjectionHash(), "targetProjectionHash");
                String derivedHash = targetSnapshotHash(observation);
                require(suppliedHash.equals(derivedHash),
                        "target projection hash does not match the typed read-only observation");
                require(observation.getUnavailabilityCode() == null,
                        "available target observation cannot carry an unavailability code");
            } else {
                requireText(observation.getUnavailabilityCode(), "unavailabilityCode", 64);
                observation.setUnavailabilityCode(upper(observation.getUnavailabilityCode()));
                require(Set.of("TARGET_MISSING", "TARGET_READ_ERROR").contains(observation.getUnavailabilityCode()),
                        "target unavailability code is not supported");
                require(observation.getTargetRecordVersion() == null && observation.getTargetCanonicalGrainHash() == null
                                && observation.getTargetOnHandQuantity() == null && observation.getTargetReservedQuantity() == null
                                && observation.getTargetInTransitQuantity() == null && observation.getTargetProjectionHash() == null,
                        "unavailable target observation cannot carry projection values");
            }
        }
        require(values.size() == expected.size(), "shadow observations omitted a pilot item");
        return values;
    }

    private void appendWindowEvent(InventoryMigrationShadowWindowDO window, Instant occurredAt,
                                   String correlationId, String causationId, String commandKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("shadow_window_id", window.getWindowId());
        payload.put("pilot_batch_id", window.getBatchId());
        payload.put("migration_run_id", window.getMigrationRunId());
        payload.put("admission_event_id", window.getAdmissionEventId());
        payload.put("manifest_hash", window.getManifestHash());
        payload.put("expected_item_set_hash", window.getExpectedItemSetHash());
        payload.put("environment", window.getEnvironment());
        payload.put("environment_fingerprint", window.getEnvironmentFingerprint());
        payload.put("policy_version", window.getPolicyVersion());
        payload.put("policy_hash", window.getPolicyHash());
        payload.put("expected_item_count", window.getExpectedItemCount());
        payload.put("target_projection_kind", window.getTargetProjectionKind());
        payload.put("target_materialized", false);
        payload.put("watermark_kind", WATERMARK_KIND);
        payload.put("required_round_count", window.getRequiredRoundCount());
        payload.put("required_duration_seconds", window.getMinimumDurationSeconds());
        payload.put("max_round_gap_seconds", window.getMaxRoundIntervalSeconds());
        payload.put("max_lag_seconds", window.getMaxWatermarkLagSeconds());
        payload.put("window_started_at", toInstantString(window.getStartedAt()));
        payload.put("window_verified_at", toInstantString(window.getFinalizedAt()));
        payload.put("window_status", window.getStatus());
        payload.put("verification_result", window.getVerificationResult());
        payload.put("gtid_validator_version", GTID_VALIDATOR);
        payload.put("collector_artifact_hash", collectorArtifactHash(window));
        payload.put("execution_available", false);
        payload.put("cutover_ready", false);
        appendEvent(window.getTenantId(), WINDOW_EVENT, "inventory_migration_shadow_window", window.getWindowId(),
                window.getAggregateVersion(), commandKey + ":shadow-window:v" + window.getAggregateVersion(),
                occurredAt, correlationId, causationId, payload, window);
    }

    private void appendRoundEvent(InventoryMigrationShadowWindowDO window, InventoryMigrationShadowRoundDO round,
                                  Instant occurredAt, String correlationId, String causationId, String commandKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("shadow_round_id", round.getRoundId());
        payload.put("shadow_window_id", window.getWindowId());
        payload.put("pilot_batch_id", window.getBatchId());
        payload.put("manifest_hash", window.getManifestHash());
        payload.put("expected_item_set_hash", window.getExpectedItemSetHash());
        payload.put("round_sequence", round.getRoundNumber());
        payload.put("expected_item_count", round.getExpectedItemCount());
        payload.put("observed_item_count", round.getExpectedItemCount());
        payload.put("match_item_count", round.getMatchCount());
        payload.put("different_item_count", round.getDifferentCount());
        payload.put("uncomparable_item_count", round.getUncomparableCount());
        payload.put("watermark_kind", WATERMARK_KIND);
        payload.put("previous_source_gtid_set", round.getPreviousSourceWatermarkValue());
        payload.put("source_gtid_set", round.getSourceWatermarkValue());
        payload.put("previous_target_gtid_set", round.getPreviousTargetWatermarkValue());
        payload.put("target_gtid_set", round.getTargetWatermarkValue());
        payload.put("source_watermark_captured_at", toInstantString(round.getSourceWatermarkCapturedAt()));
        payload.put("target_watermark_applied_at", toInstantString(round.getTargetWatermarkAppliedAt()));
        payload.put("source_monotonic", true);
        payload.put("target_monotonic", true);
        payload.put("target_contains_source", Boolean.TRUE.equals(round.getTargetContainsSource()));
        payload.put("gtid_validator_version", GTID_VALIDATOR);
        payload.put("gtid_evidence_hash", sha256(round.getWatermarkValidationEvidenceRef()));
        payload.put("lag_seconds", round.getWatermarkLagSeconds());
        payload.put("round_started_at", toInstantString(round.getSourceWatermarkCapturedAt()));
        payload.put("round_completed_at", toInstantString(round.getObservedAt()));
        payload.put("round_status", "COMPLETED");
        payload.put("round_result", roundResult(round));
        payload.put("execution_available", false);
        payload.put("cutover_ready", false);
        appendEvent(window.getTenantId(), ROUND_EVENT, "inventory_migration_shadow_round", round.getRoundId(), 1L,
                commandKey + ":shadow-round:" + round.getRoundNumber() + ":v1", occurredAt, correlationId,
                causationId, payload, window);
    }

    private void appendItemEvent(InventoryMigrationShadowWindowDO window, InventoryMigrationShadowRoundDO round,
                                 InventoryMigrationShadowComparisonDO comparison, Instant occurredAt,
                                 String correlationId, String causationId, String commandKey) {
        List<String> differences = Objects.requireNonNullElse(
                JsonUtils.parseArray(comparison.getDifferenceFields(), String.class), List.of());
        List<String> reasons = Objects.requireNonNullElse(
                JsonUtils.parseArray(comparison.getReasonCodes(), String.class), List.of());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("shadow_comparison_id", comparison.getComparisonId());
        payload.put("shadow_window_id", window.getWindowId());
        payload.put("shadow_round_id", round.getRoundId());
        payload.put("pilot_batch_id", window.getBatchId());
        payload.put("pilot_item_id", comparison.getPilotItemId());
        payload.put("round_sequence", round.getRoundNumber());
        payload.put("manifest_ordinal", comparison.getManifestOrdinal());
        payload.put("item_scope_hash", comparison.getItemScopeHash());
        payload.put("manifest_hash", window.getManifestHash());
        payload.put("expected_item_set_hash", window.getExpectedItemSetHash());
        payload.put("source_gtid_set", round.getSourceWatermarkValue());
        payload.put("target_gtid_set", round.getTargetWatermarkValue());
        payload.put("source_available", true);
        payload.put("source_version", comparison.getSourceVersion());
        payload.put("source_snapshot_hash", comparison.getSourceSnapshotHash());
        payload.put("source_grain_hash", comparison.getCanonicalGrainHash());
        payload.put("source_on_hand_quantity", decimal(comparison.getSourceOnHandQuantity()));
        payload.put("source_reserved_quantity", decimal(comparison.getSourceReservedQuantity()));
        payload.put("source_in_transit_quantity", decimal(comparison.getSourceInTransitQuantity()));
        payload.put("target_projection_kind", TARGET_PROJECTION);
        payload.put("target_materialized", false);
        payload.put("target_available", Boolean.TRUE.equals(comparison.getTargetAvailable()));
        payload.put("target_projection_version", comparison.getTargetRecordVersion());
        payload.put("target_snapshot_hash", comparison.getTargetProjectionHash());
        payload.put("target_grain_hash", comparison.getTargetCanonicalGrainHash());
        payload.put("target_on_hand_quantity", nullableDecimal(comparison.getTargetOnHandQuantity()));
        payload.put("target_reserved_quantity", nullableDecimal(comparison.getTargetReservedQuantity()));
        payload.put("target_in_transit_quantity", nullableDecimal(comparison.getTargetInTransitQuantity()));
        payload.put("comparable", Boolean.TRUE.equals(comparison.getComparable()));
        payload.put("comparison_result", comparison.getComparisonResult());
        payload.put("difference_fields", differences);
        payload.put("reason_codes", reasons);
        payload.put("source_observed_at", toInstantString(comparison.getSourceUpdatedAt()));
        payload.put("target_observed_at", Boolean.TRUE.equals(comparison.getTargetAvailable())
                ? toInstantString(round.getTargetWatermarkAppliedAt()) : null);
        payload.put("execution_available", false);
        payload.put("cutover_ready", false);
        appendEvent(window.getTenantId(), ITEM_EVENT, "inventory_migration_shadow_comparison",
                comparison.getComparisonId(), 1L,
                commandKey + ":shadow-item:" + round.getRoundNumber() + ":" + comparison.getManifestOrdinal() + ":v1",
                occurredAt, correlationId, causationId, payload, window);
    }

    private void appendEvent(Long tenantId, String eventType, String aggregateType, String aggregateId,
                             Long aggregateVersion, String idempotencyKey, Instant occurredAt,
                             String correlationId, String causationId, Map<String, Object> payload,
                             InventoryMigrationShadowWindowDO window) {
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(deterministicUuid(tenantId + "|" + idempotencyKey)).eventType(eventType).schemaVersion(1)
                .sourceSystem("cloudmold-inventory").tenantId(tenantId).aggregateType(aggregateType)
                .aggregateId(aggregateId).aggregateVersion(aggregateVersion).eventSequence((short) 1)
                .occurredAt(occurredAt).correlationId(correlationId).causationId(causationId)
                .idempotencyKey(idempotencyKey).payload(payload)
                .headers(Map.of("migration_run_id", window.getMigrationRunId(),
                        "manifest_hash", window.getManifestHash(), "expected_item_set_hash", window.getExpectedItemSetHash()))
                .destination("lakehouse").build());
    }

    private OperationAttempt beginOperation(Long tenantId, String commandType, String idempotencyKey,
                                            String sourceEventId, String requestHash, LocalDateTime now) {
        String attemptToken = UUID.randomUUID().toString();
        migrationMapper.insertOrResolveCommand(tenantId, idempotencyKey, sourceEventId, commandType,
                requestHash, attemptToken, now);
        Long operationId = migrationMapper.selectLastInsertId();
        InventoryMigrationOperationDO operation = migrationMapper.selectOperationForUpdate(tenantId, operationId);
        require(operation != null, "failed to resolve Inventory migration shadow operation");
        if (attemptToken.equals(operation.getAttemptToken())) return new OperationAttempt(operationId, null);
        require(Objects.equals(requestHash, operation.getRequestHash()),
                "shadow idempotency key or source event conflicts with a different payload");
        require(operation.getStatus() == 10 && operation.getResultJson() != null,
                "existing Inventory migration shadow operation is not complete");
        ShadowWindowResult replay = JsonUtils.parseObject(operation.getResultJson(), ShadowWindowResult.class);
        replay.setDuplicate(true);
        return new OperationAttempt(operationId, replay);
    }

    private void completeOperation(Long tenantId, Long operationId, String migrationRunId,
                                   ShadowWindowResult result, LocalDateTime now) {
        require(migrationMapper.markOperationSucceeded(tenantId, operationId, migrationRunId,
                JsonUtils.toJsonString(result), now) == 1, "shadow operation completion conflict");
    }

    private ShadowWindowResult toResult(Long operationId, InventoryMigrationShadowWindowDO window,
                                        List<InventoryMigrationShadowRoundDO> rounds) {
        return ShadowWindowResult.builder().operationId(operationId).windowId(window.getWindowId())
                .pilotBatchId(window.getBatchId()).migrationRunId(window.getMigrationRunId())
                .status(window.getStatus()).verificationResult(window.getVerificationResult())
                .manifestHash(window.getManifestHash()).expectedItemSetHash(window.getExpectedItemSetHash())
                .admissionCheckpointId(window.getAdmissionCheckpointId())
                .admissionCheckpointHash(window.getAdmissionCheckpointHash())
                .admissionEventId(window.getAdmissionEventId()).policyHash(window.getPolicyHash())
                .targetProjectionKind(window.getTargetProjectionKind())
                .targetProjectionVersion(window.getTargetProjectionVersion()).expectedItemCount(window.getExpectedItemCount())
                .requiredRoundCount(window.getRequiredRoundCount()).minimumDurationSeconds(window.getMinimumDurationSeconds())
                .maxRoundIntervalSeconds(window.getMaxRoundIntervalSeconds())
                .maxWatermarkLagSeconds(window.getMaxWatermarkLagSeconds())
                .observedRoundCount(window.getObservedRoundCount()).totalMatchCount(window.getTotalMatchCount())
                .totalDifferentCount(window.getTotalDifferentCount())
                .totalUncomparableCount(window.getTotalUncomparableCount()).collectorId(window.getCollectorId())
                .verifierId(window.getVerifierId()).version(window.getVersion()).aggregateVersion(window.getAggregateVersion())
                .startedAt(toInstant(window.getStartedAt())).lastObservedAt(toInstant(window.getLastObservedAt()))
                .finalizedAt(toInstant(window.getFinalizedAt()))
                .rounds(rounds.stream().map(this::toRoundResult).toList())
                .shadowMatchVerified("VERIFIED".equals(window.getStatus())
                        && "MATCH".equals(window.getVerificationResult()))
                .targetMaterialized(false).executionAvailable(false).cutoverReady(false).duplicate(false).build();
    }

    private ShadowRoundResult toRoundResult(InventoryMigrationShadowRoundDO round) {
        return ShadowRoundResult.builder().roundId(round.getRoundId()).roundNumber(round.getRoundNumber())
                .denominatorHash(round.getDenominatorHash()).expectedItemCount(round.getExpectedItemCount())
                .matchCount(round.getMatchCount()).differentCount(round.getDifferentCount())
                .uncomparableCount(round.getUncomparableCount())
                .previousSourceWatermarkHash(round.getPreviousSourceWatermarkHash())
                .sourceWatermarkValue(round.getSourceWatermarkValue()).sourceWatermarkHash(round.getSourceWatermarkHash())
                .sourceMonotonic(Boolean.TRUE.equals(round.getSourceMonotonic()))
                .previousTargetWatermarkHash(round.getPreviousTargetWatermarkHash())
                .targetWatermarkValue(round.getTargetWatermarkValue()).targetWatermarkHash(round.getTargetWatermarkHash())
                .targetMonotonic(Boolean.TRUE.equals(round.getTargetMonotonic()))
                .targetContainsSource(Boolean.TRUE.equals(round.getTargetContainsSource()))
                .watermarkValidator(round.getWatermarkValidator()).watermarkLagSeconds(round.getWatermarkLagSeconds())
                .roundGapSeconds(round.getRoundGapSeconds()).observedAt(toInstant(round.getObservedAt())).build();
    }

    private void advanceInMemory(InventoryMigrationShadowWindowDO window, InventoryMigrationShadowRoundDO round,
                                 LocalDateTime now) {
        window.setStatus("OBSERVING").setVerificationResult("PENDING").setAggregateVersion(2L)
                .setVersion(window.getVersion() + 1).setObservedRoundCount(window.getObservedRoundCount() + 1)
                .setTotalMatchCount(window.getTotalMatchCount() + round.getMatchCount())
                .setTotalDifferentCount(window.getTotalDifferentCount() + round.getDifferentCount())
                .setTotalUncomparableCount(window.getTotalUncomparableCount() + round.getUncomparableCount())
                .setLastSourceWatermarkKind(WATERMARK_KIND).setLastSourceWatermarkValue(round.getSourceWatermarkValue())
                .setLastSourceWatermarkHash(round.getSourceWatermarkHash())
                .setLastSourceWatermarkCapturedAt(round.getSourceWatermarkCapturedAt())
                .setLastTargetWatermarkKind(WATERMARK_KIND).setLastTargetWatermarkValue(round.getTargetWatermarkValue())
                .setLastTargetWatermarkHash(round.getTargetWatermarkHash())
                .setLastTargetWatermarkAppliedAt(round.getTargetWatermarkAppliedAt())
                .setLastObservedAt(now).setUpdatedAt(now);
    }

    private void requireShadowEnvironment() {
        require(properties.isShadowEnabled(), "Inventory migration shadow is disabled by server policy");
        require("PRODUCTION".equals(upper(properties.getEnvironment())),
                "Inventory migration shadow requires the trusted PRODUCTION server environment");
        requireText(properties.getEnvironmentFingerprint(), "environmentFingerprint", 128);
    }

    private void validatePolicyBounds() {
        require(properties.getShadowRequiredRoundCount() >= 3 && properties.getShadowRequiredRoundCount() <= 1000,
                "shadow required round count is outside the server contract");
        require(properties.getShadowMinimumDurationSeconds() >= 3600
                        && properties.getShadowMinimumDurationSeconds() <= 604800,
                "shadow minimum duration is outside the server contract");
        require(properties.getShadowMaxRoundIntervalSeconds() >= 60
                        && properties.getShadowMaxRoundIntervalSeconds() <= 86400,
                "shadow max round interval is outside the server contract");
        require(properties.getShadowMaxWatermarkLagSeconds() >= 0
                        && properties.getShadowMaxWatermarkLagSeconds() <= 300,
                "shadow max watermark lag is outside the server contract");
    }

    private void validateAdmittedDenominator(InventoryMigrationPilotBatchDO batch,
                                             List<InventoryMigrationPilotItemDO> items) {
        require(items.size() == batch.getExpectedItemCount() && !items.isEmpty()
                        && items.stream().allMatch(item -> "ADMISSION_PASSED".equals(item.getStatus())
                        && Objects.equals(item.getVersion(), 3L)),
                "shadow window requires the complete admitted pilot item denominator");
        for (int index = 0; index < items.size(); index++) {
            require(Objects.equals(items.get(index).getOrdinal(), index + 1),
                    "shadow pilot item ordinal denominator is not contiguous");
            requireHash(items.get(index).getItemScopeHash(), "itemScopeHash");
        }
    }

    private static StartShadowWindowCommand normalizeStart(StartShadowWindowCommand command) {
        require(command != null, "shadow start command is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        require(command.getExpectedBatchVersion() != null && command.getExpectedBatchVersion() >= 1,
                "expectedBatchVersion is required");
        requireEvidence(command.getEvidenceRef(), "evidenceRef");
        normalizeEnvelope(command.getCorrelationId(), command.getCausationId(), command.getOccurredAt());
        command.setCorrelationId(requireUuid(command.getCorrelationId(), "correlationId"));
        if (command.getCausationId() != null) command.setCausationId(requireUuid(command.getCausationId(), "causationId"));
        return command;
    }

    private static RecordShadowRoundCommand normalizeRound(RecordShadowRoundCommand command) {
        require(command != null, "shadow round command is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        require(command.getExpectedWindowVersion() != null && command.getExpectedWindowVersion() >= 1,
                "expectedWindowVersion is required");
        require(command.getObservedAt() != null, "observedAt is required");
        require(!command.getObservedAt().isAfter(Instant.now()), "observedAt cannot be in the future");
        command.setSourceWatermarkKind(upper(command.getSourceWatermarkKind()));
        command.setTargetWatermarkKind(upper(command.getTargetWatermarkKind()));
        requireText(command.getSourceWatermarkValue(), "sourceWatermarkValue", 4096);
        requireText(command.getTargetWatermarkValue(), "targetWatermarkValue", 4096);
        require(command.getSourceWatermarkCapturedAt() != null && command.getTargetWatermarkAppliedAt() != null,
                "source and target watermark times are required");
        requireEvidence(command.getWatermarkValidationEvidenceRef(), "watermarkValidationEvidenceRef");
        requireEvidence(command.getEvidenceRef(), "evidenceRef");
        normalizeEnvelope(command.getCorrelationId(), command.getCausationId(), command.getOccurredAt());
        command.setCorrelationId(requireUuid(command.getCorrelationId(), "correlationId"));
        if (command.getCausationId() != null) command.setCausationId(requireUuid(command.getCausationId(), "causationId"));
        return command;
    }

    private static FinalizeShadowWindowCommand normalizeFinalize(FinalizeShadowWindowCommand command) {
        require(command != null, "shadow finalize command is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        require(command.getExpectedWindowVersion() != null && command.getExpectedWindowVersion() >= 1,
                "expectedWindowVersion is required");
        requireEvidence(command.getEvidenceRef(), "evidenceRef");
        normalizeEnvelope(command.getCorrelationId(), command.getCausationId(), command.getOccurredAt());
        command.setCorrelationId(requireUuid(command.getCorrelationId(), "correlationId"));
        if (command.getCausationId() != null) command.setCausationId(requireUuid(command.getCausationId(), "causationId"));
        return command;
    }

    private boolean isSubset(String subset, String superset) {
        return Integer.valueOf(1).equals(shadowMapper.gtidSubset(subset, superset));
    }

    private void requireGtid(String value, String field) {
        requireText(value, field, 4096);
        require(isSubset(value, value), field + " is not a valid MySQL GTID set");
    }

    private static void requireActorSeparated(Long actorId, InventoryMigrationPilotBatchDO batch,
                                              List<InventoryMigrationPilotApprovalDO> approvals, String role) {
        require(!Objects.equals(actorId, batch.getRequesterId()) && !Objects.equals(actorId, batch.getExecutorId())
                        && approvals.stream().noneMatch(value -> Objects.equals(actorId, value.getApproverId())),
                "shadow " + role + " must be independent from pilot requester, approvers, and executor");
    }

    private static void requireAllowlisted(Long actorId, List<Long> allowlist, String role) {
        require(actorId != null && actorId > 0, role + " must come from an authenticated system user");
        require(allowlist != null && allowlist.contains(actorId),
                "shadow " + role + " is not allowlisted by server policy");
    }

    static String expectedItemSetHash(List<InventoryMigrationPilotItemDO> items) {
        return sha256(items.stream().sorted(Comparator.comparing(InventoryMigrationPilotItemDO::getOrdinal))
                .map(item -> item.getOrdinal() + "|" + item.getItemId() + "|" + item.getItemScopeHash())
                .collect(Collectors.joining("\u001f")));
    }

    static String canonicalGrainHash(InventoryMigrationPilotItemDO item) {
        return sha256(String.join("\u001f", item.getOwnerType(), item.getOwnerId(), item.getCanonicalSkuId(),
                item.getWarehouseId(), item.getLocationId(), Objects.toString(item.getLotId(), ""),
                item.getStockStatus(), item.getQualityStatus(), item.getBaseUomCode()));
    }

    static String targetSnapshotHash(ShadowTargetObservation observation) {
        return sha256(String.join("\u001f", TARGET_PROJECTION, "1", observation.getTargetRecordVersion().toString(),
                observation.getTargetCanonicalGrainHash(), decimal(observation.getTargetOnHandQuantity()),
                decimal(observation.getTargetReservedQuantity()), decimal(observation.getTargetInTransitQuantity())));
    }

    private static String admissionCheckpointHash(InventoryMigrationPilotCheckpointDO checkpoint) {
        return sha256(String.join("\u001f", checkpoint.getCheckpointId(), checkpoint.getTenantId().toString(),
                checkpoint.getBatchId(), checkpoint.getCheckpointType(), checkpoint.getActorId().toString(),
                checkpoint.getBatchVersion().toString(), checkpoint.getScopeHash(), checkpoint.getPolicyHash(),
                checkpoint.getItemCount().toString(), checkpoint.getDetailsJson(), checkpoint.getCreatedAt().toString()));
    }

    private static String collectorArtifactHash(InventoryMigrationShadowWindowDO window) {
        return sha256(String.join("\u001f", window.getWindowId(), window.getCollectorId().toString(),
                window.getExpectedItemSetHash(), window.getTargetProjectionKind(),
                window.getTargetProjectionVersion().toString(), GTID_VALIDATOR));
    }

    private static String denominatorHash(InventoryMigrationShadowWindowDO window, int roundNumber,
                                          List<InventoryMigrationPilotItemDO> items) {
        return sha256(window.getWindowId() + "|" + roundNumber + "|" + expectedItemSetHash(items));
    }

    private static int countResult(List<InventoryMigrationShadowComparisonDO> values, String result) {
        return (int) values.stream().filter(value -> result.equals(value.getComparisonResult())).count();
    }

    private static String roundResult(InventoryMigrationShadowRoundDO round) {
        return round.getUncomparableCount() > 0 ? "UNCOMPARABLE"
                : round.getDifferentCount() > 0 ? "DIFFERENT" : "MATCH";
    }

    private static String requestHash(Long tenantId, String commandType, Long actorId, Object command) {
        return sha256(tenantId + "|" + commandType + "|" + actorId + "|" + JsonUtils.toJsonString(command));
    }

    private static void normalizeEnvelope(String correlationId, String causationId, Instant occurredAt) {
        require(correlationId != null && occurredAt != null, "correlationId and occurredAt are required");
    }

    private static String requireUuid(String value, String field) {
        requireText(value, field, 36);
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(field + " must be a UUID", error);
        }
    }

    private static String requireHash(String value, String field) {
        require(value != null && value.matches("^[0-9a-f]{64}$"), field + " must be a lowercase SHA-256");
        return value;
    }

    private static void requireEvidence(String value, String field) {
        requireText(value, field, 256);
        require(value.matches("^(?i:sha256|sha512|ticket|run|evidence|vault|kms|token):[A-Za-z0-9._/-]+$"),
                field + " must use an approved evidence reference scheme");
    }

    private static void requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank() && value.length() <= maxLength,
                field + " is required and too long");
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        BigDecimal scaled = scaled(value);
        require(scaled.signum() >= 0, field + " must be non-negative");
        return scaled;
    }

    private static BigDecimal scaled(BigDecimal value) {
        require(value != null, "quantity is required");
        return value.setScale(6, RoundingMode.UNNECESSARY);
    }

    private static String decimal(BigDecimal value) {
        return scaled(value).toPlainString();
    }

    private static String nullableDecimal(BigDecimal value) {
        return value == null ? null : decimal(value);
    }

    private static int safeSeconds(Duration duration, String field) {
        require(!duration.isNegative() && duration.getSeconds() <= Integer.MAX_VALUE, field + " is invalid");
        return Math.toIntExact(duration.getSeconds());
    }

    private static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static LocalDateTime toDateTime(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static String toInstantString(LocalDateTime value) {
        return value == null ? null : toInstant(value).toString();
    }

    private static String sha256(String value) {
        return DigestUtil.sha256Hex(value);
    }

    private static String deterministicUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private record OperationAttempt(Long operationId, ShadowWindowResult replay) {
    }

    private record SourceSnapshot(InventoryBalanceDO balance, int activeReservationCount,
                                  BigDecimal activeReservationQuantity, String snapshotHash) {
    }

    private record WatermarkEvidence(String sourceValue, String sourceHash, LocalDateTime sourceCapturedAt,
                                     String targetValue, String targetHash, LocalDateTime targetAppliedAt,
                                     boolean targetContainsSource, int lagSeconds) {
    }
}
