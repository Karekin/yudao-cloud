package cn.iocoder.yudao.module.cloudmold.dreamplant.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.dreamplant.api.*;
import cn.iocoder.yudao.module.cloudmold.dreamplant.dal.dataobject.DreamPlantRecords.*;
import cn.iocoder.yudao.module.cloudmold.dreamplant.dal.mysql.DreamPlantStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DreamPlantServiceImpl implements DreamPlantCommandApi, DreamPlantQueryApi {
    static final int OPERATION_SUCCEEDED = 10;
    private static final int MAX_WORLD_MAP_BYTES = 5 * 1024 * 1024;
    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9-]{1,63}");
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{1,127}");
    private static final Pattern SCHEMA = Pattern.compile("dreamplant\\.[a-z][a-z0-9.-]{1,95}\\.v[1-9][0-9]*");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SAFE_REF = Pattern.compile("(?:sha256:[0-9a-f]{64}|restricted:[A-Za-z0-9][A-Za-z0-9._/-]{7,159})");
    private static final Set<String> OUTCOME_STATUSES = Set.of("RUNNING", "SUCCEEDED", "FAILED", "NEEDS_REVIEW", "CANCELLED");
    private static final Set<String> EXPLORATION_STATUSES = Set.of(
            "QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "NEEDS_REVIEW", "CANCELLED");

    private final DreamPlantStoreMapper mapper;
    @Autowired(required = false)
    private DreamPlantKnowledgeServiceImpl knowledgeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DreamPlantCommandResult execute(DreamPlantCommand command) {
        validateCommon(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Instant occurredAt = command.getOccurredAt() == null ? now.toInstant(ZoneOffset.UTC) : command.getOccurredAt();
        require(!occurredAt.isAfter(Instant.now().plusSeconds(300)), "occurredAt cannot be materially in the future");
        String requestHash = DigestUtil.sha256Hex(tenantId + "|" + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve DreamPlant operation");
        Operation operation = mapper.selectOperationForUpdate(tenantId, operationId);
        require(operation != null, "DreamPlant operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(operation.getRequestHash(), requestHash),
                    "idempotency key conflicts with a different DreamPlant payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing DreamPlant operation is not complete");
            DreamPlantCommandResult replay = JsonUtils.parseObject(operation.getResultJson(), DreamPlantCommandResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        DreamPlantCommandResult result = switch (command.getOperation()) {
            case PUBLISH_WORLD_MAP -> publishWorldMap(tenantId, operationId, command, occurredAt, now);
            case SUBMIT_EXPLORATION -> submitExploration(tenantId, operationId, command, now);
            case RECORD_EXPLORATION_OUTCOME -> recordExplorationOutcome(tenantId, operationId, command, now);
        };
        String aggregateId = result.getExplorationRunId() == null ? result.getMapKey() : result.getExplorationRunId();
        require(mapper.markOperationSucceeded(tenantId, operationId, aggregateId,
                JsonUtils.toJsonString(result), now) == 1, "DreamPlant operation completion conflict");
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public DreamPlantWorldMapSnapshot getPublishedWorldMap(String mapKey) {
        requireMapKey(mapKey);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        WorldMap worldMap = mapper.selectWorldMap(tenantId, mapKey);
        require(worldMap != null && worldMap.getCurrentVersion() > 0, "DreamPlant world map does not exist");
        Snapshot snapshot = mapper.selectSnapshot(tenantId, mapKey, worldMap.getCurrentVersion());
        require(snapshot != null, "DreamPlant published snapshot does not exist");
        return snapshotView(snapshot, worldMap.getPubliclyReadable());
    }

    @Override
    @Transactional(readOnly = true)
    public DreamPlantWorldMapSnapshot getPublicWorldMap(String mapKey) {
        requireMapKey(mapKey);
        Snapshot snapshot = mapper.selectPublicSnapshot(mapKey);
        require(snapshot != null, "public DreamPlant world map does not exist");
        return snapshotView(snapshot, true);
    }

    @Override
    @Transactional(readOnly = true)
    public DreamPlantExplorationView getExploration(String explorationRunId) {
        requireId(explorationRunId, "explorationRunId");
        ExplorationRun run = mapper.selectExploration(TenantContextHolder.getRequiredTenantId(), explorationRunId);
        require(run != null, "DreamPlant exploration does not exist");
        return explorationView(run);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DreamPlantExplorationView> listExplorations(String status, Integer limit) {
        require(EXPLORATION_STATUSES.contains(status), "unsupported exploration status");
        require(limit != null && limit > 0 && limit <= 100, "limit must be between 1 and 100");
        return mapper.selectExplorations(TenantContextHolder.getRequiredTenantId(), status, limit).stream()
                .map(DreamPlantServiceImpl::explorationView).toList();
    }

    private DreamPlantCommandResult publishWorldMap(Long tenantId, Long operationId, DreamPlantCommand command,
                                                    Instant occurredAt, LocalDateTime now) {
        requireMapKey(command.getMapKey());
        require(command.getExpectedVersion() != null && command.getExpectedVersion() >= 0,
                "expectedVersion must be non-negative");
        require(command.getSchemaVersion() != null && SCHEMA.matcher(command.getSchemaVersion()).matches(),
                "schemaVersion must be a versioned DreamPlant schema");
        requireJson(command.getPayloadJson(), "payloadJson", MAX_WORLD_MAP_BYTES);
        require(command.getPayloadSha256() != null && SHA256.matcher(command.getPayloadSha256()).matches(),
                "payloadSha256 must be lowercase SHA-256");
        require(command.getPayloadSha256().equals(DigestUtil.sha256Hex(command.getPayloadJson())),
                "payloadSha256 does not match payloadJson");
        requireSafeRef(command.getSourceRef(), "sourceRef");
        require(command.getPubliclyReadable() != null, "publiclyReadable is required");

        WorldMap worldMap = mapper.selectWorldMapForUpdate(tenantId, command.getMapKey());
        if (worldMap == null) {
            require(command.getExpectedVersion() == 0, "new world map expectedVersion must be zero");
            worldMap = new WorldMap().setTenantId(tenantId).setMapKey(command.getMapKey()).setCurrentVersion(0L)
                    .setPubliclyReadable(command.getPubliclyReadable()).setCreatedAt(now).setUpdatedAt(now);
            require(mapper.insertWorldMap(worldMap) == 1, "failed to create DreamPlant world map");
        }
        require(Objects.equals(worldMap.getCurrentVersion(), command.getExpectedVersion()),
                "DreamPlant world map version conflict");
        long nextVersion = worldMap.getCurrentVersion() + 1;
        DreamPlantCanonicalJson.CanonicalDocument canonical = DreamPlantCanonicalJson.canonicalize(command.getPayloadJson());
        Snapshot snapshot = new Snapshot().setTenantId(tenantId).setMapKey(command.getMapKey())
                .setVersion(nextVersion).setSchemaVersion(command.getSchemaVersion())
                .setPayloadJson(canonical.json()).setPayloadSha256(canonical.sha256()).setCanonicalHashVerified(true)
                .setSourceRef(command.getSourceRef()).setPublishedAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
                .setOperationId(operationId);
        require(mapper.insertSnapshot(snapshot) == 1, "failed to persist immutable DreamPlant snapshot");
        if (knowledgeService != null) {
            knowledgeService.importWorldMapProjection(command.getMapKey(), canonical.json(), command.getSourceRef(), occurredAt);
        }
        require(mapper.advanceWorldMap(tenantId, command.getMapKey(), worldMap.getCurrentVersion(),
                command.getPubliclyReadable(), now) == 1, "DreamPlant world map version conflict");
        return DreamPlantCommandResult.builder().operationId(operationId).duplicate(false)
                .mapKey(command.getMapKey()).mapVersion(nextVersion).status("PUBLISHED").build();
    }

    private DreamPlantCommandResult submitExploration(Long tenantId, Long operationId, DreamPlantCommand command,
                                                       LocalDateTime now) {
        requireMapKey(command.getMapKey());
        requireText(command.getIntent(), "intent", 8, 4000);
        requireJson(command.getContextJson(), "contextJson", 64 * 1024);
        requireId(command.getRequestedByPrincipalId(), "requestedByPrincipalId");
        String runId = command.getExplorationRunId() == null || command.getExplorationRunId().isBlank()
                ? UUID.randomUUID().toString() : command.getExplorationRunId();
        requireId(runId, "explorationRunId");
        WorldMap worldMap = mapper.selectWorldMap(tenantId, command.getMapKey());
        require(worldMap != null && worldMap.getCurrentVersion() > 0,
                "exploration requires a published tenant-scoped world map");
        DreamPlantCanonicalJson.CanonicalDocument context = DreamPlantCanonicalJson.canonicalize(command.getContextJson());
        ExplorationRun run = new ExplorationRun().setTenantId(tenantId).setExplorationRunId(runId)
                .setMapKey(command.getMapKey()).setIntent(command.getIntent().trim())
                .setContextJson(context.json()).setRequestedByPrincipalId(command.getRequestedByPrincipalId())
                .setStatus("QUEUED").setVersion(1L).setMaxAttempts(5).setNextRetryAt(now)
                .setCreatedAt(now).setUpdatedAt(now).setOperationId(operationId);
        require(mapper.insertExploration(run) == 1, "failed to queue DreamPlant exploration");
        return DreamPlantCommandResult.builder().operationId(operationId).duplicate(false)
                .mapKey(command.getMapKey()).mapVersion(worldMap.getCurrentVersion()).explorationRunId(runId)
                .explorationVersion(1L).status("QUEUED").build();
    }

    private DreamPlantCommandResult recordExplorationOutcome(Long tenantId, Long operationId,
                                                              DreamPlantCommand command, LocalDateTime now) {
        requireId(command.getExplorationRunId(), "explorationRunId");
        require(command.getExpectedVersion() != null && command.getExpectedVersion() > 0,
                "expectedVersion must be positive");
        require(OUTCOME_STATUSES.contains(command.getOutcomeStatus()), "unsupported outcomeStatus");
        requireJson(command.getOutcomeJson(), "outcomeJson", 1024 * 1024);
        DreamPlantCanonicalJson.CanonicalDocument outcome = DreamPlantCanonicalJson.canonicalize(command.getOutcomeJson());
        if (Set.of("SUCCEEDED", "FAILED", "NEEDS_REVIEW").contains(command.getOutcomeStatus())) {
            requireSafeRef(command.getEvidenceRef(), "evidenceRef");
        } else if (command.getEvidenceRef() != null) {
            requireSafeRef(command.getEvidenceRef(), "evidenceRef");
        }
        ExplorationRun run = mapper.selectExplorationForUpdate(tenantId, command.getExplorationRunId());
        require(run != null, "DreamPlant exploration does not exist");
        require(Objects.equals(run.getVersion(), command.getExpectedVersion()),
                "DreamPlant exploration version conflict");
        boolean contentAddressed = command.getEvidenceRef() != null && command.getEvidenceRef().startsWith("sha256:");
        if (contentAddressed) {
            String supplied = command.getEvidenceRef().substring("sha256:".length());
            String rawHash = DigestUtil.sha256Hex(command.getOutcomeJson());
            require(supplied.equals(rawHash) || supplied.equals(outcome.sha256()),
                    "evidenceRef hash does not match outcomeJson");
        }
        String persistedEvidenceRef = contentAddressed ? "sha256:" + outcome.sha256() : command.getEvidenceRef();
        require(mapper.updateExplorationOutcome(tenantId, run.getExplorationRunId(), run.getVersion(),
                command.getOutcomeStatus(), outcome.json(), persistedEvidenceRef, contentAddressed, now) == 1,
                "DreamPlant exploration transition conflict");
        return DreamPlantCommandResult.builder().operationId(operationId).duplicate(false).mapKey(run.getMapKey())
                .explorationRunId(run.getExplorationRunId()).explorationVersion(run.getVersion() + 1)
                .status(command.getOutcomeStatus()).build();
    }

    private static DreamPlantWorldMapSnapshot snapshotView(Snapshot snapshot, Boolean publiclyReadable) {
        return DreamPlantWorldMapSnapshot.builder().mapKey(snapshot.getMapKey()).version(snapshot.getVersion())
                .schemaVersion(snapshot.getSchemaVersion()).payloadJson(snapshot.getPayloadJson())
                .payloadSha256(snapshot.getPayloadSha256()).sourceRef(snapshot.getSourceRef())
                .publiclyReadable(publiclyReadable)
                .publishedAt(snapshot.getPublishedAt().toInstant(ZoneOffset.UTC)).build();
    }

    private static DreamPlantExplorationView explorationView(ExplorationRun run) {
        return DreamPlantExplorationView.builder().explorationRunId(run.getExplorationRunId()).mapKey(run.getMapKey())
                .intent(run.getIntent()).contextJson(run.getContextJson()).requestedByPrincipalId(run.getRequestedByPrincipalId())
                .status(run.getStatus()).version(run.getVersion()).outcomeJson(run.getOutcomeJson())
                .evidenceRef(run.getEvidenceRef()).createdAt(run.getCreatedAt().toInstant(ZoneOffset.UTC))
                .updatedAt(run.getUpdatedAt().toInstant(ZoneOffset.UTC)).build();
    }

    private static void validateCommon(DreamPlantCommand command) {
        require(command != null, "command is required");
        require(command.getOperation() != null, "operation is required");
        requireId(command.getIdempotencyKey(), "idempotencyKey");
        requireId(command.getRunTraceId(), "runTraceId");
    }

    private static void requireMapKey(String value) {
        require(value != null && KEY.matcher(value).matches(), "mapKey must be a lowercase bounded key");
    }

    private static void requireId(String value, String field) {
        require(value != null && ID.matcher(value).matches(), field + " must be a safe bounded identifier");
    }

    private static void requireSafeRef(String value, String field) {
        require(value != null && SAFE_REF.matcher(value).matches(),
                field + " must be an opaque restricted: or sha256: reference");
    }

    private static void requireText(String value, String field, int minimum, int maximum) {
        require(value != null && value.trim().length() >= minimum && value.length() <= maximum,
                field + " must contain between " + minimum + " and " + maximum + " characters");
    }

    private static void requireJson(String value, String field, int maximumBytes) {
        require(value != null && !value.isBlank(), field + " is required");
        require(value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= maximumBytes,
                field + " exceeds its governed size limit");
        try {
            JsonUtils.parseObject(value, Object.class);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(field + " must be valid JSON", ex);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
