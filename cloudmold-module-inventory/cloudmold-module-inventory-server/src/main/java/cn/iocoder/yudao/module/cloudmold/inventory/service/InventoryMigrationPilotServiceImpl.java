package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryMigrationPilotApi;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.*;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantOwnerValidationApi;
import cn.iocoder.yudao.module.cloudmold.merchant.api.SourceMappingQueryApi;
import cn.iocoder.yudao.module.cloudmold.merchant.api.SourceMappingView;
import cn.iocoder.yudao.module.cloudmold.merchant.api.SourceReference;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.*;
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

@Service
@RequiredArgsConstructor
public class InventoryMigrationPilotServiceImpl implements InventoryMigrationPilotApi {

    static final String BATCH_EVENT = "inventory.migration.pilot_batch_status_changed";
    static final String ITEM_EVENT = "inventory.migration.pilot_item_status_changed";
    private static final BigDecimal ZERO = new BigDecimal("0.000000");
    private static final Set<String> APPROVAL_ROLES = Set.of("DATA_OWNER", "CHANGE_MANAGER");
    private static final Set<String> RESOLVABLE_PRODUCTION_BLOCKERS = Set.of(
            "OWNER_TYPE_MISSING", "WAREHOUSE_UNRESOLVED", "LOCATION_UNRESOLVED",
            "LOT_POLICY_UNPROVEN", "TARGET_DIMENSION_UNRESOLVED");

