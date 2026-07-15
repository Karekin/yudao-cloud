package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.*;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantOwnerValidationApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.*;
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
public class InventoryMigrationQualificationServiceImpl implements InventoryMigrationQualificationApi {

    static final String QUALIFIED_EVENT = "inventory.migration.balance_qualified";
    private static final int OPERATION_SUCCEEDED = 10;
    private static final BigDecimal ZERO = new BigDecimal("0.000000");
    private static final Set<String> RESOLVABLE_CANARY_BLOCKERS = Set.of(
            "CONTROLLED_NON_PRODUCTION_SOURCE", "LOCATION_UNRESOLVED", "LOT_POLICY_UNPROVEN",
            "OWNER_TYPE_MISSING", "TARGET_DIMENSION_UNRESOLVED", "WAREHOUSE_UNRESOLVED");

    private final InventoryMigrationStoreMapper migrationMapper;
    private final InventoryV3OperationMapper operationMapper;
    private final InventoryV3BalanceMapper balanceMapper;
    private final InventoryV3LedgerTransactionMapper ledgerTransactionMapper;
    private final InventoryV3LedgerEntryMapper ledgerEntryMapper;
    private final InventoryLotMapper lotMapper;
    private final MerchantOwnerValidationApi merchantOwnerValidationApi;
    private final CatalogSkuValidationApi catalogSkuValidationApi;
    private final WarehouseReferenceValidationApi warehouseValidationApi;
    private final WarehouseSourceMappingQueryApi warehouseSourceMappingQueryApi;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryMigrationQualificationResult qualify(InventoryMigrationQualificationCommand rawCommand) {
        InventoryMigrationQualificationCommand command = normalizeQualification(rawCommand);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = requestHash(tenantId, "QUALIFY_V1", command);
        String attemptToken = UUID.randomUUID().toString();
        migrationMapper.insertOrResolveCommand(tenantId, command.getIdempotencyKey(), command.getSourceEventId(),
                "QUALIFY_V1", requestHash, attemptToken, now);
        Long operationId = migrationMapper.selectLastInsertId();
        InventoryMigrationOperationDO operation = requireOperation(tenantId, operationId);
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "qualification idempotency key or source event conflicts with different payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing Inventory migration qualification is not complete");
            InventoryMigrationQualificationResult replay = JsonUtils.parseObject(operation.getResultJson(),
                    InventoryMigrationQualificationResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        InventoryMigrationCandidateDO candidate = migrationMapper.selectCandidateForUpdate(
                tenantId, command.getCandidateId());
        require(candidate != null && Objects.equals(candidate.getMigrationRunId(), command.getMigrationRunId()),
                "migration assessment candidate does not belong to requested run");
        require("CONTROLLED_CANARY".equals(candidate.getSourceClassification()),
                "only CONTROLLED_CANARY assessment candidates may qualify in this slice");
        require("BLOCKED".equals(candidate.getDecisionStatus()),
                "migration assessment candidate must be BLOCKED before explicit qualification");
        require(Objects.equals(candidate.getLegacySnapshotHash(), command.getExpectedSourceSnapshotHash()),
                "qualification source snapshot does not match assessed candidate");
        List<String> blockers = JsonUtils.parseArray(candidate.getReasonCodes(), String.class);
        require(blockers != null && !blockers.isEmpty() && RESOLVABLE_CANARY_BLOCKERS.containsAll(blockers),
                "candidate contains a blocker that controlled qualification cannot resolve");

        SourceSnapshot source = lockAndVerifySource(tenantId, candidate);
        require("MIGRATION_CANARY".equalsIgnoreCase(source.sourceFact().getBusinessType())
                        && source.sourceFact().getSourceEventId() != null
                        && !source.sourceFact().getSourceEventId().isBlank(),
                "controlled migration canary requires an explicit source fact");
        require(source.activeReservationCount() == 0 && source.activeReservationQuantity().signum() == 0,
                "controlled migration canary cannot have an active reservation");
        require(scaled(source.balance().getReservedQuantity()).signum() == 0
                        && scaled(source.balance().getInTransitQuantity()).signum() == 0,
                "controlled migration canary requires zero reserved and in-transit quantity");
        require(scaled(source.balance().getOnHandQuantity()).signum() > 0,
                "controlled migration canary requires positive on-hand quantity");

        String ownerId = requireUuid(candidate.getLegacyOwnerId(), "legacyOwnerId");
        String skuId = requireUuid(candidate.getLegacyCanonicalSkuId(), "legacyCanonicalSkuId");
        merchantOwnerValidationApi.requireActiveMerchant(ownerId);
        catalogSkuValidationApi.requireActiveSku(skuId);
        WarehouseSourceMappingView sourceMapping = warehouseSourceMappingQueryApi.resolveActive(
                new WarehouseSourceReference("CLOUDMOLD_INVENTORY_V1", "WAREHOUSE",
                        candidate.getLegacyWarehouseId()), command.getOccurredAt());
        require("WAREHOUSE".equals(sourceMapping.getCanonicalType())
                        && Objects.equals(command.getWarehouseId(), sourceMapping.getWarehouseId())
                        && Objects.equals(command.getWarehouseId(), sourceMapping.getCanonicalId()),
                "legacy warehouse source mapping does not resolve to requested canonical Warehouse");
        warehouseValidationApi.requireActiveLocation(command.getWarehouseId(), command.getLocationId());
        InventoryLotDO lot = requireLot(tenantId, ownerId, skuId, command.getLotTrackingPolicy(), command.getLotId());

        String qualificationId = UUID.randomUUID().toString();
        InventoryMigrationQualificationDO row = new InventoryMigrationQualificationDO()
                .setQualificationId(qualificationId).setTenantId(tenantId)
                .setMigrationRunId(candidate.getMigrationRunId()).setCandidateId(candidate.getCandidateId())
                .setQualificationOperationId(operationId).setSourceSystem(candidate.getSourceSystem())
                .setSourceType(candidate.getSourceType()).setSourceId(candidate.getSourceId())
                .setSourceSnapshotHash(candidate.getLegacySnapshotHash())
                .setSourceVersion(candidate.getLegacyBalanceVersion()).setSourceUpdatedAt(candidate.getSourceUpdatedAt())
                .setSourceOnHandQuantity(scaled(candidate.getSourceOnHandQuantity()))
                .setSourceReservedQuantity(scaled(candidate.getSourceReservedQuantity()))
                .setSourceInTransitQuantity(scaled(candidate.getSourceInTransitQuantity()))
                .setOwnerType("MERCHANT").setOwnerId(ownerId).setCanonicalSkuId(skuId)
                .setWarehouseSourceMappingId(sourceMapping.getMappingId()).setWarehouseId(command.getWarehouseId())
                .setLocationId(command.getLocationId()).setLotTrackingPolicy(command.getLotTrackingPolicy())
                .setLotId(lot == null ? null : lot.getLotId()).setStockStatus(candidate.getStockStatus())
                .setQualityStatus(candidate.getQualityStatus()).setBaseUomCode(candidate.getBaseUomCode())
                .setResolvedBlockerCodes(JsonUtils.toJsonString(blockers)).setPolicyVersion(command.getPolicyVersion())
                .setVerificationRef(command.getEvidenceRef()).setStatus("QUALIFIED").setVersion(1L)
                .setQualifiedAt(now).setCreatedAt(now).setUpdatedAt(now);
        require(migrationMapper.insertQualification(row) == 1,
                "failed to persist Inventory migration qualification");
        appendQualifiedEvent(tenantId, command, candidate, row, blockers);

        InventoryMigrationQualificationResult result = toResult(row);
        require(migrationMapper.markOperationSucceeded(tenantId, operationId, candidate.getMigrationRunId(),
                JsonUtils.toJsonString(result), now) == 1,
                "Inventory migration qualification completion conflict");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryMigrationOpeningResult migrate(InventoryMigrationOpeningCommand rawCommand) {
        InventoryMigrationOpeningCommand command = normalizeOpening(rawCommand);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = requestHash(tenantId, "MIGRATE_V1", command);
        String attemptToken = UUID.randomUUID().toString();
        migrationMapper.insertOrResolveCommand(tenantId, command.getIdempotencyKey(), command.getSourceEventId(),
                "MIGRATE_V1", requestHash, attemptToken, now);
        Long operationId = migrationMapper.selectLastInsertId();
        InventoryMigrationOperationDO migrationOperation = requireOperation(tenantId, operationId);
        if (!attemptToken.equals(migrationOperation.getAttemptToken())) {
            require(Objects.equals(requestHash, migrationOperation.getRequestHash()),
                    "migration idempotency key or source event conflicts with different payload");
            require(migrationOperation.getStatus() == OPERATION_SUCCEEDED && migrationOperation.getResultJson() != null,
                    "existing Inventory migration opening is not complete");
            InventoryMigrationOpeningResult replay = JsonUtils.parseObject(migrationOperation.getResultJson(),
                    InventoryMigrationOpeningResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        InventoryMigrationQualificationDO qualification = migrationMapper.selectQualificationForUpdate(
                tenantId, command.getQualificationId());
        require(qualification != null, "Inventory migration qualification does not exist");
        require("QUALIFIED".equals(qualification.getStatus())
                        && Objects.equals(command.getExpectedVersion(), qualification.getVersion()),
                "Inventory migration qualification is not at the expected QUALIFIED version");
        require(Objects.equals(command.getExpectedSourceSnapshotHash(), qualification.getSourceSnapshotHash()),
                "migration opening source snapshot does not match qualification");
        InventoryMigrationCandidateDO candidate = migrationMapper.selectCandidateForUpdate(
                tenantId, qualification.getCandidateId());
        require(candidate != null && "CONTROLLED_CANARY".equals(candidate.getSourceClassification()),
                "migration opening is restricted to a CONTROLLED_CANARY candidate");
        SourceSnapshot source = lockAndVerifySource(tenantId, candidate);
        require(Objects.equals(source.snapshotHash(), qualification.getSourceSnapshotHash()),
                "legacy source changed after qualification");
        require(source.activeReservationCount() == 0 && source.activeReservationQuantity().signum() == 0
                        && scaled(source.balance().getReservedQuantity()).signum() == 0
                        && scaled(source.balance().getInTransitQuantity()).signum() == 0,
                "legacy source acquired reservation or in-transit quantity after qualification");

        revalidateTarget(tenantId, qualification, candidate, command.getOccurredAt());
        String openingKey = "migration-opening:" + qualification.getQualificationId();
        String openingSourceEvent = deterministicUuid(openingKey + ":source");
        String openingHash = DigestUtil.sha256Hex(tenantId + "|" + qualification.getQualificationId()
                + "|" + qualification.getSourceSnapshotHash() + "|" + command.getOccurredAt());
        String openingAttempt = UUID.randomUUID().toString();
        operationMapper.insertOrResolve(tenantId, openingKey, openingSourceEvent, "MIGRATION_OPENING",
                openingHash, openingAttempt, now);
        Long openingOperationId = operationMapper.selectLastInsertId();
        InventoryV3OperationDO openingOperation = operationMapper.selectForUpdate(openingOperationId, tenantId);
        require(openingOperation != null && openingAttempt.equals(openingOperation.getAttemptToken()),
                "qualification already has a different opening driver");

        balanceMapper.insertOrResolve(UUID.randomUUID().toString(), tenantId, qualification.getOwnerType(),
                qualification.getOwnerId(), qualification.getCanonicalSkuId(), qualification.getWarehouseId(),
                qualification.getLocationId(), qualification.getLotId(), qualification.getStockStatus(),
                qualification.getQualityStatus(), qualification.getBaseUomCode(), now);
        InventoryV3BalanceDO target = balanceMapper.selectDimensionForUpdate(tenantId, qualification.getOwnerType(),
                qualification.getOwnerId(), qualification.getCanonicalSkuId(), qualification.getWarehouseId(),
                qualification.getLocationId(), qualification.getLotId(), qualification.getStockStatus(),
                qualification.getQualityStatus());
        require(target != null && Objects.equals(target.getBaseUomCode(), qualification.getBaseUomCode()),
                "migration opening target base UOM does not match qualification");
        require(Objects.equals(target.getVersion(), 0L)
                        && scaled(target.getOnHandQuantity()).signum() == 0
                        && scaled(target.getReservedQuantity()).signum() == 0
                        && scaled(target.getInTransitQuantity()).signum() == 0,
                "migration opening target must be a new zero v3 balance");

        BigDecimal openingQuantity = scaled(qualification.getSourceOnHandQuantity());
        require(balanceMapper.updateBalanceCas(tenantId, target.getBalanceId(), 0L,
                openingQuantity, ZERO, ZERO, now) == 1, "migration opening balance version conflict");
        String movementGroupId = UUID.randomUUID().toString();
        InventoryV3LedgerTransactionDO transaction = new InventoryV3LedgerTransactionDO()
                .setTenantId(tenantId).setOperationId(openingOperationId).setMovementGroupId(movementGroupId)
                .setCommandType("MIGRATION_OPENING").setBusinessType("MIGRATION_OPENING")
                .setBusinessId(qualification.getMigrationRunId()).setBusinessItemId(qualification.getCandidateId())
                .setBusinessNo(qualification.getQualificationId())
                .setOccurredAt(LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC)).setCreatedAt(now);
        ledgerTransactionMapper.insert(transaction);
        require(transaction.getLedgerTransactionId() != null,
                "migration opening ledger transaction id was not generated");
        ledgerEntryMapper.insert(new InventoryV3LedgerEntryDO().setTenantId(tenantId)
                .setLedgerTransactionId(transaction.getLedgerTransactionId()).setMovementGroupId(movementGroupId)
                .setEntryRole("SINGLE").setBalanceId(target.getBalanceId()).setAggregateVersion(1L)
                .setBaseUomCode(qualification.getBaseUomCode())
                .setBeforeOnHandQuantity(ZERO).setDeltaOnHandQuantity(openingQuantity)
                .setAfterOnHandQuantity(openingQuantity).setBeforeReservedQuantity(ZERO)
                .setDeltaReservedQuantity(ZERO).setAfterReservedQuantity(ZERO)
                .setBeforeInTransitQuantity(ZERO).setDeltaInTransitQuantity(ZERO)
                .setAfterInTransitQuantity(ZERO).setCreatedAt(now));

        String bridgeId = UUID.randomUUID().toString();
        require(migrationMapper.insertResolvedBridge(bridgeId, tenantId, candidate.getLegacyBalanceId(),
                target.getBalanceId(), command.getEvidenceRef(), openingQuantity, ZERO, ZERO,
                openingQuantity, ZERO, ZERO, now) == 1, "failed to persist resolved migration bridge");
        require(migrationMapper.markQualificationMigrated(tenantId, qualification.getQualificationId(),
                openingOperationId, target.getBalanceId(), transaction.getLedgerTransactionId(), bridgeId, now) == 1,
                "Inventory migration qualification version conflict");

        BigDecimal available = "SELLABLE".equals(qualification.getStockStatus())
                && "QUALIFIED".equals(qualification.getQualityStatus()) ? openingQuantity : ZERO;
        InventoryMigrationOpeningResult result = InventoryMigrationOpeningResult.builder()
                .operationId(operationId).openingOperationId(openingOperationId)
                .qualificationId(qualification.getQualificationId()).migrationRunId(qualification.getMigrationRunId())
                .candidateId(qualification.getCandidateId()).targetBalanceId(target.getBalanceId())
                .ledgerTransactionId(transaction.getLedgerTransactionId()).movementGroupId(movementGroupId)
                .bridgeId(bridgeId).aggregateVersion(1L).onHandQuantity(openingQuantity)
                .reservedQuantity(ZERO).inTransitQuantity(ZERO).availableQuantity(available).duplicate(false).build();
        appendOpeningEvent(tenantId, command, qualification, candidate, target, transaction, movementGroupId,
                openingQuantity, available);
        require(operationMapper.markSucceeded(openingOperationId, tenantId, transaction.getLedgerTransactionId(),
                JsonUtils.toJsonString(result), now) == 1, "v3 migration opening completion conflict");
        require(migrationMapper.markOperationSucceeded(tenantId, operationId, qualification.getMigrationRunId(),
                JsonUtils.toJsonString(result), now) == 1, "migration command completion conflict");
        return result;
    }

    @Override
    public InventoryMigrationQualificationResult requireQualification(String qualificationId) {
        InventoryMigrationQualificationDO row = migrationMapper.selectQualification(
                TenantContextHolder.getRequiredTenantId(), requireUuid(qualificationId, "qualificationId"));
        require(row != null, "Inventory migration qualification does not exist");
        return toResult(row);
    }

    private SourceSnapshot lockAndVerifySource(Long tenantId, InventoryMigrationCandidateDO candidate) {
        InventoryBalanceDO balance = migrationMapper.selectLegacyBalanceForUpdate(tenantId,
                candidate.getLegacyBalanceId());
        require(balance != null, "legacy Inventory source balance does not exist");
        InventoryLegacySourceFactDO sourceFact = migrationMapper.selectInitialSourceFact(tenantId,
                candidate.getLegacyBalanceId());
        require(sourceFact != null, "legacy Inventory source fact does not exist");
        int count = migrationMapper.countActiveReservations(tenantId, candidate.getLegacyBalanceId());
        BigDecimal quantity = scaled(migrationMapper.sumActiveReservationQuantity(tenantId,
                candidate.getLegacyBalanceId()));
        String snapshot = InventoryMigrationAssessmentServiceImpl.snapshotHash(tenantId, balance, sourceFact,
                count, quantity);
        require(Objects.equals(candidate.getLegacySnapshotHash(), snapshot)
                        && Objects.equals(candidate.getLegacyBalanceVersion(), balance.getVersion())
                        && Objects.equals(candidate.getSourceUpdatedAt(), balance.getUpdatedAt()),
                "legacy Inventory source changed after assessment");
        return new SourceSnapshot(balance, sourceFact, count, quantity, snapshot);
    }

    private void revalidateTarget(Long tenantId, InventoryMigrationQualificationDO qualification,
                                  InventoryMigrationCandidateDO candidate, Instant occurredAt) {
        merchantOwnerValidationApi.requireActiveMerchant(qualification.getOwnerId());
        catalogSkuValidationApi.requireActiveSku(qualification.getCanonicalSkuId());
        WarehouseSourceMappingView mapping = warehouseSourceMappingQueryApi.resolveActive(
                new WarehouseSourceReference("CLOUDMOLD_INVENTORY_V1", "WAREHOUSE",
                        candidate.getLegacyWarehouseId()), occurredAt);
        require(Objects.equals(mapping.getMappingId(), qualification.getWarehouseSourceMappingId())
                        && "WAREHOUSE".equals(mapping.getCanonicalType())
                        && Objects.equals(mapping.getCanonicalId(), qualification.getWarehouseId())
                        && Objects.equals(mapping.getWarehouseId(), qualification.getWarehouseId()),
                "legacy Warehouse source mapping changed after qualification");
        warehouseValidationApi.requireActiveLocation(qualification.getWarehouseId(), qualification.getLocationId());
        requireLot(tenantId, qualification.getOwnerId(), qualification.getCanonicalSkuId(),
                qualification.getLotTrackingPolicy(), qualification.getLotId());
    }

    private InventoryLotDO requireLot(Long tenantId, String ownerId, String skuId, String policy, String lotId) {
        if ("NOT_TRACKED".equals(policy)) {
            require(lotId == null, "NOT_TRACKED migration qualification cannot provide lotId");
            return null;
        }
        require("TRACKED".equals(policy), "lotTrackingPolicy must be NOT_TRACKED or TRACKED");
        String normalizedLotId = requireUuid(lotId, "lotId");
        InventoryLotDO lot = lotMapper.selectCurrent(tenantId, normalizedLotId);
        require(lot != null && "ACTIVE".equals(lot.getStatus())
                        && "MERCHANT".equals(lot.getOwnerType()) && Objects.equals(ownerId, lot.getOwnerId())
                        && Objects.equals(skuId, lot.getCanonicalSkuId()),
                "migration qualification Lot is not an ACTIVE Lot for the exact owner and SKU");
        return lot;
    }

    private void appendQualifiedEvent(Long tenantId, InventoryMigrationQualificationCommand command,
                                      InventoryMigrationCandidateDO candidate,
                                      InventoryMigrationQualificationDO qualification, List<String> blockers) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("migration_run_id", qualification.getMigrationRunId());
        payload.put("assessment_id", candidate.getCandidateId());
        payload.put("qualification_id", qualification.getQualificationId());
        payload.put("source_system", qualification.getSourceSystem());
        payload.put("source_type", qualification.getSourceType());
        payload.put("source_id", qualification.getSourceId());
        payload.put("source_classification", candidate.getSourceClassification());
        payload.put("source_version", qualification.getSourceVersion());
        payload.put("source_updated_at", toInstantString(qualification.getSourceUpdatedAt()));
        payload.put("source_snapshot_hash", qualification.getSourceSnapshotHash());
        payload.put("owner_type", qualification.getOwnerType());
        payload.put("owner_id", qualification.getOwnerId());
        payload.put("canonical_sku_id", qualification.getCanonicalSkuId());
        payload.put("warehouse_source_mapping_id", qualification.getWarehouseSourceMappingId());
        payload.put("warehouse_id", qualification.getWarehouseId());
        payload.put("location_id", qualification.getLocationId());
        payload.put("lot_tracking_policy", qualification.getLotTrackingPolicy());
        payload.put("lot_id", qualification.getLotId());
        payload.put("stock_status", qualification.getStockStatus());
        payload.put("quality_status", qualification.getQualityStatus());
        payload.put("base_uom_code", qualification.getBaseUomCode());
        payload.put("source_on_hand_quantity", decimal(qualification.getSourceOnHandQuantity()));
        payload.put("source_reserved_quantity", decimal(qualification.getSourceReservedQuantity()));
        payload.put("source_in_transit_quantity", decimal(qualification.getSourceInTransitQuantity()));
        payload.put("resolved_blocker_codes", blockers);
        payload.put("qualification_status", "QUALIFIED");
        payload.put("policy_version", qualification.getPolicyVersion());
        payload.put("verification_ref", qualification.getVerificationRef());
        payload.put("qualified_at", command.getOccurredAt().toString());
        String idempotency = command.getIdempotencyKey() + ":event:qualified";
        outboxAppender.append(AppendDomainEventCommand.builder().eventId(deterministicUuid(tenantId + "|" + idempotency))
                .eventType(QUALIFIED_EVENT).schemaVersion(1).sourceSystem("cloudmold-inventory")
                .tenantId(tenantId).aggregateType("inventory_migration_qualification")
                .aggregateId(qualification.getQualificationId()).aggregateVersion(1L).eventSequence((short) 1)
                .occurredAt(command.getOccurredAt()).correlationId(command.getCorrelationId())
                .causationId(command.getCausationId()).idempotencyKey(idempotency).payload(payload)
                .headers(Map.of("migration_run_id", qualification.getMigrationRunId(),
                        "source_snapshot_hash", qualification.getSourceSnapshotHash()))
                .destination("lakehouse").build());
    }

    private void appendOpeningEvent(Long tenantId, InventoryMigrationOpeningCommand command,
                                    InventoryMigrationQualificationDO qualification,
                                    InventoryMigrationCandidateDO candidate, InventoryV3BalanceDO target,
                                    InventoryV3LedgerTransactionDO transaction, String movementGroupId,
                                    BigDecimal openingQuantity, BigDecimal available) {
        InventoryLotDO lot = qualification.getLotId() == null ? null
                : lotMapper.selectCurrent(tenantId, qualification.getLotId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("owner_type", qualification.getOwnerType());
        payload.put("owner_id", qualification.getOwnerId());
        payload.put("canonical_sku_id", qualification.getCanonicalSkuId());
        payload.put("warehouse_id", qualification.getWarehouseId());
        payload.put("location_id", qualification.getLocationId());
        payload.put("lot_id", qualification.getLotId());
        payload.put("lot_code", lot == null ? null : lot.getLotCode());
        payload.put("stock_status", qualification.getStockStatus());
        payload.put("quality_status", qualification.getQualityStatus());
        payload.put("base_uom_code", qualification.getBaseUomCode());
        payload.put("uom_code", qualification.getBaseUomCode());
        payload.put("delta_quantity", decimal(openingQuantity));
        payload.put("delta_on_hand_quantity", decimal(openingQuantity));
        payload.put("delta_reserved_quantity", decimal(ZERO));
        payload.put("delta_in_transit_quantity", decimal(ZERO));
        payload.put("after_on_hand_quantity", decimal(openingQuantity));
        payload.put("after_reserved_quantity", decimal(ZERO));
        payload.put("after_in_transit_quantity", decimal(ZERO));
        payload.put("after_available_quantity", decimal(available));
        payload.put("movement_type", "MIGRATION_OPENING");
        payload.put("ledger_transaction_id", transaction.getLedgerTransactionId());
        payload.put("movement_group_id", movementGroupId);
        payload.put("entry_role", "SINGLE");
        payload.put("counterparty_balance_id", null);
        payload.put("business_type", "MIGRATION_OPENING");
        payload.put("business_id", qualification.getMigrationRunId());
        payload.put("business_item_id", qualification.getCandidateId());
        payload.put("business_no", qualification.getQualificationId());
        payload.put("reservation_id", null);
        payload.put("allocation_id", null);
        payload.put("migration_run_id", qualification.getMigrationRunId());
        payload.put("migration_candidate_id", qualification.getCandidateId());
        payload.put("migration_qualification_id", qualification.getQualificationId());
        payload.put("source_system", qualification.getSourceSystem());
        payload.put("source_type", qualification.getSourceType());
        payload.put("source_id", qualification.getSourceId());
        payload.put("source_snapshot_hash", qualification.getSourceSnapshotHash());
        payload.put("opening_driver", "INVENTORY_MIGRATION_QUALIFICATION");
        payload.put("migration_stage", "MIGRATED");
        payload.put("verification_ref", command.getEvidenceRef());
        String idempotency = "migration-opening:" + qualification.getQualificationId() + ":event:v4";
        outboxAppender.append(AppendDomainEventCommand.builder().eventId(deterministicUuid(tenantId + "|" + idempotency))
                .eventType("inventory.stock.changed").schemaVersion(4).sourceSystem("cloudmold-inventory")
                .tenantId(tenantId).aggregateType("inventory_balance_v3").aggregateId(target.getBalanceId())
                .aggregateVersion(1L).eventSequence((short) 1).occurredAt(command.getOccurredAt())
                .correlationId(command.getCorrelationId()).causationId(command.getCausationId())
                .idempotencyKey(idempotency).payload(payload)
                .headers(Map.of("migration_run_id", qualification.getMigrationRunId(),
                        "migration_qualification_id", qualification.getQualificationId(),
                        "ledger_transaction_id", transaction.getLedgerTransactionId(),
                        "movement_group_id", movementGroupId)).destination("lakehouse").build());
    }

    private InventoryMigrationOperationDO requireOperation(Long tenantId, Long operationId) {
        require(operationId != null && operationId > 0, "failed to resolve Inventory migration operation");
        InventoryMigrationOperationDO operation = migrationMapper.selectOperationForUpdate(tenantId, operationId);
        require(operation != null, "Inventory migration operation disappeared");
        return operation;
    }

    private static InventoryMigrationQualificationResult toResult(InventoryMigrationQualificationDO row) {
        return InventoryMigrationQualificationResult.builder().operationId(row.getQualificationOperationId())
                .qualificationId(row.getQualificationId()).migrationRunId(row.getMigrationRunId())
                .candidateId(row.getCandidateId()).sourceSnapshotHash(row.getSourceSnapshotHash())
                .status(row.getStatus()).ownerType(row.getOwnerType()).ownerId(row.getOwnerId())
                .canonicalSkuId(row.getCanonicalSkuId()).warehouseSourceMappingId(row.getWarehouseSourceMappingId())
                .warehouseId(row.getWarehouseId()).locationId(row.getLocationId())
                .lotTrackingPolicy(row.getLotTrackingPolicy()).lotId(row.getLotId())
                .stockStatus(row.getStockStatus()).qualityStatus(row.getQualityStatus())
                .baseUomCode(row.getBaseUomCode()).sourceOnHandQuantity(row.getSourceOnHandQuantity())
                .resolvedBlockerCodes(JsonUtils.parseArray(row.getResolvedBlockerCodes(), String.class))
                .openingOperationId(row.getOpeningOperationId()).targetBalanceId(row.getTargetBalanceId())
                .ledgerTransactionId(row.getLedgerTransactionId()).bridgeId(row.getBridgeId())
                .version(row.getVersion()).qualifiedAt(toInstant(row.getQualifiedAt()))
                .migratedAt(toInstant(row.getMigratedAt())).duplicate(false).build();
    }

    private static InventoryMigrationQualificationCommand normalizeQualification(
            InventoryMigrationQualificationCommand command) {
        require(command != null, "Inventory migration qualification command is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        command.setMigrationRunId(requireUuid(command.getMigrationRunId(), "migrationRunId"));
        command.setCandidateId(requireUuid(command.getCandidateId(), "candidateId"));
        command.setExpectedSourceSnapshotHash(requireHash(command.getExpectedSourceSnapshotHash()));
        command.setWarehouseId(requireUuid(command.getWarehouseId(), "warehouseId"));
        command.setLocationId(requireUuid(command.getLocationId(), "locationId"));
        command.setLotTrackingPolicy(upper(command.getLotTrackingPolicy()));
        if (command.getLotId() != null) command.setLotId(requireUuid(command.getLotId(), "lotId"));
        requireText(command.getPolicyVersion(), "policyVersion", 32);
        requireText(command.getEvidenceRef(), "evidenceRef", 256);
        command.setCorrelationId(requireUuid(command.getCorrelationId(), "correlationId"));
        if (command.getCausationId() != null) command.setCausationId(requireUuid(command.getCausationId(), "causationId"));
        require(command.getOccurredAt() != null, "occurredAt is required");
        return command;
    }

    private static InventoryMigrationOpeningCommand normalizeOpening(InventoryMigrationOpeningCommand command) {
        require(command != null, "Inventory migration opening command is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        command.setQualificationId(requireUuid(command.getQualificationId(), "qualificationId"));
        require(command.getExpectedVersion() != null && command.getExpectedVersion() == 1L,
                "expectedVersion must be 1 for a migration opening");
        command.setExpectedSourceSnapshotHash(requireHash(command.getExpectedSourceSnapshotHash()));
        requireText(command.getEvidenceRef(), "evidenceRef", 256);
        command.setCorrelationId(requireUuid(command.getCorrelationId(), "correlationId"));
        if (command.getCausationId() != null) command.setCausationId(requireUuid(command.getCausationId(), "causationId"));
        require(command.getOccurredAt() != null, "occurredAt is required");
        return command;
    }

    private static String requestHash(Long tenantId, String commandType, Object command) {
        return DigestUtil.sha256Hex(tenantId + "|" + commandType + "|" + JsonUtils.toJsonString(command));
    }

    private static String requireHash(String value) {
        require(value != null && value.matches("^[0-9a-f]{64}$"),
                "expectedSourceSnapshotHash must be a lowercase SHA-256");
        return value;
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

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal scaled(BigDecimal value) {
        require(value != null, "quantity is required");
        try {
            return value.setScale(6, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("quantity supports at most 6 fractional digits", error);
        }
    }

    private static String decimal(BigDecimal value) {
        return scaled(value).toPlainString();
    }

    private static String toInstantString(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC).toString();
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static void requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank() && value.length() <= maxLength,
                field + " is required and too long");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private record SourceSnapshot(InventoryBalanceDO balance, InventoryLegacySourceFactDO sourceFact,
                                  int activeReservationCount, BigDecimal activeReservationQuantity,
                                  String snapshotHash) {
    }
}
