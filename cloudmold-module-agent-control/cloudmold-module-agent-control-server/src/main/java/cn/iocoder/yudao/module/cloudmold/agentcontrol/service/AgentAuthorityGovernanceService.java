package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.*;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.*;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

@ConditionalOnProperty(prefix = "cloudmold.agent-control", name = "enabled", havingValue = "true")
@Service
public class AgentAuthorityGovernanceService implements AgentAuthorityGovernanceApi {
    private static final Pattern ROLE_CODE = Pattern.compile("[a-z][a-z0-9-]{1,63}");
    private static final Pattern ACTION_CODE = Pattern.compile("[a-z][a-z0-9._-]{2,127}");
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> RISK_LEVELS = Set.of("R0", "R1", "R2", "R3");

    private final AgentControlStoreMapper mapper;
    private final Clock clock;

    @Autowired
    public AgentAuthorityGovernanceService(AgentControlStoreMapper mapper) {
        this(mapper, Clock.systemUTC());
    }

    AgentAuthorityGovernanceService(AgentControlStoreMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentControlResult executeAuthorityGovernance(AgentAuthorityCommand command, Long governanceUserId) {
        validateEnvelope(command, governanceUserId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(JsonUtils.toJsonString(Map.of(
                "command", command, "governance_user_id", governanceUserId)));
        String attemptToken = UUID.randomUUID().toString();
        String commandType = "AUTHORITY_" + command.getOperation().name();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), commandType,
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve authority governance operation");
        Operation operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "authority governance operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with different authority payload or governance actor");
            require(operation.getStatus() == AgentControlServiceImpl.OPERATION_SUCCEEDED
                            && operation.getResultJson() != null,
                    "existing authority governance operation is incomplete");
            AgentControlResult replay = JsonUtils.parseObject(operation.getResultJson(), AgentControlResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        Outcome outcome = switch (command.getOperation()) {
            case GRANT_ROLE -> grantRole(tenantId, governanceUserId, command, now);
            case REVOKE_ROLE -> revokeRole(tenantId, governanceUserId, command, now);
            case GRANT_APPROVER -> grantApprover(tenantId, governanceUserId, command, now);
            case REVOKE_APPROVER -> revokeApprover(tenantId, governanceUserId, command, now);
        };
        appendAudit(tenantId, governanceUserId, command, outcome, now);
        AgentControlResult result = AgentControlResult.builder().operationId(operationId).duplicate(false)
                .aggregateType(outcome.aggregateType()).aggregateId(outcome.aggregateId())
                .aggregateVersion(outcome.version()).status(outcome.status()).build();
        require(mapper.markOperationSucceeded(operationId, tenantId, outcome.aggregateType(), outcome.aggregateId(),
                JsonUtils.toJsonString(result), now) == 1, "authority governance operation completion conflict");
        return result;
    }

    private Outcome grantRole(Long tenantId, Long governanceUserId, AgentAuthorityCommand command,
                              LocalDateTime now) {
        AgentAuthorityCommand.RoleGrantDefinition input = requireNonNull(command.getRoleGrant(),
                "roleGrant is required");
        requirePositiveUser(input.getActorUserId(), "actorUserId");
        require(!governanceUserId.equals(input.getActorUserId()),
                "governance actor cannot grant authority to itself");
        requireRoleCode(input.getRoleCode());
        requireActiveRole(tenantId, input.getRoleCode());
        Validity validity = requireValidity(input.getValidFrom(), input.getValidUntil(), now);
        mapper.expireActorRoleGrant(tenantId, input.getActorUserId(), input.getRoleCode(), now);
        require(mapper.selectActiveActorRoleGrantForUpdate(tenantId, input.getActorUserId(), input.getRoleCode()) == null,
                "actor already has an active or scheduled grant for this role");
        ActorRoleGrant row = new ActorRoleGrant().setGrantId(valueOrUuid(input.getGrantId()))
                .setTenantId(tenantId).setActorUserId(input.getActorUserId()).setRoleCode(input.getRoleCode())
                .setStatus("ACTIVE").setValidFrom(validity.validFrom()).setValidUntil(validity.validUntil())
                .setGrantedByUserId(governanceUserId).setVersion(1L).setGrantedAt(now).setUpdatedAt(now);
        require(mapper.insertActorRoleGrant(row) == 1, "failed to persist actor role grant");
        return new Outcome("actor_role_grant", row.getGrantId(), 1L, "ACTIVE",
                "agent_control.actor_role_grant.granted");
    }

    private Outcome revokeRole(Long tenantId, Long governanceUserId, AgentAuthorityCommand command,
                               LocalDateTime now) {
        AgentAuthorityCommand.RoleGrantDefinition input = requireNonNull(command.getRoleGrant(),
                "roleGrant is required");
        String grantId = requireRef(input.getGrantId(), "grantId");
        ActorRoleGrant row = requireNonNull(mapper.selectActorRoleGrantForUpdate(tenantId, grantId),
                "actor role grant not found");
        require(input.getActorUserId() != null && input.getActorUserId().equals(row.getActorUserId()),
                "actorUserId does not match the stored role grant");
        require(Objects.equals(input.getRoleCode(), row.getRoleCode()),
                "roleCode does not match the stored role grant");
        require(!governanceUserId.equals(row.getActorUserId()),
                "governance actor cannot revoke its own authority grant");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("ACTIVE".equals(row.getStatus()), "only an ACTIVE role grant can be revoked");
        require(mapper.revokeActorRoleGrant(tenantId, grantId, row.getVersion(), governanceUserId,
                "GOVERNANCE_REVOKE", now) == 1, "actor role grant revocation conflict");
        return new Outcome("actor_role_grant", grantId, row.getVersion() + 1, "REVOKED",
                "agent_control.actor_role_grant.revoked");
    }

    private Outcome grantApprover(Long tenantId, Long governanceUserId, AgentAuthorityCommand command,
                                  LocalDateTime now) {
        AgentAuthorityCommand.ApprovalGrantDefinition input = requireNonNull(command.getApprovalGrant(),
                "approvalGrant is required");
        requirePositiveUser(input.getApproverUserId(), "approverUserId");
        require(!governanceUserId.equals(input.getApproverUserId()),
                "governance actor cannot grant authority to itself");
        requireRoleCode(input.getRoleCode());
        requireActionCode(input.getActionCode());
        require(RISK_LEVELS.contains(input.getRiskLevel()), "unsupported riskLevel");
        require(input.getScopeHash() != null && SHA256.matcher(input.getScopeHash()).matches(),
                "invalid scopeHash");
        String approvalId = requireRef(input.getApprovalId(), "approvalId");
        Approval approval = requireNonNull(mapper.selectApprovalForUpdate(tenantId, approvalId),
                "approval not found");
        require("PENDING".equals(approval.getStatus()), "only a PENDING approval can receive an approver grant");
        require(!input.getApproverUserId().equals(approval.getRequesterUserId()),
                "approval requester cannot be granted approver authority");
        require(!governanceUserId.equals(approval.getRequesterUserId()),
                "approval requester cannot issue its own approver grant");
        WorkOrder workOrder = requireNonNull(mapper.selectWorkOrderForUpdate(tenantId, approval.getWorkOrderId()),
                "work order not found");
        require("WAITING_APPROVAL".equals(workOrder.getStatus())
                        && approvalId.equals(workOrder.getApprovalId()),
                "approval is not bound to the current waiting work order");
        require(input.getRoleCode().equals(workOrder.getRoleCode()),
                "approver grant roleCode does not match the frozen work order");
        require(input.getActionCode().equals(workOrder.getActionCode())
                        && input.getActionCode().equals(approval.getActionCode()),
                "approver grant actionCode does not match the frozen approval");
        require(input.getScopeHash().equals(approval.getScopeHash())
                        && input.getScopeHash().equals(approvalScopeHash(workOrder)),
                "approver grant scopeHash does not match the frozen approval scope");
        RoleActionPolicy policy = requireNonNull(mapper.selectActionPolicy(tenantId, workOrder.getRoleCode(),
                workOrder.getActionCode()), "frozen action policy not found");
        require(Boolean.TRUE.equals(policy.getEnabled()) && "ALLOW".equals(policy.getPermissionMode())
                        && Boolean.TRUE.equals(policy.getApprovalRequired()),
                "frozen action policy is not approval-enabled");
        require(Objects.equals(workOrder.getActionPolicyId(), policy.getPolicyId())
                        && Objects.equals(workOrder.getActionPolicyVersion(), policy.getVersion())
                        && Objects.equals(workOrder.getRiskLevel(), policy.getRiskLevel()),
                "frozen work-order policy snapshot does not match the configured action policy");
        require(input.getRiskLevel().equals(workOrder.getRiskLevel()),
                "approver grant riskLevel does not match the frozen action policy");
        Validity validity = requireValidity(input.getValidFrom(), input.getValidUntil(), now);
        mapper.expireApprovalAuthorityGrant(tenantId, approvalId, now);
        require(mapper.selectActiveApprovalAuthorityGrantForUpdate(tenantId, approvalId) == null,
                "approval already has an active or scheduled approver grant");
        ApprovalAuthorityGrant row = new ApprovalAuthorityGrant().setGrantId(valueOrUuid(input.getGrantId()))
                .setTenantId(tenantId).setApproverUserId(input.getApproverUserId())
                .setRequesterUserId(approval.getRequesterUserId()).setApprovalId(approvalId)
                .setRoleCode(input.getRoleCode()).setActionCode(input.getActionCode())
                .setRiskLevel(input.getRiskLevel()).setScopeHash(input.getScopeHash()).setStatus("ACTIVE")
                .setValidFrom(validity.validFrom()).setValidUntil(validity.validUntil())
                .setGrantedByUserId(governanceUserId).setVersion(1L).setGrantedAt(now).setUpdatedAt(now);
        require(mapper.insertApprovalAuthorityGrant(row) == 1, "failed to persist exact approver grant");
        return new Outcome("approval_authority_grant", row.getGrantId(), 1L, "ACTIVE",
                "agent_control.approval_authority_grant.granted");
    }

    private Outcome revokeApprover(Long tenantId, Long governanceUserId, AgentAuthorityCommand command,
                                   LocalDateTime now) {
        AgentAuthorityCommand.ApprovalGrantDefinition input = requireNonNull(command.getApprovalGrant(),
                "approvalGrant is required");
        String grantId = requireRef(input.getGrantId(), "grantId");
        ApprovalAuthorityGrant row = requireNonNull(
                mapper.selectApprovalAuthorityGrantForUpdate(tenantId, grantId),
                "approval authority grant not found");
        require(input.getApproverUserId() != null && input.getApproverUserId().equals(row.getApproverUserId()),
                "approverUserId does not match the stored approver grant");
        require(Objects.equals(input.getApprovalId(), row.getApprovalId())
                        && Objects.equals(input.getRoleCode(), row.getRoleCode())
                        && Objects.equals(input.getActionCode(), row.getActionCode())
                        && Objects.equals(input.getRiskLevel(), row.getRiskLevel())
                        && Objects.equals(input.getScopeHash(), row.getScopeHash()),
                "approval authority revoke must match the exact stored grant scope");
        require(!governanceUserId.equals(row.getApproverUserId()),
                "governance actor cannot revoke its own authority grant");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("ACTIVE".equals(row.getStatus()), "only an ACTIVE approver grant can be revoked");
        require(mapper.revokeApprovalAuthorityGrant(tenantId, grantId, row.getVersion(), governanceUserId,
                "GOVERNANCE_REVOKE", now) == 1, "approval authority grant revocation conflict");
        return new Outcome("approval_authority_grant", grantId, row.getVersion() + 1, "REVOKED",
                "agent_control.approval_authority_grant.revoked");
    }

    private void appendAudit(Long tenantId, Long governanceUserId, AgentAuthorityCommand command,
                             Outcome outcome, LocalDateTime now) {
        String detailJson = JsonUtils.toJsonString(Map.of("operation", command.getOperation().name(),
                "status", outcome.status(), "idempotency_key_sha256",
                DigestUtil.sha256Hex(command.getIdempotencyKey())));
        AuditEvent event = new AuditEvent().setAuditEventId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setAggregateType(outcome.aggregateType()).setAggregateId(outcome.aggregateId())
                .setAggregateVersion(outcome.version()).setEventType(outcome.eventType())
                .setActorUserId(governanceUserId).setDetailJson(detailJson).setOccurredAt(now).setCreatedAt(now);
        require(mapper.insertAuditEvent(event) == 1, "failed to append authority governance audit event");
    }

    private void validateEnvelope(AgentAuthorityCommand command, Long governanceUserId) {
        require(command != null && command.getOperation() != null, "authority operation is required");
        requirePositiveUser(governanceUserId, "governanceUserId");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
    }

    private Validity requireValidity(Instant validFrom, Instant validUntil, LocalDateTime now) {
        require(validFrom != null && validUntil != null, "validFrom and validUntil are required");
        Instant current = now.toInstant(ZoneOffset.UTC);
        require(validUntil.isAfter(current) && validFrom.isBefore(validUntil),
                "authority validUntil must be in the future and validFrom must precede validUntil");
        return new Validity(LocalDateTime.ofInstant(validFrom, ZoneOffset.UTC),
                LocalDateTime.ofInstant(validUntil, ZoneOffset.UTC));
    }

    private RoleDefinition requireActiveRole(Long tenantId, String roleCode) {
        RoleDefinition role = requireNonNull(mapper.selectRole(tenantId, roleCode), "role not found");
        require("ACTIVE".equals(role.getStatus()), "role is not ACTIVE");
        return role;
    }

    private String approvalScopeHash(WorkOrder workOrder) {
        return DigestUtil.sha256Hex(String.join("\n",
                String.valueOf(workOrder.getTenantId()), workOrder.getWorkOrderId(), workOrder.getRoleCode(),
                workOrder.getActionCode(), Objects.toString(workOrder.getActionPolicyId(), "-"),
                Objects.toString(workOrder.getActionPolicyVersion(), "-"),
                Objects.toString(workOrder.getSkillId(), "-"), Objects.toString(workOrder.getSkillVersion(), "-"),
                Objects.toString(workOrder.getSkillDefinitionClosureSha256(), "-"),
                Objects.toString(workOrder.getExecutionInputSha256(), "-"),
                Objects.toString(workOrder.getRiskLevel(), "-")));
    }

    private void requireRoleCode(String value) {
        require(value != null && ROLE_CODE.matcher(value).matches(), "invalid roleCode");
    }

    private void requireActionCode(String value) {
        require(value != null && ACTION_CODE.matcher(value).matches(), "invalid actionCode");
    }

    private String requireRef(String value, String field) {
        require(value != null && SAFE_REF.matcher(value).matches(), "invalid " + field);
        return value;
    }

    private void requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank() && value.length() <= maxLength, "invalid " + field);
    }

    private void requirePositiveUser(Long value, String field) {
        require(value != null && value > 0, field + " is required");
    }

    private void requireExpectedVersion(Long expected, Long actual) {
        require(expected != null && expected > 0, "expectedVersion is required");
        require(expected.equals(actual), "expectedVersion is stale");
    }

    private String valueOrUuid(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : requireRef(value, "grantId");
    }

    private static <T> T requireNonNull(T value, String message) {
        require(value != null, message);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record Validity(LocalDateTime validFrom, LocalDateTime validUntil) {}
    private record Outcome(String aggregateType, String aggregateId, Long version, String status,
                           String eventType) {}
}
