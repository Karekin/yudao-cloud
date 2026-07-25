package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentExecutionTicketApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentExecutionTicketCommand;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentExecutionTicketResult;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ActorRoleGrant;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.Approval;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ApprovalAuthorityGrant;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.AuditEvent;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.RoleActionPolicy;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.WorkOrder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalPermitClaims;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskApprovalRefCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@ConditionalOnProperty(prefix = "cloudmold.agent-control", name = "enabled", havingValue = "true")
@Service
public class AgentExecutionTicketService implements AgentExecutionTicketApi {

    private final AgentControlStoreMapper mapper;
    private final AgentExecutionPermitSigner permitSigner;
    private final java.time.Clock clock;

    @Autowired
    public AgentExecutionTicketService(AgentControlStoreMapper mapper, AgentExecutionPermitSigner permitSigner) {
        this(mapper, permitSigner, java.time.Clock.systemUTC());
    }

    AgentExecutionTicketService(AgentControlStoreMapper mapper, AgentExecutionPermitSigner permitSigner,
                                java.time.Clock clock) {
        this.mapper = mapper;
        this.permitSigner = permitSigner;
        this.clock = clock;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentExecutionTicketResult issue(AgentExecutionTicketCommand command, Long operatorUserId) {
        require(command != null, "command is required");
        requireActor(operatorUserId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = now();
        WorkOrder workOrder = requireNonNull(mapper.selectWorkOrderForUpdate(tenantId,
                requireRef(command.getWorkOrderId(), "workOrderId")), "work order not found");
        require(Boolean.TRUE.equals(workOrder.getExecutionRequired()),
                "execution ticket requires an execution-bound work order");
        require("READY".equals(workOrder.getStatus()), "execution ticket requires READY work order");
        require(Objects.equals(command.getWorkOrderExpectedVersion(), workOrder.getVersion()),
                "workOrderExpectedVersion is stale");
        require(operatorUserId.equals(workOrder.getAssigneeUserId()),
                "only the authenticated assignee can issue the execution ticket");
        ActorRoleGrant actorGrant = requireExactRoleGrant(tenantId, operatorUserId, workOrder.getRoleCode(), now);
        requireRef(workOrder.getApprovalId(), "workOrder.approvalId");
        require(Objects.equals(workOrder.getApprovalId(), command.getApprovalId()),
                "approvalId does not match the current work order approval");
        requireRef(workOrder.getSkillId(), "workOrder.skillId");
        requireRef(workOrder.getSkillVersion(), "workOrder.skillVersion");
        requireSha256(workOrder.getSkillDefinitionClosureSha256(), "workOrder.skillDefinitionClosureSha256");
        requireSha256(workOrder.getExecutionInputSha256(), "workOrder.executionInputSha256");
        requireRisk(workOrder.getRiskLevel());

        Approval approval = requireNonNull(mapper.selectApprovalForUpdate(tenantId, workOrder.getApprovalId()),
                "approval not found");
        require("APPROVED".equals(approval.getStatus()), "execution ticket requires APPROVED approval");
        require(Objects.equals(approval.getWorkOrderId(), workOrder.getWorkOrderId()),
                "approval is not bound to the requested work order");
        require(Objects.equals(approval.getActionCode(), workOrder.getActionCode()),
                "approval action does not match the frozen work order");
        require(approval.getApproverUserId() != null && approval.getApproverUserId() > 0,
                "approved execution is missing its approver identity");

        String scopeHash = approvalScopeHash(workOrder);
        require(Objects.equals(approval.getScopeHash(), scopeHash),
                "approval scope drifted after the request was frozen");

        RoleActionPolicy policy = requireNonNull(mapper.selectActionPolicy(tenantId, workOrder.getRoleCode(),
                workOrder.getActionCode()), "frozen action policy not found");
        require(Boolean.TRUE.equals(policy.getEnabled()) && "ALLOW".equals(policy.getPermissionMode())
                        && Boolean.TRUE.equals(policy.getApprovalRequired())
                        && Boolean.TRUE.equals(policy.getExecutionRequired()),
                "frozen action policy is not executable and approval-enabled");
        require(Objects.equals(workOrder.getActionPolicyId(), policy.getPolicyId())
                        && Objects.equals(workOrder.getActionPolicyVersion(), policy.getVersion())
                        && Objects.equals(workOrder.getRiskLevel(), policy.getRiskLevel())
                        && Objects.equals(workOrder.getSkillId(), policy.getSkillId())
                        && Objects.equals(workOrder.getSkillVersion(), policy.getSkillVersion())
                        && Objects.equals(workOrder.getSkillDefinitionClosureSha256(),
                        policy.getSkillDefinitionClosureSha256()),
                "frozen work-order policy snapshot no longer matches the configured action policy");

        ApprovalAuthorityGrant grant = mapper.selectEffectiveApprovalAuthorityGrant(tenantId, approval.getApproverUserId(),
                approval.getApprovalId(), workOrder.getRoleCode(), workOrder.getActionCode(), workOrder.getRiskLevel(),
                scopeHash, now);
        require(grant != null, "approved execution has no exact active approver grant");
        require(Objects.equals(grant.getRequesterUserId(), workOrder.getRequesterUserId())
                        && Objects.equals(grant.getApprovalId(), approval.getApprovalId())
                        && Objects.equals(grant.getRoleCode(), workOrder.getRoleCode())
                        && Objects.equals(grant.getActionCode(), workOrder.getActionCode())
                        && Objects.equals(grant.getRiskLevel(), workOrder.getRiskLevel())
                        && Objects.equals(grant.getScopeHash(), scopeHash),
                "approved execution approver grant drifted from the frozen work order");

        Instant issuedAt = clock.instant();
        Instant expiresAt = effectiveExpiry(now, issuedAt.plus(requireRequestedValidity(command.getValidForSeconds())),
                actorGrant, grant);
        String subject = permitSubject(UserTypeEnum.ADMIN.getValue(), operatorUserId);
        SignedAgentExecutionPermit signed = permitSigner.sign(new SkillTaskApprovalPermitClaims(
                SkillTaskApprovalRefCodec.CLAIMS_VERSION, SkillTaskApprovalRefCodec.LOCAL_HMAC_KEY_ID,
                SkillTaskApprovalRefCodec.DEFAULT_ISSUER, SkillTaskApprovalRefCodec.DEFAULT_AUDIENCE,
                UUID.randomUUID().toString().replace("-", ""), workOrder.getWorkOrderId(), approval.getApprovalId(),
                rootRequestIdentity(workOrder, subject), workOrder.getSkillId(), workOrder.getSkillVersion(),
                workOrder.getSkillDefinitionClosureSha256(), workOrder.getExecutionInputSha256(),
                workOrder.getRiskLevel(), subject, tenantId, issuedAt, issuedAt, expiresAt));
        appendAudit(tenantId, operatorUserId, workOrder, approval, signed, now);
        return AgentExecutionTicketResult.builder().workOrderId(workOrder.getWorkOrderId())
                .approvalId(approval.getApprovalId()).approvalRef(signed.approvalRef())
                .approvalRefSha256(signed.approvalRefSha256())
                .expiresAt(expiresAt).build();
    }

    private ActorRoleGrant requireExactRoleGrant(Long tenantId, Long actorUserId, String roleCode, LocalDateTime now) {
        ActorRoleGrant grant = mapper.selectEffectiveActorRoleGrant(tenantId, actorUserId, roleCode, now);
        require(grant != null,
                "authenticated actor has no effective grant for role " + roleCode);
        return grant;
    }

    private Duration requireRequestedValidity(Long validForSeconds) {
        require(validForSeconds != null && validForSeconds > 0, "validForSeconds is required");
        return Duration.ofSeconds(validForSeconds);
    }

    private Instant effectiveExpiry(LocalDateTime now, Instant requestedExpiry, ActorRoleGrant actorGrant,
                                    ApprovalAuthorityGrant approvalGrant) {
        Instant actorValidUntil = requireFutureWindow(actorGrant.getValidUntil(), "actor role grant", now);
        Instant approvalValidUntil = requireFutureWindow(approvalGrant.getValidUntil(), "approval authority grant", now);
        Instant expiry = requestedExpiry.isBefore(actorValidUntil) ? requestedExpiry : actorValidUntil;
        expiry = expiry.isBefore(approvalValidUntil) ? expiry : approvalValidUntil;
        if (!expiry.isAfter(now.toInstant(ZoneOffset.UTC))) {
            throw new IllegalStateException("execution ticket has no remaining authority validity window");
        }
        return expiry;
    }

    private Instant requireFutureWindow(LocalDateTime validUntil, String field, LocalDateTime now) {
        require(validUntil != null, field + " validUntil is missing");
        Instant value = validUntil.toInstant(ZoneOffset.UTC);
        if (!value.isAfter(now.toInstant(ZoneOffset.UTC))) {
            throw new IllegalStateException("execution ticket has no remaining authority validity window");
        }
        return value;
    }

    private void appendAudit(Long tenantId, Long operatorUserId, WorkOrder workOrder, Approval approval,
                             SignedAgentExecutionPermit signed, LocalDateTime now) {
        String detailJson = JsonUtils.toJsonString(Map.ofEntries(
                Map.entry("approvalId", approval.getApprovalId()),
                Map.entry("workOrderId", workOrder.getWorkOrderId()),
                Map.entry("approvalRefSha256", signed.approvalRefSha256()),
                Map.entry("approvalRefVersion", signed.permitVersion()),
                Map.entry("signingKeyId", signed.keyId()),
                Map.entry("expiresAt", signed.expiresAt().toString()),
                Map.entry("skillId", workOrder.getSkillId()),
                Map.entry("skillVersion", workOrder.getSkillVersion()),
                Map.entry("inputSha256", workOrder.getExecutionInputSha256()),
                Map.entry("definitionClosureSha256", workOrder.getSkillDefinitionClosureSha256()),
                Map.entry("riskLevel", workOrder.getRiskLevel())));
        AuditEvent event = new AuditEvent().setAuditEventId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setAggregateType("role_approval").setAggregateId(approval.getApprovalId())
                .setAggregateVersion(approval.getVersion()).setEventType("agent_control.execution_ticket.issued")
                .setActorUserId(operatorUserId).setDetailJson(detailJson).setOccurredAt(now).setCreatedAt(now);
        require(mapper.insertAuditEvent(event) == 1, "failed to append execution-ticket audit event");
    }

    private String rootRequestIdentity(WorkOrder workOrder, String subject) {
        return SkillTaskApprovalRefCodec.sha256(String.join("\n",
                String.valueOf(workOrder.getTenantId()), workOrder.getWorkOrderId(), workOrder.getApprovalId(),
                workOrder.getSkillId(), workOrder.getSkillVersion(),
                workOrder.getSkillDefinitionClosureSha256(), workOrder.getExecutionInputSha256(),
                workOrder.getRiskLevel(), subject));
    }

    private static String permitSubject(int operatorType, Long operatorUserId) {
        return operatorType + ":" + operatorUserId;
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

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static void requireActor(Long actorUserId) {
        require(actorUserId != null && actorUserId > 0, "operatorUserId is required");
    }

    private static String requireRef(String value, String field) {
        require(value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}"), "invalid " + field);
        return value;
    }

    private static void requireSha256(String value, String field) {
        require(value != null && value.matches("[0-9a-f]{64}"), "invalid " + field);
    }

    private static void requireRisk(String value) {
        String risk = requireNonBlank(value, "workOrder.riskLevel").toUpperCase(Locale.ROOT);
        require("R2".equals(risk) || "R3".equals(risk), "execution ticket is only valid for R2/R3 Skills");
    }

    private static Duration requirePositive(Duration value, String field) {
        require(value != null && !value.isZero() && !value.isNegative(), "invalid " + field);
        return value;
    }

    private static String requireNonBlank(String value, String field) {
        require(value != null && !value.isBlank(), "invalid " + field);
        return value.trim();
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
}
