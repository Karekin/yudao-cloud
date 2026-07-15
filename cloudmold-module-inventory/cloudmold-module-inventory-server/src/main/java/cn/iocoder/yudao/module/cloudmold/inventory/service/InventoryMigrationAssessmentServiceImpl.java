package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryMigrationStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InventoryMigrationAssessmentServiceImpl implements InventoryMigrationAssessmentApi,
        InventoryMigrationQueryApi {

    static final String ASSESSMENT_EVENT = "inventory.migration.balance_assessed";
    private static final int OPERATION_SUCCEEDED = 10;
    private static final BigDecimal ZERO = new BigDecimal("0.000000");

    private final InventoryMigrationStoreMapper mapper;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryMigrationAssessmentResult assessV1(InventoryMigrationAssessmentCommand rawCommand) {
        InventoryMigrationAssessmentCommand command = normalize(rawCommand);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(tenantId + "|" + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), command.getSourceEventId(),
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve inventory migration operation");
        InventoryMigrationOperationDO operation = mapper.selectOperationForUpdate(tenantId, operationId);
        require(operation != null, "inventory migration operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key or source event conflicts with a different migration assessment");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing inventory migration operation is not complete");
            InventoryMigrationAssessmentResult replay = JsonUtils.parseObject(operation.getResultJson(),
                    InventoryMigrationAssessmentResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        List<InventoryBalanceDO> balances;
        if (command.getSourceBalanceId() == null) {
            balances = mapper.selectLegacyBalancesForUpdate(tenantId);
        } else {
            InventoryBalanceDO selected = mapper.selectLegacyBalanceForUpdate(tenantId, command.getSourceBalanceId());
            balances = selected == null ? List.of() : List.of(selected);
        }
        require(balances != null && !balances.isEmpty(),
                "inventory v1 migration assessment requires non-empty source evidence");
        List<InventoryMigrationCandidateDO> candidates = new ArrayList<>(balances.size());
        for (InventoryBalanceDO balance : balances) {
            candidates.add(assessOne(tenantId, command, balance, now));
        }
        String sourceSnapshotHash = DigestUtil.sha256Hex(candidates.stream()
                .map(InventoryMigrationCandidateDO::getLegacySnapshotHash).sorted().reduce("", (a, b) -> a + "\n" + b));
        int eligibleCount = (int) candidates.stream().filter(c -> "ELIGIBLE".equals(c.getDecisionStatus())).count();
        int blockedCount = (int) candidates.stream().filter(c -> "BLOCKED".equals(c.getDecisionStatus())).count();
        int rejectedCount = candidates.size() - eligibleCount - blockedCount;
        InventoryMigrationRunDO run = new InventoryMigrationRunDO()
                .setMigrationRunId(command.getMigrationRunId()).setTenantId(tenantId)
                .setSourceScope("INVENTORY_V1").setPolicyVersion(command.getPolicyVersion())
                .setEvidenceRef(command.getEvidenceRef()).setSourceSnapshotHash(sourceSnapshotHash)
                .setCandidateCount(candidates.size()).setEligibleCount(eligibleCount)
                .setBlockedCount(blockedCount).setRejectedCount(rejectedCount)
                .setStatus("ASSESSED").setVersion(1L).setAssessedAt(now)
                .setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertRun(run) == 1, "failed to persist inventory migration assessment run");
        for (InventoryMigrationCandidateDO candidate : candidates) {
            require(mapper.insertCandidate(candidate) == 1,
                    "failed to persist inventory migration assessment candidate");
            appendAssessmentEvent(tenantId, command, run, candidate);
        }

        InventoryMigrationAssessmentResult result = toResult(run).setOperationId(operationId);
        require(mapper.markOperationSucceeded(tenantId, operationId, run.getMigrationRunId(),
                JsonUtils.toJsonString(result), now) == 1, "inventory migration operation completion conflict");
        return result;
    }

    @Override
    public InventoryMigrationAssessmentResult requireRun(String migrationRunId) {
        String normalizedRunId = requireUuid(migrationRunId, "migrationRunId");
        InventoryMigrationRunDO run = mapper.selectRun(TenantContextHolder.getRequiredTenantId(), normalizedRunId);
        require(run != null, "inventory migration assessment run does not exist");
        return toResult(run);
    }

    @Override
    public List<InventoryMigrationCandidateView> listCandidates(String migrationRunId) {
        String normalizedRunId = requireUuid(migrationRunId, "migrationRunId");
        List<InventoryMigrationCandidateDO> rows = mapper.selectCandidates(
                TenantContextHolder.getRequiredTenantId(), normalizedRunId);
        require(rows != null && !rows.isEmpty(), "inventory migration assessment candidates do not exist");
        return rows.stream().map(InventoryMigrationAssessmentServiceImpl::toView).toList();
    }

    private InventoryMigrationCandidateDO assessOne(Long tenantId, InventoryMigrationAssessmentCommand command,
                                                      InventoryBalanceDO balance, LocalDateTime now) {
        InventoryLegacySourceFactDO sourceFact = mapper.selectInitialSourceFact(tenantId, balance.getBalanceId());
        int activeReservationCount = mapper.countActiveReservations(tenantId, balance.getBalanceId());
        BigDecimal activeReservationQuantity = scaled(mapper.sumActiveReservationQuantity(
                tenantId, balance.getBalanceId()));
        String snapshotHash = snapshotHash(tenantId, balance, sourceFact,
                activeReservationCount, activeReservationQuantity);
        List<String> reasons = new ArrayList<>();
        reasons.add("OWNER_TYPE_MISSING");
        if (!isUuid(balance.getOwnerId())) reasons.add("OWNER_UNRESOLVED");
        if (!isUuid(balance.getCanonicalSkuId())) reasons.add("SKU_UNRESOLVED");
        if (!isUuid(balance.getWarehouseId())) reasons.add("WAREHOUSE_UNRESOLVED");
        reasons.add("LOCATION_UNRESOLVED");
        reasons.add("LOT_POLICY_UNPROVEN");
        if (sourceFact == null || sourceFact.getSourceEventId() == null
                || sourceFact.getSourceEventId().isBlank()) reasons.add("SOURCE_FACT_MISSING");
        if (activeReservationCount > 0) reasons.add("ACTIVE_RESERVATION");
        if (activeReservationCount > 0 || scaled(balance.getReservedQuantity()).signum() != 0) {
            reasons.add("RESERVATION_PROVENANCE_MISSING");
        }
        if (scaled(balance.getReservedQuantity()).signum() != 0) reasons.add("RESERVED_QUANTITY_NONZERO");
        if (scaled(balance.getInTransitQuantity()).signum() != 0) reasons.add("IN_TRANSIT_QUANTITY_NONZERO");
        String classification = sourceClassification(balance, sourceFact);
        if (!"UNCLASSIFIED".equals(classification)) reasons.add("CONTROLLED_NON_PRODUCTION_SOURCE");
        reasons.add("TARGET_DIMENSION_UNRESOLVED");
        reasons = reasons.stream().distinct().sorted().toList();
        return new InventoryMigrationCandidateDO().setCandidateId(UUID.randomUUID().toString())
                .setTenantId(tenantId).setMigrationRunId(command.getMigrationRunId())
                .setLegacyBalanceId(balance.getBalanceId()).setLegacyBalanceVersion(balance.getVersion())
                .setLegacySnapshotHash(snapshotHash).setSourceSystem("CLOUDMOLD_INVENTORY_V1")
                .setSourceType("BALANCE").setSourceId(balance.getBalanceId())
                .setSourceClassification(classification).setSourceUpdatedAt(balance.getUpdatedAt())
                .setLegacyOwnerId(balance.getOwnerId()).setLegacyCanonicalSkuId(balance.getCanonicalSkuId())
                .setLegacyWarehouseId(balance.getWarehouseId()).setStockStatus(balance.getStockStatus())
                .setQualityStatus(balance.getQualityStatus()).setBaseUomCode(balance.getBaseUomCode())
                .setSourceOnHandQuantity(scaled(balance.getOnHandQuantity()))
                .setSourceReservedQuantity(scaled(balance.getReservedQuantity()))
                .setSourceInTransitQuantity(scaled(balance.getInTransitQuantity()))
                .setInitialBusinessType(sourceFact == null ? null : sourceFact.getBusinessType())
                .setInitialSourceEventId(sourceFact == null ? null : sourceFact.getSourceEventId())
                .setActiveReservationCount(activeReservationCount)
                .setActiveReservationQuantity(activeReservationQuantity).setLotTrackingPolicy("UNRESOLVED")
                .setDecisionStatus("BLOCKED").setReasonCodes(JsonUtils.toJsonString(reasons))
                .setVerificationRef(command.getEvidenceRef()).setVersion(1L).setAssessedAt(now)
                .setCreatedAt(now).setUpdatedAt(now);
    }

    private void appendAssessmentEvent(Long tenantId, InventoryMigrationAssessmentCommand command,
                                       InventoryMigrationRunDO run, InventoryMigrationCandidateDO candidate) {
        List<String> reasonCodes = JsonUtils.parseArray(candidate.getReasonCodes(), String.class);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("migration_run_id", run.getMigrationRunId());
        payload.put("assessment_id", candidate.getCandidateId());
        payload.put("legacy_balance_id", candidate.getLegacyBalanceId());
        payload.put("source_system", candidate.getSourceSystem());
        payload.put("source_type", candidate.getSourceType());
        payload.put("source_id", candidate.getSourceId());
        payload.put("source_classification", candidate.getSourceClassification());
        payload.put("source_version", candidate.getLegacyBalanceVersion());
        payload.put("source_updated_at", toInstantString(candidate.getSourceUpdatedAt()));
        payload.put("source_snapshot_hash", candidate.getLegacySnapshotHash());
        payload.put("source_owner_id", candidate.getLegacyOwnerId());
        payload.put("resolved_owner_type", null);
        payload.put("resolved_owner_id", null);
        payload.put("source_sku_id", candidate.getLegacyCanonicalSkuId());
        payload.put("resolved_canonical_sku_id", null);
        payload.put("source_warehouse_id", candidate.getLegacyWarehouseId());
        payload.put("resolved_warehouse_id", null);
        payload.put("source_location_id", null);
        payload.put("resolved_location_id", null);
        payload.put("source_lot_id", null);
        payload.put("resolved_lot_id", null);
        payload.put("lot_tracking_policy", candidate.getLotTrackingPolicy());
        payload.put("stock_status", candidate.getStockStatus());
        payload.put("quality_status", candidate.getQualityStatus());
        payload.put("source_uom_code", candidate.getBaseUomCode());
        payload.put("resolved_base_uom_code", null);
        payload.put("source_on_hand_quantity", decimal(candidate.getSourceOnHandQuantity()));
        payload.put("source_reserved_quantity", decimal(candidate.getSourceReservedQuantity()));
        payload.put("source_in_transit_quantity", decimal(candidate.getSourceInTransitQuantity()));
        payload.put("active_reservation_count", candidate.getActiveReservationCount());
        payload.put("active_reservation_quantity", decimal(candidate.getActiveReservationQuantity()));
        // v1 reservations have no canonical allocation lineage. Never present them as v3 allocations.
        payload.put("reservation_allocation_count", 0);
        payload.put("reservation_allocation_quantity", decimal(BigDecimal.ZERO));
        payload.put("assessment_status", candidate.getDecisionStatus());
        payload.put("blocker_codes", reasonCodes);
        payload.put("policy_version", run.getPolicyVersion());
        payload.put("verification_ref", candidate.getVerificationRef());
        payload.put("assessed_at", command.getOccurredAt().toString());
        String idempotency = command.getIdempotencyKey() + ":candidate:" + candidate.getLegacyBalanceId();
        String eventId = deterministicUuid(tenantId + "|" + idempotency);
        outboxAppender.append(AppendDomainEventCommand.builder().eventId(eventId)
                .eventType(ASSESSMENT_EVENT).schemaVersion(2).sourceSystem("cloudmold-inventory")
                .tenantId(tenantId).aggregateType("inventory_migration_assessment")
                .aggregateId(candidate.getCandidateId()).aggregateVersion(1L).eventSequence((short) 1)
                .occurredAt(command.getOccurredAt()).correlationId(command.getCorrelationId())
                .causationId(command.getCausationId()).idempotencyKey(idempotency).payload(payload)
                .headers(Map.of("migration_run_id", run.getMigrationRunId(),
                        "source_snapshot_hash", run.getSourceSnapshotHash()))
                .destination("lakehouse").build());
    }

    private static InventoryMigrationAssessmentResult toResult(InventoryMigrationRunDO run) {
        return new InventoryMigrationAssessmentResult().setMigrationRunId(run.getMigrationRunId())
                .setSourceSnapshotHash(run.getSourceSnapshotHash()).setCandidateCount(run.getCandidateCount())
                .setEligibleCount(run.getEligibleCount()).setBlockedCount(run.getBlockedCount())
                .setRejectedCount(run.getRejectedCount()).setStatus(run.getStatus());
    }

    private static InventoryMigrationCandidateView toView(InventoryMigrationCandidateDO value) {
        return new InventoryMigrationCandidateView().setCandidateId(value.getCandidateId())
                .setMigrationRunId(value.getMigrationRunId()).setLegacyBalanceId(value.getLegacyBalanceId())
                .setLegacyBalanceVersion(value.getLegacyBalanceVersion())
                .setLegacySnapshotHash(value.getLegacySnapshotHash()).setSourceSystem(value.getSourceSystem())
                .setSourceType(value.getSourceType()).setSourceId(value.getSourceId())
                .setSourceClassification(value.getSourceClassification()).setLegacyOwnerId(value.getLegacyOwnerId())
                .setLegacyCanonicalSkuId(value.getLegacyCanonicalSkuId())
                .setLegacyWarehouseId(value.getLegacyWarehouseId()).setStockStatus(value.getStockStatus())
                .setQualityStatus(value.getQualityStatus()).setBaseUomCode(value.getBaseUomCode())
                .setSourceOnHandQuantity(value.getSourceOnHandQuantity())
                .setSourceReservedQuantity(value.getSourceReservedQuantity())
                .setSourceInTransitQuantity(value.getSourceInTransitQuantity())
                .setInitialBusinessType(value.getInitialBusinessType())
                .setInitialSourceEventId(value.getInitialSourceEventId())
                .setActiveReservationCount(value.getActiveReservationCount())
                .setActiveReservationQuantity(value.getActiveReservationQuantity())
                .setLotTrackingPolicy(value.getLotTrackingPolicy()).setDecisionStatus(value.getDecisionStatus())
                .setReasonCodes(JsonUtils.parseArray(value.getReasonCodes(), String.class))
                .setVerificationRef(value.getVerificationRef())
                .setAssessedAt(value.getAssessedAt().toInstant(ZoneOffset.UTC));
    }

    static String snapshotHash(Long tenantId, InventoryBalanceDO balance, InventoryLegacySourceFactDO sourceFact,
                               int activeReservationCount, BigDecimal activeReservationQuantity) {
        return DigestUtil.sha256Hex(String.join("\u001f", tenantId.toString(), balance.getBalanceId(),
                balance.getVersion().toString(), Objects.toString(balance.getUpdatedAt(), ""),
                balance.getOwnerId(), balance.getCanonicalSkuId(),
                balance.getWarehouseId(), balance.getStockStatus(), balance.getQualityStatus(),
                balance.getBaseUomCode(), decimal(balance.getOnHandQuantity()),
                decimal(balance.getReservedQuantity()), decimal(balance.getInTransitQuantity()),
                sourceFact == null ? "" : Objects.toString(sourceFact.getBusinessType(), ""),
                sourceFact == null ? "" : Objects.toString(sourceFact.getSourceEventId(), ""),
                Integer.toString(activeReservationCount), decimal(activeReservationQuantity)));
    }

    private static String sourceClassification(InventoryBalanceDO balance, InventoryLegacySourceFactDO sourceFact) {
        String warehouse = balance.getWarehouseId().toLowerCase(Locale.ROOT);
        String businessType = sourceFact == null ? "" : Objects.toString(sourceFact.getBusinessType(), "");
        if ("MIGRATION_CANARY".equalsIgnoreCase(businessType)
                || warehouse.startsWith("migration-canary:")) {
            return "CONTROLLED_CANARY";
        }
        if (warehouse.startsWith("oversell-") || businessType.toLowerCase(Locale.ROOT).contains("concurrency")) {
            return "CONCURRENCY_PROBE";
        }
        if ("TEST_FIXTURE".equalsIgnoreCase(businessType)
                || "canonical-warehouse-demo".equalsIgnoreCase(balance.getWarehouseId())) {
            return "CONTROLLED_FIXTURE";
        }
        if (warehouse.startsWith("scenario:")) return "CONTROLLED_SCENARIO";
        return "UNCLASSIFIED";
    }

    private static InventoryMigrationAssessmentCommand normalize(InventoryMigrationAssessmentCommand command) {
        require(command != null, "inventory migration assessment command is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        command.setMigrationRunId(requireUuid(command.getMigrationRunId(), "migrationRunId"));
        if (command.getSourceBalanceId() != null) {
            command.setSourceBalanceId(requireUuid(command.getSourceBalanceId(), "sourceBalanceId"));
        }
        requireText(command.getPolicyVersion(), "policyVersion", 32);
        requireText(command.getEvidenceRef(), "evidenceRef", 200);
        command.setCorrelationId(requireUuid(command.getCorrelationId(), "correlationId"));
        if (command.getCausationId() != null) {
            command.setCausationId(requireUuid(command.getCausationId(), "causationId"));
        }
        require(command.getOccurredAt() != null, "occurredAt is required");
        return command;
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String requireUuid(String value, String field) {
        requireText(value, field, 36);
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(field + " must be a UUID", error);
        }
    }

    private static String deterministicUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static BigDecimal scaled(BigDecimal value) {
        if (value == null) return ZERO;
        return value.setScale(6, RoundingMode.UNNECESSARY);
    }

    private static String decimal(BigDecimal value) {
        return scaled(value).toPlainString();
    }

    private static String toInstantString(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC).toString();
    }

    private static void requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank() && value.length() <= maxLength,
                field + " is required and too long");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
