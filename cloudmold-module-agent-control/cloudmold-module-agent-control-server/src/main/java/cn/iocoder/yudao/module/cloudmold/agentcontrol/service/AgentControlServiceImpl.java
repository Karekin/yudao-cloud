package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.*;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.*;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm.AgentApprovalWorkflowAttestationGate;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm.AgentApprovalWorkflowRegistrar;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

@ConditionalOnProperty(prefix = "cloudmold.agent-control", name = "enabled", havingValue = "true")
@Service
public class AgentControlServiceImpl implements AgentControlCommandApi, AgentControlQueryApi {
    static final int OPERATION_SUCCEEDED = 10;
    private static final Pattern ROLE_CODE = Pattern.compile("[a-z][a-z0-9-]{1,63}");
    private static final Pattern ACTION_CODE = Pattern.compile("[a-z][a-z0-9._-]{2,127}");
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Set<String> RISK_LEVELS = Set.of("R0", "R1", "R2", "R3");
    private static final Set<String> APPROVAL_DECISIONS = Set.of("APPROVE", "REJECT");
    private static final List<String> TECHNICAL_PROTOCOL_TOKENS = List.of(
            "tenantid", "operatorid", "idempotencykey", "clientrequestkey", "cloudmoldskilltask");

    private final AgentControlStoreMapper mapper;
    private final Clock clock;
    private final AgentApprovalWorkflowRegistrar approvalWorkflows;
    private final AgentApprovalWorkflowAttestationGate approvalAttestations;

    @Autowired
    public AgentControlServiceImpl(AgentControlStoreMapper mapper,
                                   ObjectProvider<AgentApprovalWorkflowRegistrar> approvalWorkflows,
                                   ObjectProvider<AgentApprovalWorkflowAttestationGate> approvalAttestations) {
        this(mapper, Clock.systemUTC(),
                approvalWorkflows.getIfAvailable(() -> AgentApprovalWorkflowRegistrar.DISABLED),
                approvalAttestations.getIfAvailable(() -> AgentApprovalWorkflowAttestationGate.DISABLED));
    }

    AgentControlServiceImpl(AgentControlStoreMapper mapper, Clock clock) {
        this(mapper, clock, AgentApprovalWorkflowRegistrar.DISABLED,
                AgentApprovalWorkflowAttestationGate.DISABLED);
    }

    AgentControlServiceImpl(AgentControlStoreMapper mapper, Clock clock,
                            AgentApprovalWorkflowRegistrar approvalWorkflows) {
        this(mapper, clock, approvalWorkflows, AgentApprovalWorkflowAttestationGate.DISABLED);
    }

