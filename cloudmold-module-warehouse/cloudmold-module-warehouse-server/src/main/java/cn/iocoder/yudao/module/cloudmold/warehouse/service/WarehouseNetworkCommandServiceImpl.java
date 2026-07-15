package cn.iocoder.yudao.module.cloudmold.warehouse.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.identity.api.PrincipalValidationApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.*;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WarehouseNetworkCommandServiceImpl implements WarehouseNetworkCommandApi {

    static final int OPERATION_PROCESSING = 0;
    static final int OPERATION_SUCCEEDED = 10;

    private final WarehouseOperationMapper operationMapper;
    private final CanonicalWarehouseMapper warehouseMapper;
    private final WarehouseZoneMapper zoneMapper;
    private final WarehouseLocationMapper locationMapper;
    private final WarehouseSourceMappingMapper sourceMappingMapper;
    private final WarehouseOperatorAssignmentMapper assignmentMapper;
    private final PrincipalValidationApi principalValidationApi;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WarehouseNetworkCommandResult execute(WarehouseNetworkCommand command) {
        validate(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String hash = fingerprint(tenantId, command);
        String attemptToken = UUID.randomUUID().toString();
        operationMapper.insertOrResolve(tenantId, command.getIdempotencyKey(), command.getSourceEventId(),
                command.getOperation().name(), hash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve warehouse operation");
        WarehouseOperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "warehouse operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(hash, operation.getRequestHash()),
                    "idempotency key or source event conflicts with different payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing warehouse operation is not complete");
            WarehouseNetworkCommandResult replay = JsonUtils.parseObject(operation.getResultJson(),
                    WarehouseNetworkCommandResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        Outcome outcome = switch (command.getOperation()) {
            case DEFINE_WAREHOUSE -> defineWarehouse(tenantId, command, now);
            case CHANGE_WAREHOUSE_STATUS -> changeWarehouseStatus(tenantId, command, now);
            case DEFINE_ZONE -> defineZone(tenantId, command, now);
            case CHANGE_ZONE_STATUS -> changeZoneStatus(tenantId, command, now);
            case DEFINE_LOCATION -> defineLocation(tenantId, command, now);
            case CHANGE_LOCATION_STATUS -> changeLocationStatus(tenantId, command, now);
            case LINK_SOURCE -> linkSource(tenantId, command, now);
            case END_SOURCE_MAPPING -> endSourceMapping(tenantId, command, now);
            case ASSIGN_OPERATOR -> assignOperator(tenantId, command, now);
            case END_OPERATOR_ASSIGNMENT -> endOperatorAssignment(tenantId, command, now);
        };
        appendEvent(tenantId, command, outcome);
        WarehouseNetworkCommandResult result = outcome.result();
        result.setOperationId(operationId);
        require(operationMapper.markSucceeded(operationId, tenantId, outcome.aggregateId(),
                JsonUtils.toJsonString(result), now) == 1, "warehouse operation completion conflict");
        return result;
    }

    private Outcome defineWarehouse(Long tenantId, WarehouseNetworkCommand command, LocalDateTime now) {
        WarehouseDefinition input = command.getWarehouse();
        require(warehouseMapper.selectByCode(tenantId, input.getWarehouseCode()) == null,
                "warehouseCode already exists");
        String id = valueOrUuid(input.getWarehouseId());
        WarehouseDO row = new WarehouseDO().setWarehouseId(id).setTenantId(tenantId)
                .setWarehouseCode(input.getWarehouseCode()).setName(input.getName())
                .setWarehouseType(input.getWarehouseType()).setTimezone(input.getTimezone())
                .setStatus("DRAFT").setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        warehouseMapper.insert(row);
        Map<String, Object> payload = entityPayload("WAREHOUSE", id, null, "DRAFT");
        payload.put("warehouse_id", id);
        payload.put("warehouse_code", row.getWarehouseCode()); payload.put("name", row.getName());
        payload.put("warehouse_type", row.getWarehouseType());
        return outcome("warehouse.entity.status_changed", "warehouse", id, 1L, payload,
                WarehouseNetworkCommandResult.builder().warehouseId(id).aggregateVersion(1L)
                        .status("DRAFT").duplicate(false).build());
    }

    private Outcome changeWarehouseStatus(Long tenantId, WarehouseNetworkCommand command, LocalDateTime now) {
        WarehouseDefinition input = command.getWarehouse();
        WarehouseDO row = requireWarehouse(tenantId, input.getWarehouseId());
        requireVersion(row.getVersion(), input.getExpectedVersion());
        String next = requireLifecycle(input.getStatus());
        require(!Objects.equals(row.getStatus(), next), "warehouse status is unchanged");
        require(warehouseMapper.updateStatusCas(tenantId, row.getWarehouseId(), row.getVersion(), next, now) == 1,
                "warehouse version conflict");
        Map<String, Object> payload = entityPayload("WAREHOUSE", row.getWarehouseId(), row.getStatus(), next);
        payload.put("warehouse_id", row.getWarehouseId());
        return outcome("warehouse.entity.status_changed", "warehouse", row.getWarehouseId(), row.getVersion() + 1,
                payload, WarehouseNetworkCommandResult.builder().warehouseId(row.getWarehouseId())
                        .aggregateVersion(row.getVersion() + 1).status(next).duplicate(false).build());
    }

    private Outcome defineZone(Long tenantId, WarehouseNetworkCommand command, LocalDateTime now) {
        WarehouseZoneDefinition input = command.getZone();
        requireWarehouse(tenantId, input.getWarehouseId());
        require(zoneMapper.selectByCode(tenantId, input.getWarehouseId(), input.getZoneCode()) == null,
                "zoneCode already exists in warehouse");
        String id = valueOrUuid(input.getZoneId());
        WarehouseZoneDO row = new WarehouseZoneDO().setZoneId(id).setTenantId(tenantId)
                .setWarehouseId(input.getWarehouseId()).setZoneCode(input.getZoneCode()).setName(input.getName())
                .setZoneType(input.getZoneType()).setStatus("DRAFT").setVersion(1L)
                .setCreatedAt(now).setUpdatedAt(now);
        zoneMapper.insert(row);
        Map<String, Object> payload = entityPayload("ZONE", id, null, "DRAFT");
        payload.put("zone_id", id);
        payload.put("warehouse_id", row.getWarehouseId()); payload.put("zone_code", row.getZoneCode());
        payload.put("zone_type", row.getZoneType());
        return outcome("warehouse.entity.status_changed", "warehouse_zone", id, 1L, payload,
                WarehouseNetworkCommandResult.builder().warehouseId(row.getWarehouseId()).zoneId(id)
                        .aggregateVersion(1L).status("DRAFT").duplicate(false).build());
    }

    private Outcome changeZoneStatus(Long tenantId, WarehouseNetworkCommand command, LocalDateTime now) {
        WarehouseZoneDefinition input = command.getZone();
        WarehouseZoneDO row = requireZone(tenantId, input.getZoneId());
        requireVersion(row.getVersion(), input.getExpectedVersion());
        String next = requireLifecycle(input.getStatus());
        if ("ACTIVE".equals(next)) requireActive(requireWarehouse(tenantId, row.getWarehouseId()), "warehouse");
        require(zoneMapper.updateStatusCas(tenantId, row.getZoneId(), row.getVersion(), next, now) == 1,
                "zone version conflict");
        Map<String, Object> payload = entityPayload("ZONE", row.getZoneId(), row.getStatus(), next);
        payload.put("zone_id", row.getZoneId());
        payload.put("warehouse_id", row.getWarehouseId());
        return outcome("warehouse.entity.status_changed", "warehouse_zone", row.getZoneId(), row.getVersion() + 1, payload,
                WarehouseNetworkCommandResult.builder().warehouseId(row.getWarehouseId()).zoneId(row.getZoneId())
                        .aggregateVersion(row.getVersion() + 1).status(next).duplicate(false).build());
    }

    private Outcome defineLocation(Long tenantId, WarehouseNetworkCommand command, LocalDateTime now) {
        WarehouseLocationDefinition input = command.getLocation();
        WarehouseDO warehouse = requireWarehouse(tenantId, input.getWarehouseId());
        WarehouseZoneDO zone = requireZone(tenantId, input.getZoneId());
        require(Objects.equals(zone.getWarehouseId(), warehouse.getWarehouseId()),
                "zone does not belong to warehouse");
        require(locationMapper.selectByCode(tenantId, input.getWarehouseId(), input.getLocationCode()) == null,
                "locationCode already exists in warehouse");
        String id = valueOrUuid(input.getLocationId());
        WarehouseLocationDO row = new WarehouseLocationDO().setLocationId(id).setTenantId(tenantId)
                .setWarehouseId(input.getWarehouseId()).setZoneId(input.getZoneId())
                .setLocationCode(input.getLocationCode()).setName(input.getName())
                .setLocationType(input.getLocationType()).setAisleCode(input.getAisleCode())
                .setRackCode(input.getRackCode()).setBayCode(input.getBayCode()).setLevelCode(input.getLevelCode())
                .setAllowItemMixing(Boolean.TRUE.equals(input.getAllowItemMixing()))
                .setAllowLotMixing(Boolean.TRUE.equals(input.getAllowLotMixing()))
                .setCapacityQuantity(input.getCapacityQuantity()).setCapacityUomCode(input.getCapacityUomCode())
                .setStatus("DRAFT").setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        locationMapper.insert(row);
        Map<String, Object> payload = entityPayload("LOCATION", id, null, "DRAFT");
        payload.put("location_id", id);
        payload.put("warehouse_id", row.getWarehouseId()); payload.put("zone_id", row.getZoneId());
        payload.put("location_code", row.getLocationCode()); payload.put("location_type", row.getLocationType());
        return outcome("warehouse.entity.status_changed", "warehouse_location", id, 1L, payload,
                WarehouseNetworkCommandResult.builder().warehouseId(row.getWarehouseId()).zoneId(row.getZoneId())
                        .locationId(id).aggregateVersion(1L).status("DRAFT").duplicate(false).build());
    }

    private Outcome changeLocationStatus(Long tenantId, WarehouseNetworkCommand command, LocalDateTime now) {
        WarehouseLocationDefinition input = command.getLocation();
        WarehouseLocationDO row = requireLocation(tenantId, input.getLocationId());
        requireVersion(row.getVersion(), input.getExpectedVersion());
        String next = requireLifecycle(input.getStatus());
        if ("ACTIVE".equals(next)) {
            requireActive(requireWarehouse(tenantId, row.getWarehouseId()), "warehouse");
            WarehouseZoneDO zone = requireZone(tenantId, row.getZoneId());
            require(Objects.equals(row.getWarehouseId(), zone.getWarehouseId()), "zone does not belong to warehouse");
            requireActive(zone, "zone");
        }
        require(locationMapper.updateStatusCas(tenantId, row.getLocationId(), row.getVersion(), next, now) == 1,
                "location version conflict");
        Map<String, Object> payload = entityPayload("LOCATION", row.getLocationId(), row.getStatus(), next);
        payload.put("location_id", row.getLocationId());
        payload.put("warehouse_id", row.getWarehouseId()); payload.put("zone_id", row.getZoneId());
        payload.put("location_type", row.getLocationType());
        return outcome("warehouse.entity.status_changed", "warehouse_location", row.getLocationId(), row.getVersion() + 1,
                payload, WarehouseNetworkCommandResult.builder().warehouseId(row.getWarehouseId())
                        .zoneId(row.getZoneId()).locationId(row.getLocationId()).aggregateVersion(row.getVersion() + 1)
                        .status(next).duplicate(false).build());
    }

    private Outcome linkSource(Long tenantId, WarehouseNetworkCommand command, LocalDateTime now) {
        WarehouseSourceMappingDefinition input = command.getSourceMapping();
        String sourceSystem = input.getSourceSystem().trim().toUpperCase(Locale.ROOT);
        String sourceType = input.getSourceType().trim().toUpperCase(Locale.ROOT);
        String sourceId = input.getSourceId().trim();
        String targetType = input.getTargetType().trim().toUpperCase(Locale.ROOT);
        validateTarget(tenantId, targetType, input.getWarehouseId(), input.getZoneId(), input.getLocationId());
        String canonicalId = canonicalId(targetType, input.getWarehouseId(), input.getZoneId(),
                input.getLocationId());
        require(!canonicalId.equals(sourceId), "canonicalId must not equal sourceId");
        List<WarehouseSourceMappingDO> existing = sourceMappingMapper.selectEffectiveForUpdate(tenantId,
                sourceSystem, sourceType, sourceId,
                LocalDateTime.ofInstant(input.getValidFrom(), ZoneOffset.UTC));
        require(existing == null || existing.isEmpty(), "active source mapping already exists");
        String id = valueOrUuid(input.getMappingId());
        WarehouseSourceMappingDO row = new WarehouseSourceMappingDO().setMappingId(id).setTenantId(tenantId)
                .setSourceSystem(sourceSystem).setSourceType(sourceType).setSourceId(sourceId)
                .setCanonicalType(targetType).setCanonicalId(canonicalId)
                .setWarehouseId(input.getWarehouseId()).setZoneId(input.getZoneId())
                .setLocationId(input.getLocationId()).setValidFrom(at(input.getValidFrom())).setValidTo(at(input.getValidTo()))
                .setVerificationRef(input.getVerificationRef()).setStatus("ACTIVE").setVersion(1L)
                .setCreatedAt(now).setUpdatedAt(now);
        sourceMappingMapper.insert(row);
        Map<String, Object> payload = payload("mapping_id", id);
        payload.put("source_system", row.getSourceSystem()); payload.put("source_type", row.getSourceType());
        payload.put("source_id", row.getSourceId()); payload.put("canonical_type", row.getCanonicalType());
        payload.put("canonical_id", row.getCanonicalId());
        put(payload, "warehouse_id", row.getWarehouseId()); put(payload, "zone_id", row.getZoneId());
        put(payload, "location_id", row.getLocationId()); payload.put("current_status", row.getStatus());
        return outcome("warehouse.source_mapping.changed", "warehouse_source_mapping", id, 1L, payload,
                WarehouseNetworkCommandResult.builder().warehouseId(row.getWarehouseId()).zoneId(row.getZoneId())
                        .locationId(row.getLocationId()).mappingId(id).aggregateVersion(1L).status("ACTIVE")
                        .duplicate(false).build());
    }

    private Outcome endSourceMapping(Long tenantId, WarehouseNetworkCommand command, LocalDateTime now) {
        WarehouseSourceMappingDefinition input = command.getSourceMapping();
        WarehouseSourceMappingDO row = sourceMappingMapper.selectForUpdate(tenantId, input.getMappingId());
        require(row != null && "ACTIVE".equals(row.getStatus()), "source mapping is not ACTIVE");
        requireVersion(row.getVersion(), input.getExpectedVersion());
        require(sourceMappingMapper.endCas(tenantId, row.getMappingId(), row.getVersion(), now) == 1,
                "source mapping version conflict");
        Map<String, Object> payload = payload("mapping_id", row.getMappingId());
        payload.put("source_system", row.getSourceSystem()); payload.put("source_type", row.getSourceType());
        payload.put("source_id", row.getSourceId()); payload.put("canonical_type", row.getCanonicalType());
        payload.put("canonical_id", row.getCanonicalId()); payload.put("current_status", "ENDED");
        return outcome("warehouse.source_mapping.changed", "warehouse_source_mapping", row.getMappingId(),
                row.getVersion() + 1, payload, WarehouseNetworkCommandResult.builder().warehouseId(row.getWarehouseId())
                        .zoneId(row.getZoneId()).locationId(row.getLocationId()).mappingId(row.getMappingId())
                        .aggregateVersion(row.getVersion() + 1).status("ENDED").duplicate(false).build());
    }

    private Outcome assignOperator(Long tenantId, WarehouseNetworkCommand command, LocalDateTime now) {
        WarehouseOperatorAssignmentDefinition input = command.getOperatorAssignment();
        requireActive(requireWarehouse(tenantId, input.getWarehouseId()), "warehouse");
        validateOptionalHierarchy(tenantId, input.getWarehouseId(), input.getZoneId(), input.getLocationId());
        principalValidationApi.requireActivePrincipal(input.getPrincipalId());
        String id = valueOrUuid(input.getAssignmentId());
        WarehouseOperatorAssignmentDO row = new WarehouseOperatorAssignmentDO().setAssignmentId(id).setTenantId(tenantId)
                .setWarehouseId(input.getWarehouseId()).setZoneId(input.getZoneId()).setLocationId(input.getLocationId())
                .setPrincipalId(input.getPrincipalId()).setRoleCode(input.getRoleCode()).setShiftCode(input.getShiftCode())
                .setValidFrom(at(input.getValidFrom())).setValidTo(at(input.getValidTo()))
                .setStatus("ACTIVE").setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        assignmentMapper.insert(row);
        Map<String, Object> payload = payload("assignment_id", id);
        payload.put("warehouse_id", row.getWarehouseId()); payload.put("principal_id", row.getPrincipalId());
        payload.put("role_code", row.getRoleCode()); put(payload, "zone_id", row.getZoneId());
        put(payload, "location_id", row.getLocationId()); payload.put("current_status", row.getStatus());
        return outcome("warehouse.operator_assignment.changed", "warehouse_operator_assignment", id, 1L, payload,
                WarehouseNetworkCommandResult.builder().warehouseId(row.getWarehouseId()).zoneId(row.getZoneId())
                        .locationId(row.getLocationId()).assignmentId(id).aggregateVersion(1L).status("ACTIVE")
                        .duplicate(false).build());
    }

    private Outcome endOperatorAssignment(Long tenantId, WarehouseNetworkCommand command, LocalDateTime now) {
        WarehouseOperatorAssignmentDefinition input = command.getOperatorAssignment();
        WarehouseOperatorAssignmentDO row = assignmentMapper.selectForUpdate(tenantId, input.getAssignmentId());
        require(row != null && "ACTIVE".equals(row.getStatus()), "operator assignment is not ACTIVE");
        requireVersion(row.getVersion(), input.getExpectedVersion());
        require(assignmentMapper.endCas(tenantId, row.getAssignmentId(), row.getVersion(), now) == 1,
                "operator assignment version conflict");
        Map<String, Object> payload = payload("assignment_id", row.getAssignmentId());
        payload.put("warehouse_id", row.getWarehouseId()); payload.put("principal_id", row.getPrincipalId());
        payload.put("role_code", row.getRoleCode()); payload.put("current_status", "ENDED");
        return outcome("warehouse.operator_assignment.changed", "warehouse_operator_assignment", row.getAssignmentId(),
                row.getVersion() + 1, payload, WarehouseNetworkCommandResult.builder().warehouseId(row.getWarehouseId())
                        .zoneId(row.getZoneId()).locationId(row.getLocationId()).assignmentId(row.getAssignmentId())
                        .aggregateVersion(row.getVersion() + 1).status("ENDED").duplicate(false).build());
    }

    private void appendEvent(Long tenantId, WarehouseNetworkCommand command, Outcome outcome) {
        outboxAppender.append(AppendDomainEventCommand.builder().eventType(outcome.eventType()).schemaVersion(1)
                .sourceSystem("cloudmold-warehouse").tenantId(tenantId).aggregateType(outcome.aggregateType())
                .aggregateId(outcome.aggregateId()).aggregateVersion(outcome.aggregateVersion()).eventSequence((short) 1)
                .occurredAt(command.getOccurredAt()).correlationId(command.getCorrelationId())
                .causationId(command.getCausationId()).idempotencyKey(command.getIdempotencyKey() + ":event")
                .payload(outcome.payload()).headers(Map.of("operation", command.getOperation().name()))
                .destination("lakehouse").build());
    }

    static String fingerprint(Long tenantId, WarehouseNetworkCommand command) {
        return DigestUtil.sha256Hex(tenantId + "\u001f" + JsonUtils.toJsonString(command));
    }

    private static void validate(WarehouseNetworkCommand command) {
        require(command != null && command.getOperation() != null, "operation is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) requireUuid(command.getCausationId(), "causationId");
        require(command.getOccurredAt() != null, "occurredAt is required");
        switch (command.getOperation()) {
            case DEFINE_WAREHOUSE -> {
                require(command.getWarehouse() != null, "warehouse is required");
                requireText(command.getWarehouse().getWarehouseCode(), "warehouseCode", 64);
                requireText(command.getWarehouse().getName(), "warehouse name", 128);
                requireText(command.getWarehouse().getWarehouseType(), "warehouseType", 32);
                requireText(command.getWarehouse().getTimezone(), "timezone", 64);
            }
            case CHANGE_WAREHOUSE_STATUS -> validateStatusTarget(command.getWarehouse(), "warehouse");
            case DEFINE_ZONE -> {
                require(command.getZone() != null, "zone is required");
                requireText(command.getZone().getWarehouseId(), "warehouseId", 128);
                requireText(command.getZone().getZoneCode(), "zoneCode", 64);
                requireText(command.getZone().getName(), "zone name", 128);
                requireText(command.getZone().getZoneType(), "zoneType", 32);
            }
            case CHANGE_ZONE_STATUS -> {
                require(command.getZone() != null, "zone is required");
                requireText(command.getZone().getZoneId(), "zoneId", 128);
                requireText(command.getZone().getStatus(), "status", 32);
                require(command.getZone().getExpectedVersion() != null, "expectedVersion is required");
            }
            case DEFINE_LOCATION -> {
                WarehouseLocationDefinition input = command.getLocation();
                require(input != null, "location is required");
                requireText(input.getWarehouseId(), "warehouseId", 128); requireText(input.getZoneId(), "zoneId", 128);
                requireText(input.getLocationCode(), "locationCode", 64); requireText(input.getName(), "location name", 128);
                requireText(input.getLocationType(), "locationType", 32);
            }
            case CHANGE_LOCATION_STATUS -> {
                require(command.getLocation() != null, "location is required");
                requireText(command.getLocation().getLocationId(), "locationId", 128);
                requireText(command.getLocation().getStatus(), "status", 32);
                require(command.getLocation().getExpectedVersion() != null, "expectedVersion is required");
            }
            case LINK_SOURCE -> validateSourceMapping(command.getSourceMapping(), false);
            case END_SOURCE_MAPPING -> validateSourceMapping(command.getSourceMapping(), true);
            case ASSIGN_OPERATOR -> validateAssignment(command.getOperatorAssignment(), false);
            case END_OPERATOR_ASSIGNMENT -> validateAssignment(command.getOperatorAssignment(), true);
        }
    }

    private static void validateStatusTarget(WarehouseDefinition input, String name) {
        require(input != null, name + " is required"); requireText(input.getWarehouseId(), "warehouseId", 128);
        requireText(input.getStatus(), "status", 32); require(input.getExpectedVersion() != null, "expectedVersion is required");
    }
    private static void validateSourceMapping(WarehouseSourceMappingDefinition input, boolean ending) {
        require(input != null, "sourceMapping is required");
        if (ending) {
            requireText(input.getMappingId(), "mappingId", 128); require(input.getExpectedVersion() != null, "expectedVersion is required");
            return;
        }
        requireText(input.getSourceSystem(), "sourceSystem", 32); requireText(input.getSourceType(), "sourceType", 32);
        requireText(input.getSourceId(), "sourceId", 128); requireText(input.getTargetType(), "targetType", 32);
        requireText(input.getWarehouseId(), "warehouseId", 128); requireText(input.getVerificationRef(), "verificationRef", 256);
        require(input.getValidFrom() != null, "validFrom is required");
        require(input.getValidTo() == null || input.getValidTo().isAfter(input.getValidFrom()), "validTo must be after validFrom");
    }
    private static void validateAssignment(WarehouseOperatorAssignmentDefinition input, boolean ending) {
        require(input != null, "operatorAssignment is required");
        if (ending) {
            requireText(input.getAssignmentId(), "assignmentId", 128); require(input.getExpectedVersion() != null, "expectedVersion is required");
            return;
        }
        requireText(input.getWarehouseId(), "warehouseId", 128); requireText(input.getPrincipalId(), "principalId", 128);
        requireText(input.getRoleCode(), "roleCode", 64); require(input.getValidFrom() != null, "validFrom is required");
        require(input.getValidTo() == null || input.getValidTo().isAfter(input.getValidFrom()), "validTo must be after validFrom");
    }

    private void validateTarget(Long tenantId, String type, String warehouseId, String zoneId, String locationId) {
        requireWarehouse(tenantId, warehouseId);
        switch (type) {
            case "WAREHOUSE" -> require(zoneId == null && locationId == null, "warehouse mapping cannot carry zone/location");
            case "ZONE" -> { requireText(zoneId, "zoneId", 128); require(locationId == null, "zone mapping cannot carry location");
                WarehouseZoneDO zone = requireZone(tenantId, zoneId); require(warehouseId.equals(zone.getWarehouseId()), "zone does not belong to warehouse"); }
            case "LOCATION" -> { requireText(zoneId, "zoneId", 128); requireText(locationId, "locationId", 128);
                WarehouseLocationDO location = requireLocation(tenantId, locationId);
                require(warehouseId.equals(location.getWarehouseId()) && zoneId.equals(location.getZoneId()),
                        "location hierarchy does not match mapping target"); }
            default -> throw new IllegalArgumentException("unsupported targetType");
        }
    }

    private void validateOptionalHierarchy(Long tenantId, String warehouseId, String zoneId, String locationId) {
        if (zoneId != null) {
            WarehouseZoneDO zone = requireZone(tenantId, zoneId);
            require(warehouseId.equals(zone.getWarehouseId()), "zone does not belong to warehouse"); requireActive(zone, "zone");
        }
        if (locationId != null) {
            require(zoneId != null, "location assignment requires zoneId");
            WarehouseLocationDO location = requireLocation(tenantId, locationId);
            require(warehouseId.equals(location.getWarehouseId()) && zoneId.equals(location.getZoneId()),
                    "location hierarchy does not match assignment"); requireActive(location, "location");
            require(!"LEGACY_UNALLOCATED".equals(location.getLocationType()), "migration-only location cannot receive operators");
        }
    }

    private WarehouseDO requireWarehouse(Long tenantId, String id) {
        WarehouseDO row = warehouseMapper.selectForUpdate(tenantId, id); require(row != null, "warehouse does not exist"); return row;
    }
    private WarehouseZoneDO requireZone(Long tenantId, String id) {
        WarehouseZoneDO row = zoneMapper.selectForUpdate(tenantId, id); require(row != null, "zone does not exist"); return row;
    }
    private WarehouseLocationDO requireLocation(Long tenantId, String id) {
        WarehouseLocationDO row = locationMapper.selectForUpdate(tenantId, id); require(row != null, "location does not exist"); return row;
    }
    private static void requireActive(Object row, String name) {
        String status = row instanceof WarehouseDO value ? value.getStatus()
                : row instanceof WarehouseZoneDO value ? value.getStatus() : ((WarehouseLocationDO) row).getStatus();
        require("ACTIVE".equals(status), name + " is not ACTIVE");
    }
    private static String requireLifecycle(String status) {
        require("ACTIVE".equals(status) || "INACTIVE".equals(status), "status must be ACTIVE or INACTIVE"); return status;
    }
    private static void requireVersion(Long actual, Long expected) {
        require(expected != null && Objects.equals(actual, expected), "expectedVersion does not match aggregate version");
    }
    private static Outcome outcome(String event, String type, String id, Long version, Map<String, Object> payload,
                                   WarehouseNetworkCommandResult result) {
        return new Outcome(event, type, id, version, payload, result);
    }
    private static Map<String, Object> payload(String idName, String id) {
        Map<String, Object> result = new LinkedHashMap<>(); result.put(idName, id); return result;
    }
    private static Map<String, Object> entityPayload(String entityType, String entityId, String previousStatus,
                                                     String currentStatus) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entity_type", entityType); result.put("entity_id", entityId);
        result.put("previous_status", previousStatus);
        result.put("current_status", currentStatus); return result;
    }
    private static String canonicalId(String canonicalType, String warehouseId, String zoneId, String locationId) {
        return switch (canonicalType) {
            case "WAREHOUSE" -> warehouseId;
            case "ZONE" -> zoneId;
            case "LOCATION" -> locationId;
            default -> throw new IllegalArgumentException("unsupported targetType");
        };
    }
    private static void put(Map<String, Object> map, String key, Object value) { if (value != null) map.put(key, value); }
    private static String valueOrUuid(String value) { return value == null || value.isBlank() ? UUID.randomUUID().toString() : value; }
    private static LocalDateTime at(Instant value) { return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
    private static void requireUuid(String value, String field) { requireText(value, field, 36); try { UUID.fromString(value); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException(field + " must be a UUID", error); } }
    private static void requireText(String value, String field, int max) {
        require(value != null && !value.isBlank() && value.length() <= max, field + " is required and too long");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }

    private record Outcome(String eventType, String aggregateType, String aggregateId, Long aggregateVersion,
                           Map<String, Object> payload, WarehouseNetworkCommandResult result) {}
}
