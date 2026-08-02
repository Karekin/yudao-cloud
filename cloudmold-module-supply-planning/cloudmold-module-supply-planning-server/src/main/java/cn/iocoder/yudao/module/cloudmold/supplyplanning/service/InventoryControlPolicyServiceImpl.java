package cn.iocoder.yudao.module.cloudmold.supplyplanning.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.InventoryHealthSnapshotCommand;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.InventoryHealthSnapshotCommandApi;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.InventoryHealthSnapshotIssueRefView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.InventoryHealthSnapshotResult;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.InventoryHealthSnapshotView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SafetyStockPolicyCommand;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SafetyStockPolicyCommandApi;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SafetyStockPolicyOperation;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SafetyStockPolicyResult;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SafetyStockPolicyVersionView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SafetyStockPolicyView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.controller.admin.vo.InventoryHealthSnapshotPageReqVO;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.controller.admin.vo.SafetyStockPolicyPageReqVO;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.dataobject.InventoryControlPolicyRecords.InventoryHealthSnapshot;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.dataobject.InventoryControlPolicyRecords.InventoryHealthSnapshotIssueRef;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.dataobject.InventoryControlPolicyRecords.InventoryIssueReference;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.dataobject.InventoryControlPolicyRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.dataobject.InventoryControlPolicyRecords.SafetyStockPolicy;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.dataobject.InventoryControlPolicyRecords.SafetyStockPolicyVersion;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.mysql.InventoryControlPolicyMapper;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.service.actor.SupplyPlanningActorPrincipalPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class InventoryControlPolicyServiceImpl
        implements SafetyStockPolicyCommandApi, InventoryHealthSnapshotCommandApi {
    private static final int OPERATION_SUCCEEDED = 10;
    private static final String SOURCE_SYSTEM = "cloudmold-supply-planning";
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> POLICY_STATUSES = Set.of("DRAFT", "APPROVED", "PUBLISHED", "RETIRED");
    private static final Set<String> ISSUE_STATUSES = Set.of("OPEN", "ACKNOWLEDGED", "RESOLVED", "DISMISSED");

    private final InventoryControlPolicyMapper mapper;
    private final OutboxAppender outboxAppender;
    private final SupplyPlanningActorPrincipalPort actorPrincipalPort;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SafetyStockPolicyResult execute(SafetyStockPolicyCommand command, String actorPrincipalId) {
        validatePolicyEnvelope(command);
        requireRef(actorPrincipalId, "actorPrincipalId", 128);
        actorPrincipalPort.requireActive(actorPrincipalId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(actorPrincipalId + "\n" + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();
        String commandType = "SAFETY_STOCK_POLICY_" + command.getOperation().name();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), commandType, requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve safety-stock policy operation");
        Operation operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "safety-stock policy operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with different payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing safety-stock policy operation is incomplete");
            SafetyStockPolicyResult replay =
                    JsonUtils.parseObject(operation.getResultJson(), SafetyStockPolicyResult.class);
            replay.setDuplicate(true);
            return replay;
        }
        Outcome outcome = switch (command.getOperation()) {
            case SAVE_DRAFT -> saveDraft(tenantId, operationId, command, actorPrincipalId, now);
            case APPROVE -> approve(tenantId, operationId, command, actorPrincipalId, now);
            case PUBLISH -> publish(tenantId, operationId, command, actorPrincipalId, now);
            case RETIRE -> retire(tenantId, operationId, command, actorPrincipalId, now);
        };
        appendEvent(tenantId, command.getOccurredAt(), command.getIdempotencyKey(), command.getCorrelationId(),
                command.getCausationId(), outcome);
        SafetyStockPolicyResult result = SafetyStockPolicyResult.builder()
                .operationId(operationId)
                .duplicate(false)
                .aggregateType(outcome.aggregateType())
                .aggregateId(outcome.aggregateId())
                .aggregateVersion(outcome.aggregateVersion())
                .status(outcome.status())
                .build();
        require(mapper.markOperationSucceeded(operationId, tenantId, outcome.aggregateType(), outcome.aggregateId(),
                        JsonUtils.toJsonString(result), now) == 1,
                "safety-stock policy operation completion conflict");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryHealthSnapshotResult capture(InventoryHealthSnapshotCommand command, String actorPrincipalId) {
        validateSnapshotEnvelope(command);
        requireRef(actorPrincipalId, "actorPrincipalId", 128);
        actorPrincipalPort.requireActive(actorPrincipalId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(actorPrincipalId + "\n" + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(),
                "INVENTORY_HEALTH_SNAPSHOT_CAPTURE", requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve inventory-health snapshot operation");
        Operation operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "inventory-health snapshot operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with different payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing inventory-health snapshot operation is incomplete");
            InventoryHealthSnapshotResult replay =
                    JsonUtils.parseObject(operation.getResultJson(), InventoryHealthSnapshotResult.class);
            replay.setDuplicate(true);
            return replay;
        }
        Outcome outcome = captureSnapshot(tenantId, operationId, command, actorPrincipalId, now);
        appendEvent(tenantId, command.getOccurredAt(), command.getIdempotencyKey(), command.getCorrelationId(),
                command.getCausationId(), outcome);
        InventoryHealthSnapshotResult result = InventoryHealthSnapshotResult.builder()
                .operationId(operationId)
                .duplicate(false)
                .aggregateType(outcome.aggregateType())
                .aggregateId(outcome.aggregateId())
                .aggregateVersion(outcome.aggregateVersion())
                .status(outcome.status())
                .build();
        require(mapper.markOperationSucceeded(operationId, tenantId, outcome.aggregateType(), outcome.aggregateId(),
                        JsonUtils.toJsonString(result), now) == 1,
                "inventory-health snapshot operation completion conflict");
        return result;
    }

    public PageResult<SafetyStockPolicyView> getPolicyPage(SafetyStockPolicyPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String status = normalizeStatus(request.getStatus());
        String keyword = normalize(request.getKeyword());
        long total = mapper.countPolicyPage(tenantId, status, keyword);
        if (total <= 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        List<SafetyStockPolicyView> rows =
                mapper.selectPolicyPage(tenantId, status, keyword, offset, request.getPageSize());
        return new PageResult<>(rows, total);
    }

    public SafetyStockPolicyView requirePolicy(String policyId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireRef(policyId, "policyId", 128);
        SafetyStockPolicyView view = mapper.selectPolicy(tenantId, policyId);
        require(view != null, "safety-stock policy not found");
        view.setHistory(mapper.selectPolicyVersions(tenantId, policyId));
        return view;
    }

    public PageResult<InventoryHealthSnapshotView> getSnapshotPage(InventoryHealthSnapshotPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String policyId = normalize(request.getPolicyId());
        String keyword = normalize(request.getKeyword());
        long total = mapper.countSnapshotPage(tenantId, policyId, keyword);
        if (total <= 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        List<InventoryHealthSnapshotView> rows =
                mapper.selectSnapshotPage(tenantId, policyId, keyword, offset, request.getPageSize());
        return new PageResult<>(rows, total);
    }

    public InventoryHealthSnapshotView requireSnapshot(String snapshotId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireRef(snapshotId, "snapshotId", 128);
        InventoryHealthSnapshotView view = mapper.selectSnapshot(tenantId, snapshotId);
        require(view != null, "inventory-health snapshot not found");
        view.setIssues(mapper.selectSnapshotIssues(tenantId, snapshotId));
        return view;
    }

    private Outcome saveDraft(Long tenantId, Long operationId, SafetyStockPolicyCommand command,
                              String actorPrincipalId, LocalDateTime now) {
        SafetyStockPolicyCommand.PolicyDefinition input = nonNull(command.getPolicy(), "policy is required");
        validatePolicyDefinition(input);
        String policyId = valueOrUuid(input.getPolicyId());
        SafetyStockPolicy existing = mapper.selectPolicyForUpdate(tenantId, policyId);
        if (existing == null) {
            require(input.getExpectedVersion() == null, "new safety-stock policy must not carry expectedVersion");
            SafetyStockPolicy created = buildHead(tenantId, input, policyId, actorPrincipalId, 1L, now);
            require(mapper.insertPolicy(created) == 1, "failed to persist safety-stock policy");
            require(mapper.insertPolicyVersion(buildVersion(created, "DRAFT", actorPrincipalId, operationId, 1L, now)) == 1,
                    "failed to persist safety-stock policy version");
            return outcome("supply_planning.safety_stock_policy.saved", "safety_stock_policy",
                    policyId, 1L, "DRAFT", payload("policy_id", policyId, "policy_code", created.getPolicyCode()));
        }
        requireExpectedVersion(input.getExpectedVersion(), existing.getCurrentVersion());
        require("DRAFT".equals(existing.getStatus()), "only a draft safety-stock policy can be edited");
        long nextVersion = existing.getCurrentVersion() + 1;
        SafetyStockPolicy updated = buildHead(tenantId, input, policyId, existing.getCreatedByPrincipalId(), nextVersion, now);
        updated.setExpectedVersion(existing.getCurrentVersion());
        require(mapper.updatePolicyDraft(updated) == 1, "safety-stock policy draft update conflict");
        require(mapper.insertPolicyVersion(buildVersion(updated, "DRAFT", actorPrincipalId, operationId, nextVersion, now)) == 1,
                "failed to persist safety-stock policy version");
        return outcome("supply_planning.safety_stock_policy.saved", "safety_stock_policy",
                policyId, nextVersion, "DRAFT", payload("policy_id", policyId, "policy_code", updated.getPolicyCode()));
    }

    private Outcome approve(Long tenantId, Long operationId, SafetyStockPolicyCommand command,
                            String actorPrincipalId, LocalDateTime now) {
        SafetyStockPolicyCommand.PolicyDefinition input = nonNull(command.getPolicy(), "policy is required");
        requireRef(input.getPolicyId(), "policyId", 128);
        SafetyStockPolicy existing = nonNull(mapper.selectPolicyForUpdate(tenantId, input.getPolicyId()),
                "safety-stock policy not found");
        requireExpectedVersion(input.getExpectedVersion(), existing.getCurrentVersion());
        require("DRAFT".equals(existing.getStatus()), "only a draft safety-stock policy can be approved");
        long nextVersion = existing.getCurrentVersion() + 1;
        require(mapper.approvePolicy(tenantId, existing.getPolicyId(), existing.getCurrentVersion(),
                nextVersion, actorPrincipalId, now) == 1, "safety-stock policy approve conflict");
        SafetyStockPolicyVersion version = buildVersion(existing, "APPROVED", actorPrincipalId, operationId, nextVersion, now);
        require(mapper.insertPolicyVersion(version) == 1, "failed to persist approved safety-stock policy version");
        return outcome("supply_planning.safety_stock_policy.approved", "safety_stock_policy",
                existing.getPolicyId(), nextVersion, "APPROVED",
                payload("policy_id", existing.getPolicyId(), "policy_code", existing.getPolicyCode()));
    }

    private Outcome publish(Long tenantId, Long operationId, SafetyStockPolicyCommand command,
                            String actorPrincipalId, LocalDateTime now) {
        SafetyStockPolicyCommand.PolicyDefinition input = nonNull(command.getPolicy(), "policy is required");
        requireRef(input.getPolicyId(), "policyId", 128);
        SafetyStockPolicy existing = nonNull(mapper.selectPolicyForUpdate(tenantId, input.getPolicyId()),
                "safety-stock policy not found");
        requireExpectedVersion(input.getExpectedVersion(), existing.getCurrentVersion());
        require("APPROVED".equals(existing.getStatus()), "only an approved safety-stock policy can be published");
        require(mapper.countOverlappingPublishedPolicies(tenantId, existing.getPolicyId(), existing.getOwnerType(),
                        existing.getOwnerId(), existing.getCanonicalSkuId(), existing.getWarehouseNetworkId(),
                        existing.getEffectiveFrom(), existing.getEffectiveTo()) == 0,
                "an overlapping published safety-stock policy already exists for this scope");
        long nextVersion = existing.getCurrentVersion() + 1;
        String policyVersionId = UUID.randomUUID().toString();
        require(mapper.publishPolicy(tenantId, existing.getPolicyId(), existing.getCurrentVersion(), nextVersion,
                policyVersionId, actorPrincipalId, now) == 1, "safety-stock policy publish conflict");
        SafetyStockPolicyVersion version = buildVersion(existing, "PUBLISHED", actorPrincipalId, operationId, nextVersion, now);
        version.setPolicyVersionId(policyVersionId);
        require(mapper.insertPolicyVersion(version) == 1, "failed to persist published safety-stock policy version");
        return outcome("supply_planning.safety_stock_policy.published", "safety_stock_policy",
                existing.getPolicyId(), nextVersion, "PUBLISHED",
                payload("policy_id", existing.getPolicyId(), "policy_code", existing.getPolicyCode(),
                        "policy_version_id", policyVersionId));
    }

    private Outcome retire(Long tenantId, Long operationId, SafetyStockPolicyCommand command,
                           String actorPrincipalId, LocalDateTime now) {
        SafetyStockPolicyCommand.PolicyDefinition input = nonNull(command.getPolicy(), "policy is required");
        requireRef(input.getPolicyId(), "policyId", 128);
        SafetyStockPolicy existing = nonNull(mapper.selectPolicyForUpdate(tenantId, input.getPolicyId()),
                "safety-stock policy not found");
        requireExpectedVersion(input.getExpectedVersion(), existing.getCurrentVersion());
        require("PUBLISHED".equals(existing.getStatus()), "only a published safety-stock policy can be retired");
        long nextVersion = existing.getCurrentVersion() + 1;
        require(mapper.retirePolicy(tenantId, existing.getPolicyId(), existing.getCurrentVersion(), nextVersion,
                actorPrincipalId, now) == 1, "safety-stock policy retire conflict");
        SafetyStockPolicyVersion version = buildVersion(existing, "RETIRED", actorPrincipalId, operationId, nextVersion, now);
        require(mapper.insertPolicyVersion(version) == 1, "failed to persist retired safety-stock policy version");
        return outcome("supply_planning.safety_stock_policy.retired", "safety_stock_policy",
                existing.getPolicyId(), nextVersion, "RETIRED",
                payload("policy_id", existing.getPolicyId(), "policy_code", existing.getPolicyCode()));
    }

    private Outcome captureSnapshot(Long tenantId, Long operationId, InventoryHealthSnapshotCommand command,
                                    String actorPrincipalId, LocalDateTime now) {
        InventoryHealthSnapshotCommand.SnapshotDefinition input =
                nonNull(command.getSnapshot(), "snapshot is required");
        validateSnapshotDefinition(input);
        SafetyStockPolicyVersion policyVersion = nonNull(
                mapper.selectPolicyVersion(tenantId, input.getPolicyVersionId()),
                "published safety-stock policy version not found");
        require("PUBLISHED".equals(policyVersion.getStatus()),
                "inventory-health snapshot requires a published safety-stock policy version");
        if (input.getPolicyId() != null) {
            require(input.getPolicyId().equals(policyVersion.getPolicyId()),
                    "snapshot policyId does not match policyVersionId");
        }
        List<InventoryHealthSnapshotCommand.IssueRefDefinition> refs =
                input.getIssueRefs() == null ? List.of() : input.getIssueRefs();
        List<InventoryIssueReference> issues = new ArrayList<>();
        Set<String> issueIds = new java.util.HashSet<>();
        for (InventoryHealthSnapshotCommand.IssueRefDefinition ref : refs) {
            require(ref != null, "issueRef must not be null");
            requireRef(ref.getIssueId(), "issueId", 128);
            require(issueIds.add(ref.getIssueId()), "snapshot contains a duplicate issueId");
            InventoryIssueReference issue = nonNull(
                    mapper.selectInventoryIssueReference(tenantId, ref.getIssueId()),
                    "inventory-health issue not found");
            require(ISSUE_STATUSES.contains(issue.getStatus()), "inventory-health issue status is invalid");
            issues.add(issue);
        }
        String snapshotId = valueOrUuid(input.getSnapshotId());
        String snapshotCode = nonNull(normalize(input.getSnapshotCode()), "snapshotCode is required");
        InventoryHealthSnapshot snapshot = new InventoryHealthSnapshot()
                .setSnapshotId(snapshotId)
                .setTenantId(tenantId)
                .setSnapshotCode(snapshotCode)
                .setPolicyId(policyVersion.getPolicyId())
                .setPolicyCode(policyVersion.getPolicyCode())
                .setPolicyVersionId(policyVersion.getPolicyVersionId())
                .setPolicyVersion(policyVersion.getVersion())
                .setLedgerWatermarkRef(input.getLedgerWatermarkRef())
                .setLedgerWatermarkOccurredAt(LocalDateTime.ofInstant(input.getLedgerWatermarkOccurredAt(), ZoneOffset.UTC))
                .setStockoutCount(input.getStockoutCount())
                .setLowStockCount(input.getLowStockCount())
                .setOverstockCount(input.getOverstockCount())
                .setObsoleteCount(input.getObsoleteCount())
                .setAgedCount(input.getAgedCount())
                .setShelfLifeRiskCount(input.getShelfLifeRiskCount())
                .setShortageQuantity(input.getShortageQuantity())
                .setExcessQuantity(input.getExcessQuantity())
                .setAtRiskQuantity(input.getAtRiskQuantity())
                .setIssueCount(issues.size())
                .setSnapshotSha256(input.getSnapshotSha256())
                .setStatus("CAPTURED")
                .setCreatedByPrincipalId(actorPrincipalId)
                .setCreatedAt(now);
        require(mapper.insertSnapshot(snapshot) == 1, "failed to persist inventory-health snapshot");
        for (InventoryIssueReference issue : issues) {
            InventoryHealthSnapshotIssueRef row = new InventoryHealthSnapshotIssueRef()
                    .setTenantId(tenantId)
                    .setSnapshotId(snapshotId)
                    .setIssueId(issue.getIssueId())
                    .setIssueType(issue.getIssueType())
                    .setSeverity(issue.getSeverity())
                    .setStatus(issue.getStatus())
                    .setSourceBalanceId(issue.getSourceBalanceId())
                    .setCreatedAt(now);
            require(mapper.insertSnapshotIssueRef(row) == 1, "failed to persist inventory-health snapshot issue ref");
        }
        return outcome("supply_planning.inventory_health_snapshot.captured", "inventory_health_snapshot",
                snapshotId, 1L, "CAPTURED",
                payload("snapshot_id", snapshotId, "snapshot_code", snapshotCode, "policy_id", snapshot.getPolicyId(),
                        "policy_version_id", snapshot.getPolicyVersionId(), "issue_count", issues.size()));
    }

    private void appendEvent(Long tenantId, Instant occurredAt, String idempotencyKey, String correlationId,
                             String causationId, Outcome outcome) {
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(outcome.eventType())
                .schemaVersion(1)
                .sourceSystem(SOURCE_SYSTEM)
                .tenantId(tenantId)
                .aggregateType(outcome.aggregateType())
                .aggregateId(outcome.aggregateId())
                .aggregateVersion(outcome.aggregateVersion())
                .eventSequence((short) 1)
                .occurredAt(occurredAt)
                .correlationId(correlationId)
                .causationId(causationId)
                .idempotencyKey(idempotencyKey + ":" + outcome.eventType())
                .payload(outcome.payload())
                .build());
    }

    private static SafetyStockPolicy buildHead(Long tenantId, SafetyStockPolicyCommand.PolicyDefinition input,
                                               String policyId, String createdByPrincipalId,
                                               Long currentVersion, LocalDateTime now) {
        return new SafetyStockPolicy()
                .setPolicyId(policyId)
                .setTenantId(tenantId)
                .setPolicyCode(input.getPolicyCode())
                .setOwnerType(input.getOwnerType())
                .setOwnerId(input.getOwnerId())
                .setCanonicalSkuId(input.getCanonicalSkuId())
                .setWarehouseNetworkId(input.getWarehouseNetworkId())
                .setEffectiveFrom(input.getEffectiveFrom())
                .setEffectiveTo(input.getEffectiveTo())
                .setTargetServiceLevelBasisPoints(input.getTargetServiceLevelBasisPoints())
                .setSafetyStockQuantity(input.getSafetyStockQuantity())
                .setReorderPointQuantity(input.getReorderPointQuantity())
                .setMaximumStockQuantity(input.getMaximumStockQuantity())
                .setReplenishmentCycleDays(input.getReplenishmentCycleDays())
                .setLeadTimeDays(input.getLeadTimeDays())
                .setPolicyBasisCode(input.getPolicyBasisCode())
                .setPolicySha256(input.getPolicySha256())
                .setEvidenceRef(input.getEvidenceRef())
                .setStatus("DRAFT")
                .setCurrentVersion(currentVersion)
                .setCreatedByPrincipalId(createdByPrincipalId)
                .setCreatedAt(now)
                .setUpdatedAt(now);
    }

    private static SafetyStockPolicyVersion buildVersion(SafetyStockPolicy policy, String status,
                                                         String actorPrincipalId, Long operationId,
                                                         Long version, LocalDateTime now) {
        return new SafetyStockPolicyVersion()
                .setPolicyVersionId(UUID.randomUUID().toString())
                .setTenantId(policy.getTenantId())
                .setPolicyId(policy.getPolicyId())
                .setPolicyCode(policy.getPolicyCode())
                .setVersion(version)
                .setStatus(status)
                .setOwnerType(policy.getOwnerType())
                .setOwnerId(policy.getOwnerId())
                .setCanonicalSkuId(policy.getCanonicalSkuId())
                .setWarehouseNetworkId(policy.getWarehouseNetworkId())
                .setEffectiveFrom(policy.getEffectiveFrom())
                .setEffectiveTo(policy.getEffectiveTo())
                .setTargetServiceLevelBasisPoints(policy.getTargetServiceLevelBasisPoints())
                .setSafetyStockQuantity(policy.getSafetyStockQuantity())
                .setReorderPointQuantity(policy.getReorderPointQuantity())
                .setMaximumStockQuantity(policy.getMaximumStockQuantity())
                .setReplenishmentCycleDays(policy.getReplenishmentCycleDays())
                .setLeadTimeDays(policy.getLeadTimeDays())
                .setPolicyBasisCode(policy.getPolicyBasisCode())
                .setPolicySha256(policy.getPolicySha256())
                .setEvidenceRef(policy.getEvidenceRef())
                .setActorPrincipalId(actorPrincipalId)
                .setSourceOperationId(operationId)
                .setCreatedAt(now);
    }

    private static void validatePolicyEnvelope(SafetyStockPolicyCommand command) {
        require(command != null, "safety-stock policy command is required");
        require(command.getOperation() != null, "safety-stock policy operation is required");
        requireRef(command.getIdempotencyKey(), "idempotencyKey", 128);
        requireRef(command.getRunId(), "runId", 128);
        requireRef(command.getCorrelationId(), "correlationId", 128);
        require(command.getOccurredAt() != null, "occurredAt is required");
    }

    private static void validateSnapshotEnvelope(InventoryHealthSnapshotCommand command) {
        require(command != null, "inventory-health snapshot command is required");
        requireRef(command.getIdempotencyKey(), "idempotencyKey", 128);
        requireRef(command.getRunId(), "runId", 128);
        requireRef(command.getCorrelationId(), "correlationId", 128);
        require(command.getOccurredAt() != null, "occurredAt is required");
    }

    private static void validatePolicyDefinition(SafetyStockPolicyCommand.PolicyDefinition input) {
        requireRef(input.getPolicyCode(), "policyCode", 64);
        requireCode(input.getOwnerType(), "ownerType");
        requireRef(input.getOwnerId(), "ownerId", 128);
        requireRef(input.getCanonicalSkuId(), "canonicalSkuId", 128);
        requireRef(input.getWarehouseNetworkId(), "warehouseNetworkId", 128);
        require(input.getEffectiveFrom() != null, "effectiveFrom is required");
        require(input.getEffectiveTo() == null || !input.getEffectiveTo().isBefore(input.getEffectiveFrom()),
                "effective period is invalid");
        require(input.getTargetServiceLevelBasisPoints() != null
                        && input.getTargetServiceLevelBasisPoints() >= 0
                        && input.getTargetServiceLevelBasisPoints() <= 10_000,
                "targetServiceLevelBasisPoints must be between 0 and 10000");
        require(input.getSafetyStockQuantity() != null && input.getSafetyStockQuantity().signum() >= 0,
                "safetyStockQuantity must not be negative");
        require(input.getReorderPointQuantity() != null && input.getReorderPointQuantity().signum() >= 0,
                "reorderPointQuantity must not be negative");
        require(input.getMaximumStockQuantity() != null && input.getMaximumStockQuantity().signum() > 0,
                "maximumStockQuantity must be positive");
        require(input.getMaximumStockQuantity().compareTo(input.getReorderPointQuantity()) >= 0,
                "maximumStockQuantity must be greater than or equal to reorderPointQuantity");
        require(input.getReorderPointQuantity().compareTo(input.getSafetyStockQuantity()) >= 0,
                "reorderPointQuantity must be greater than or equal to safetyStockQuantity");
        require(input.getReplenishmentCycleDays() != null && input.getReplenishmentCycleDays() > 0,
                "replenishmentCycleDays must be positive");
        require(input.getLeadTimeDays() != null && input.getLeadTimeDays() >= 0,
                "leadTimeDays must not be negative");
        requireCode(input.getPolicyBasisCode(), "policyBasisCode");
        requireSha256(input.getPolicySha256(), "policySha256");
        if (input.getEvidenceRef() != null) {
            requireRef(input.getEvidenceRef(), "evidenceRef", 255);
        }
    }

    private static void validateSnapshotDefinition(InventoryHealthSnapshotCommand.SnapshotDefinition input) {
        requireRef(input.getSnapshotCode(), "snapshotCode", 64);
        requireRef(input.getPolicyVersionId(), "policyVersionId", 128);
        requireRef(input.getLedgerWatermarkRef(), "ledgerWatermarkRef", 255);
        require(input.getLedgerWatermarkOccurredAt() != null, "ledgerWatermarkOccurredAt is required");
        requireNonNegative(input.getStockoutCount(), "stockoutCount");
        requireNonNegative(input.getLowStockCount(), "lowStockCount");
        requireNonNegative(input.getOverstockCount(), "overstockCount");
        requireNonNegative(input.getObsoleteCount(), "obsoleteCount");
        requireNonNegative(input.getAgedCount(), "agedCount");
        requireNonNegative(input.getShelfLifeRiskCount(), "shelfLifeRiskCount");
        requireNonNegative(input.getShortageQuantity(), "shortageQuantity");
        requireNonNegative(input.getExcessQuantity(), "excessQuantity");
        requireNonNegative(input.getAtRiskQuantity(), "atRiskQuantity");
        requireSha256(input.getSnapshotSha256(), "snapshotSha256");
    }

    private static void requireExpectedVersion(Long expectedVersion, Long actualVersion) {
        require(expectedVersion != null, "expectedVersion is required");
        require(Objects.equals(expectedVersion, actualVersion), "aggregate version conflict");
    }

    private static void requireNonNegative(Integer value, String field) {
        require(value != null && value >= 0, field + " must not be negative");
    }

    private static void requireNonNegative(BigDecimal value, String field) {
        require(value != null && value.signum() >= 0, field + " must not be negative");
    }

    private static String valueOrUuid(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        requireRef(value, "id", 128);
        return value;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeStatus(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase();
        require(POLICY_STATUSES.contains(upper), "unsupported safety-stock policy status");
        return upper;
    }

    private static void requireRef(String value, String field, int maxLength) {
        require(value != null && value.length() <= maxLength && SAFE_REF.matcher(value).matches(),
                field + " is invalid");
    }

    private static void requireCode(String value, String field) {
        require(value != null && SAFE_CODE.matcher(value).matches(), field + " is invalid");
    }

    private static void requireSha256(String value, String field) {
        require(value != null && SHA256.matcher(value).matches(), field + " must be a lowercase SHA-256 digest");
    }

    private static <T> T nonNull(T value, String message) {
        require(value != null, message);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static Map<String, Object> payload(Object... pairs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            payload.put((String) pairs[i], pairs[i + 1]);
        }
        return payload;
    }

    private static Outcome outcome(String eventType, String aggregateType, String aggregateId,
                                   Long aggregateVersion, String status, Map<String, Object> payload) {
        return new Outcome(eventType, aggregateType, aggregateId, aggregateVersion, status, payload);
    }

    private record Outcome(String eventType, String aggregateType, String aggregateId,
                           Long aggregateVersion, String status, Map<String, Object> payload) {
    }
}