    AgentControlServiceImpl(AgentControlStoreMapper mapper, Clock clock,
                            AgentApprovalWorkflowRegistrar approvalWorkflows,
                            AgentApprovalWorkflowAttestationGate approvalAttestations) {
        this.mapper = mapper;
        this.clock = clock;
        this.approvalWorkflows = approvalWorkflows;
        this.approvalAttestations = approvalAttestations;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentControlResult execute(AgentControlCommand command, Long operatorUserId) {
        validateEnvelope(command, operatorUserId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(JsonUtils.toJsonString(Map.of(
                "command", command, "operator_user_id", operatorUserId)));
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve agent-control operation");
        Operation operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "agent-control operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with different payload or operator");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing agent-control operation is incomplete");
            AgentControlResult replay = JsonUtils.parseObject(operation.getResultJson(), AgentControlResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        Outcome outcome = switch (command.getOperation()) {
            case DEFINE_ROLE -> defineRole(tenantId, command, now);
            case SET_ACTION_POLICY -> setActionPolicy(tenantId, command, now);
            case CREATE_WORK_ORDER -> createWorkOrder(tenantId, operatorUserId, command, now);
            case START_WORK_ORDER -> startWorkOrder(tenantId, operatorUserId, command, now);
            case CREATE_HANDOFF -> createHandoff(tenantId, operatorUserId, command, now);
            case ACCEPT_HANDOFF -> acceptHandoff(tenantId, operatorUserId, command, now);
            case REQUEST_APPROVAL -> requestApproval(tenantId, operatorUserId, command, now);
            case DECIDE_APPROVAL -> decideApproval(tenantId, operatorUserId, command, now);
            case RECORD_BUSINESS_RESULT -> recordBusinessResult(tenantId, operatorUserId, command, now);
        };
        appendAudit(tenantId, operatorUserId, command, outcome, now);
        AgentControlResult result = AgentControlResult.builder().operationId(operationId).duplicate(false)
                .aggregateType(outcome.aggregateType()).aggregateId(outcome.aggregateId())
                .aggregateVersion(outcome.version()).status(outcome.status()).build();
        require(mapper.markOperationSucceeded(operationId, tenantId, outcome.aggregateType(), outcome.aggregateId(),
                JsonUtils.toJsonString(result), now) == 1, "agent-control operation completion conflict");
        return result;
    }

    private Outcome defineRole(Long tenantId, AgentControlCommand command, LocalDateTime now) {
        AgentControlCommand.RoleDefinition input = requireNonNull(command.getRole(), "role is required");
        requireRoleCode(input.getRoleCode());
        requireText(input.getRoleName(), "roleName", 128);
        validateJson(input.getResponsibilityJson(), "responsibilityJson");
        validateJson(input.getKpiJson(), "kpiJson");
        validateJson(input.getApprovalBoundaryJson(), "approvalBoundaryJson");
        validateJson(input.getMemoryPolicyJson(), "memoryPolicyJson");
        RoleDefinition row = new RoleDefinition().setRoleId(valueOrUuid(input.getRoleId())).setTenantId(tenantId)
                .setRoleCode(input.getRoleCode()).setRoleName(input.getRoleName())
                .setResponsibilityJson(input.getResponsibilityJson()).setKpiJson(input.getKpiJson())
                .setApprovalBoundaryJson(input.getApprovalBoundaryJson()).setMemoryPolicyJson(input.getMemoryPolicyJson())
                .setStatus("ACTIVE").setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertRole(row) == 1, "failed to persist role definition");
        return new Outcome("agent_role_definition", row.getRoleCode(), 1L, "ACTIVE",
                "agent_control.role.defined");
    }

    private Outcome setActionPolicy(Long tenantId, AgentControlCommand command, LocalDateTime now) {
        AgentControlCommand.RoleActionPolicyDefinition input = requireNonNull(command.getActionPolicy(),
                "actionPolicy is required");
        requireRoleCode(input.getRoleCode());
        requireActiveRole(tenantId, input.getRoleCode());
        requireActionCode(input.getActionCode());
        require(RISK_LEVELS.contains(input.getRiskLevel()), "unsupported riskLevel");
        require(input.getApprovalRequired() != null, "approvalRequired is required");
        require(!"R3".equals(input.getRiskLevel()) || input.getApprovalRequired(),
                "R3 actions must require an independent approval");
        boolean executionRequired = Boolean.TRUE.equals(input.getExecutionRequired());
        if (executionRequired) {
            requireRef(input.getSkillId(), "skillId");
            requireRef(input.getSkillVersion(), "skillVersion");
            requireSha256(input.getSkillDefinitionClosureSha256(), "skillDefinitionClosureSha256");
        } else {
            require(input.getSkillId() == null && input.getSkillVersion() == null
                            && input.getSkillDefinitionClosureSha256() == null,
                    "non-executable policy must not declare a Skill binding");
        }
        RoleActionPolicy row = new RoleActionPolicy().setPolicyId(valueOrUuid(input.getPolicyId()))
                .setTenantId(tenantId).setRoleCode(input.getRoleCode()).setActionCode(input.getActionCode())
                .setPermissionMode("ALLOW").setRiskLevel(input.getRiskLevel())
                .setApprovalRequired(input.getApprovalRequired()).setEnabled(true)
                .setExecutionRequired(executionRequired).setSkillId(input.getSkillId())
                .setSkillVersion(input.getSkillVersion())
                .setSkillDefinitionClosureSha256(input.getSkillDefinitionClosureSha256())
                .setVersion(1L)
                .setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertActionPolicy(row) == 1, "failed to persist role action policy");
        return new Outcome("role_action_policy", row.getPolicyId(), 1L, "ENABLED",
                "agent_control.action_policy.set");
    }