    private final InventoryMigrationStoreMapper migrationMapper;
    private final InventoryMigrationPilotMapper pilotMapper;
    private final InventoryV3BalanceMapper v3BalanceMapper;
    private final InventoryLotMapper lotMapper;
    private final MerchantOwnerValidationApi merchantOwnerValidationApi;
    private final SourceMappingQueryApi merchantSourceMappingQueryApi;
    private final CatalogSkuValidationApi catalogSkuValidationApi;
    private final WarehouseSourceMappingQueryApi warehouseSourceMappingQueryApi;
    private final WarehouseReferenceValidationApi warehouseValidationApi;
    private final OutboxAppender outboxAppender;
    private final InventoryMigrationPilotProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PilotBatchResult freeze(FreezePilotBatchCommand rawCommand, Long requesterId) {
        FreezePilotBatchCommand command = normalizeFreeze(rawCommand);
        requireActor(requesterId, "requesterId");
        requirePilotEnvironment(false);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = utcNow();
        String requestHash = requestHash(tenantId, "FREEZE_PILOT_V1", requesterId, command);
        OperationAttempt attempt = beginOperation(tenantId, "FREEZE_PILOT_V1", command.getIdempotencyKey(),
                command.getSourceEventId(), requestHash, now);
        if (attempt.replay() != null) return attempt.replay();

        require(command.getItems().size() <= effectiveMaxItems(), "pilot batch exceeds the server item limit");
        validateWindow(command, now);
        validateWatermarks(command, now);
        InventoryMigrationRunDO run = migrationMapper.selectRun(tenantId, command.getMigrationRunId());
        require(run != null && "ASSESSED".equals(run.getStatus()), "pilot batch requires an immutable assessed run");

        List<PreparedItem> prepared = new ArrayList<>();
        Set<String> targetGrains = new HashSet<>();
        for (PilotItemEvidence evidence : command.getItems()) {
            InventoryMigrationCandidateDO candidate = migrationMapper.selectCandidateForUpdate(
                    tenantId, evidence.getCandidateId());
            require(candidate != null && Objects.equals(command.getMigrationRunId(), candidate.getMigrationRunId()),
                    "pilot item does not belong to the requested assessment run");
            prepared.add(prepareItem(tenantId, candidate, evidence, command.getOccurredAt(), now, targetGrains));
        }
        prepared.sort(Comparator.comparing(value -> value.item().getLegacyBalanceId()));
        String warehouseId = oneValue(prepared, value -> value.item().getWarehouseId(), "warehouse");
        String baseUomCode = oneValue(prepared, value -> value.item().getBaseUomCode(), "base UOM");
        BigDecimal expectedOnHand = prepared.stream().map(value -> value.item().getSourceOnHandQuantity())
                .reduce(ZERO, BigDecimal::add).setScale(6, RoundingMode.UNNECESSARY);
        String policyHash = policyHash(command);
        List<String> itemHashes = new ArrayList<>();
        for (int index = 0; index < prepared.size(); index++) {
            InventoryMigrationPilotItemDO item = prepared.get(index).item();
            item.setOrdinal(index + 1);
            item.setItemScopeHash(itemScopeHash(item));
            itemHashes.add(item.getItemScopeHash());
        }
        String manifestHash = manifestHash(tenantId, command, run, policyHash, itemHashes);
        String batchId = UUID.randomUUID().toString();
        InventoryMigrationPilotBatchDO batch = new InventoryMigrationPilotBatchDO()
                .setBatchId(batchId).setTenantId(tenantId).setMigrationRunId(command.getMigrationRunId())
                .setEnvironment("PRODUCTION").setSourceSystem("CLOUDMOLD_INVENTORY_V1")
                .setSourceType("BALANCE").setSourceClassification("PRODUCTION_HISTORY")
                .setPolicyVersion(command.getPolicyVersion()).setPolicyHash(policyHash).setManifestHash(manifestHash)
                .setExpectedItemCount(prepared.size()).setExpectedOnHandQuantity(expectedOnHand)
                .setWarehouseId(warehouseId).setBaseUomCode(baseUomCode)
                .setSourceWatermarkKind(command.getSourceWatermarkKind())
                .setSourceWatermarkValue(command.getSourceWatermarkValue())
                .setSourceWatermarkCapturedAt(toDateTime(command.getSourceWatermarkCapturedAt()))
                .setTargetWatermarkKind(command.getTargetWatermarkKind())
                .setTargetWatermarkValue(command.getTargetWatermarkValue())
                .setTargetWatermarkAppliedAt(toDateTime(command.getTargetWatermarkAppliedAt()))
                .setMaxLagSeconds(command.getMaxLagSeconds())
                .setExecutionWindowStart(toDateTime(command.getExecutionWindowStart()))
                .setExecutionWindowEnd(toDateTime(command.getExecutionWindowEnd()))
                .setChangeTicket(command.getChangeTicket()).setPurpose(command.getPurpose())
                .setRequesterId(requesterId).setApprovalCount(0).setStatus("FROZEN").setVersion(1L)
                .setFrozenAt(now).setCreatedAt(now).setUpdatedAt(now);
        require(pilotMapper.insertBatch(batch) == 1, "failed to persist the frozen pilot batch");
        for (PreparedItem value : prepared) {
            value.item().setBatchId(batchId);
            require(pilotMapper.insertItem(value.item()) == 1, "failed to persist a frozen pilot item");
        }
        insertCheckpoint(batch, requesterId, "FROZEN", Map.of("manifest_hash", manifestHash), now);
        appendBatchEvent(batch, List.of(), command.getOccurredAt(), command.getCorrelationId(),
                command.getCausationId(), command.getIdempotencyKey());
        for (PreparedItem value : prepared) appendItemEvent(batch, value.item(), command.getOccurredAt(),
                command.getCorrelationId(), command.getCausationId(), command.getIdempotencyKey());

        PilotBatchResult result = toResult(attempt.operationId(), batch,
                prepared.stream().map(PreparedItem::item).toList(), List.of());
        completeOperation(tenantId, attempt.operationId(), batch.getMigrationRunId(), result, now);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PilotBatchResult approve(String rawBatchId, ApprovePilotBatchCommand rawCommand, Long approverId) {
        String batchId = requireUuid(rawBatchId, "batchId");
        ApprovePilotBatchCommand command = normalizeApproval(rawCommand);
        requireActor(approverId, "approverId");
        requirePilotEnvironment(false);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = utcNow();
        String requestHash = requestHash(tenantId, "APPROVE_PILOT_V1", approverId,
                Map.of("batch_id", batchId, "command", command));
        OperationAttempt attempt = beginOperation(tenantId, "APPROVE_PILOT_V1", command.getIdempotencyKey(),
                command.getSourceEventId(), requestHash, now);
        if (attempt.replay() != null) return attempt.replay();

        InventoryMigrationPilotBatchDO batch = requireBatchForUpdate(tenantId, batchId);
        require(Objects.equals(command.getExpectedVersion(), batch.getVersion()),
                "pilot batch version changed before approval");
        require(Set.of("FROZEN", "PARTIALLY_APPROVED").contains(batch.getStatus()),
                "pilot batch is not awaiting approval");
        require(!Objects.equals(batch.getRequesterId(), approverId),
                "pilot requester cannot approve the same batch");
        require(toDateTime(command.getExpiresAt()).isAfter(now)
                        && !toDateTime(command.getExpiresAt()).isAfter(batch.getExecutionWindowEnd()),
                "pilot approval expiry must be after now and no later than the execution window end");
        List<InventoryMigrationPilotApprovalDO> approvals = pilotMapper.selectApprovalsForUpdate(tenantId, batchId);
        require(approvals.stream().noneMatch(value -> value.getApprovalRole().equals(command.getApprovalRole())),
                "pilot approval role is already bound");
        require(approvals.stream().noneMatch(value -> Objects.equals(value.getApproverId(), approverId)),
                "one actor cannot satisfy both pilot approval roles");
        InventoryMigrationPilotApprovalDO approval = new InventoryMigrationPilotApprovalDO()
                .setApprovalId(UUID.randomUUID().toString()).setTenantId(tenantId).setBatchId(batchId)
                .setApprovalRole(command.getApprovalRole()).setApproverId(approverId)
                .setScopeHash(batch.getManifestHash()).setPolicyHash(batch.getPolicyHash())
                .setEvidenceRef(command.getEvidenceRef()).setIdempotencyKey(command.getIdempotencyKey())
                .setRequestHash(requestHash).setStatus("APPROVED").setVersion(1L)
                .setApprovedAt(now).setExpiresAt(toDateTime(command.getExpiresAt())).setCreatedAt(now);
        require(pilotMapper.insertApproval(approval) == 1, "failed to persist pilot approval");
        approvals = new ArrayList<>(approvals);
        approvals.add(approval);
        int approvalCount = approvals.size();
        require(approvalCount <= 2, "pilot batch has too many active approvals");
        String nextStatus = approvalCount == 2 ? "APPROVED" : "PARTIALLY_APPROVED";
        LocalDateTime approvedAt = approvalCount == 2 ? now : null;
        require(pilotMapper.markApproved(tenantId, batchId, batch.getVersion(), approvalCount,
                nextStatus, approvedAt, now) == 1, "pilot batch approval state changed concurrently");
        batch.setApprovalCount(approvalCount).setStatus(nextStatus).setVersion(batch.getVersion() + 1)
                .setApprovedAt(approvedAt).setUpdatedAt(now);
        List<InventoryMigrationPilotItemDO> items = pilotMapper.selectItemsForUpdate(tenantId, batchId);
        if (approvalCount == 2) {
            require(pilotMapper.markItemsApproved(tenantId, batchId, now) == batch.getExpectedItemCount(),
                    "pilot items did not advance to APPROVED as one denominator");
            items.forEach(item -> item.setStatus("APPROVED").setVersion(2L).setUpdatedAt(now));
        }
        insertCheckpoint(batch, approverId, nextStatus,
                Map.of("approval_role", command.getApprovalRole(), "approval_count", approvalCount), now);
        appendBatchEvent(batch, approvals, command.getOccurredAt(), command.getCorrelationId(),
                command.getCausationId(), command.getIdempotencyKey());
        if (approvalCount == 2) for (InventoryMigrationPilotItemDO item : items) {
            appendItemEvent(batch, item, command.getOccurredAt(), command.getCorrelationId(),
                    command.getCausationId(), command.getIdempotencyKey());
        }
        PilotBatchResult result = toResult(attempt.operationId(), batch, items, approvals);
        completeOperation(tenantId, attempt.operationId(), batch.getMigrationRunId(), result, now);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PilotBatchResult admit(String rawBatchId, AdmitPilotBatchCommand rawCommand, Long executorId) {
        String batchId = requireUuid(rawBatchId, "batchId");
        AdmitPilotBatchCommand command = normalizeAdmission(rawCommand);
        requireActor(executorId, "executorId");
        requirePilotEnvironment(true);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = utcNow();
        String requestHash = requestHash(tenantId, "ADMIT_PILOT_V1", executorId,
                Map.of("batch_id", batchId, "command", command));
        OperationAttempt attempt = beginOperation(tenantId, "ADMIT_PILOT_V1", command.getIdempotencyKey(),
                command.getSourceEventId(), requestHash, now);
        if (attempt.replay() != null) return attempt.replay();

        InventoryMigrationPilotBatchDO batch = requireBatchForUpdate(tenantId, batchId);
        require("APPROVED".equals(batch.getStatus()) && Objects.equals(command.getExpectedVersion(), batch.getVersion()),
                "pilot batch is not at the expected APPROVED version");
        require(!now.isBefore(batch.getExecutionWindowStart()) && now.isBefore(batch.getExecutionWindowEnd()),
                "pilot admission is outside the frozen execution window");
        require(Duration.between(batch.getTargetWatermarkAppliedAt(), now).getSeconds() <= batch.getMaxLagSeconds(),
                "pilot target watermark is stale at admission");
        List<InventoryMigrationPilotApprovalDO> approvals = pilotMapper.selectApprovalsForUpdate(tenantId, batchId);
        require(approvals.size() == 2 && approvals.stream().map(InventoryMigrationPilotApprovalDO::getApprovalRole)
                        .collect(Collectors.toSet()).equals(APPROVAL_ROLES),
                "pilot admission requires both approval roles");
        require(approvals.stream().allMatch(value -> "APPROVED".equals(value.getStatus())
                        && value.getExpiresAt().isAfter(now)
                        && Objects.equals(value.getScopeHash(), batch.getManifestHash())
                        && Objects.equals(value.getPolicyHash(), batch.getPolicyHash())),
                "pilot approval is expired or no longer binds the frozen scope and policy");
        require(approvals.stream().noneMatch(value -> Objects.equals(value.getApproverId(), executorId)),
                "pilot approver cannot execute admission for the same batch");
        require(!Objects.equals(batch.getRequesterId(), executorId),
                "pilot requester cannot execute admission for the same batch");
        List<InventoryMigrationPilotItemDO> items = pilotMapper.selectItemsForUpdate(tenantId, batchId);
        require(items.size() == batch.getExpectedItemCount()
                        && items.stream().allMatch(item -> "APPROVED".equals(item.getStatus()) && item.getVersion() == 2L),
                "pilot item denominator is not fully approved");
        BigDecimal itemQuantity = items.stream().map(InventoryMigrationPilotItemDO::getSourceOnHandQuantity)
                .reduce(ZERO, BigDecimal::add).setScale(6, RoundingMode.UNNECESSARY);
        require(itemQuantity.compareTo(batch.getExpectedOnHandQuantity()) == 0,
                "pilot approved quantity denominator changed");
        for (InventoryMigrationPilotItemDO item : items) revalidateItem(tenantId, item, command.getOccurredAt());

        require(pilotMapper.markAdmitted(tenantId, batchId, batch.getVersion(), executorId, now) == 1,
                "pilot batch admission state changed concurrently");
        require(pilotMapper.markItemsAdmitted(tenantId, batchId, now) == batch.getExpectedItemCount(),
                "pilot items did not advance to ADMISSION_PASSED as one denominator");
        batch.setStatus("ADMISSION_PASSED").setExecutorId(executorId).setVersion(batch.getVersion() + 1)
                .setAdmittedAt(now).setUpdatedAt(now);
        items.forEach(item -> item.setStatus("ADMISSION_PASSED").setVersion(3L).setUpdatedAt(now));
        insertCheckpoint(batch, executorId, "ADMISSION_PASSED",
                Map.of("evidence_ref", command.getEvidenceRef(), "execution_available", false,
                        "cutover_ready", false), now);
        appendBatchEvent(batch, approvals, command.getOccurredAt(), command.getCorrelationId(),
                command.getCausationId(), command.getIdempotencyKey());
        for (InventoryMigrationPilotItemDO item : items) appendItemEvent(batch, item, command.getOccurredAt(),
                command.getCorrelationId(), command.getCausationId(), command.getIdempotencyKey());
        PilotBatchResult result = toResult(attempt.operationId(), batch, items, approvals);
        completeOperation(tenantId, attempt.operationId(), batch.getMigrationRunId(), result, now);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public PilotBatchResult requireBatch(String rawBatchId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String batchId = requireUuid(rawBatchId, "batchId");
        InventoryMigrationPilotBatchDO batch = pilotMapper.selectBatch(tenantId, batchId);
        require(batch != null, "Inventory migration pilot batch does not exist");
        return toResult(null, batch, pilotMapper.selectItems(tenantId, batchId),
                pilotMapper.selectApprovals(tenantId, batchId));
    }

    private PreparedItem prepareItem(Long tenantId, InventoryMigrationCandidateDO candidate,
                                     PilotItemEvidence evidence, Instant effectiveAt, LocalDateTime now,
                                     Set<String> targetGrains) {
        require("UNCLASSIFIED".equals(candidate.getSourceClassification()),
                "pilot items must be production-history candidates, never controlled test sources");
        List<String> blockers = JsonUtils.parseArray(candidate.getReasonCodes(), String.class);
        require(blockers != null && !blockers.isEmpty() && RESOLVABLE_PRODUCTION_BLOCKERS.containsAll(blockers),
                "pilot item contains a blocker that evidence approval cannot resolve");
        require(Objects.equals(candidate.getLegacySnapshotHash(), evidence.getExpectedSourceSnapshotHash()),
                "pilot item snapshot does not match its assessment");
        require(candidate.getInitialSourceEventId() != null && !candidate.getInitialSourceEventId().isBlank(),
                "pilot production source fact is missing");
        SourceSnapshot source = lockAndVerifySource(tenantId, candidate);
        require(source.activeReservationCount() == 0 && source.activeReservationQuantity().signum() == 0
                        && scaled(source.balance().getReservedQuantity()).signum() == 0
                        && scaled(source.balance().getInTransitQuantity()).signum() == 0
                        && scaled(source.balance().getOnHandQuantity()).signum() > 0,
                "pilot scope only supports positive on-hand with zero reservations and in-transit quantity");
        String ownerId = requireUuid(candidate.getLegacyOwnerId(), "ownerId");
        String skuId = requireUuid(candidate.getLegacyCanonicalSkuId(), "canonicalSkuId");
        merchantOwnerValidationApi.requireActiveMerchant(ownerId);
        catalogSkuValidationApi.requireActiveSku(skuId);

        SourceMappingView ownerMapping = merchantSourceMappingQueryApi.resolveActive(new SourceReference()
                .setSourceSystem(evidence.getOwnerSourceSystem()).setSourceType(evidence.getOwnerSourceType())
                .setSourceId(evidence.getOwnerSourceId()).setEffectiveAt(effectiveAt));
        require(ownerMapping != null && "ACTIVE".equals(ownerMapping.getStatus())
                        && "MERCHANT".equals(ownerMapping.getTargetType())
                        && Objects.equals(ownerId, ownerMapping.getTargetId()) && ownerMapping.getVersion() >= 1,
                "pilot owner source mapping does not resolve to the assessed active Merchant");
        requireEvidence(ownerMapping.getVerificationRef(), "ownerMappingEvidenceRef");
        WarehouseSourceMappingView warehouseMapping = warehouseSourceMappingQueryApi.resolveActive(
                new WarehouseSourceReference(evidence.getWarehouseSourceSystem(), evidence.getWarehouseSourceType(),
                        evidence.getWarehouseSourceId()), effectiveAt);
        require(warehouseMapping != null && "WAREHOUSE".equals(warehouseMapping.getCanonicalType())
                        && Objects.equals(evidence.getWarehouseSourceId(), candidate.getLegacyWarehouseId()),
                "pilot warehouse mapping is not bound to the assessed legacy warehouse");
        WarehouseSourceMappingView locationMapping = warehouseSourceMappingQueryApi.resolveActive(
                new WarehouseSourceReference(evidence.getLocationSourceSystem(), evidence.getLocationSourceType(),
                        evidence.getLocationSourceId()), effectiveAt);
        require(locationMapping != null && "LOCATION".equals(locationMapping.getCanonicalType())
                        && Objects.equals(warehouseMapping.getWarehouseId(), locationMapping.getWarehouseId())
                        && locationMapping.getLocationId() != null,
                "pilot location mapping does not resolve inside the target Warehouse");
        warehouseValidationApi.requireActiveLocation(warehouseMapping.getWarehouseId(), locationMapping.getLocationId());

        require(Objects.equals(evidence.getSourceUomCode(), candidate.getBaseUomCode())
                        && BigDecimal.ONE.compareTo(evidence.getUomConversionRatio()) == 0,
                "pilot scope supports only an exact base-UOM match with conversion ratio 1");
        String lotPolicy = evidence.getLotTrackingPolicy();
        if ("NOT_TRACKED".equals(lotPolicy)) {
            require(evidence.getLotId() == null && evidence.getLotMappingId() == null
                            && evidence.getLotMappingVersion() == null,
                    "NOT_TRACKED pilot evidence cannot carry a Lot or Lot mapping");
        } else {
            require("TRACKED".equals(lotPolicy), "lotTrackingPolicy must be NOT_TRACKED or TRACKED");
            InventoryLotDO lot = lotMapper.selectCurrent(tenantId, evidence.getLotId());
            require(lot != null && "ACTIVE".equals(lot.getStatus()) && "MERCHANT".equals(lot.getOwnerType())
                            && Objects.equals(ownerId, lot.getOwnerId()) && Objects.equals(skuId, lot.getCanonicalSkuId()),
                    "pilot Lot is not ACTIVE for the exact owner and SKU");
        }
        require(migrationMapper.selectResolvedBridgeIdForLegacyBalanceForUpdate(tenantId,
                candidate.getLegacyBalanceId()) == null, "pilot source already has a resolved migration bridge");
        require(migrationMapper.selectQualificationBySourceForUpdate(tenantId,
                candidate.getLegacyBalanceId()) == null, "pilot source already has a migration qualification");
        InventoryV3BalanceDO target = v3BalanceMapper.selectDimensionForUpdate(tenantId, "MERCHANT", ownerId, skuId,
                warehouseMapping.getWarehouseId(), locationMapping.getLocationId(), evidence.getLotId(),
                candidate.getStockStatus(), candidate.getQualityStatus());
        require(target == null, "pilot target v3 grain must not already exist");
        String targetGrain = String.join("|", ownerId, skuId, warehouseMapping.getWarehouseId(),
                locationMapping.getLocationId(), Objects.toString(evidence.getLotId(), ""),
                candidate.getStockStatus(), candidate.getQualityStatus());
        require(targetGrains.add(targetGrain), "pilot manifest contains a duplicate target v3 grain");

        InventoryMigrationPilotItemDO item = new InventoryMigrationPilotItemDO()
                .setItemId(UUID.randomUUID().toString()).setTenantId(tenantId).setCandidateId(candidate.getCandidateId())
                .setLegacyBalanceId(candidate.getLegacyBalanceId()).setSourceVersion(candidate.getLegacyBalanceVersion())
                .setSourceUpdatedAt(candidate.getSourceUpdatedAt()).setSourceSnapshotHash(candidate.getLegacySnapshotHash())
                .setSourceOnHandQuantity(scaled(candidate.getSourceOnHandQuantity()))
                .setSourceReservedQuantity(scaled(candidate.getSourceReservedQuantity()))
                .setSourceInTransitQuantity(scaled(candidate.getSourceInTransitQuantity()))
                .setActiveReservationCount(candidate.getActiveReservationCount())
                .setActiveReservationQuantity(scaled(candidate.getActiveReservationQuantity()))
                .setOwnerType("MERCHANT").setOwnerId(ownerId)
                .setOwnerSourceSystem(evidence.getOwnerSourceSystem()).setOwnerSourceType(evidence.getOwnerSourceType())
                .setOwnerSourceId(evidence.getOwnerSourceId()).setOwnerMappingId(ownerMapping.getMappingId())
                .setOwnerMappingVersion(ownerMapping.getVersion()).setOwnerMappingEvidenceRef(ownerMapping.getVerificationRef())
                .setCanonicalSkuId(skuId).setSkuMappingId(evidence.getSkuMappingId())
                .setSkuMappingVersion(evidence.getSkuMappingVersion()).setSkuMappingEvidenceRef(evidence.getSkuMappingEvidenceRef())
                .setSourceUomCode(evidence.getSourceUomCode()).setBaseUomCode(candidate.getBaseUomCode())
                .setUomConversionRatio(evidence.getUomConversionRatio()).setUomEvidenceRef(evidence.getUomEvidenceRef())
                .setWarehouseSourceSystem(evidence.getWarehouseSourceSystem())
                .setWarehouseSourceType(evidence.getWarehouseSourceType()).setWarehouseSourceId(evidence.getWarehouseSourceId())
                .setWarehouseSourceMappingId(warehouseMapping.getMappingId())
                .setWarehouseMappingVersion(warehouseMapping.getVersion())
                .setWarehouseMappingEvidenceRef(evidence.getWarehouseMappingEvidenceRef())
                .setWarehouseId(warehouseMapping.getWarehouseId())
                .setLocationSourceSystem(evidence.getLocationSourceSystem())
                .setLocationSourceType(evidence.getLocationSourceType()).setLocationSourceId(evidence.getLocationSourceId())
                .setLocationSourceMappingId(locationMapping.getMappingId()).setLocationMappingVersion(locationMapping.getVersion())
                .setLocationMappingEvidenceRef(evidence.getLocationMappingEvidenceRef())
                .setLocationId(locationMapping.getLocationId()).setZoneId(locationMapping.getZoneId())
                .setLotTrackingPolicy(lotPolicy).setLotId(evidence.getLotId()).setLotMappingId(evidence.getLotMappingId())
                .setLotMappingVersion(evidence.getLotMappingVersion()).setLotEvidenceRef(evidence.getLotEvidenceRef())
                .setStockStatus(candidate.getStockStatus()).setQualityStatus(candidate.getQualityStatus())
                .setAuthoritativeRecordRef(evidence.getAuthoritativeRecordRef())
                .setQuantityEvidenceRef(evidence.getQuantityEvidenceRef()).setSourceCdcPosition(evidence.getSourceCdcPosition())
                .setSourceExtractedAt(toDateTime(evidence.getSourceExtractedAt()))
                .setTargetBalanceAbsent(true).setBridgeAbsent(true).setStatus("FROZEN").setVersion(1L)
                .setCreatedAt(now).setUpdatedAt(now);
        return new PreparedItem(item);
    }

    private void revalidateItem(Long tenantId, InventoryMigrationPilotItemDO item, Instant effectiveAt) {
        InventoryMigrationCandidateDO candidate = migrationMapper.selectCandidateForUpdate(tenantId, item.getCandidateId());
        require(candidate != null && Objects.equals(candidate.getLegacySnapshotHash(), item.getSourceSnapshotHash()),
                "pilot assessment candidate changed before admission");
        SourceSnapshot source = lockAndVerifySource(tenantId, candidate);
        require(Objects.equals(source.snapshotHash(), item.getSourceSnapshotHash()),
                "pilot legacy source changed after freeze");
        require(source.activeReservationCount() == 0 && source.activeReservationQuantity().signum() == 0,
                "pilot legacy source acquired a reservation after freeze");
        merchantOwnerValidationApi.requireActiveMerchant(item.getOwnerId());
        catalogSkuValidationApi.requireActiveSku(item.getCanonicalSkuId());
        SourceMappingView ownerMapping = merchantSourceMappingQueryApi.resolveActive(new SourceReference()
                .setSourceSystem(item.getOwnerSourceSystem()).setSourceType(item.getOwnerSourceType())
                .setSourceId(item.getOwnerSourceId()).setEffectiveAt(effectiveAt));
        require(ownerMapping != null && Objects.equals(ownerMapping.getMappingId(), item.getOwnerMappingId())
                        && Objects.equals(ownerMapping.getVersion(), item.getOwnerMappingVersion())
                        && Objects.equals(ownerMapping.getVerificationRef(), item.getOwnerMappingEvidenceRef()),
                "pilot owner mapping changed after freeze");
        requireEvidence(ownerMapping.getVerificationRef(), "ownerMappingEvidenceRef");
        WarehouseSourceMappingView warehouseMapping = warehouseSourceMappingQueryApi.resolveActive(
                new WarehouseSourceReference(item.getWarehouseSourceSystem(), item.getWarehouseSourceType(),
                        item.getWarehouseSourceId()), effectiveAt);
        WarehouseSourceMappingView locationMapping = warehouseSourceMappingQueryApi.resolveActive(
                new WarehouseSourceReference(item.getLocationSourceSystem(), item.getLocationSourceType(),
                        item.getLocationSourceId()), effectiveAt);
        require(warehouseMapping != null && locationMapping != null
                        && Objects.equals(warehouseMapping.getMappingId(), item.getWarehouseSourceMappingId())
                        && Objects.equals(warehouseMapping.getVersion(), item.getWarehouseMappingVersion())
                        && Objects.equals(locationMapping.getMappingId(), item.getLocationSourceMappingId())
                        && Objects.equals(locationMapping.getVersion(), item.getLocationMappingVersion()),
                "pilot Warehouse or Location mapping changed after freeze");
        warehouseValidationApi.requireActiveLocation(item.getWarehouseId(), item.getLocationId());
        if ("TRACKED".equals(item.getLotTrackingPolicy())) {
            InventoryLotDO lot = lotMapper.selectCurrent(tenantId, item.getLotId());
            require(lot != null && "ACTIVE".equals(lot.getStatus()), "pilot Lot is no longer ACTIVE");
        }
        require(migrationMapper.selectResolvedBridgeIdForLegacyBalanceForUpdate(tenantId,
                item.getLegacyBalanceId()) == null, "pilot source gained a resolved bridge after freeze");
        require(migrationMapper.selectQualificationBySourceForUpdate(tenantId,
                item.getLegacyBalanceId()) == null, "pilot source gained a qualification after freeze");
        require(v3BalanceMapper.selectDimensionForUpdate(tenantId, item.getOwnerType(), item.getOwnerId(),
                item.getCanonicalSkuId(), item.getWarehouseId(), item.getLocationId(), item.getLotId(),
                item.getStockStatus(), item.getQualityStatus()) == null,
                "pilot target v3 grain appeared after freeze");
    }

    private SourceSnapshot lockAndVerifySource(Long tenantId, InventoryMigrationCandidateDO candidate) {
        InventoryBalanceDO balance = migrationMapper.selectLegacyBalanceForUpdate(tenantId,
                candidate.getLegacyBalanceId());
        require(balance != null, "pilot legacy balance disappeared");
        InventoryLegacySourceFactDO fact = migrationMapper.selectInitialSourceFact(tenantId, balance.getBalanceId());
        int count = migrationMapper.countActiveReservations(tenantId, balance.getBalanceId());
        BigDecimal quantity = scaled(migrationMapper.sumActiveReservationQuantity(tenantId, balance.getBalanceId()));
        String hash = InventoryMigrationAssessmentServiceImpl.snapshotHash(tenantId, balance, fact, count, quantity);
        require(Objects.equals(hash, candidate.getLegacySnapshotHash()),
                "pilot legacy source changed after assessment");
        return new SourceSnapshot(balance, count, quantity, hash);
    }

    private OperationAttempt beginOperation(Long tenantId, String commandType, String idempotencyKey,
                                            String sourceEventId, String requestHash, LocalDateTime now) {
        String attemptToken = UUID.randomUUID().toString();
        migrationMapper.insertOrResolveCommand(tenantId, idempotencyKey, sourceEventId, commandType,
                requestHash, attemptToken, now);
        Long operationId = migrationMapper.selectLastInsertId();
        InventoryMigrationOperationDO operation = migrationMapper.selectOperationForUpdate(tenantId, operationId);
        require(operation != null, "failed to resolve Inventory migration pilot operation");
        if (attemptToken.equals(operation.getAttemptToken())) return new OperationAttempt(operationId, null);
        require(Objects.equals(requestHash, operation.getRequestHash()),
                "pilot idempotency key or source event conflicts with a different payload");
        require(operation.getStatus() == 10 && operation.getResultJson() != null,
                "existing Inventory migration pilot operation is not complete");
        PilotBatchResult replay = JsonUtils.parseObject(operation.getResultJson(), PilotBatchResult.class);
        replay.setDuplicate(true);
        return new OperationAttempt(operationId, replay);
    }

    private void completeOperation(Long tenantId, Long operationId, String migrationRunId,
                                   PilotBatchResult result, LocalDateTime now) {
        require(migrationMapper.markOperationSucceeded(tenantId, operationId, migrationRunId,
                JsonUtils.toJsonString(result), now) == 1, "pilot operation completion conflict");
    }

    private InventoryMigrationPilotBatchDO requireBatchForUpdate(Long tenantId, String batchId) {
        InventoryMigrationPilotBatchDO batch = pilotMapper.selectBatchForUpdate(tenantId, batchId);
        require(batch != null, "Inventory migration pilot batch does not exist");
        require("PRODUCTION".equals(batch.getEnvironment())
                        && "PRODUCTION_HISTORY".equals(batch.getSourceClassification()),
                "pilot batch environment or source classification is invalid");
        return batch;
    }

    private void insertCheckpoint(InventoryMigrationPilotBatchDO batch, Long actorId, String checkpointType,
                                  Map<String, Object> details, LocalDateTime now) {
        require(pilotMapper.insertCheckpoint(UUID.randomUUID().toString(), batch.getTenantId(), batch.getBatchId(),
                checkpointType, actorId, batch.getVersion(), batch.getManifestHash(), batch.getPolicyHash(),
                batch.getExpectedItemCount(), JsonUtils.toJsonString(details), now) == 1,
                "failed to persist immutable pilot checkpoint");
    }

    private void appendBatchEvent(InventoryMigrationPilotBatchDO batch,
                                  List<InventoryMigrationPilotApprovalDO> approvals,
                                  Instant occurredAt, String correlationId, String causationId, String commandKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pilot_batch_id", batch.getBatchId());
        payload.put("migration_run_id", batch.getMigrationRunId());
        payload.put("environment", batch.getEnvironment());
        payload.put("environment_fingerprint", properties.getEnvironmentFingerprint());
        payload.put("source_system", batch.getSourceSystem());
        payload.put("source_type", batch.getSourceType());
        payload.put("source_classification", batch.getSourceClassification());
        payload.put("manifest_hash", batch.getManifestHash());
        payload.put("policy_version", batch.getPolicyVersion());
        payload.put("policy_hash", batch.getPolicyHash());
        payload.put("expected_item_count", batch.getExpectedItemCount());
        payload.put("expected_on_hand_quantity", decimal(batch.getExpectedOnHandQuantity()));
        payload.put("approved_item_count", batch.getApprovalCount() == 2 ? batch.getExpectedItemCount() : 0);
        payload.put("approved_on_hand_quantity", batch.getApprovalCount() == 2
                ? decimal(batch.getExpectedOnHandQuantity()) : decimal(ZERO));
        payload.put("admitted_item_count", "ADMISSION_PASSED".equals(batch.getStatus())
                ? batch.getExpectedItemCount() : 0);
        payload.put("admitted_on_hand_quantity", "ADMISSION_PASSED".equals(batch.getStatus())
                ? decimal(batch.getExpectedOnHandQuantity()) : decimal(ZERO));
        payload.put("approval_count", batch.getApprovalCount());
        payload.put("approval_roles", approvals.stream().map(InventoryMigrationPilotApprovalDO::getApprovalRole).sorted().toList());
        payload.put("requester_system_user_id", batch.getRequesterId());
        payload.put("approver_system_user_ids", approvals.stream().map(InventoryMigrationPilotApprovalDO::getApproverId).sorted().toList());
        payload.put("executor_system_user_id", batch.getExecutorId());
        payload.put("warehouse_id", batch.getWarehouseId());
        payload.put("base_uom_code", batch.getBaseUomCode());
        payload.put("source_watermark_kind", batch.getSourceWatermarkKind());
        payload.put("source_watermark_value", batch.getSourceWatermarkValue());
        payload.put("source_watermark_captured_at", toInstantString(batch.getSourceWatermarkCapturedAt()));
        payload.put("target_watermark_kind", batch.getTargetWatermarkKind());
        payload.put("target_watermark_value", batch.getTargetWatermarkValue());
        payload.put("target_watermark_applied_at", toInstantString(batch.getTargetWatermarkAppliedAt()));
        payload.put("max_lag_seconds", batch.getMaxLagSeconds());
        payload.put("execution_window_start", toInstantString(batch.getExecutionWindowStart()));
        payload.put("execution_window_end", toInstantString(batch.getExecutionWindowEnd()));
        payload.put("batch_status", batch.getStatus());
        payload.put("execution_available", false);
        payload.put("cutover_ready", false);
        String idempotency = commandKey + ":batch:event:v" + batch.getVersion();
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(deterministicUuid(batch.getTenantId() + "|" + idempotency)).eventType(BATCH_EVENT)
                .schemaVersion(1).sourceSystem("cloudmold-inventory").tenantId(batch.getTenantId())
                .aggregateType("inventory_migration_pilot_batch").aggregateId(batch.getBatchId())
                .aggregateVersion(batch.getVersion()).eventSequence((short) 1).occurredAt(occurredAt)
                .correlationId(correlationId).causationId(causationId).idempotencyKey(idempotency)
                .payload(payload).headers(Map.of("migration_run_id", batch.getMigrationRunId(),
                        "manifest_hash", batch.getManifestHash())).destination("lakehouse").build());
    }

    private void appendItemEvent(InventoryMigrationPilotBatchDO batch, InventoryMigrationPilotItemDO item,
                                 Instant occurredAt, String correlationId, String causationId, String commandKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pilot_batch_id", batch.getBatchId());
        payload.put("pilot_item_id", item.getItemId());
        payload.put("manifest_ordinal", item.getOrdinal());
        payload.put("item_scope_hash", item.getItemScopeHash());
        payload.put("manifest_hash", batch.getManifestHash());
        payload.put("migration_run_id", batch.getMigrationRunId());
        payload.put("assessment_id", item.getCandidateId());
        payload.put("source_system", batch.getSourceSystem());
        payload.put("source_type", batch.getSourceType());
        payload.put("source_id", item.getLegacyBalanceId());
        payload.put("source_classification", batch.getSourceClassification());
        payload.put("source_version", item.getSourceVersion());
        payload.put("source_updated_at", toInstantString(item.getSourceUpdatedAt()));
        payload.put("source_snapshot_hash", item.getSourceSnapshotHash());
        payload.put("source_on_hand_quantity", decimal(item.getSourceOnHandQuantity()));
        payload.put("source_reserved_quantity", decimal(item.getSourceReservedQuantity()));
        payload.put("source_in_transit_quantity", decimal(item.getSourceInTransitQuantity()));
        payload.put("active_reservation_count", item.getActiveReservationCount());
        payload.put("active_reservation_quantity", decimal(item.getActiveReservationQuantity()));
        payload.put("owner_type", item.getOwnerType());
        payload.put("owner_id", item.getOwnerId());
        payload.put("owner_mapping_id", item.getOwnerMappingId());
        payload.put("owner_mapping_version", item.getOwnerMappingVersion());
        payload.put("owner_mapping_evidence_ref", item.getOwnerMappingEvidenceRef());
        payload.put("canonical_sku_id", item.getCanonicalSkuId());
        payload.put("sku_mapping_id", item.getSkuMappingId());
        payload.put("sku_mapping_version", item.getSkuMappingVersion());
        payload.put("sku_mapping_evidence_ref", item.getSkuMappingEvidenceRef());
        payload.put("warehouse_source_mapping_id", item.getWarehouseSourceMappingId());
        payload.put("warehouse_mapping_version", item.getWarehouseMappingVersion());
        payload.put("warehouse_mapping_evidence_ref", item.getWarehouseMappingEvidenceRef());
        payload.put("warehouse_id", item.getWarehouseId());
        payload.put("location_source_mapping_id", item.getLocationSourceMappingId());
        payload.put("location_mapping_version", item.getLocationMappingVersion());
        payload.put("location_mapping_evidence_ref", item.getLocationMappingEvidenceRef());
        payload.put("location_id", item.getLocationId());
        payload.put("zone_id", item.getZoneId());
        payload.put("lot_tracking_policy", item.getLotTrackingPolicy());
        payload.put("lot_id", item.getLotId());
        payload.put("lot_mapping_id", item.getLotMappingId());
        payload.put("lot_mapping_version", item.getLotMappingVersion());
        payload.put("lot_evidence_ref", item.getLotEvidenceRef());
        payload.put("stock_status", item.getStockStatus());
        payload.put("quality_status", item.getQualityStatus());
        payload.put("base_uom_code", item.getBaseUomCode());
        payload.put("uom_conversion_ratio", item.getUomConversionRatio().toPlainString());
        payload.put("uom_evidence_ref", item.getUomEvidenceRef());
        payload.put("authoritative_record_ref", item.getAuthoritativeRecordRef());
        payload.put("quantity_evidence_ref", item.getQuantityEvidenceRef());
        payload.put("source_cdc_position", item.getSourceCdcPosition());
        payload.put("source_extracted_at", toInstantString(item.getSourceExtractedAt()));
        payload.put("target_balance_absent", item.getTargetBalanceAbsent());
        payload.put("bridge_absent", item.getBridgeAbsent());
        payload.put("item_status", item.getStatus());
        payload.put("execution_available", false);
        String idempotency = commandKey + ":item:" + item.getItemId() + ":event:v" + item.getVersion();
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(deterministicUuid(batch.getTenantId() + "|" + idempotency)).eventType(ITEM_EVENT)
                .schemaVersion(1).sourceSystem("cloudmold-inventory").tenantId(batch.getTenantId())
                .aggregateType("inventory_migration_pilot_item").aggregateId(item.getItemId())
                .aggregateVersion(item.getVersion()).eventSequence((short) 1).occurredAt(occurredAt)
                .correlationId(correlationId).causationId(causationId).idempotencyKey(idempotency)
                .payload(payload).headers(Map.of("pilot_batch_id", batch.getBatchId(),
                        "manifest_hash", batch.getManifestHash())).destination("lakehouse").build());
    }

    private static PilotBatchResult toResult(Long operationId, InventoryMigrationPilotBatchDO batch,
                                             List<InventoryMigrationPilotItemDO> items,
                                             List<InventoryMigrationPilotApprovalDO> approvals) {
        return PilotBatchResult.builder().operationId(operationId).batchId(batch.getBatchId())
                .migrationRunId(batch.getMigrationRunId()).environment(batch.getEnvironment())
                .sourceClassification(batch.getSourceClassification()).status(batch.getStatus())
                .manifestHash(batch.getManifestHash()).policyHash(batch.getPolicyHash())
                .expectedItemCount(batch.getExpectedItemCount()).expectedOnHandQuantity(batch.getExpectedOnHandQuantity())
                .approvalCount(batch.getApprovalCount()).requesterId(batch.getRequesterId()).executorId(batch.getExecutorId())
                .version(batch.getVersion()).executionWindowStart(toInstant(batch.getExecutionWindowStart()))
                .executionWindowEnd(toInstant(batch.getExecutionWindowEnd()))
                .items(items.stream().map(item -> PilotItemResult.builder().itemId(item.getItemId())
                        .ordinal(item.getOrdinal()).candidateId(item.getCandidateId())
                        .legacyBalanceId(item.getLegacyBalanceId()).sourceSnapshotHash(item.getSourceSnapshotHash())
                        .ownerId(item.getOwnerId()).canonicalSkuId(item.getCanonicalSkuId())
                        .warehouseId(item.getWarehouseId()).locationId(item.getLocationId())
                        .lotTrackingPolicy(item.getLotTrackingPolicy()).lotId(item.getLotId())
                        .baseUomCode(item.getBaseUomCode()).sourceOnHandQuantity(item.getSourceOnHandQuantity())
                        .itemScopeHash(item.getItemScopeHash()).status(item.getStatus()).version(item.getVersion()).build()).toList())
                .approvals(approvals.stream().map(value -> PilotApprovalResult.builder()
                        .approvalId(value.getApprovalId()).approvalRole(value.getApprovalRole())
                        .approverId(value.getApproverId()).evidenceRef(value.getEvidenceRef())
                        .expiresAt(toInstant(value.getExpiresAt())).status(value.getStatus()).build()).toList())
                .duplicate(false).build();
    }

    private FreezePilotBatchCommand normalizeFreeze(FreezePilotBatchCommand command) {
        require(command != null, "pilot freeze command is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        command.setMigrationRunId(requireUuid(command.getMigrationRunId(), "migrationRunId"));
        requireText(command.getPolicyVersion(), "policyVersion", 32);
        requireText(command.getChangeTicket(), "changeTicket", 128);
        requireText(command.getPurpose(), "purpose", 512);
        command.setSourceWatermarkKind(upper(command.getSourceWatermarkKind()));
        requireText(command.getSourceWatermarkKind(), "sourceWatermarkKind", 32);
        requireText(command.getSourceWatermarkValue(), "sourceWatermarkValue", 256);
        command.setTargetWatermarkKind(upper(command.getTargetWatermarkKind()));
        requireText(command.getTargetWatermarkKind(), "targetWatermarkKind", 32);
        requireText(command.getTargetWatermarkValue(), "targetWatermarkValue", 256);
        require(command.getSourceWatermarkCapturedAt() != null && command.getTargetWatermarkAppliedAt() != null,
                "source and target watermark timestamps are required");
        require(command.getMaxLagSeconds() != null, "maxLagSeconds is required");
        require(command.getExecutionWindowStart() != null && command.getExecutionWindowEnd() != null,
                "execution window is required");
        normalizeEnvelope(command.getCorrelationId(), command.getCausationId(), command.getOccurredAt());
        command.setCorrelationId(requireUuid(command.getCorrelationId(), "correlationId"));
        if (command.getCausationId() != null) command.setCausationId(requireUuid(command.getCausationId(), "causationId"));
        require(command.getItems() != null && !command.getItems().isEmpty(), "pilot batch items are required");
        for (PilotItemEvidence item : command.getItems()) normalizeItem(item);
        command.getItems().sort(Comparator.comparing(PilotItemEvidence::getCandidateId));
        require(command.getItems().stream().map(PilotItemEvidence::getCandidateId).distinct().count()
                        == command.getItems().size(), "pilot candidate IDs must be unique");
        return command;
    }

    private static void normalizeItem(PilotItemEvidence item) {
        require(item != null, "pilot item evidence is required");
        item.setCandidateId(requireUuid(item.getCandidateId(), "candidateId"));
        item.setExpectedSourceSnapshotHash(requireHash(item.getExpectedSourceSnapshotHash(), "expectedSourceSnapshotHash"));
        requireEvidence(item.getAuthoritativeRecordRef(), "authoritativeRecordRef");
        requireEvidence(item.getQuantityEvidenceRef(), "quantityEvidenceRef");
        requireText(item.getSourceCdcPosition(), "sourceCdcPosition", 256);
        require(item.getSourceExtractedAt() != null, "sourceExtractedAt is required");
        item.setOwnerSourceSystem(upper(item.getOwnerSourceSystem()));
        item.setOwnerSourceType(upper(item.getOwnerSourceType()));
        requireText(item.getOwnerSourceSystem(), "ownerSourceSystem", 32);
        requireText(item.getOwnerSourceType(), "ownerSourceType", 32);
        requireText(item.getOwnerSourceId(), "ownerSourceId", 128);
        item.setSkuMappingId(requireUuid(item.getSkuMappingId(), "skuMappingId"));
        require(item.getSkuMappingVersion() != null && item.getSkuMappingVersion() >= 1, "skuMappingVersion must be positive");
        requireEvidence(item.getSkuMappingEvidenceRef(), "skuMappingEvidenceRef");
        item.setSourceUomCode(upper(item.getSourceUomCode()));
        requireText(item.getSourceUomCode(), "sourceUomCode", 32);
        require(item.getUomConversionRatio() != null, "uomConversionRatio is required");
        requireEvidence(item.getUomEvidenceRef(), "uomEvidenceRef");
        item.setWarehouseSourceSystem(upper(item.getWarehouseSourceSystem()));
        item.setWarehouseSourceType(upper(item.getWarehouseSourceType()));
        requireText(item.getWarehouseSourceSystem(), "warehouseSourceSystem", 32);
        requireText(item.getWarehouseSourceType(), "warehouseSourceType", 32);
        requireText(item.getWarehouseSourceId(), "warehouseSourceId", 128);
        requireEvidence(item.getWarehouseMappingEvidenceRef(), "warehouseMappingEvidenceRef");
        item.setLocationSourceSystem(upper(item.getLocationSourceSystem()));
        item.setLocationSourceType(upper(item.getLocationSourceType()));
        requireText(item.getLocationSourceSystem(), "locationSourceSystem", 32);
        requireText(item.getLocationSourceType(), "locationSourceType", 32);
        requireText(item.getLocationSourceId(), "locationSourceId", 128);
        requireEvidence(item.getLocationMappingEvidenceRef(), "locationMappingEvidenceRef");
        item.setLotTrackingPolicy(upper(item.getLotTrackingPolicy()));
        if (item.getLotId() != null) item.setLotId(requireUuid(item.getLotId(), "lotId"));
        if (item.getLotMappingId() != null) item.setLotMappingId(requireUuid(item.getLotMappingId(), "lotMappingId"));
        requireEvidence(item.getLotEvidenceRef(), "lotEvidenceRef");
    }

    private static ApprovePilotBatchCommand normalizeApproval(ApprovePilotBatchCommand command) {
        require(command != null, "pilot approval command is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        command.setApprovalRole(upper(command.getApprovalRole()));
        require(APPROVAL_ROLES.contains(command.getApprovalRole()), "approvalRole must be DATA_OWNER or CHANGE_MANAGER");
        require(command.getExpectedVersion() != null && command.getExpectedVersion() >= 1, "expectedVersion is required");
        requireEvidence(command.getEvidenceRef(), "evidenceRef");
        require(command.getExpiresAt() != null, "expiresAt is required");
        normalizeEnvelope(command.getCorrelationId(), command.getCausationId(), command.getOccurredAt());
        command.setCorrelationId(requireUuid(command.getCorrelationId(), "correlationId"));
        if (command.getCausationId() != null) command.setCausationId(requireUuid(command.getCausationId(), "causationId"));
        return command;
    }

    private static AdmitPilotBatchCommand normalizeAdmission(AdmitPilotBatchCommand command) {
        require(command != null, "pilot admission command is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        require(command.getExpectedVersion() != null && command.getExpectedVersion() >= 1, "expectedVersion is required");
        requireEvidence(command.getEvidenceRef(), "evidenceRef");
        normalizeEnvelope(command.getCorrelationId(), command.getCausationId(), command.getOccurredAt());
        command.setCorrelationId(requireUuid(command.getCorrelationId(), "correlationId"));
        if (command.getCausationId() != null) command.setCausationId(requireUuid(command.getCausationId(), "causationId"));
        return command;
    }

    private void validateWindow(FreezePilotBatchCommand command, LocalDateTime now) {
        LocalDateTime start = toDateTime(command.getExecutionWindowStart());
        LocalDateTime end = toDateTime(command.getExecutionWindowEnd());
        require(end.isAfter(start) && end.isAfter(now), "pilot execution window must end in the future");
        require(Duration.between(start, end).getSeconds() <= properties.getMaxPilotWindowSeconds(),
                "pilot execution window exceeds the server limit");
    }

    private void validateWatermarks(FreezePilotBatchCommand command, LocalDateTime now) {
        LocalDateTime sourceAt = toDateTime(command.getSourceWatermarkCapturedAt());
        LocalDateTime targetAt = toDateTime(command.getTargetWatermarkAppliedAt());
        require(Objects.equals(command.getSourceWatermarkKind(), command.getTargetWatermarkKind()),
                "source and target watermark kinds must match");
        require(!targetAt.isBefore(sourceAt) && !targetAt.isAfter(now),
                "target watermark must cover the source watermark and cannot be in the future");
        require(command.getMaxLagSeconds() >= 0
                        && command.getMaxLagSeconds() <= properties.getMaxWatermarkLagSeconds()
                        && Duration.between(sourceAt, targetAt).getSeconds() <= command.getMaxLagSeconds(),
                "pilot watermark lag exceeds the server policy");
    }

    private void requirePilotEnvironment(boolean admission) {
        require(properties.isPilotEnabled(), "Inventory migration pilot is disabled by server policy");
        require("PRODUCTION".equals(upper(properties.getEnvironment())),
                "Inventory migration pilot requires the trusted PRODUCTION server environment");
        requireText(properties.getEnvironmentFingerprint(), "environmentFingerprint", 128);
        if (admission) require(properties.isProductionAdmissionEnabled(),
                "Inventory migration production admission is disabled by server policy");
    }

    private int effectiveMaxItems() {
        return Math.max(1, Math.min(10, properties.getMaxPilotItems()));
    }

    private String policyHash(FreezePilotBatchCommand command) {
        return DigestUtil.sha256Hex(String.join("\u001f", "production-pilot-admission-v1",
                properties.getEnvironmentFingerprint(), command.getPolicyVersion(),
                Integer.toString(effectiveMaxItems()), Integer.toString(properties.getMaxPilotWindowSeconds()),
                Integer.toString(properties.getMaxWatermarkLagSeconds()), "max_concurrency=1", "execution_available=false"));
    }

    private String manifestHash(Long tenantId, FreezePilotBatchCommand command, InventoryMigrationRunDO run,
                                String policyHash, List<String> itemHashes) {
        List<String> parts = new ArrayList<>(List.of(tenantId.toString(), "PRODUCTION",
                properties.getEnvironmentFingerprint(), command.getMigrationRunId(), run.getSourceSnapshotHash(),
                policyHash, command.getSourceWatermarkKind(), command.getSourceWatermarkValue(),
                command.getTargetWatermarkKind(), command.getTargetWatermarkValue(),
                command.getExecutionWindowStart().toString(), command.getExecutionWindowEnd().toString()));
        parts.addAll(itemHashes);
        return DigestUtil.sha256Hex(String.join("\u001f", parts));
    }

    private static String itemScopeHash(InventoryMigrationPilotItemDO item) {
        return DigestUtil.sha256Hex(String.join("\u001f", item.getLegacyBalanceId(), item.getSourceSnapshotHash(),
                item.getOwnerId(), item.getCanonicalSkuId(), item.getWarehouseSourceMappingId(), item.getWarehouseId(),
                item.getLocationSourceMappingId(), item.getLocationId(), Objects.toString(item.getZoneId(), ""),
                item.getLotTrackingPolicy(), Objects.toString(item.getLotId(), ""), item.getStockStatus(),
                item.getQualityStatus(), item.getBaseUomCode(), decimal(item.getSourceOnHandQuantity()),
                item.getOwnerMappingId(), item.getSkuMappingId(), item.getSourceCdcPosition()));
    }

    private static <T> String oneValue(List<T> values, Function<T, String> getter, String field) {
        Set<String> distinct = values.stream().map(getter).collect(Collectors.toSet());
        require(distinct.size() == 1, "pilot batch must use one " + field);
        return distinct.iterator().next();
    }

    private static String requestHash(Long tenantId, String commandType, Long actorId, Object command) {
        return DigestUtil.sha256Hex(tenantId + "|" + commandType + "|" + actorId + "|" + JsonUtils.toJsonString(command));
    }

    private static void normalizeEnvelope(String correlationId, String causationId, Instant occurredAt) {
        require(correlationId != null && occurredAt != null, "correlationId and occurredAt are required");
    }

    private static void requireActor(Long value, String field) {
        require(value != null && value > 0, field + " must come from an authenticated system user");
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

    private static BigDecimal scaled(BigDecimal value) {
        require(value != null, "quantity is required");
        return value.setScale(6, RoundingMode.UNNECESSARY);
    }

    private static String decimal(BigDecimal value) {
        return scaled(value).toPlainString();
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

    private static String deterministicUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private record PreparedItem(InventoryMigrationPilotItemDO item) {
    }

    private record SourceSnapshot(InventoryBalanceDO balance, int activeReservationCount,
                                  BigDecimal activeReservationQuantity, String snapshotHash) {
    }

    private record OperationAttempt(Long operationId, PilotBatchResult replay) {
    }
}
