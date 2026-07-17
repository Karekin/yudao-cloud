package cn.iocoder.yudao.module.cloudmold.order.service.migration;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.order.api.migration.LegacyTradeProductIdentityQualificationApi;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.migration.LegacyTradeProductIdentityQualificationMapper;
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
public class LegacyTradeProductIdentityQualificationServiceImpl
        implements LegacyTradeProductIdentityQualificationApi {

    static final String QUALIFICATION_EVENT =
            "order.migration.legacy_trade_product_identity_qualification_reviewed";
    private static final Set<String> ACTIONS = Set.of("QUALIFY", "REVOKE");
    private static final Set<String> APPROVAL_ROLES = Set.of("DATA_OWNER", "CHANGE_MANAGER");
    private static final List<String> EVIDENCE_REF_PREFIXES =
            List.of("evidence:", "ticket:", "change:", "review:");
    private static final List<String> EVIDENCE_URI_PREFIXES =
            List.of("s3://", "oss://", "restricted://", "evidence://");

    private final LegacyTradeProductIdentityQualificationMapper mapper;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QualificationRequestResult request(QualificationRequestCommand rawCommand, Long requesterId) {
        QualificationRequestCommand command = normalizeRequest(rawCommand);
        requireActor(requesterId, "requesterId");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = utcNow();
        String requestHash = DigestUtil.sha256Hex(tenantId + "|" + requesterId + "|"
                + JsonUtils.toJsonString(command));
        LegacyTradeProductIdentityQualificationRequestDO replay =
                mapper.selectRequestByIdempotencyForUpdate(tenantId, command.getIdempotencyKey());
        if (replay != null) {
            require(Objects.equals(requestHash, replay.getRequestHash()),
                    "qualification request idempotency key conflicts with another payload or requester");
            return toResult(replay, mapper.selectApprovalsForUpdate(tenantId, replay.getRequestId()), true);
        }

        LegacyTradeProductIdentityQualificationSourceDO source = requireSource(tenantId,
                command.getSourceMigrationRunId(), command.getItemEvidenceId());
        LegacyTradeProductIdentityQualificationDO target = null;
        if ("QUALIFY".equals(command.getActionType())) {
            require(!Boolean.TRUE.equals(source.getDeleted()) && !Boolean.TRUE.equals(source.getOrderDeleted()),
                    "deleted source Order Items cannot receive historical product qualification");
            require(Objects.equals(source.getLegacySpuId(), command.getHistoricalSpuId())
                            && Objects.equals(source.getLegacySkuId(), command.getHistoricalSkuId()),
                    "historical product identity must equal the immutable source Order Item identity");
            require(Objects.equals(source.getSourceItemEvidenceHash(), command.getSourceItemEvidenceHash()),
                    "qualification source item evidence hash mismatch");
            require(Objects.equals(source.getHistoricalProductSnapshotHash(),
                            command.getHistoricalProductSnapshotHash()),
                    "historical product snapshot hash must equal the server-captured source Order Item snapshot");
            require(mapper.selectActiveQualificationForUpdate(tenantId, command.getSourceMigrationRunId(),
                    command.getItemEvidenceId()) == null,
                    "an active historical product qualification already exists for this source item");
        } else {
            target = mapper.selectQualificationForUpdate(tenantId, command.getTargetQualificationId());
            require(target != null && "QUALIFIED".equals(target.getStatus()),
                    "revocation target must be an active historical product qualification");
            require(Objects.equals(target.getSourceMigrationRunId(), command.getSourceMigrationRunId())
                            && Objects.equals(target.getItemEvidenceId(), command.getItemEvidenceId()),
                    "revocation target does not belong to the requested source item");
            requireQualificationMatchesSource(target, source);
        }

        String historicalSpuId = Objects.toString(target == null
                ? command.getHistoricalSpuId() : target.getHistoricalSpuId());
        String historicalSkuId = Objects.toString(target == null
                ? command.getHistoricalSkuId() : target.getHistoricalSkuId());
        String sourceHash = target == null ? command.getSourceItemEvidenceHash()
                : target.getSourceItemEvidenceHash();
        String snapshotHash = target == null ? command.getHistoricalProductSnapshotHash()
                : target.getHistoricalProductSnapshotHash();
        String sourceUri = target == null ? command.getSourceEvidenceUri() : target.getSourceEvidenceUri();
        String scopeHash = DigestUtil.sha256Hex(String.join("\u001f", command.getActionType(),
                Objects.toString(command.getTargetQualificationId(), ""), command.getSourceMigrationRunId(),
                command.getItemEvidenceId(), Objects.toString(source.getLegacyOrderItemId()),
                historicalSpuId, historicalSkuId, sourceHash, snapshotHash, sourceUri,
                command.getQualificationRef()));
        String requestId = deterministicUuid(tenantId + "|product-identity-qualification-request|"
                + command.getIdempotencyKey());
        LegacyTradeProductIdentityQualificationRequestDO request =
                new LegacyTradeProductIdentityQualificationRequestDO()
                        .setRequestId(requestId).setTenantId(tenantId)
                        .setIdempotencyKey(command.getIdempotencyKey()).setRequestHash(requestHash)
                        .setActionType(command.getActionType())
                        .setTargetQualificationId(command.getTargetQualificationId())
                        .setSourceMigrationRunId(command.getSourceMigrationRunId())
                        .setItemEvidenceId(command.getItemEvidenceId())
                        .setLegacyOrderItemId(source.getLegacyOrderItemId())
                        .setHistoricalSpuId(Long.valueOf(historicalSpuId)).setHistoricalSkuId(Long.valueOf(historicalSkuId))
                        .setSourceItemEvidenceHash(sourceHash).setHistoricalProductSnapshotHash(snapshotHash)
                        .setSourceEvidenceUri(sourceUri).setQualificationRef(command.getQualificationRef())
                        .setScopeHash(scopeHash).setRequesterId(requesterId).setApprovalCount(0)
                        .setStatus("PENDING").setVersion(1L).setRequestedAt(now)
                        .setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertRequest(request) == 1, "failed to persist qualification request");
        appendEvent(request, List.of(), "NOT_APPLIED", command.getOccurredAt(), command.getCorrelationId(),
                command.getCausationId(), command.getIdempotencyKey());
        return toResult(request, List.of(), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QualificationRequestResult approve(String rawRequestId, QualificationApprovalCommand rawCommand,
                                               Long approverId) {
        String requestId = requireUuid(rawRequestId, "requestId");
        QualificationApprovalCommand command = normalizeApproval(rawCommand);
        requireActor(approverId, "approverId");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = utcNow();
        String requestHash = DigestUtil.sha256Hex(tenantId + "|" + requestId + "|" + approverId + "|"
                + JsonUtils.toJsonString(command));
        LegacyTradeProductIdentityQualificationApprovalDO replay =
                mapper.selectApprovalByIdempotencyForUpdate(tenantId, command.getIdempotencyKey());
        if (replay != null) {
            require(Objects.equals(requestId, replay.getRequestId())
                            && Objects.equals(requestHash, replay.getRequestHash()),
                    "qualification approval idempotency key conflicts with another request or actor");
            LegacyTradeProductIdentityQualificationRequestDO current = requireRequestForUpdate(tenantId, requestId);
            return toResult(current, mapper.selectApprovalsForUpdate(tenantId, requestId), true);
        }

        LegacyTradeProductIdentityQualificationRequestDO request = requireRequestForUpdate(tenantId, requestId);
        require(Objects.equals(command.getExpectedVersion(), request.getVersion()),
                "qualification request version changed before approval");
        require(Set.of("PENDING", "PARTIALLY_APPROVED").contains(request.getStatus()),
                "qualification request is not awaiting approval");
        require(!Objects.equals(request.getRequesterId(), approverId),
                "qualification requester cannot approve the same request");
        List<LegacyTradeProductIdentityQualificationApprovalDO> approvals =
                mapper.selectApprovalsForUpdate(tenantId, requestId);
        require(approvals.stream().noneMatch(value -> value.getApprovalRole().equals(command.getApprovalRole())),
                "qualification approval role is already bound");
        require(approvals.stream().noneMatch(value -> Objects.equals(value.getApproverId(), approverId)),
                "one actor cannot satisfy both qualification approval roles");
        LegacyTradeProductIdentityQualificationApprovalDO approval =
                new LegacyTradeProductIdentityQualificationApprovalDO()
                        .setApprovalId(deterministicUuid(tenantId + "|product-identity-approval|"
                                + command.getIdempotencyKey()))
                        .setTenantId(tenantId).setRequestId(requestId).setApprovalRole(command.getApprovalRole())
                        .setApproverId(approverId).setScopeHash(request.getScopeHash())
                        .setExpectedRequestVersion(command.getExpectedVersion()).setEvidenceRef(command.getEvidenceRef())
                        .setIdempotencyKey(command.getIdempotencyKey()).setRequestHash(requestHash)
                        .setStatus("APPROVED").setVersion(1L).setApprovedAt(now).setCreatedAt(now);
        require(mapper.insertApproval(approval) == 1, "failed to persist qualification approval");
        approvals = new ArrayList<>(approvals);
        approvals.add(approval);
        require(approvals.size() <= 2, "qualification request has too many approvals");

        String nextStatus = approvals.size() == 2 ? "APPLIED" : "PARTIALLY_APPROVED";
        String qualificationId = null;
        String qualificationStatus = "NOT_APPLIED";
        LocalDateTime appliedAt = null;
        if (approvals.size() == 2) {
            require(approvals.stream().map(LegacyTradeProductIdentityQualificationApprovalDO::getApprovalRole)
                            .collect(java.util.stream.Collectors.toSet()).equals(APPROVAL_ROLES),
                    "qualification requires DATA_OWNER and CHANGE_MANAGER approvals");
            LegacyTradeProductIdentityQualificationSourceDO source = requireSource(tenantId,
                    request.getSourceMigrationRunId(), request.getItemEvidenceId());
            requireRequestMatchesSource(request, source);
            String approvalSetHash = approvalSetHash(approvals);
            if ("QUALIFY".equals(request.getActionType())) {
                require(!Boolean.TRUE.equals(source.getDeleted()) && !Boolean.TRUE.equals(source.getOrderDeleted()),
                        "source Order Item became deleted before qualification approval");
                require(mapper.selectActiveQualificationForUpdate(tenantId, request.getSourceMigrationRunId(),
                        request.getItemEvidenceId()) == null,
                        "an active historical product qualification appeared before final approval");
                qualificationId = deterministicUuid(requestId + "|qualification");
                LegacyTradeProductIdentityQualificationDO qualification =
                        new LegacyTradeProductIdentityQualificationDO()
                                .setQualificationId(qualificationId).setTenantId(tenantId)
                                .setSourceMigrationRunId(request.getSourceMigrationRunId())
                                .setItemEvidenceId(request.getItemEvidenceId())
                                .setLegacyOrderItemId(request.getLegacyOrderItemId())
                                .setHistoricalSpuId(request.getHistoricalSpuId())
                                .setHistoricalSkuId(request.getHistoricalSkuId())
                                .setSourceItemEvidenceHash(request.getSourceItemEvidenceHash())
                                .setHistoricalProductSnapshotHash(request.getHistoricalProductSnapshotHash())
                                .setSourceEvidenceUri(request.getSourceEvidenceUri())
                                .setQualificationRef(request.getQualificationRef()).setRequestId(requestId)
                                .setApprovalSetHash(approvalSetHash).setQualifiedBy(qualifiedBy(request, approvals))
                                .setQualifiedAt(now).setStatus("QUALIFIED").setVersion(1L)
                                .setCreatedAt(now).setUpdatedAt(now);
                require(mapper.insertQualification(qualification) == 1,
                        "failed to persist approved historical product qualification");
                qualificationStatus = "QUALIFIED";
            } else {
                LegacyTradeProductIdentityQualificationDO target = mapper.selectQualificationForUpdate(
                        tenantId, request.getTargetQualificationId());
                require(target != null && "QUALIFIED".equals(target.getStatus()),
                        "revocation target is no longer an active qualification");
                requireQualificationMatchesRequest(target, request);
                require(mapper.revokeQualification(tenantId, target.getQualificationId(), target.getVersion(),
                                requestId, approvalSetHash, now) == 1,
                        "historical product qualification changed before revocation");
                qualificationId = target.getQualificationId();
                qualificationStatus = "REVOKED";
            }
            appliedAt = now;
        }
        require(mapper.advanceRequest(tenantId, requestId, request.getVersion(), approvals.size(), nextStatus,
                qualificationId, appliedAt, now) == 1, "qualification request changed concurrently");
        request.setApprovalCount(approvals.size()).setStatus(nextStatus).setQualificationId(qualificationId)
                .setVersion(request.getVersion() + 1).setAppliedAt(appliedAt).setUpdatedAt(now);
        appendEvent(request, approvals, qualificationStatus, command.getOccurredAt(), command.getCorrelationId(),
                command.getCausationId(), command.getIdempotencyKey());
        return toResult(request, approvals, false);
    }

    @Override
    public QualificationRequestResult requireRequest(String rawRequestId) {
        String requestId = requireUuid(rawRequestId, "requestId");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LegacyTradeProductIdentityQualificationRequestDO request = mapper.selectRequest(tenantId, requestId);
        require(request != null, "qualification request does not exist");
        return toResult(request, mapper.selectApprovals(tenantId, requestId), false);
    }

    private LegacyTradeProductIdentityQualificationRequestDO requireRequestForUpdate(Long tenantId, String requestId) {
        LegacyTradeProductIdentityQualificationRequestDO request = mapper.selectRequestForUpdate(tenantId, requestId);
        require(request != null, "qualification request does not exist");
        return request;
    }

    private LegacyTradeProductIdentityQualificationSourceDO requireSource(Long tenantId, String sourceRunId,
                                                                          String itemEvidenceId) {
        LegacyTradeProductIdentityQualificationSourceDO source =
                mapper.selectSourceItem(tenantId, sourceRunId, itemEvidenceId);
        require(source != null, "immutable v5 source Order Item evidence does not exist");
        require("legacy-trade-benefit-v5".equals(source.getPolicyVersion())
                        && Boolean.TRUE.equals(source.getItemEvidenceComplete())
                        && Boolean.TRUE.equals(source.getProductSnapshotEvidenceComplete())
                        && "CAPTURED".equals(source.getProductSnapshotStatus())
                        && source.getHistoricalProductSnapshotHash() != null
                        && source.getHistoricalProductSnapshotHash().matches("[0-9a-f]{64}"),
                "qualification requires a complete server-captured immutable v5 product snapshot");
        return source;
    }

    private void appendEvent(LegacyTradeProductIdentityQualificationRequestDO request,
                             List<LegacyTradeProductIdentityQualificationApprovalDO> approvals,
                             String qualificationStatus, java.time.Instant occurredAt,
                             String correlationId, String causationId, String idempotencyKey) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("request_id", request.getRequestId());
        payload.put("action_type", request.getActionType());
        payload.put("target_qualification_id", request.getTargetQualificationId());
        payload.put("source_migration_run_id", request.getSourceMigrationRunId());
        payload.put("item_evidence_id", request.getItemEvidenceId());
        payload.put("legacy_order_item_id", request.getLegacyOrderItemId());
        payload.put("historical_spu_id", request.getHistoricalSpuId());
        payload.put("historical_sku_id", request.getHistoricalSkuId());
        payload.put("source_item_evidence_hash", request.getSourceItemEvidenceHash());
        payload.put("historical_product_snapshot_hash", request.getHistoricalProductSnapshotHash());
        payload.put("source_evidence_uri", request.getSourceEvidenceUri());
        payload.put("qualification_ref", request.getQualificationRef());
        payload.put("scope_hash", request.getScopeHash());
        payload.put("requester_system_user_id", request.getRequesterId());
        payload.put("approval_count", request.getApprovalCount());
        payload.put("approval_roles", approvals.stream().map(
                LegacyTradeProductIdentityQualificationApprovalDO::getApprovalRole).sorted().toList());
        payload.put("approver_system_user_ids", approvals.stream().map(
                LegacyTradeProductIdentityQualificationApprovalDO::getApproverId).sorted().toList());
        payload.put("approvals", approvals.stream()
                .sorted(Comparator.comparing(LegacyTradeProductIdentityQualificationApprovalDO::getApprovalRole))
                .map(value -> {
                    Map<String, Object> record = new LinkedHashMap<>();
                    record.put("approval_id", value.getApprovalId());
                    record.put("approval_role", value.getApprovalRole());
                    record.put("approver_system_user_id", value.getApproverId());
                    record.put("evidence_ref", value.getEvidenceRef());
                    record.put("expected_request_version", value.getExpectedRequestVersion());
                    record.put("status", value.getStatus());
                    return record;
                }).toList());
        payload.put("approval_set_hash", approvals.size() == 2 ? approvalSetHash(approvals) : null);
        payload.put("request_status", request.getStatus());
        payload.put("qualification_id", request.getQualificationId());
        payload.put("qualification_status", qualificationStatus);
        payload.put("request_version", request.getVersion());
        payload.put("canonical_import_allowed", false);
        payload.put("production_migration_enabled", false);
        payload.put("policy_version", "legacy-trade-product-identity-qualification-v1");
        payload.put("reviewed_at", request.getUpdatedAt().toInstant(ZoneOffset.UTC).toString());
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(deterministicUuid(request.getTenantId() + "|" + QUALIFICATION_EVENT + "|" + idempotencyKey))
                .eventType(QUALIFICATION_EVENT).schemaVersion(1).sourceSystem("cloudmold-order")
                .tenantId(request.getTenantId()).aggregateType("legacy_trade_product_identity_qualification_request")
                .aggregateId(request.getRequestId()).aggregateVersion(request.getVersion()).eventSequence((short) 1)
                .occurredAt(occurredAt).correlationId(correlationId).causationId(causationId)
                .idempotencyKey(idempotencyKey).payload(payload)
                .headers(Map.of("request_id", request.getRequestId(), "scope_hash", request.getScopeHash()))
                .destination("lakehouse").build());
    }

    private static QualificationRequestResult toResult(
            LegacyTradeProductIdentityQualificationRequestDO request,
            List<LegacyTradeProductIdentityQualificationApprovalDO> approvals, boolean duplicate) {
        String qualificationStatus = request.getQualificationId() == null ? "NOT_APPLIED"
                : "REVOKE".equals(request.getActionType()) ? "REVOKED" : "QUALIFIED";
        return QualificationRequestResult.builder()
                .requestId(request.getRequestId()).actionType(request.getActionType())
                .targetQualificationId(request.getTargetQualificationId())
                .sourceMigrationRunId(request.getSourceMigrationRunId()).itemEvidenceId(request.getItemEvidenceId())
                .legacyOrderItemId(request.getLegacyOrderItemId()).historicalSpuId(request.getHistoricalSpuId())
                .historicalSkuId(request.getHistoricalSkuId())
                .sourceItemEvidenceHash(request.getSourceItemEvidenceHash())
                .historicalProductSnapshotHash(request.getHistoricalProductSnapshotHash())
                .sourceEvidenceUri(request.getSourceEvidenceUri()).qualificationRef(request.getQualificationRef())
                .scopeHash(request.getScopeHash()).requesterId(request.getRequesterId())
                .approvalCount(request.getApprovalCount()).status(request.getStatus())
                .qualificationId(request.getQualificationId()).qualificationStatus(qualificationStatus)
                .version(request.getVersion()).approvals(approvals.stream().map(value ->
                        QualificationApprovalResult.builder().approvalId(value.getApprovalId())
                                .approvalRole(value.getApprovalRole()).approverId(value.getApproverId())
                                .evidenceRef(value.getEvidenceRef())
                                .expectedRequestVersion(value.getExpectedRequestVersion())
                                .status(value.getStatus()).build()).toList())
                .duplicate(duplicate).build();
    }

    private static void requireRequestMatchesSource(LegacyTradeProductIdentityQualificationRequestDO request,
                                                    LegacyTradeProductIdentityQualificationSourceDO source) {
        require(Objects.equals(request.getLegacyOrderItemId(), source.getLegacyOrderItemId())
                        && Objects.equals(request.getHistoricalSpuId(), source.getLegacySpuId())
                        && Objects.equals(request.getHistoricalSkuId(), source.getLegacySkuId())
                        && Objects.equals(request.getSourceItemEvidenceHash(), source.getSourceItemEvidenceHash())
                        && Objects.equals(request.getHistoricalProductSnapshotHash(),
                        source.getHistoricalProductSnapshotHash()),
                "immutable source Order Item evidence changed before final approval");
    }

    private static void requireQualificationMatchesSource(LegacyTradeProductIdentityQualificationDO target,
                                                           LegacyTradeProductIdentityQualificationSourceDO source) {
        require(Objects.equals(target.getLegacyOrderItemId(), source.getLegacyOrderItemId())
                        && Objects.equals(target.getHistoricalSpuId(), source.getLegacySpuId())
                        && Objects.equals(target.getHistoricalSkuId(), source.getLegacySkuId())
                        && Objects.equals(target.getSourceItemEvidenceHash(), source.getSourceItemEvidenceHash())
                        && Objects.equals(target.getHistoricalProductSnapshotHash(),
                        source.getHistoricalProductSnapshotHash()),
                "active qualification no longer matches immutable source Order Item evidence");
    }

    private static void requireQualificationMatchesRequest(LegacyTradeProductIdentityQualificationDO target,
                                                            LegacyTradeProductIdentityQualificationRequestDO request) {
        require(Objects.equals(target.getSourceMigrationRunId(), request.getSourceMigrationRunId())
                        && Objects.equals(target.getItemEvidenceId(), request.getItemEvidenceId())
                        && Objects.equals(target.getLegacyOrderItemId(), request.getLegacyOrderItemId())
                        && Objects.equals(target.getHistoricalSpuId(), request.getHistoricalSpuId())
                        && Objects.equals(target.getHistoricalSkuId(), request.getHistoricalSkuId())
                        && Objects.equals(target.getSourceItemEvidenceHash(), request.getSourceItemEvidenceHash())
                        && Objects.equals(target.getHistoricalProductSnapshotHash(),
                        request.getHistoricalProductSnapshotHash()),
                "revocation request scope differs from the active qualification");
    }

    private static String approvalSetHash(List<LegacyTradeProductIdentityQualificationApprovalDO> approvals) {
        return DigestUtil.sha256Hex(approvals.stream()
                .map(value -> value.getApprovalRole() + "|" + value.getApproverId() + "|"
                        + value.getScopeHash() + "|" + value.getEvidenceRef() + "|" + value.getRequestHash())
                .sorted().reduce("", (left, right) -> left + "\n" + right));
    }

    private static String qualifiedBy(LegacyTradeProductIdentityQualificationRequestDO request,
                                      List<LegacyTradeProductIdentityQualificationApprovalDO> approvals) {
        return "requester=" + request.getRequesterId() + ";approvers=" + approvals.stream()
                .map(value -> value.getApprovalRole() + ":" + value.getApproverId()).sorted()
                .reduce((left, right) -> left + "," + right).orElseThrow();
    }

    private static QualificationRequestCommand normalizeRequest(QualificationRequestCommand raw) {
        require(raw != null, "qualification request command is required");
        String action = requireEnum(raw.getActionType(), "actionType", ACTIONS);
        QualificationRequestCommand value = new QualificationRequestCommand()
                .setIdempotencyKey(requireText(raw.getIdempotencyKey(), "idempotencyKey", 128))
                .setActionType(action)
                .setTargetQualificationId("REVOKE".equals(action)
                        ? requireUuid(raw.getTargetQualificationId(), "targetQualificationId") : null)
                .setSourceMigrationRunId(requireUuid(raw.getSourceMigrationRunId(), "sourceMigrationRunId"))
                .setItemEvidenceId(requireUuid(raw.getItemEvidenceId(), "itemEvidenceId"))
                .setQualificationRef(requireEvidenceRef(raw.getQualificationRef(), "qualificationRef"))
                .setCorrelationId(requireUuid(raw.getCorrelationId(), "correlationId"))
                .setCausationId(optionalUuid(raw.getCausationId(), "causationId"))
                .setOccurredAt(requireOccurredAt(raw.getOccurredAt()));
        if ("QUALIFY".equals(action)) {
            require(raw.getHistoricalSpuId() != null && raw.getHistoricalSpuId() > 0,
                    "historicalSpuId must be positive");
            require(raw.getHistoricalSkuId() != null && raw.getHistoricalSkuId() > 0,
                    "historicalSkuId must be positive");
            value.setHistoricalSpuId(raw.getHistoricalSpuId()).setHistoricalSkuId(raw.getHistoricalSkuId())
                    .setSourceItemEvidenceHash(requireSha256(raw.getSourceItemEvidenceHash(),
                            "sourceItemEvidenceHash"))
                    .setHistoricalProductSnapshotHash(requireSha256(raw.getHistoricalProductSnapshotHash(),
                            "historicalProductSnapshotHash"))
                    .setSourceEvidenceUri(requireEvidenceUri(raw.getSourceEvidenceUri()));
        }
        return value;
    }

    private static QualificationApprovalCommand normalizeApproval(QualificationApprovalCommand raw) {
        require(raw != null, "qualification approval command is required");
        require(raw.getExpectedVersion() != null && Set.of(1L, 2L).contains(raw.getExpectedVersion()),
                "expectedVersion must be 1 or 2");
        return new QualificationApprovalCommand()
                .setIdempotencyKey(requireText(raw.getIdempotencyKey(), "idempotencyKey", 128))
                .setApprovalRole(requireEnum(raw.getApprovalRole(), "approvalRole", APPROVAL_ROLES))
                .setExpectedVersion(raw.getExpectedVersion())
                .setEvidenceRef(requireEvidenceRef(raw.getEvidenceRef(), "evidenceRef"))
                .setCorrelationId(requireUuid(raw.getCorrelationId(), "correlationId"))
                .setCausationId(optionalUuid(raw.getCausationId(), "causationId"))
                .setOccurredAt(requireOccurredAt(raw.getOccurredAt()));
    }

    private static String requireEvidenceRef(String value, String field) {
        String normalized = requireText(value, field, 256);
        require(EVIDENCE_REF_PREFIXES.stream().anyMatch(normalized::startsWith),
                field + " must use evidence:, ticket:, change:, or review:");
        return normalized;
    }

    private static String requireEvidenceUri(String value) {
        String normalized = requireText(value, "sourceEvidenceUri", 512);
        require(EVIDENCE_URI_PREFIXES.stream().anyMatch(normalized::startsWith),
                "sourceEvidenceUri must use an immutable approved storage scheme");
        return normalized;
    }

    private static String requireSha256(String value, String field) {
        String normalized = requireText(value, field, 64).toLowerCase(Locale.ROOT);
        require(normalized.matches("[0-9a-f]{64}"), field + " must be lowercase SHA-256");
        return normalized;
    }

    private static String requireEnum(String value, String field, Set<String> allowed) {
        String normalized = requireText(value, field, 32).toUpperCase(Locale.ROOT);
        require(allowed.contains(normalized), field + " is unsupported");
        return normalized;
    }

    private static String requireText(String value, String field, int max) {
        require(value != null && !value.isBlank(), field + " is required");
        String normalized = value.trim();
        require(normalized.length() <= max, field + " exceeds " + max + " characters");
        return normalized;
    }

    private static String requireUuid(String value, String field) {
        String normalized = requireText(value, field, 36).toLowerCase(Locale.ROOT);
        try {
            return UUID.fromString(normalized).toString();
        } catch (IllegalArgumentException ex) {
            throw new ServiceException(400, field + " must be a UUID");
        }
    }

    private static String optionalUuid(String value, String field) {
        return value == null ? null : requireUuid(value, field);
    }

    private static void requireActor(Long actorId, String field) {
        require(actorId != null && actorId > 0, field + " must come from an authenticated system user");
    }

    private static java.time.Instant requireOccurredAt(java.time.Instant value) {
        require(value != null, "occurredAt is required");
        return value;
    }

    private static String deterministicUuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new ServiceException(400, message);
    }
}