    private Outcome createWorkOrder(Long tenantId, Long operatorUserId, AgentControlCommand command,
                                    LocalDateTime now) {
        AgentControlCommand.WorkOrderDefinition input = requireNonNull(command.getWorkOrder(),
                "workOrder is required");
        requireRoleCode(input.getRoleCode());
        requireActiveRole(tenantId, input.getRoleCode());
        requireActorRoleGrant(tenantId, operatorUserId, input.getRoleCode(), now);
        requireActionCode(input.getActionCode());
        RoleActionPolicy policy = mapper.selectActionPolicy(tenantId, input.getRoleCode(), input.getActionCode());
        require(policy != null && Boolean.TRUE.equals(policy.getEnabled()) && "ALLOW".equals(policy.getPermissionMode()),
                "role action is not present in the configured policy catalog");
        requireText(input.getTitle(), "title", 256);
        String businessContextJson = AgentControlJson.canonicalBusinessObject(
                input.getBusinessContextJson() == null ? "{}" : input.getBusinessContextJson(),
                "businessContextJson");
        String status = Boolean.TRUE.equals(policy.getApprovalRequired()) ? "WAITING_APPROVAL" : "READY";
        WorkOrder row = new WorkOrder().setWorkOrderId(valueOrUuid(input.getWorkOrderId())).setTenantId(tenantId)
                .setRoleCode(input.getRoleCode()).setActionCode(input.getActionCode()).setTitle(input.getTitle())
                .setBusinessContextJson(businessContextJson).setStatus(status).setRequesterUserId(operatorUserId)
                .setAssigneeUserId(operatorUserId)
                .setActionPolicyId(policy.getPolicyId()).setActionPolicyVersion(policy.getVersion())
                .setRiskLevel(policy.getRiskLevel()).setExecutionRequired(policy.getExecutionRequired())
                .setSkillId(policy.getSkillId()).setSkillVersion(policy.getSkillVersion())
                .setSkillDefinitionClosureSha256(policy.getSkillDefinitionClosureSha256())
                .setExecutionInputSha256(AgentControlJson.sha256(businessContextJson))
                .setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertWorkOrder(row) == 1, "failed to persist role work order");
        return new Outcome("role_work_order", row.getWorkOrderId(), 1L, status,
                "agent_control.work_order.created");
    }

    private Outcome startWorkOrder(Long tenantId, Long operatorUserId, AgentControlCommand command,
                                   LocalDateTime now) {
        AgentControlCommand.WorkOrderDefinition input = requireNonNull(command.getWorkOrder(),
                "workOrder is required");
        requireRef(input.getWorkOrderId(), "workOrderId");
        WorkOrder row = requireNonNull(mapper.selectWorkOrderForUpdate(tenantId, input.getWorkOrderId()),
                "work order not found");
        requireActiveRole(tenantId, row.getRoleCode());
        requireActorRoleGrant(tenantId, operatorUserId, row.getRoleCode(), now);
        requireExpectedVersion(input.getWorkOrderExpectedVersion(), row.getVersion(), "workOrderExpectedVersion");
        require("READY".equals(row.getStatus()), "only a READY work order can start");
        require(mapper.transitionWorkOrder(tenantId, row.getWorkOrderId(), row.getVersion(), "READY", "IN_PROGRESS",
                operatorUserId, null, now) == 1, "work order start conflict");
        return new Outcome("role_work_order", row.getWorkOrderId(), row.getVersion() + 1, "IN_PROGRESS",
                "agent_control.work_order.started");
    }

    private Outcome createHandoff(Long tenantId, Long operatorUserId, AgentControlCommand command,
                                  LocalDateTime now) {
        AgentControlCommand.HandoffDefinition input = requireNonNull(command.getHandoff(), "handoff is required");
        requireRef(input.getWorkOrderId(), "workOrderId");
        requireRoleCode(input.getToRoleCode());
        requireActiveRole(tenantId, input.getToRoleCode());
        requireActionCode(input.getToActionCode());
        RoleActionPolicy targetPolicy = mapper.selectActionPolicy(
                tenantId, input.getToRoleCode(), input.getToActionCode());
        require(targetPolicy != null && Boolean.TRUE.equals(targetPolicy.getEnabled())
                        && "ALLOW".equals(targetPolicy.getPermissionMode()),
                "handoff target action is not present in the configured policy catalog");
        require(!Boolean.TRUE.equals(targetPolicy.getApprovalRequired()),
                "an approval-bound target action requires a new work order");
        require(!Boolean.TRUE.equals(targetPolicy.getExecutionRequired()),
                "an executable target action requires a new successor work order");
        requireBusinessSummary(input.getSummary());
        WorkOrder workOrder = requireNonNull(mapper.selectWorkOrderForUpdate(tenantId, input.getWorkOrderId()),
                "work order not found");
        requireActiveRole(tenantId, workOrder.getRoleCode());
        requireActorRoleGrant(tenantId, operatorUserId, workOrder.getRoleCode(), now);
        requireExpectedVersion(input.getWorkOrderExpectedVersion(), workOrder.getVersion(),
                "workOrderExpectedVersion");
        require("IN_PROGRESS".equals(workOrder.getStatus()), "only an IN_PROGRESS work order can be handed off");
        require(!Boolean.TRUE.equals(workOrder.getExecutionRequired()),
                "an executable work order cannot transfer its frozen action identity");
        require(!workOrder.getRoleCode().equals(input.getToRoleCode()), "handoff target role must be different");
        Handoff row = new Handoff().setHandoffId(valueOrUuid(input.getHandoffId())).setTenantId(tenantId)
                .setWorkOrderId(workOrder.getWorkOrderId()).setFromRoleCode(workOrder.getRoleCode())
                .setFromActionCode(workOrder.getActionCode()).setToRoleCode(input.getToRoleCode())
                .setToActionCode(input.getToActionCode()).setSummary(input.getSummary()).setStatus("PENDING")
                .setRequestedByUserId(operatorUserId).setVersion(1L).setRequestedAt(now);
        require(mapper.insertHandoff(row) == 1, "failed to persist role handoff");
        return new Outcome("role_handoff", row.getHandoffId(), 1L, "PENDING",
                "agent_control.handoff.created");
    }

    private Outcome acceptHandoff(Long tenantId, Long operatorUserId, AgentControlCommand command,
                                  LocalDateTime now) {
        AgentControlCommand.HandoffDefinition input = requireNonNull(command.getHandoff(), "handoff is required");
        requireRef(input.getHandoffId(), "handoffId");
        Handoff row = requireNonNull(mapper.selectHandoffForUpdate(tenantId, input.getHandoffId()),
                "handoff not found");
        requireActiveRole(tenantId, row.getToRoleCode());
        requireActorRoleGrant(tenantId, operatorUserId, row.getToRoleCode(), now);
        requireExpectedVersion(input.getHandoffExpectedVersion(), row.getVersion(), "handoffExpectedVersion");
        require("PENDING".equals(row.getStatus()), "only a PENDING handoff can be accepted");
        require(!operatorUserId.equals(row.getRequestedByUserId()),
                "handoff requester and accepter must be different authenticated users");
        WorkOrder workOrder = requireNonNull(mapper.selectWorkOrderForUpdate(tenantId, row.getWorkOrderId()),
                "work order not found");
        requireExpectedVersion(input.getWorkOrderExpectedVersion(), workOrder.getVersion(),
                "workOrderExpectedVersion");
        require("IN_PROGRESS".equals(workOrder.getStatus()) && workOrder.getRoleCode().equals(row.getFromRoleCode())
                        && workOrder.getActionCode().equals(row.getFromActionCode()),
                "work order ownership changed before handoff acceptance");
        require(mapper.acceptHandoff(tenantId, row.getHandoffId(), row.getVersion(), operatorUserId, now) == 1,
                "handoff acceptance conflict");
        require(mapper.acceptWorkOrderHandoff(tenantId, workOrder.getWorkOrderId(), workOrder.getVersion(),
                row.getFromRoleCode(), row.getFromActionCode(), row.getToRoleCode(), row.getToActionCode(),
                operatorUserId, now) == 1,
                "work order handoff conflict");
        return new Outcome("role_handoff", row.getHandoffId(), row.getVersion() + 1, "ACCEPTED",
                "agent_control.handoff.accepted");
    }

    private Outcome requestApproval(Long tenantId, Long operatorUserId, AgentControlCommand command,
                                    LocalDateTime now) {
        AgentControlCommand.ApprovalDefinition input = requireNonNull(command.getApproval(), "approval is required");
        requireRef(input.getWorkOrderId(), "workOrderId");
        requireText(input.getReasonCode(), "reasonCode", 128);
        WorkOrder workOrder = requireNonNull(mapper.selectWorkOrderForUpdate(tenantId, input.getWorkOrderId()),
                "work order not found");
        requireActiveRole(tenantId, workOrder.getRoleCode());
        requireActorRoleGrant(tenantId, operatorUserId, workOrder.getRoleCode(), now);
        requireExpectedVersion(input.getWorkOrderExpectedVersion(), workOrder.getVersion(),
                "workOrderExpectedVersion");
        require("WAITING_APPROVAL".equals(workOrder.getStatus()),
                "only a WAITING_APPROVAL work order can request approval");
        require(operatorUserId.equals(workOrder.getRequesterUserId()),
                "only the authenticated work-order requester can request approval");
        require(workOrder.getApprovalId() == null, "work order already has an approval request");
        Approval row = new Approval().setApprovalId(valueOrUuid(input.getApprovalId())).setTenantId(tenantId)
                .setWorkOrderId(workOrder.getWorkOrderId()).setActionCode(workOrder.getActionCode())
                .setRequesterUserId(operatorUserId).setScopeHash(approvalScopeHash(workOrder))
                .setStatus("PENDING").setReasonCode(input.getReasonCode())
                .setVersion(1L).setRequestedAt(now);
        require(mapper.insertApproval(row) == 1, "failed to persist approval request");
        require(mapper.attachApproval(tenantId, workOrder.getWorkOrderId(), workOrder.getVersion(),
                row.getApprovalId(), now) == 1, "work order approval attachment conflict");
        approvalWorkflows.register(tenantId, row, workOrder, now);
        return new Outcome("role_approval", row.getApprovalId(), 1L, "PENDING",
                "agent_control.approval.requested");
    }

    private Outcome decideApproval(Long tenantId, Long operatorUserId, AgentControlCommand command,
                                   LocalDateTime now) {
        AgentControlCommand.ApprovalDefinition input = requireNonNull(command.getApproval(), "approval is required");
        requireRef(input.getApprovalId(), "approvalId");
        require(APPROVAL_DECISIONS.contains(input.getDecision()), "decision must be APPROVE or REJECT");
        requireText(input.getReasonCode(), "reasonCode", 128);
        Approval approval = requireNonNull(mapper.selectApprovalForUpdate(tenantId, input.getApprovalId()),
                "approval not found");
        requireExpectedVersion(input.getApprovalExpectedVersion(), approval.getVersion(), "approvalExpectedVersion");
        require("PENDING".equals(approval.getStatus()), "only a PENDING approval can be decided");
        require(!operatorUserId.equals(approval.getRequesterUserId()),
                "approval requester and approver must be different authenticated users");
        WorkOrder workOrder = requireNonNull(mapper.selectWorkOrderForUpdate(tenantId, approval.getWorkOrderId()),
                "work order not found");
        require(workOrder.getAssigneeUserId() == null
                        || !operatorUserId.equals(workOrder.getAssigneeUserId()),
                "work-order executor and approver must be different authenticated users");
        requireActiveRole(tenantId, workOrder.getRoleCode());
        requireExpectedVersion(input.getWorkOrderExpectedVersion(), workOrder.getVersion(),
                "workOrderExpectedVersion");
        require("WAITING_APPROVAL".equals(workOrder.getStatus())
                        && approval.getApprovalId().equals(workOrder.getApprovalId()),
                "approval is not bound to the current waiting work order");
        require(Objects.equals(approval.getScopeHash(), approvalScopeHash(workOrder)),
                "approval scope drifted after the request was frozen");
        RoleActionPolicy policy = requireNonNull(mapper.selectActionPolicy(tenantId, workOrder.getRoleCode(),
                workOrder.getActionCode()), "work order action policy not found");
        require(Boolean.TRUE.equals(policy.getEnabled()) && "ALLOW".equals(policy.getPermissionMode())
                        && Boolean.TRUE.equals(policy.getApprovalRequired()),
                "work order action policy is not approval-enabled");
        require(Objects.equals(workOrder.getActionPolicyId(), policy.getPolicyId())
                        && Objects.equals(workOrder.getActionPolicyVersion(), policy.getVersion())
                        && Objects.equals(workOrder.getRiskLevel(), policy.getRiskLevel()),
                "work order action policy snapshot no longer matches the configured policy");
        if ("R3".equals(workOrder.getRiskLevel())) {
            require(approvalAttestations.supportsR3MultiPartyApproval(),
                    "R3 decision requires the governed multi-party BPM approval gate");
        } else {
            require(mapper.selectEffectiveApprovalAuthorityGrant(tenantId, operatorUserId, approval.getApprovalId(),
                    workOrder.getRoleCode(), workOrder.getActionCode(), workOrder.getRiskLevel(),
                    approval.getScopeHash(), now) != null,
                    "authenticated actor has no exact effective approver grant");
        }
        approvalAttestations.assertDecisionAllowed(tenantId, operatorUserId, approval, input.getDecision());
        String approvalStatus = "APPROVE".equals(input.getDecision()) ? "APPROVED" : "REJECTED";
        String workOrderStatus = "APPROVE".equals(input.getDecision()) ? "READY" : "CANCELLED";
        require(mapper.decideApproval(tenantId, approval.getApprovalId(), approval.getVersion(), approvalStatus,
                operatorUserId, input.getReasonCode(), now) == 1, "approval decision conflict");
        require(mapper.transitionWorkOrder(tenantId, workOrder.getWorkOrderId(), workOrder.getVersion(),
                "WAITING_APPROVAL", workOrderStatus, null, null, now) == 1,
                "approved work order transition conflict");
        emitApprovalOutcomeNotification(tenantId, workOrder, approval, approvalStatus, workOrderStatus,
                operatorUserId, input.getReasonCode(), now);
        return new Outcome("role_approval", approval.getApprovalId(), approval.getVersion() + 1, approvalStatus,
                "agent_control.approval.decided");
    }

    private Outcome recordBusinessResult(Long tenantId, Long operatorUserId, AgentControlCommand command,
                                         LocalDateTime now) {
        AgentControlCommand.BusinessResultDefinition input = requireNonNull(command.getBusinessResult(),
                "businessResult is required");
        requireRef(input.getWorkOrderId(), "workOrderId");
        requireActionCode(input.getOutcomeCode().toLowerCase(Locale.ROOT).replace('_', '.'));
        requireBusinessSummary(input.getSummary());
        requireRef(input.getEvidenceRef(), "evidenceRef");
        WorkOrder workOrder = requireNonNull(mapper.selectWorkOrderForUpdate(tenantId, input.getWorkOrderId()),
                "work order not found");
        requireActiveRole(tenantId, workOrder.getRoleCode());
        requireActorRoleGrant(tenantId, operatorUserId, workOrder.getRoleCode(), now);
        requireExpectedVersion(input.getWorkOrderExpectedVersion(), workOrder.getVersion(),
                "workOrderExpectedVersion");
        require("IN_PROGRESS".equals(workOrder.getStatus()),
                "business result requires an IN_PROGRESS work order");
        require(operatorUserId.equals(workOrder.getAssigneeUserId()),
                "only the authenticated assignee can record the business result");
        require(!Boolean.TRUE.equals(workOrder.getExecutionRequired()),
                "executable work orders can only complete from an accepted Skill Task terminal proof");
        if (workOrder.getMissionId() != null
                && mapper.countWaitingExecutableSuccessors(tenantId, workOrder.getWorkOrderId()) > 0) {
            require(workOrder.getActiveRunId() != null
                            && mapper.selectMissionCheckpointForRunForUpdate(tenantId, workOrder.getWorkOrderId(),
                            workOrder.getActiveRunId()) != null,
                    "current mission run must checkpoint structured output before unlocking executable successor work");
        }
        BusinessResult row = new BusinessResult().setResultId(valueOrUuid(input.getResultId())).setTenantId(tenantId)
                .setWorkOrderId(workOrder.getWorkOrderId()).setOutcomeCode(input.getOutcomeCode())
                .setSummary(input.getSummary()).setEvidenceRef(input.getEvidenceRef())
                .setRecordedByUserId(operatorUserId).setRecordedAt(now);
        require(mapper.insertBusinessResult(row) == 1, "failed to persist immutable business result");
        require(mapper.transitionWorkOrder(tenantId, workOrder.getWorkOrderId(), workOrder.getVersion(),
                "IN_PROGRESS", "COMPLETED", operatorUserId, null, now) == 1,
                "work order completion conflict");
        return new Outcome("business_result", row.getResultId(), 1L, "RECORDED",
                "agent_control.business_result.recorded");
    }

    private void appendAudit(Long tenantId, Long operatorUserId, AgentControlCommand command, Outcome outcome,
                             LocalDateTime now) {
        String detailJson = JsonUtils.toJsonString(Map.of("operation", command.getOperation().name(),
                "status", outcome.status(), "idempotency_key_sha256",
                DigestUtil.sha256Hex(command.getIdempotencyKey())));
        AuditEvent event = new AuditEvent().setAuditEventId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setAggregateType(outcome.aggregateType()).setAggregateId(outcome.aggregateId())
                .setAggregateVersion(outcome.version()).setEventType(outcome.eventType())
                .setActorUserId(operatorUserId).setDetailJson(detailJson).setOccurredAt(now).setCreatedAt(now);
        require(mapper.insertAuditEvent(event) == 1, "failed to append immutable audit event");
    }

    @Override
    @Transactional(readOnly = true)
    public AgentControlResult getRole(String roleCode) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireRoleCode(roleCode);
        RoleDefinition row = requireNonNull(mapper.selectRole(tenantId, roleCode), "role not found");
        return view("agent_role_definition", row.getRoleCode(), row.getVersion(), row.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public AgentControlResult getWorkOrder(String workOrderId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        WorkOrder row = requireNonNull(mapper.selectWorkOrder(tenantId, requireRef(workOrderId, "workOrderId")),
                "work order not found");
        return view("role_work_order", row.getWorkOrderId(), row.getVersion(), row.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public AgentControlResult getHandoff(String handoffId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Handoff row = requireNonNull(mapper.selectHandoff(tenantId, requireRef(handoffId, "handoffId")),
                "handoff not found");
        return view("role_handoff", row.getHandoffId(), row.getVersion(), row.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public AgentControlResult getApproval(String approvalId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Approval row = requireNonNull(mapper.selectApproval(tenantId, requireRef(approvalId, "approvalId")),
                "approval not found");
        return view("role_approval", row.getApprovalId(), row.getVersion(), row.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public AgentApprovalDetailView getApprovalDetail(String approvalId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        return requireNonNull(mapper.selectApprovalDetail(tenantId, requireRef(approvalId, "approvalId")),
                "approval not found");
    }

    @Override
    @Transactional(readOnly = true)
    public AgentControlResult getBusinessResult(String resultId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        BusinessResult row = requireNonNull(mapper.selectBusinessResult(tenantId, requireRef(resultId, "resultId")),
                "business result not found");
        return view("business_result", row.getResultId(), 1L, "RECORDED");
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentBusinessCardView> listBusinessCards(String roleCode, String cardType, String status,
                                                         Integer limit) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String normalizedRole = roleCode == null || roleCode.isBlank() ? null : roleCode.trim();
        if (normalizedRole != null) requireRoleCode(normalizedRole);
        String normalizedType = cardType == null || cardType.isBlank() ? null
                : cardType.trim().toUpperCase(Locale.ROOT);
        require(normalizedType == null || Set.of("APPROVAL", "HANDOFF", "RESULT").contains(normalizedType),
                "unsupported cardType");
        String normalizedStatus = status == null || status.isBlank() ? null
                : requireRef(status.trim().toUpperCase(Locale.ROOT), "status");
        int normalizedLimit = limit == null ? 50 : limit;
        require(normalizedLimit >= 1 && normalizedLimit <= 100, "limit must be between 1 and 100");
        List<AgentBusinessCardView> cards = mapper.selectBusinessCards(tenantId, normalizedRole, normalizedType,
                normalizedStatus, normalizedLimit);
        return cards == null ? List.of() : List.copyOf(cards);
    }

    @Override
    public List<ActorRoleGrantView> listActorRoleGrants(String roleCode, String status, Long actorUserId,
                                                        Integer limit) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String normalizedRole = roleCode == null || roleCode.isBlank() ? null : roleCode.trim();
        if (normalizedRole != null) requireRoleCode(normalizedRole);
        String normalizedStatus = status == null || status.isBlank() ? null
                : requireRef(status.trim().toUpperCase(Locale.ROOT), "status");
        int normalizedLimit = limit == null ? 50 : limit;
        require(normalizedLimit >= 1 && normalizedLimit <= 200, "limit must be between 1 and 200");
        List<ActorRoleGrantView> grants = mapper.selectActorRoleGrants(tenantId, normalizedRole, normalizedStatus,
                actorUserId, normalizedLimit);
        return grants == null ? List.of() : List.copyOf(grants);
    }

    private RoleDefinition requireActiveRole(Long tenantId, String roleCode) {
        RoleDefinition role = requireNonNull(mapper.selectRole(tenantId, roleCode), "role not found");
        require("ACTIVE".equals(role.getStatus()), "role is not ACTIVE");
        return role;
    }

    private void requireActorRoleGrant(Long tenantId, Long actorUserId, String roleCode, LocalDateTime now) {
        require(mapper.selectEffectiveActorRoleGrant(tenantId, actorUserId, roleCode, now) != null,
                "authenticated actor has no effective grant for role " + roleCode);
    }

    private void validateEnvelope(AgentControlCommand command, Long operatorUserId) {
        require(command != null && command.getOperation() != null, "operation is required");
        require(operatorUserId != null && operatorUserId > 0, "authenticated operatorUserId is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        // occurredAt is an optional caller-reported fact only. Persistence and audit time always use the server clock.
    }

    private void validateJson(String value, String field) {
        requireText(value, field, 16_384);
        try {
            JsonUtils.parseObject(value, Object.class);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " must contain valid JSON", exception);
        }
    }

    private void validateBusinessContextJson(String value) {
        validateJson(value, "businessContextJson");
        requireNoTechnicalProtocolTokens(value,
                "businessContextJson must not contain technical execution parameters");
    }

    private void emitApprovalOutcomeNotification(Long tenantId, WorkOrder workOrder, Approval approval,
                                                 String approvalStatus, String workOrderStatus,
                                                 Long operatorUserId, String reasonCode,
                                                 LocalDateTime now) {
        String notificationType = "READY".equals(workOrderStatus)
                ? "WORK_ORDER_READY"
                : "WORK_ORDER_CANCELLED";
        String eventType = "READY".equals(workOrderStatus)
                ? "agent_control.work_order.ready"
                : "agent_control.work_order.cancelled";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("approvalId", approval.getApprovalId());
        payload.put("approvalStatus", approvalStatus);
        payload.put("decisionByUserId", operatorUserId);
        payload.put("notificationType", notificationType);
        payload.put("reasonCode", reasonCode);
        payload.put("workOrderStatus", workOrderStatus);
        require(mapper.insertAgentOutbox(UUID.randomUUID().toString(), tenantId, "role_work_order",
                workOrder.getWorkOrderId(), eventType, JsonUtils.toJsonString(payload), now) == 1,
                "failed to persist Agent Control approval notification");
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

    private void requireBusinessSummary(String value) {
        requireText(value, "summary", 2000);
        requireNoTechnicalProtocolTokens(value,
                "business result summary must not expose technical protocol parameters");
    }

    private void requireNoTechnicalProtocolTokens(String value, String message) {
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        require(TECHNICAL_PROTOCOL_TOKENS.stream().noneMatch(normalized::contains), message);
    }

    private void requireExpectedVersion(Long expectedVersion, Long currentVersion, String fieldName) {
        require(expectedVersion != null && expectedVersion > 0, fieldName + " is required");
        require(expectedVersion.equals(currentVersion), fieldName + " is stale");
    }

    private String approvalScopeHash(WorkOrder workOrder) {
        if (workOrder.getActionPolicyId() == null) {
            String context = workOrder.getBusinessContextJson() == null ? "{}" : workOrder.getBusinessContextJson();
            return DigestUtil.sha256Hex(workOrder.getActionCode() + "\n" + DigestUtil.sha256Hex(context));
        }
        return DigestUtil.sha256Hex(String.join("\n",
                String.valueOf(workOrder.getTenantId()), workOrder.getWorkOrderId(), workOrder.getRoleCode(),
                workOrder.getActionCode(), Objects.toString(workOrder.getActionPolicyId(), "-"),
                Objects.toString(workOrder.getActionPolicyVersion(), "-"),
                Objects.toString(workOrder.getSkillId(), "-"), Objects.toString(workOrder.getSkillVersion(), "-"),
                Objects.toString(workOrder.getSkillDefinitionClosureSha256(), "-"),
                Objects.toString(workOrder.getExecutionInputSha256(), "-"),
                Objects.toString(workOrder.getRiskLevel(), "-")));
    }

    private void requireSha256(String value, String field) {
        require(value != null && value.matches("[0-9a-f]{64}"), "invalid " + field);
    }

    private String valueOrUuid(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : requireRef(value, "id");
    }

    private AgentControlResult view(String type, String id, Long version, String status) {
        return AgentControlResult.builder().duplicate(false).aggregateType(type).aggregateId(id)
                .aggregateVersion(version).status(status).build();
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

    private record Outcome(String aggregateType, String aggregateId, Long version, String status,
                           String eventType) {}
}
