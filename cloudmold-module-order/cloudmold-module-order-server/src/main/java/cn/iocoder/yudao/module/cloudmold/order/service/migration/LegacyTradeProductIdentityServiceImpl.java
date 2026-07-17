package cn.iocoder.yudao.module.cloudmold.order.service.migration;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.order.api.migration.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.migration.LegacyTradeProductIdentityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LegacyTradeProductIdentityServiceImpl implements LegacyTradeProductIdentityApi {

    static final String IDENTITY_EVENT = "order.migration.legacy_trade_product_identity_assessed";
    static final String POLICY_VERSION = "legacy-trade-product-identity-v1";
    private static final int OPERATION_SUCCEEDED = 10;

    private final LegacyTradeProductIdentityMapper mapper;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LegacyTradeProductIdentityResult assess(LegacyTradeProductIdentityCommand rawCommand) {
        LegacyTradeProductIdentityCommand command = normalize(rawCommand);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        String requestHash = DigestUtil.sha256Hex(tenantId + "|" + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), command.getSourceEventId(),
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve product-identity operation");
        LegacyTradeProductIdentityOperationDO operation = mapper.selectOperationForUpdate(tenantId, operationId);
        require(operation != null, "product-identity operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key or source event conflicts with another product-identity assessment");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing product-identity assessment is not complete");
            LegacyTradeProductIdentityResult replay = JsonUtils.parseObject(
                    operation.getResultJson(), LegacyTradeProductIdentityResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        LegacyTradeBenefitMigrationRunDO sourceRun = mapper.selectSourceRun(
                tenantId, command.getSourceMigrationRunId());
        require(sourceRun != null, "source legacy Trade assessment run does not exist");
        require("legacy-trade-benefit-v5".equals(sourceRun.getPolicyVersion())
                        && Boolean.TRUE.equals(sourceRun.getItemEvidenceComplete())
                        && Boolean.TRUE.equals(sourceRun.getProductSnapshotEvidenceComplete()),
                "product identity requires complete immutable server-captured product snapshots from v5");
        List<LegacyTradeProductIdentityItemSourceDO> sourceItems = mapper.selectSourceItems(
                tenantId, command.getSourceMigrationRunId());
        require(sourceItems != null && !sourceItems.isEmpty(),
                "product identity requires a non-empty source Order Item denominator");
        require(sourceItems.size() == sourceRun.getSourceItemCount(),
                "product-identity denominator differs from the immutable source run");

        List<LegacyTradeProductIdentityItemDO> items = sourceItems.stream()
                .map(source -> assessItem(tenantId, command, source, now)).toList();
        int excludedCount = (int) items.stream().filter(value -> "EXCLUDED".equals(value.getSourcePairStatus())).count();
        int activeCount = items.size() - excludedCount;
        int unambiguousCount = countActive(items,
                value -> "SOURCE_PAIR_UNAMBIGUOUS_NOT_HISTORICAL_VERSION".equals(value.getSourcePairStatus()));
        int conflictCount = countActive(items,
                value -> "SOURCE_SKU_PARENT_CONFLICT".equals(value.getSourcePairStatus()));
        int currentObservedCount = countActive(items,
                value -> "CURRENT_RELATION_OBSERVED_NOT_HISTORICAL_VERSION".equals(value.getCurrentReferenceStatus()));
        int qualifiedCount = countActive(items,
                value -> "QUALIFIED".equals(value.getHistoricalIdentityStatus()));
        int admittedCount = countActive(items, value -> Boolean.TRUE.equals(value.getIdentityAdmissionAllowed()));
        boolean targetMappingEnabled = activeCount > 0 && admittedCount == activeCount;
        String evidenceHash = DigestUtil.sha256Hex(items.stream()
                .map(LegacyTradeProductIdentityItemDO::getEvidenceHash).sorted()
                .reduce("", (left, right) -> left + "\n" + right));
        LegacyTradeProductIdentityRunDO run = new LegacyTradeProductIdentityRunDO()
                .setIdentityRunId(command.getIdentityRunId()).setTenantId(tenantId)
                .setSourceMigrationRunId(command.getSourceMigrationRunId())
                .setPolicyVersion(command.getPolicyVersion()).setEvidenceRef(command.getEvidenceRef())
                .setGovernanceEvidenceHash(evidenceHash).setSourceItemCount(items.size())
                .setActiveItemCount(activeCount).setExcludedItemCount(excludedCount)
                .setSourcePairUnambiguousCount(unambiguousCount)
                .setSourceParentConflictItemCount(conflictCount)
                .setCurrentRelationObservedCount(currentObservedCount)
                .setHistoricalIdentityQualifiedCount(qualifiedCount)
                .setIdentityAdmittedItemCount(admittedCount).setTargetMappingEnabled(targetMappingEnabled)
                .setStatus(targetMappingEnabled ? "READY_FOR_TARGET_MAPPING_ASSESSMENT"
                        : "BLOCKED_REQUIRES_HISTORICAL_PRODUCT_IDENTITY")
                .setVersion(1L).setAssessedAt(now).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertRun(run) == 1, "failed to persist product-identity run");
        for (LegacyTradeProductIdentityItemDO item : items) {
            require(mapper.insertItem(item) == 1, "failed to persist product-identity item evidence");
            appendAssessmentEvent(tenantId, command, run, item);
        }
        LegacyTradeProductIdentityResult result = toResult(run).setOperationId(operationId);
        require(mapper.markOperationSucceeded(tenantId, operationId, run.getIdentityRunId(),
                JsonUtils.toJsonString(result), now) == 1, "product-identity operation completion conflict");
        return result;
    }

    @Override
    public LegacyTradeProductIdentityResult requireRun(String identityRunId) {
        String runId = requireUuid(identityRunId, "identityRunId");
        LegacyTradeProductIdentityRunDO run = mapper.selectRun(TenantContextHolder.getRequiredTenantId(), runId);
        require(run != null, "product-identity run does not exist");
        return toResult(run);
    }

    @Override
    public List<LegacyTradeProductIdentityItemView> listItems(String identityRunId) {
        String runId = requireUuid(identityRunId, "identityRunId");
        List<LegacyTradeProductIdentityItemDO> items = mapper.selectItems(
                TenantContextHolder.getRequiredTenantId(), runId);
        require(items != null && !items.isEmpty(), "product-identity item evidence does not exist");
        return items.stream().map(LegacyTradeProductIdentityServiceImpl::toView).toList();
    }

    static LegacyTradeProductIdentityItemDO assessItem(Long tenantId,
                                                        LegacyTradeProductIdentityCommand command,
                                                        LegacyTradeProductIdentityItemSourceDO source,
                                                        LocalDateTime now) {
        require(Objects.equals(tenantId, source.getTenantId()), "product-identity item tenant mismatch");
        boolean excluded = Boolean.TRUE.equals(source.getDeleted()) || Boolean.TRUE.equals(source.getOrderDeleted());
        String pairStatus;
        if (excluded) {
            pairStatus = "EXCLUDED";
        } else if (source.getLegacySpuId() == null || source.getLegacySpuId() <= 0
                || source.getLegacySkuId() == null || source.getLegacySkuId() <= 0
                || source.getSourceParentCardinality() == null || source.getSourceParentCardinality() == 0) {
            pairStatus = "SOURCE_IDS_MISSING";
        } else if (source.getSourceParentCardinality() == 1) {
            pairStatus = "SOURCE_PAIR_UNAMBIGUOUS_NOT_HISTORICAL_VERSION";
        } else {
            pairStatus = "SOURCE_SKU_PARENT_CONFLICT";
        }
        boolean currentObserved = !excluded && source.getCurrentSpuId() != null && source.getCurrentSkuId() != null
                && !Boolean.TRUE.equals(source.getCurrentSpuDeleted())
                && !Boolean.TRUE.equals(source.getCurrentSkuDeleted())
                && Objects.equals(source.getLegacySpuId(), source.getCurrentSpuId())
                && Objects.equals(source.getLegacySkuId(), source.getCurrentSkuId())
                && Objects.equals(source.getLegacySpuId(), source.getCurrentSkuSpuId());
        String currentStatus = excluded ? "NOT_OBSERVED_EXCLUDED"
                : currentObserved ? "CURRENT_RELATION_OBSERVED_NOT_HISTORICAL_VERSION"
                : "CURRENT_RELATION_MISSING_OR_MISMATCH";
        String currentHash = currentObserved ? currentSnapshotHash(source) : null;
        boolean qualificationExact = Objects.equals(source.getQualificationCount(), 1)
                && Objects.equals(source.getLegacyOrderItemId(), source.getQualifiedLegacyOrderItemId())
                && Objects.equals(source.getLegacySpuId(), source.getHistoricalSpuId())
                && Objects.equals(source.getLegacySkuId(), source.getHistoricalSkuId())
                && Objects.equals(source.getSourceItemEvidenceHash(), source.getQualificationSourceItemEvidenceHash())
                && isSha256(source.getHistoricalProductSnapshotHash());
        String historicalStatus = excluded ? "EXCLUDED"
                : qualificationExact ? "QUALIFIED"
                : Objects.equals(source.getQualificationCount(), 0) ? "MISSING" : "AMBIGUOUS";
        List<String> blockers = new ArrayList<>();
        if (excluded) {
            blockers.add("SOURCE_DELETED");
        } else if (!qualificationExact) {
            if ("SOURCE_IDS_MISSING".equals(pairStatus)) blockers.add("SOURCE_PRODUCT_IDS_MISSING");
            if ("SOURCE_SKU_PARENT_CONFLICT".equals(pairStatus)) {
                blockers.add("SKU_PARENT_CONFLICT_IN_IMMUTABLE_ORDER_HISTORY");
            }
            if (!currentObserved) blockers.add("CURRENT_PRODUCT_RELATION_MISSING_OR_MISMATCH");
            blockers.add("MISSING".equals(historicalStatus)
                    ? "HISTORICAL_PRODUCT_IDENTITY_QUALIFICATION_MISSING"
                    : "HISTORICAL_PRODUCT_IDENTITY_QUALIFICATION_AMBIGUOUS_OR_INVALID");
        }
        blockers = blockers.stream().distinct().sorted().toList();
        boolean admitted = !excluded && qualificationExact;
        String evidenceHash = DigestUtil.sha256Hex(String.join("\u001f",
                Objects.toString(source.getSourceItemEvidenceHash(), ""),
                Objects.toString(source.getLegacySpuId(), ""), Objects.toString(source.getLegacySkuId(), ""),
                Objects.toString(source.getSourceParentCardinality(), ""), pairStatus, currentStatus,
                Objects.toString(currentHash, ""), historicalStatus,
                Objects.toString(source.getQualificationId(), ""),
                Objects.toString(source.getHistoricalProductSnapshotHash(), ""),
                JsonUtils.toJsonString(blockers)));
        return new LegacyTradeProductIdentityItemDO()
                .setIdentityItemId(deterministicUuid(command.getIdentityRunId()
                        + "|item|" + source.getItemEvidenceId()))
                .setTenantId(tenantId).setIdentityRunId(command.getIdentityRunId())
                .setSourceMigrationRunId(command.getSourceMigrationRunId()).setCandidateId(source.getCandidateId())
                .setItemEvidenceId(source.getItemEvidenceId()).setLegacyOrderId(source.getLegacyOrderId())
                .setLegacyOrderItemId(source.getLegacyOrderItemId()).setLegacySpuId(source.getLegacySpuId())
                .setLegacySkuId(source.getLegacySkuId()).setSourceItemEvidenceHash(source.getSourceItemEvidenceHash())
                .setSourceParentCardinality(source.getSourceParentCardinality()).setSourcePairStatus(pairStatus)
                .setCurrentReferenceStatus(currentStatus).setCurrentProductSnapshotHash(currentHash)
                .setQualificationId(qualificationExact ? source.getQualificationId() : null)
                .setHistoricalIdentityStatus(historicalStatus).setBlockerCodes(JsonUtils.toJsonString(blockers))
                .setIdentityAdmissionAllowed(admitted).setTargetMappingAllowed(admitted)
                .setEvidenceHash(evidenceHash).setVersion(1L)
                .setAssessedAt(now).setCreatedAt(now).setUpdatedAt(now);
    }

    private void appendAssessmentEvent(Long tenantId, LegacyTradeProductIdentityCommand command,
                                       LegacyTradeProductIdentityRunDO run,
                                       LegacyTradeProductIdentityItemDO item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("identity_run_id", run.getIdentityRunId());
        payload.put("source_migration_run_id", run.getSourceMigrationRunId());
        payload.put("identity_item_id", item.getIdentityItemId());
        payload.put("candidate_id", item.getCandidateId());
        payload.put("item_evidence_id", item.getItemEvidenceId());
        payload.put("legacy_order_id", item.getLegacyOrderId());
        payload.put("legacy_order_item_id", item.getLegacyOrderItemId());
        payload.put("legacy_spu_id", item.getLegacySpuId());
        payload.put("legacy_sku_id", item.getLegacySkuId());
        payload.put("source_item_evidence_hash", item.getSourceItemEvidenceHash());
        payload.put("source_parent_cardinality", item.getSourceParentCardinality());
        payload.put("source_pair_status", item.getSourcePairStatus());
        payload.put("current_reference_status", item.getCurrentReferenceStatus());
        payload.put("current_product_snapshot_hash", item.getCurrentProductSnapshotHash());
        payload.put("qualification_id", item.getQualificationId());
        payload.put("historical_identity_status", item.getHistoricalIdentityStatus());
        payload.put("blocker_codes", JsonUtils.parseArray(item.getBlockerCodes(), String.class));
        payload.put("identity_admission_allowed", item.getIdentityAdmissionAllowed());
        payload.put("target_mapping_allowed", item.getTargetMappingAllowed());
        payload.put("evidence_hash", item.getEvidenceHash());
        payload.put("run_source_item_count", run.getSourceItemCount());
        payload.put("run_active_item_count", run.getActiveItemCount());
        payload.put("run_source_pair_unambiguous_count", run.getSourcePairUnambiguousCount());
        payload.put("run_source_parent_conflict_item_count", run.getSourceParentConflictItemCount());
        payload.put("run_current_relation_observed_count", run.getCurrentRelationObservedCount());
        payload.put("run_historical_identity_qualified_count", run.getHistoricalIdentityQualifiedCount());
        payload.put("run_identity_admitted_item_count", run.getIdentityAdmittedItemCount());
        payload.put("target_mapping_enabled", run.getTargetMappingEnabled());
        payload.put("run_status", run.getStatus());
        payload.put("governance_evidence_hash", run.getGovernanceEvidenceHash());
        payload.put("policy_version", run.getPolicyVersion());
        payload.put("verification_ref", run.getEvidenceRef());
        payload.put("assessed_at", item.getAssessedAt().toInstant(ZoneOffset.UTC).toString());
        String idempotency = command.getIdempotencyKey() + ":item:" + item.getItemEvidenceId();
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(deterministicUuid(tenantId + "|" + idempotency))
                .eventType(IDENTITY_EVENT).schemaVersion(1).sourceSystem("cloudmold-order")
                .tenantId(tenantId).aggregateType("legacy_trade_product_identity")
                .aggregateId(item.getIdentityItemId()).aggregateVersion(1L).eventSequence((short) 1)
                .occurredAt(command.getOccurredAt()).correlationId(command.getCorrelationId())
                .causationId(command.getCausationId()).idempotencyKey(idempotency).payload(payload)
                .headers(Map.of("identity_run_id", run.getIdentityRunId(),
                        "source_migration_run_id", run.getSourceMigrationRunId(),
                        "governance_evidence_hash", run.getGovernanceEvidenceHash()))
                .destination("lakehouse").build());
    }

    private static String currentSnapshotHash(LegacyTradeProductIdentityItemSourceDO source) {
        return DigestUtil.sha256Hex(String.join("\u001f",
                Objects.toString(source.getCurrentSpuId(), ""),
                Objects.toString(source.getCurrentSpuStatus(), ""),
                Objects.toString(source.getCurrentSpuDeleted(), ""),
                Objects.toString(source.getCurrentSpuCreatedAt(), ""),
                Objects.toString(source.getCurrentSpuUpdatedAt(), ""),
                Objects.toString(source.getCurrentSkuId(), ""),
                Objects.toString(source.getCurrentSkuSpuId(), ""),
                Objects.toString(source.getCurrentSkuDeleted(), ""),
                Objects.toString(source.getCurrentSkuCreatedAt(), ""),
                Objects.toString(source.getCurrentSkuUpdatedAt(), "")));
    }

    private static LegacyTradeProductIdentityCommand normalize(LegacyTradeProductIdentityCommand raw) {
        require(raw != null, "product-identity command is required");
        return new LegacyTradeProductIdentityCommand()
                .setIdempotencyKey(requireText(raw.getIdempotencyKey(), "idempotencyKey", 128))
                .setSourceEventId(optionalText(raw.getSourceEventId(), "sourceEventId", 128))
                .setIdentityRunId(requireUuid(raw.getIdentityRunId(), "identityRunId"))
                .setSourceMigrationRunId(requireUuid(raw.getSourceMigrationRunId(), "sourceMigrationRunId"))
                .setPolicyVersion(requirePolicy(raw.getPolicyVersion()))
                .setEvidenceRef(requireText(raw.getEvidenceRef(), "evidenceRef", 256))
                .setCorrelationId(requireUuid(raw.getCorrelationId(), "correlationId"))
                .setCausationId(optionalUuid(raw.getCausationId(), "causationId"))
                .setOccurredAt(Objects.requireNonNull(raw.getOccurredAt(), "occurredAt is required"));
    }

    private static String requirePolicy(String value) {
        String policy = requireText(value, "policyVersion", 64);
        require(POLICY_VERSION.equals(policy), "new product-identity assessments require policy v1");
        return policy;
    }

    private static LegacyTradeProductIdentityResult toResult(LegacyTradeProductIdentityRunDO run) {
        return new LegacyTradeProductIdentityResult()
                .setIdentityRunId(run.getIdentityRunId()).setSourceMigrationRunId(run.getSourceMigrationRunId())
                .setGovernanceEvidenceHash(run.getGovernanceEvidenceHash())
                .setSourceItemCount(run.getSourceItemCount()).setActiveItemCount(run.getActiveItemCount())
                .setExcludedItemCount(run.getExcludedItemCount())
                .setSourcePairUnambiguousCount(run.getSourcePairUnambiguousCount())
                .setSourceParentConflictItemCount(run.getSourceParentConflictItemCount())
                .setCurrentRelationObservedCount(run.getCurrentRelationObservedCount())
                .setHistoricalIdentityQualifiedCount(run.getHistoricalIdentityQualifiedCount())
                .setIdentityAdmittedItemCount(run.getIdentityAdmittedItemCount())
                .setTargetMappingEnabled(run.getTargetMappingEnabled()).setStatus(run.getStatus());
    }

    private static LegacyTradeProductIdentityItemView toView(LegacyTradeProductIdentityItemDO value) {
        return new LegacyTradeProductIdentityItemView()
                .setIdentityItemId(value.getIdentityItemId()).setIdentityRunId(value.getIdentityRunId())
                .setSourceMigrationRunId(value.getSourceMigrationRunId()).setCandidateId(value.getCandidateId())
                .setItemEvidenceId(value.getItemEvidenceId()).setLegacyOrderId(value.getLegacyOrderId())
                .setLegacyOrderItemId(value.getLegacyOrderItemId()).setLegacySpuId(value.getLegacySpuId())
                .setLegacySkuId(value.getLegacySkuId()).setSourceParentCardinality(value.getSourceParentCardinality())
                .setSourcePairStatus(value.getSourcePairStatus()).setCurrentReferenceStatus(value.getCurrentReferenceStatus())
                .setCurrentProductSnapshotHash(value.getCurrentProductSnapshotHash())
                .setQualificationId(value.getQualificationId()).setHistoricalIdentityStatus(value.getHistoricalIdentityStatus())
                .setBlockerCodes(JsonUtils.parseArray(value.getBlockerCodes(), String.class))
                .setIdentityAdmissionAllowed(value.getIdentityAdmissionAllowed())
                .setTargetMappingAllowed(value.getTargetMappingAllowed()).setEvidenceHash(value.getEvidenceHash());
    }

    private static int countActive(List<LegacyTradeProductIdentityItemDO> items,
                                   java.util.function.Predicate<LegacyTradeProductIdentityItemDO> predicate) {
        return (int) items.stream().filter(value -> !"EXCLUDED".equals(value.getSourcePairStatus()))
                .filter(predicate).count();
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static String requireText(String value, String name, int max) {
        require(value != null && !value.isBlank(), name + " is required");
        String normalized = value.trim();
        require(normalized.length() <= max, name + " exceeds " + max + " characters");
        return normalized;
    }

    private static String optionalText(String value, String name, int max) {
        return value == null ? null : requireText(value, name, max);
    }

    private static String requireUuid(String value, String name) {
        String normalized = requireText(value, name, 36).toLowerCase(Locale.ROOT);
        try {
            return UUID.fromString(normalized).toString();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(name + " must be a UUID", ex);
        }
    }

    private static String optionalUuid(String value, String name) {
        return value == null ? null : requireUuid(value, name);
    }

    private static String deterministicUuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
