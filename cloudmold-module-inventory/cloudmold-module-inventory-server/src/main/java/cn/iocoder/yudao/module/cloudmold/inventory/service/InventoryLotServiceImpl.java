package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryLotStoreMapper;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantOwnerValidationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InventoryLotServiceImpl implements InventoryLotCommandApi, InventoryLotQueryApi,
        InventoryV3AvailabilityQueryApi {

    static final String LIFECYCLE_EVENT = "inventory.lot.lifecycle.changed";
    static final String MAPPING_EVENT = "inventory.lot.source_mapping.changed";
    private static final int OPERATION_SUCCEEDED = 10;
    private static final BigDecimal ZERO = new BigDecimal("0.000000");
    private static final String EVIDENCE_PATTERN =
            "(?i)(sha256|sha512|ticket|run|evidence|vault|kms|token):[a-z0-9._/-]+";

    private final InventoryLotStoreMapper mapper;
    private final MerchantOwnerValidationApi merchantOwnerValidationApi;
    private final CatalogSkuValidationApi catalogSkuValidationApi;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryLotResult execute(InventoryLotCommand command) {
        normalizeAndValidate(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String requestHash = DigestUtil.sha256Hex(tenantId + "|" + JsonUtils.toJsonString(command));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), command.getSourceEventId(),
                command.getOperation().name(), requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve inventory Lot operation");
        InventoryLotOperationDO operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "inventory Lot operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key or source event conflicts with different Lot payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing inventory Lot operation is not complete");
            InventoryLotResult replay = JsonUtils.parseObject(operation.getResultJson(), InventoryLotResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        InventoryLotResult result = switch (command.getOperation()) {
            case REGISTER -> register(tenantId, operationId, command, now);
            case RECALL, CLOSE -> changeLifecycle(tenantId, operationId, command, now);
            case LINK_SOURCE -> linkSource(tenantId, operationId, command, now);
            case END_SOURCE -> endSource(tenantId, operationId, command, now);
        };
        require(mapper.markOperationSucceeded(operationId, tenantId, result.getLotId(), result.getMappingId(),
                JsonUtils.toJsonString(result), now) == 1, "inventory Lot operation completion conflict");
        return result;
    }

    @Override
    public InventoryLotView requireCurrent(String lotId, Instant eligibilityAt) {
        requireUuid(lotId, "lotId");
        Instant evaluatedAt = requireInstant(eligibilityAt, "eligibilityAt");
        InventoryLotDO lot = mapper.selectLot(TenantContextHolder.getRequiredTenantId(), lotId);
        require(lot != null, "inventory Lot does not exist");
        return toView(lot, null, evaluatedAt);
    }

    @Override
    public InventoryLotView requireBySource(String sourceSystem, String sourceType, String sourceId,
                                            Instant effectiveAt, Instant eligibilityAt) {
        String normalizedSystem = normalizeCode(sourceSystem, "sourceSystem", 32);
        String normalizedType = normalizeCode(sourceType, "sourceType", 32);
        String normalizedId = requireTrimmed(sourceId, "sourceId", 128);
        Instant effective = requireInstant(effectiveAt, "effectiveAt");
        Instant evaluatedAt = requireInstant(eligibilityAt, "eligibilityAt");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        List<InventoryLotSourceMappingDO> mappings = mapper.selectEffectiveMappings(tenantId, normalizedSystem,
                normalizedType, normalizedId, toUtc(effective));
        require(mappings != null && mappings.size() == 1,
                "Lot source identity must resolve to exactly one effective mapping");
        InventoryLotSourceMappingDO mapping = mappings.get(0);
        InventoryLotDO lot = mapper.selectLot(tenantId, mapping.getLotId());
        require(lot != null, "mapped inventory Lot does not exist");
        return toView(lot, mapping, evaluatedAt);
    }

    @Override
    public List<InventoryV3AvailabilityView> listByLot(String lotId, Instant eligibilityAt) {
        requireUuid(lotId, "lotId");
        Instant evaluatedAt = requireInstant(eligibilityAt, "eligibilityAt");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        InventoryLotDO lot = mapper.selectLot(tenantId, lotId);
        require(lot != null, "inventory Lot does not exist");
        List<InventoryV3AvailabilityDO> rows = mapper.selectAvailabilityByLot(tenantId, lotId);
        if (rows == null) return List.of();
        List<InventoryV3AvailabilityView> result = new ArrayList<>(rows.size());
        for (InventoryV3AvailabilityDO row : rows) {
            BigDecimal onHand = scaled(row.getOnHandQuantity());
            BigDecimal reserved = scaled(row.getReservedQuantity());
            BigDecimal unreserved = onHand.subtract(reserved);
            String eligibility = allocationEligibility(lot, evaluatedAt);
            if (!"SELLABLE".equals(row.getStockStatus())) eligibility = "STOCK_NOT_SELLABLE";
            else if (!"QUALIFIED".equals(row.getQualityStatus())) eligibility = "QUALITY_NOT_QUALIFIED";
            BigDecimal allocatable = "ALLOCATABLE".equals(eligibility) ? unreserved.max(ZERO) : ZERO;
            result.add(new InventoryV3AvailabilityView().setBalanceId(row.getBalanceId())
                    .setOwnerType(row.getOwnerType()).setOwnerId(row.getOwnerId())
                    .setCanonicalSkuId(row.getCanonicalSkuId()).setWarehouseId(row.getWarehouseId())
                    .setLocationId(row.getLocationId()).setLotId(row.getLotId())
                    .setStockStatus(row.getStockStatus()).setQualityStatus(row.getQualityStatus())
                    .setBaseUomCode(row.getBaseUomCode()).setOnHandQuantity(onHand)
                    .setReservedQuantity(reserved).setInTransitQuantity(scaled(row.getInTransitQuantity()))
                    .setUnreservedQuantity(unreserved).setAllocatableQuantity(allocatable)
                    .setAllocationEligibility(eligibility).setAggregateVersion(row.getAggregateVersion())
                    .setEligibilityAt(evaluatedAt));
        }
        return List.copyOf(result);
    }

    private InventoryLotResult register(Long tenantId, Long operationId, InventoryLotCommand command,
                                        LocalDateTime now) {
        require(command.getLotId() == null, "lotId must be absent when registering a Lot");
        require(command.getExpectedLotVersion() == null,
                "expectedLotVersion must be absent when registering a Lot");
        require("MERCHANT".equals(command.getOwnerType()), "first Lot slice only supports MERCHANT owner");
        merchantOwnerValidationApi.requireActiveMerchant(command.getOwnerId());
        catalogSkuValidationApi.requireActiveSku(command.getCanonicalSkuId());
        InventoryLotDO lot = new InventoryLotDO().setLotId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setOwnerType(command.getOwnerType()).setOwnerId(command.getOwnerId())
                .setCanonicalSkuId(command.getCanonicalSkuId()).setLotCode(command.getLotCode())
                .setManufacturedOn(command.getManufacturedOn()).setExpiresOn(command.getExpiresOn())
                .setReceivedAt(toUtcNullable(command.getReceivedAt())).setStatus("ACTIVE").setVersion(1L)
                .setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertLot(lot) == 1, "failed to register inventory Lot");
        String eventId = appendLifecycleEvent(tenantId, command, lot, null, "CREATED");
        return new InventoryLotResult().setOperationId(operationId).setLotId(lot.getLotId())
                .setLotStatus(lot.getStatus()).setLotVersion(lot.getVersion()).setEventId(eventId);
    }

    private InventoryLotResult changeLifecycle(Long tenantId, Long operationId, InventoryLotCommand command,
                                               LocalDateTime now) {
        InventoryLotDO lot = mapper.selectLotForUpdate(tenantId, command.getLotId());
        require(lot != null, "inventory Lot does not exist");
        require(Objects.equals(lot.getVersion(), command.getExpectedLotVersion()),
                "inventory Lot version conflict");
        require("ACTIVE".equals(lot.getStatus()), "inventory Lot lifecycle transition requires ACTIVE status");
        String currentStatus = command.getOperation() == InventoryLotOperation.RECALL ? "RECALLED" : "CLOSED";
        if (command.getOperation() == InventoryLotOperation.CLOSE) {
            mapper.lockBalanceIds(tenantId, lot.getLotId());
            mapper.lockActiveAllocationIds(tenantId, lot.getLotId());
            require(mapper.countNonZeroBalances(tenantId, lot.getLotId()) == 0,
                    "inventory Lot cannot close while any quantity remains");
            require(mapper.countActiveAllocations(tenantId, lot.getLotId()) == 0,
                    "inventory Lot cannot close while an allocation is active");
        }
        require(mapper.updateLotStatus(tenantId, lot.getLotId(), lot.getVersion(), lot.getStatus(), currentStatus,
                now) == 1, "inventory Lot lifecycle update conflict");
        String previousStatus = lot.getStatus();
        lot.setStatus(currentStatus).setVersion(lot.getVersion() + 1).setUpdatedAt(now);
        String eventId = appendLifecycleEvent(tenantId, command, lot, previousStatus,
                command.getOperation() == InventoryLotOperation.RECALL ? "RECALLED" : "CLOSED");
        return new InventoryLotResult().setOperationId(operationId).setLotId(lot.getLotId())
                .setLotStatus(lot.getStatus()).setLotVersion(lot.getVersion()).setEventId(eventId);
    }

    private InventoryLotResult linkSource(Long tenantId, Long operationId, InventoryLotCommand command,
                                          LocalDateTime now) {
        InventoryLotDO lot = mapper.selectLotForUpdate(tenantId, command.getLotId());
        require(lot != null, "inventory Lot does not exist");
        require(!"CLOSED".equals(lot.getStatus()), "cannot create a current source mapping to a CLOSED Lot");
        require(Objects.equals(lot.getVersion(), command.getExpectedLotVersion()),
                "inventory Lot version conflict");
        LocalDateTime validFrom = toUtc(command.getValidFrom());
        List<InventoryLotSourceMappingDO> overlaps = mapper.selectOverlappingMappingsForUpdate(tenantId,
                command.getMappedSourceSystem(), command.getMappedSourceType(), command.getMappedSourceId(), validFrom);
        require(overlaps == null || overlaps.isEmpty(), "Lot source mapping validity interval overlaps");
        InventoryLotSourceMappingDO mapping = new InventoryLotSourceMappingDO()
                .setMappingId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setSourceSystem(command.getMappedSourceSystem()).setSourceType(command.getMappedSourceType())
                .setSourceId(command.getMappedSourceId()).setLotId(lot.getLotId()).setValidFrom(validFrom)
                .setValidTo(null).setVerificationRef(command.getVerificationRef()).setStatus("ACTIVE")
                .setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertMapping(mapping) == 1, "failed to link inventory Lot source mapping");
        String eventId = appendMappingEvent(tenantId, command, mapping, null, "LINKED");
        return new InventoryLotResult().setOperationId(operationId).setLotId(lot.getLotId())
                .setLotStatus(lot.getStatus()).setLotVersion(lot.getVersion()).setMappingId(mapping.getMappingId())
                .setMappingStatus(mapping.getStatus()).setMappingVersion(mapping.getVersion()).setEventId(eventId);
    }

    private InventoryLotResult endSource(Long tenantId, Long operationId, InventoryLotCommand command,
                                         LocalDateTime now) {
        InventoryLotSourceMappingDO mapping = mapper.selectMappingForUpdate(tenantId, command.getMappingId());
        require(mapping != null, "inventory Lot source mapping does not exist");
        InventoryLotDO lot = mapper.selectLotForUpdate(tenantId, mapping.getLotId());
        require(lot != null, "mapped inventory Lot does not exist");
        require(Objects.equals(mapping.getVersion(), command.getExpectedMappingVersion()),
                "inventory Lot source mapping version conflict");
        require("ACTIVE".equals(mapping.getStatus()), "inventory Lot source mapping is not ACTIVE");
        LocalDateTime validTo = toUtc(command.getValidTo());
        require(validTo.isAfter(mapping.getValidFrom()), "validTo must be after mapping validFrom");
        require(mapper.endMapping(tenantId, mapping.getMappingId(), mapping.getVersion(), validTo, now) == 1,
                "inventory Lot source mapping update conflict");
        String previousStatus = mapping.getStatus();
        mapping.setStatus("ENDED").setValidTo(validTo).setVersion(mapping.getVersion() + 1).setUpdatedAt(now);
        String eventId = appendMappingEvent(tenantId, command, mapping, previousStatus, "ENDED");
        return new InventoryLotResult().setOperationId(operationId).setLotId(lot.getLotId())
                .setLotStatus(lot.getStatus()).setLotVersion(lot.getVersion()).setMappingId(mapping.getMappingId())
                .setMappingStatus(mapping.getStatus()).setMappingVersion(mapping.getVersion()).setEventId(eventId);
    }

    private String appendLifecycleEvent(Long tenantId, InventoryLotCommand command, InventoryLotDO lot,
                                        String previousStatus, String changeType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", command.getRunId());
        payload.put("migration_run_id", command.getMigrationRunId());
        payload.put("lot_id", lot.getLotId());
        payload.put("owner_type", lot.getOwnerType());
        payload.put("owner_id", lot.getOwnerId());
        payload.put("canonical_sku_id", lot.getCanonicalSkuId());
        payload.put("lot_code", lot.getLotCode());
        payload.put("manufactured_on", toDateString(lot.getManufacturedOn()));
        payload.put("expires_on", toDateString(lot.getExpiresOn()));
        payload.put("received_at", toInstantString(lot.getReceivedAt()));
        payload.put("previous_status", previousStatus);
        payload.put("current_status", lot.getStatus());
        payload.put("change_type", changeType);
        payload.put("reason_code", command.getReasonCode());
        payload.put("evidence_ref", command.getEvidenceRef());
        payload.put("recall_reference", command.getRecallReference());
        String idempotency = command.getIdempotencyKey() + ":INVENTORY_LOT:" + lot.getVersion();
        String eventId = deterministicEventId(tenantId, idempotency);
        outboxAppender.append(AppendDomainEventCommand.builder().eventId(eventId).eventType(LIFECYCLE_EVENT)
                .schemaVersion(1).sourceSystem("cloudmold-inventory").tenantId(tenantId)
                .aggregateType("inventory_lot").aggregateId(lot.getLotId()).aggregateVersion(lot.getVersion())
                .eventSequence((short) 1).occurredAt(command.getOccurredAt()).traceId(command.getTraceId())
                .correlationId(resolvedCorrelationId(tenantId, command)).causationId(command.getCausationId())
                .idempotencyKey(idempotency).payload(payload).headers(headers(command)).destination("lakehouse")
                .build());
        return eventId;
    }

    private String appendMappingEvent(Long tenantId, InventoryLotCommand command,
                                      InventoryLotSourceMappingDO mapping, String previousStatus, String changeType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", command.getRunId());
        payload.put("migration_run_id", command.getMigrationRunId());
        payload.put("mapping_id", mapping.getMappingId());
        payload.put("mapping_source_system", mapping.getSourceSystem());
        payload.put("source_type", mapping.getSourceType());
        payload.put("source_id", mapping.getSourceId());
        payload.put("lot_id", mapping.getLotId());
        payload.put("valid_from", toInstantString(mapping.getValidFrom()));
        payload.put("valid_to", toInstantString(mapping.getValidTo()));
        payload.put("previous_status", previousStatus);
        payload.put("current_status", mapping.getStatus());
        payload.put("change_type", changeType);
        payload.put("verification_ref", mapping.getVerificationRef());
        payload.put("change_reason_code", command.getReasonCode());
        String idempotency = command.getIdempotencyKey() + ":INVENTORY_LOT_SOURCE_MAPPING:" + mapping.getVersion();
        String eventId = deterministicEventId(tenantId, idempotency);
        outboxAppender.append(AppendDomainEventCommand.builder().eventId(eventId).eventType(MAPPING_EVENT)
                .schemaVersion(1).sourceSystem("cloudmold-inventory").tenantId(tenantId)
                .aggregateType("inventory_lot_source_mapping").aggregateId(mapping.getMappingId())
                .aggregateVersion(mapping.getVersion()).eventSequence((short) 1).occurredAt(command.getOccurredAt())
                .traceId(command.getTraceId()).correlationId(resolvedCorrelationId(tenantId, command))
                .causationId(command.getCausationId()).idempotencyKey(idempotency).payload(payload)
                .headers(headers(command)).destination("lakehouse").build());
        return eventId;
    }

    private static InventoryLotView toView(InventoryLotDO lot, InventoryLotSourceMappingDO mapping,
                                           Instant eligibilityAt) {
        InventoryLotView view = new InventoryLotView().setLotId(lot.getLotId()).setOwnerType(lot.getOwnerType())
                .setOwnerId(lot.getOwnerId()).setCanonicalSkuId(lot.getCanonicalSkuId()).setLotCode(lot.getLotCode())
                .setManufacturedOn(lot.getManufacturedOn()).setExpiresOn(lot.getExpiresOn())
                .setReceivedAt(toInstant(lot.getReceivedAt())).setStatus(lot.getStatus()).setVersion(lot.getVersion())
                .setAllocationEligibility(allocationEligibility(lot, eligibilityAt)).setEligibilityAt(eligibilityAt);
        if (mapping != null) view.setMappingId(mapping.getMappingId())
                .setMappedSourceSystem(mapping.getSourceSystem()).setMappedSourceType(mapping.getSourceType())
                .setMappedSourceId(mapping.getSourceId()).setMappingValidFrom(toInstant(mapping.getValidFrom()))
                .setMappingValidTo(toInstant(mapping.getValidTo())).setMappingStatus(mapping.getStatus())
                .setMappingVersion(mapping.getVersion());
        return view;
    }

    private static String allocationEligibility(InventoryLotDO lot, Instant eligibilityAt) {
        if ("RECALLED".equals(lot.getStatus())) return "LOT_RECALLED";
        if ("CLOSED".equals(lot.getStatus())) return "LOT_CLOSED";
        LocalDate date = eligibilityAt.atZone(ZoneOffset.UTC).toLocalDate();
        if (lot.getExpiresOn() != null && lot.getExpiresOn().isBefore(date)) return "LOT_EXPIRED";
        return "ALLOCATABLE";
    }

    private static void normalizeAndValidate(InventoryLotCommand command) {
        require(command != null, "inventory Lot command is required");
        require(command.getOperation() != null, "inventory Lot operation is required");
        command.setIdempotencyKey(requireTrimmed(command.getIdempotencyKey(), "idempotencyKey", 128));
        command.setRunId(requireTrimmed(command.getRunId(), "runId", 128));
        command.setTraceId(requireTrimmed(command.getTraceId(), "traceId", 128));
        command.setReasonCode(normalizeCode(command.getReasonCode(), "reasonCode", 64));
        command.setEvidenceRef(requireEvidence(command.getEvidenceRef(), "evidenceRef"));
        require(command.getOccurredAt() != null, "occurredAt is required");
        if (command.getSourceEventId() != null) requireUuid(command.getSourceEventId(), "sourceEventId");
        if (command.getCorrelationId() != null) requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) requireUuid(command.getCausationId(), "causationId");
        if (command.getMigrationRunId() != null)
            command.setMigrationRunId(requireTrimmed(command.getMigrationRunId(), "migrationRunId", 128));
        switch (command.getOperation()) {
            case REGISTER -> {
                command.setOwnerType(normalizeCode(command.getOwnerType(), "ownerType", 32));
                requireUuid(command.getOwnerId(), "ownerId");
                requireUuid(command.getCanonicalSkuId(), "canonicalSkuId");
                command.setLotCode(requireTrimmed(command.getLotCode(), "lotCode", 128));
                require(command.getExpiresOn() == null || command.getManufacturedOn() == null
                                || !command.getExpiresOn().isBefore(command.getManufacturedOn()),
                        "expiresOn must not precede manufacturedOn");
            }
            case RECALL, CLOSE -> {
                requireUuid(command.getLotId(), "lotId");
                requirePositiveVersion(command.getExpectedLotVersion(), "expectedLotVersion");
                if (command.getOperation() == InventoryLotOperation.RECALL)
                    command.setRecallReference(requireEvidence(command.getRecallReference(), "recallReference"));
            }
            case LINK_SOURCE -> {
                requireUuid(command.getLotId(), "lotId");
                requirePositiveVersion(command.getExpectedLotVersion(), "expectedLotVersion");
                command.setMappedSourceSystem(normalizeCode(command.getMappedSourceSystem(),
                        "mappedSourceSystem", 32));
                command.setMappedSourceType(normalizeCode(command.getMappedSourceType(), "mappedSourceType", 32));
                command.setMappedSourceId(requireTrimmed(command.getMappedSourceId(), "mappedSourceId", 128));
                command.setVerificationRef(requireEvidence(command.getVerificationRef(), "verificationRef"));
                require(command.getValidFrom() != null, "validFrom is required");
                require(command.getValidTo() == null, "LINK_SOURCE only creates an open-ended mapping");
            }
            case END_SOURCE -> {
                requireUuid(command.getMappingId(), "mappingId");
                requirePositiveVersion(command.getExpectedMappingVersion(), "expectedMappingVersion");
                require(command.getValidTo() != null, "validTo is required");
            }
        }
    }

    private static Map<String, Object> headers(InventoryLotCommand command) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("run_id", command.getRunId());
        if (command.getMigrationRunId() != null) headers.put("migration_run_id", command.getMigrationRunId());
        return headers;
    }

    private static String deterministicEventId(Long tenantId, String idempotency) {
        return UUID.nameUUIDFromBytes((tenantId + "|" + idempotency).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String resolvedCorrelationId(Long tenantId, InventoryLotCommand command) {
        return command.getCorrelationId() == null
                ? UUID.nameUUIDFromBytes((tenantId + "|correlation|" + command.getRunId())
                .getBytes(StandardCharsets.UTF_8)).toString() : command.getCorrelationId();
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value == null ? ZERO : value.setScale(6);
    }

    private static LocalDateTime toUtc(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static LocalDateTime toUtcNullable(Instant value) {
        return value == null ? null : toUtc(value);
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static String toInstantString(LocalDateTime value) {
        Instant instant = toInstant(value);
        return instant == null ? null : instant.toString();
    }

    private static String toDateString(LocalDate value) {
        return value == null ? null : value.toString();
    }

    private static Instant requireInstant(Instant value, String field) {
        require(value != null, field + " is required");
        return value;
    }

    private static String requireEvidence(String value, String field) {
        String normalized = requireTrimmed(value, field, 256);
        require(normalized.matches(EVIDENCE_PATTERN), field + " must be an opaque evidence reference");
        return normalized;
    }

    private static String normalizeCode(String value, String field, int max) {
        String normalized = requireTrimmed(value, field, max).toUpperCase(Locale.ROOT);
        require(normalized.matches("[A-Z0-9_]+"), field + " must be a normalized uppercase code");
        return normalized;
    }

    private static String requireTrimmed(String value, String field, int max) {
        require(value != null && !value.isBlank(), field + " is required");
        String trimmed = value.trim();
        require(trimmed.length() <= max, field + " exceeds " + max + " characters");
        require(trimmed.equals(value), field + " must be trimmed");
        return trimmed;
    }

    private static void requirePositiveVersion(Long value, String field) {
        require(value != null && value > 0, field + " must be positive");
    }

    private static void requireUuid(String value, String field) {
        require(value != null, field + " is required");
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(field + " must be a UUID", error);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
