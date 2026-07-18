package cn.iocoder.yudao.module.cloudmold.dreamplant.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.dreamplant.api.*;
import cn.iocoder.yudao.module.cloudmold.dreamplant.dal.dataobject.DreamPlantRecords.WorldMap;
import cn.iocoder.yudao.module.cloudmold.dreamplant.dal.mysql.DreamPlantKnowledgeMapper;
import cn.iocoder.yudao.module.cloudmold.dreamplant.dal.mysql.DreamPlantStoreMapper;
import cn.iocoder.yudao.module.cloudmold.dreamplant.service.worker.DreamPlantExplorationWorker;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DreamPlantKnowledgeServiceImpl implements DreamPlantKnowledgeCommandApi, DreamPlantKnowledgeQueryApi {

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.:-]{1,191}");
    private static final Pattern SAFE_REF = Pattern.compile("(?:sha256:[0-9a-f]{64}|restricted:[A-Za-z0-9][A-Za-z0-9._/-]{7,159})");
    private static final int DEFAULT_LIMIT = 50;
    private final DreamPlantKnowledgeMapper mapper;
    private final DreamPlantStoreMapper storeMapper;
    private final ObjectProvider<DreamPlantCommandApi> commandApi;
    private final ObjectProvider<DreamPlantExplorationWorker> explorationWorker;

    @Transactional(rollbackFor = Exception.class)
    public void importWorldMapProjection(String mapKey, String payloadJson, String sourceRef, Instant observedAt) {
        JsonNode root = JsonUtils.parseTree(payloadJson);
        importAssets(mapKey, root.path("capabilities"), DreamPlantAssetType.CAPABILITY, "id", sourceRef, observedAt);
        importAssets(mapKey, root.path("products"), DreamPlantAssetType.PRODUCT, "id", sourceRef, observedAt);
        importAssets(mapKey, root.path("operationAgents"), DreamPlantAssetType.OPERATION_AGENT, "id", sourceRef, observedAt);
        importAssets(mapKey, root.path("solutions"), DreamPlantAssetType.SOLUTION, "id", sourceRef, observedAt);
        importAssets(mapKey, root.path("agentRoles"), DreamPlantAssetType.WORKFLOW, "id", sourceRef, observedAt);
        importAssets(mapKey, root.path("phaseRoadmap"), DreamPlantAssetType.PLAYBOOK, "phase", sourceRef, observedAt);
        importRelations(mapKey, root.path("products"), "capabilityIds", DreamPlantAssetType.PRODUCT,
                DreamPlantAssetType.CAPABILITY, sourceRef, observedAt);
        importRelations(mapKey, root.path("operationAgents"), "productIds", DreamPlantAssetType.OPERATION_AGENT,
                DreamPlantAssetType.PRODUCT, sourceRef, observedAt);
        importRelations(mapKey, root.path("solutions"), "operationAgentIds", DreamPlantAssetType.SOLUTION,
                DreamPlantAssetType.OPERATION_AGENT, sourceRef, observedAt);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DreamPlantCommandResult upsertAsset(DreamPlantAssetCommand command) {
        require(command != null && command.getAssetType() != null, "asset command and assetType are required");
        String table = assetTable(command.getAssetType());
        validateMapAndId(command.getMapKey(), command.getAssetId());
        requireText(command.getDisplayName(), "displayName");
        requireText(command.getStatus(), "status");
        requireSafeRef(command.getSourceRef(), "sourceRef");
        CanonicalValue details = canonical(command.getDetailsJson(), command.getDetailsSha256());
        Long tenantId = tenant();
        LocalDateTime now = utc(command.getOccurredAt());
        Long expected = Optional.ofNullable(command.getExpectedVersion()).orElse(0L);
        command.setCanonicalKey(blankTo(command.getCanonicalKey(), command.getAssetId()));
        DreamPlantAssetView existing = mapper.selectAsset(table, tenantId, command.getMapKey(), command.getAssetId());
        if (assetUnchanged(existing, command, details.sha256())) {
            return result(command.getMapKey(), existing.getVersion(), "UNCHANGED", true);
        }
        int changed;
        long nextVersion;
        if (existing == null) {
            require(expected == 0, "new asset expectedVersion must be zero");
            changed = mapper.insertAsset(table, tenantId, command, details.json(), details.sha256(),
                    utcNullable(command.getEffectiveAt()), utcNullable(command.getObservedAt()), now);
            nextVersion = 1L;
        } else {
            require(Objects.equals(existing.getVersion(), expected), "asset version conflict");
            changed = mapper.updateAsset(table, tenantId, command, details.json(), details.sha256(),
                    utcNullable(command.getEffectiveAt()), utcNullable(command.getObservedAt()), now);
            nextVersion = expected + 1;
        }
        require(changed == 1, "asset version conflict");
        mapper.insertAssetRevision(tenantId, command.getMapKey(), command.getAssetType().name(), command.getAssetId(),
                nextVersion, details.json(), details.sha256(), command.getSourceRef(), command.getEvidenceRef(), now);
        return result(command.getMapKey(), nextVersion, "UPSERTED", false);
    }

    private static boolean assetUnchanged(DreamPlantAssetView existing, DreamPlantAssetCommand command,
                                          String detailsSha256) {
        return existing != null
                && Objects.equals(existing.getDisplayName(), command.getDisplayName())
                && Objects.equals(existing.getStatus(), command.getStatus())
                && Objects.equals(existing.getLifecycleStage(), command.getLifecycleStage())
                && Objects.equals(existing.getOwnerPrincipalId(), command.getOwnerPrincipalId())
                && Objects.equals(existing.getCanonicalKey(), command.getCanonicalKey())
                && Objects.equals(existing.getDetailsSha256(), detailsSha256);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DreamPlantCommandResult linkAssets(DreamPlantRelationCommand command) {
        require(command != null, "relation command is required");
        validateMapAndId(command.getMapKey(), command.getRelationId());
        validateId(command.getFromAssetId(), "fromAssetId");
        validateId(command.getToAssetId(), "toAssetId");
        requireSafeRef(command.getSourceRef(), "sourceRef");
        CanonicalValue details = canonical(command.getDetailsJson(), command.getDetailsSha256());
        String table = relationTable(command.getFromAssetType(), command.getToAssetType());
        mapper.upsertRelation(table, tenant(), command, details.json(), details.sha256(),
                utcNullable(command.getEffectiveAt()), utcNullable(command.getObservedAt()), utc(command.getOccurredAt()));
        DreamPlantRelationView saved = mapper.selectRelation(table, tenant(), command.getMapKey(), command.getRelationId());
        return result(command.getMapKey(), saved.getVersion(), "LINKED", false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DreamPlantCommandResult recordEvidence(DreamPlantEvidenceCommand command) {
        require(command != null, "evidence command is required");
        validateMapAndId(command.getMapKey(), command.getEvidenceId());
        requireSafeRef(command.getContentRef(), "contentRef");
        requireSafeRef(command.getSourceRef(), "sourceRef");
        CanonicalValue details = canonical(command.getDetailsJson(), command.getDetailsSha256());
        LocalDateTime now = utc(command.getOccurredAt());
        mapper.upsertEvidence(tenant(), command, details.json(), details.sha256(),
                utcNullable(command.getObservedAt()), utc(Optional.ofNullable(command.getCapturedAt()).orElse(command.getOccurredAt())), now);
        return result(command.getMapKey(), mapper.selectEvidence(tenant(), command.getMapKey(), command.getEvidenceId()).getVersion(),
                "RECORDED", false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DreamPlantCommandResult recordMetric(DreamPlantMetricCommand command) {
        require(command != null && command.getMetricValue() != null, "metric command and metricValue are required");
        validateMapAndId(command.getMapKey(), command.getMetricId());
        requireSafeRef(command.getSourceRef(), "sourceRef");
        CanonicalValue details = canonical(command.getDetailsJson(), command.getDetailsSha256());
        String dimensions = canonical(blankTo(command.getDimensionJson(), "{}"), null).json();
        LocalDateTime now = utc(command.getOccurredAt());
        mapper.upsertMetric(tenant(), command, dimensions, details.json(), details.sha256(),
                utcNullable(command.getWindowStartAt()), utcNullable(command.getWindowEndAt()),
                utc(Optional.ofNullable(command.getMeasuredAt()).orElse(command.getOccurredAt())), now);
        return result(command.getMapKey(), mapper.selectMetric(tenant(), command.getMapKey(), command.getMetricId()).getVersion(),
                "RECORDED", false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DreamPlantCommandResult upsertSyncCheckpoint(DreamPlantSyncCommand command) {
        require(command != null, "sync command is required");
        validateMapAndId(command.getMapKey(), command.getSyncKey());
        requireSafeRef(command.getSourceRef(), "sourceRef");
        CanonicalValue details = canonical(command.getDetailsJson(), command.getDetailsSha256());
        LocalDateTime now = utc(command.getOccurredAt());
        mapper.upsertSync(tenant(), command, details.json(), details.sha256(),
                utc(Optional.ofNullable(command.getCheckpointAt()).orElse(command.getOccurredAt())), now);
        return result(command.getMapKey(), mapper.selectSync(tenant(), command.getMapKey(), command.getSyncKey()).getVersion(),
                "CHECKPOINTED", false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DreamPlantCommandResult recordDrift(DreamPlantDriftCommand command) {
        require(command != null, "drift command is required");
        validateMapAndId(command.getMapKey(), command.getDriftId());
        requireSafeRef(command.getSourceRef(), "sourceRef");
        CanonicalValue details = canonical(command.getDetailsJson(), command.getDetailsSha256());
        LocalDateTime now = utc(command.getOccurredAt());
        mapper.upsertDrift(tenant(), command, details.json(), details.sha256(),
                utc(Optional.ofNullable(command.getDetectedAt()).orElse(command.getOccurredAt())),
                utcNullable(command.getResolvedAt()), now);
        return result(command.getMapKey(), mapper.selectDrift(tenant(), command.getMapKey(), command.getDriftId()).getVersion(),
                "RECORDED", false);
    }

    @Override
    public DreamPlantCommandResult rebuildProjection(String mapKey, String projectionKey, String idempotencyKey,
                                                     String runTraceId, Instant requestedAt) {
        require("frontend".equals(projectionKey), "only the governed frontend projection is supported");
        Long tenantId = tenant();
        WorldMap map = storeMapper.selectWorldMap(tenantId, mapKey);
        require(map != null && map.getCurrentVersion() > 0, "world map does not exist");
        ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();
        root.set("capabilities", assetArray(mapKey, DreamPlantAssetType.CAPABILITY));
        root.set("products", assetArray(mapKey, DreamPlantAssetType.PRODUCT));
        root.set("operationAgents", assetArray(mapKey, DreamPlantAssetType.OPERATION_AGENT));
        root.set("solutions", assetArray(mapKey, DreamPlantAssetType.SOLUTION));
        root.set("agentRoles", assetArray(mapKey, DreamPlantAssetType.WORKFLOW));
        root.set("phaseRoadmap", assetArray(mapKey, DreamPlantAssetType.PLAYBOOK));
        CanonicalValue projection = canonical(JsonUtils.toJsonString(root), null);
        return commandApi.getObject().execute(DreamPlantCommand.builder().operation(DreamPlantOperation.PUBLISH_WORLD_MAP)
                .idempotencyKey(idempotencyKey).runTraceId(runTraceId).occurredAt(requestedAt).mapKey(mapKey)
                .expectedVersion(map.getCurrentVersion()).schemaVersion("dreamplant.bootstrap.v2")
                .payloadJson(projection.json()).payloadSha256(projection.sha256())
                .sourceRef("sha256:" + projection.sha256()).publiclyReadable(map.getPubliclyReadable()).build());
    }

    @Override
    public DreamPlantCommandResult runExplorationNow(String mapKey, String explorationRunId, String idempotencyKey,
                                                     String runTraceId, Instant requestedAt) {
        var run = explorationWorker.getObject().runBatch("hsf-" + runTraceId, 100, utc(requestedAt));
        return DreamPlantCommandResult.builder().duplicate(false).mapKey(mapKey).explorationRunId(explorationRunId)
                .status(run.completed() > 0 ? "DISPATCHED" : "NOT_DUE").build();
    }

    @Override public DreamPlantAssetView getAsset(String mapKey, String assetId) {
        for (DreamPlantAssetType type : storedAssetTypes()) {
            DreamPlantAssetView value = mapper.selectAsset(assetTable(type), tenant(), mapKey, assetId);
            if (value != null) { value.setAssetType(type); return value; }
        }
        throw new IllegalArgumentException("DreamPlant asset does not exist");
    }

    @Override public List<DreamPlantAssetView> listAssets(String mapKey, DreamPlantAssetType type, String status, Integer limit) {
        require(type != null, "assetType is required");
        List<DreamPlantAssetView> values = mapper.selectAssets(assetTable(type), tenant(), mapKey, status, limit(limit));
        values.forEach(value -> value.setAssetType(type));
        return values;
    }

    @Override public DreamPlantRelationView getRelation(String mapKey, String relationId) {
        for (String table : relationTables()) {
            DreamPlantRelationView value = mapper.selectRelation(table, tenant(), mapKey, relationId);
            if (value != null) return decorateRelation(table, value);
        }
        throw new IllegalArgumentException("DreamPlant relation does not exist");
    }

    @Override public List<DreamPlantRelationView> listRelations(String mapKey, String assetId, String relationType, Integer limit) {
        List<DreamPlantRelationView> values = new ArrayList<>();
        for (String table : relationTables()) {
            mapper.selectRelations(table, tenant(), mapKey, assetId, relationType, limit(limit)).stream()
                    .map(value -> decorateRelation(table, value)).forEach(values::add);
        }
        return values.stream().sorted(Comparator.comparing(DreamPlantRelationView::getUpdatedAt).reversed())
                .limit(limit(limit)).toList();
    }

    @Override public DreamPlantEvidenceView getEvidence(String mapKey, String evidenceId) { return required(mapper.selectEvidence(tenant(), mapKey, evidenceId), "evidence"); }
    @Override public List<DreamPlantEvidenceView> listEvidence(String mapKey, String subjectType, String subjectId, Integer limit) { return mapper.selectEvidenceList(tenant(), mapKey, subjectType, subjectId, limit(limit)); }
    @Override public DreamPlantMetricView getMetric(String mapKey, String metricId) { return required(mapper.selectMetric(tenant(), mapKey, metricId), "metric"); }
    @Override public List<DreamPlantMetricView> listMetrics(String mapKey, String subjectType, String subjectId, String metricCode, Integer limit) { return mapper.selectMetrics(tenant(), mapKey, subjectType, subjectId, metricCode, limit(limit)); }
    @Override public DreamPlantSyncView getSyncCheckpoint(String mapKey, String syncKey) { return required(mapper.selectSync(tenant(), mapKey, syncKey), "sync checkpoint"); }
    @Override public List<DreamPlantSyncView> listSyncCheckpoints(String mapKey, String sourceSystem, Integer limit) { return mapper.selectSyncs(tenant(), mapKey, sourceSystem, limit(limit)); }
    @Override public DreamPlantDriftView getDrift(String mapKey, String driftId) { return required(mapper.selectDrift(tenant(), mapKey, driftId), "drift record"); }
    @Override public List<DreamPlantDriftView> listDrift(String mapKey, String subjectType, String subjectId, String severity, Integer limit) { return mapper.selectDrifts(tenant(), mapKey, subjectType, subjectId, severity, limit(limit)); }

    private ArrayNode assetArray(String mapKey, DreamPlantAssetType type) {
        ArrayNode array = JsonUtils.getObjectMapper().createArrayNode();
        mapper.selectAssets(assetTable(type), tenant(), mapKey, null, 1000)
                .forEach(asset -> array.add(JsonUtils.parseTree(asset.getDetailsJson())));
        return array;
    }

    private void importAssets(String mapKey, JsonNode items, DreamPlantAssetType type, String idField,
                              String sourceRef, Instant observedAt) {
        if (!items.isArray()) return;
        for (JsonNode item : items) {
            String originalId = item.path(idField).asText();
            if (originalId.isBlank()) continue;
            String assetId = type == DreamPlantAssetType.PLAYBOOK ? stableId(originalId) : originalId;
            DreamPlantAssetView existing = mapper.selectAsset(assetTable(type), tenant(), mapKey, assetId);
            String name = item.path("name").asText(item.path("title").asText(originalId));
            String status = item.path("status").asText("ACTIVE");
            String detailsJson = JsonUtils.toJsonString(item);
            String hash = DreamPlantCanonicalJson.canonicalize(detailsJson).sha256();
            upsertAsset(DreamPlantAssetCommand.builder().mapKey(mapKey).assetId(assetId).assetType(type)
                    .idempotencyKey("import:" + type + ":" + assetId + ":" + hash.substring(0, 12))
                    .runTraceId("projection-import").expectedVersion(existing == null ? 0L : existing.getVersion())
                    .displayName(name).status(status).lifecycleStage(item.path("lifecycleStage").asText(null))
                    .ownerPrincipalId(item.path("owner").asText(item.path("ownerAgent").asText(null)))
                    .canonicalKey(assetId).detailsJson(detailsJson).detailsSha256(hash).sourceRef(sourceRef)
                    .observedAt(observedAt).occurredAt(observedAt).build());
        }
    }

    private void importRelations(String mapKey, JsonNode items, String targetIdsField, DreamPlantAssetType fromType,
                                 DreamPlantAssetType toType, String sourceRef, Instant observedAt) {
        if (!items.isArray()) return;
        for (JsonNode item : items) {
            String fromId = item.path("id").asText();
            if (fromId.isBlank() || !item.path(targetIdsField).isArray()) continue;
            for (JsonNode target : item.path(targetIdsField)) {
                String toId = target.asText();
                if (toId.isBlank()) continue;
                String relationId = stableId(fromType.name() + ":" + fromId + ":" + toType.name() + ":" + toId);
                String details = "{\"source\":\"world-map-projection\"}";
                linkAssets(DreamPlantRelationCommand.builder().mapKey(mapKey).relationId(relationId)
                        .idempotencyKey("import-relation:" + relationId).runTraceId("projection-import")
                        .fromAssetId(fromId).fromAssetType(fromType).toAssetId(toId).toAssetType(toType)
                        .relationType("CONTAINS").status("ACTIVE").detailsJson(details)
                        .detailsSha256(DigestUtil.sha256Hex(details)).sourceRef(sourceRef)
                        .observedAt(observedAt).occurredAt(observedAt).build());
            }
        }
    }

    private static String stableId(String value) {
        String slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.length() >= 2 ? slug.substring(0, Math.min(96, slug.length()))
                : "asset-" + DigestUtil.sha256Hex(value).substring(0, 16);
    }

    private static String assetTable(DreamPlantAssetType type) {
        return switch (type) {
            case CAPABILITY -> "cloudmold_dreamplant_capability";
            case PRODUCT -> "cloudmold_dreamplant_product";
            case OPERATION_AGENT -> "cloudmold_dreamplant_operation_agent";
            case SOLUTION -> "cloudmold_dreamplant_solution";
            case WORKFLOW -> "cloudmold_dreamplant_agent_role";
            case PLAYBOOK -> "cloudmold_dreamplant_phase_roadmap";
            default -> throw new IllegalArgumentException("unsupported normalized DreamPlant assetType: " + type);
        };
    }

    private static List<DreamPlantAssetType> storedAssetTypes() {
        return List.of(DreamPlantAssetType.CAPABILITY, DreamPlantAssetType.PRODUCT, DreamPlantAssetType.OPERATION_AGENT,
                DreamPlantAssetType.SOLUTION, DreamPlantAssetType.WORKFLOW, DreamPlantAssetType.PLAYBOOK);
    }

    private static String relationTable(DreamPlantAssetType from, DreamPlantAssetType to) {
        if (from == DreamPlantAssetType.PRODUCT && to == DreamPlantAssetType.CAPABILITY) return relationTables().get(0);
        if (from == DreamPlantAssetType.OPERATION_AGENT && to == DreamPlantAssetType.PRODUCT) return relationTables().get(1);
        if (from == DreamPlantAssetType.SOLUTION && to == DreamPlantAssetType.OPERATION_AGENT) return relationTables().get(2);
        throw new IllegalArgumentException("unsupported normalized DreamPlant relation");
    }

    private static List<String> relationTables() {
        return List.of("cloudmold_dreamplant_product_capability", "cloudmold_dreamplant_operation_agent_product",
                "cloudmold_dreamplant_solution_operation_agent");
    }

    private static DreamPlantRelationView decorateRelation(String table, DreamPlantRelationView value) {
        if (table.endsWith("product_capability")) { value.setFromAssetType(DreamPlantAssetType.PRODUCT); value.setToAssetType(DreamPlantAssetType.CAPABILITY); }
        else if (table.endsWith("agent_product")) { value.setFromAssetType(DreamPlantAssetType.OPERATION_AGENT); value.setToAssetType(DreamPlantAssetType.PRODUCT); }
        else { value.setFromAssetType(DreamPlantAssetType.SOLUTION); value.setToAssetType(DreamPlantAssetType.OPERATION_AGENT); }
        return value;
    }

    private static CanonicalValue canonical(String json, String submittedHash) {
        require(json != null && !json.isBlank(), "detailsJson is required");
        DreamPlantCanonicalJson.CanonicalDocument value = DreamPlantCanonicalJson.canonicalize(json);
        if (submittedHash != null) {
            require(submittedHash.equals(DigestUtil.sha256Hex(json)) || submittedHash.equals(value.sha256()),
                    "detailsSha256 does not match detailsJson");
        }
        return new CanonicalValue(value.json(), value.sha256());
    }

    private static DreamPlantCommandResult result(String mapKey, Long version, String status, boolean duplicate) {
        return DreamPlantCommandResult.builder().duplicate(duplicate).mapKey(mapKey).mapVersion(version).status(status).build();
    }

    private static Long tenant() { return TenantContextHolder.getRequiredTenantId(); }
    private static LocalDateTime utc(Instant value) { return LocalDateTime.ofInstant(Optional.ofNullable(value).orElseGet(Instant::now), ZoneOffset.UTC); }
    private static LocalDateTime utcNullable(Instant value) { return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
    private static int limit(Integer value) { int result = value == null ? DEFAULT_LIMIT : value; require(result > 0 && result <= 1000, "limit must be between 1 and 1000"); return result; }
    private static void validateMapAndId(String mapKey, String id) { require(mapKey != null && SAFE_ID.matcher(mapKey).matches(), "mapKey is invalid"); validateId(id, "id"); }
    private static void validateId(String id, String field) { require(id != null && SAFE_ID.matcher(id).matches(), field + " is invalid"); }
    private static void requireText(String value, String field) { require(value != null && !value.isBlank() && value.length() <= 256, field + " is required"); }
    private static void requireSafeRef(String value, String field) { require(value != null && SAFE_REF.matcher(value).matches(), field + " must be an opaque restricted: or sha256: reference"); }
    private static String blankTo(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static <T> T required(T value, String name) { require(value != null, "DreamPlant " + name + " does not exist"); return value; }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
    private record CanonicalValue(String json, String sha256) {}
}
