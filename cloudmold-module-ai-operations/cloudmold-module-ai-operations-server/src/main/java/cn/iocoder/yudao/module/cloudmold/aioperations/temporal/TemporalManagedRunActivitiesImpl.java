package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlCommand;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlCommandApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlOperation;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlQueryApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlResult;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentAuthorityCommand;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentAuthorityGovernanceApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentAuthorityOperation;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentBusinessCardView;
import cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo.ManagedSkillTaskTriggerReqVO;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.command.AiOperationsManagedRunCommandService;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.command.AiOperationsManagedRunQueryServiceFacade;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.command.ManagedSkillTaskTriggerResult;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.ai-operations.temporal", name = "enabled", havingValue = "true")
public class TemporalManagedRunActivitiesImpl implements TemporalManagedRunActivities {

    private final AiOperationsManagedRunQueryServiceFacade workflows;
    private final AiOperationsManagedRunCommandService managedRuns;
    private final AgentControlCommandApi agentCommands;
    private final AgentControlQueryApi agentQueries;
    private final AgentAuthorityGovernanceApi authorityGovernance;
    private final AiOperationsTemporalMapper mapper;

    @Override
    public TemporalManagedRunState prepare(TemporalManagedRunRequest request, String workflowId, String runId) {
        return TenantUtils.execute(request.getTenantId(), () -> prepareInTenant(request, workflowId, runId));
    }

    @Override
    public TemporalManagedRunState resumeApproved(TemporalManagedRunRequest request,
                                                  TemporalManagedRunState prepared) {
        return TenantUtils.execute(request.getTenantId(), () -> {
            AgentControlResult workOrder = agentQueries.getWorkOrder(prepared.getWorkOrderId());
            require("READY".equals(workOrder.getStatus()), "BPM approval did not leave the work order READY");
            ManagedSkillTaskTriggerReqVO trigger = triggerRequest(request, prepared.getTemporalRunId());
            ManagedSkillTaskTriggerReqVO.ApprovalContext approval =
                    new ManagedSkillTaskTriggerReqVO.ApprovalContext();
            approval.setWorkOrderId(prepared.getWorkOrderId());
            approval.setApprovalId(prepared.getApprovalId());
            approval.setWorkOrderExpectedVersion(workOrder.getAggregateVersion());
            approval.setValidForSeconds(300L);
            trigger.setApproval(approval);
            ManagedSkillTaskTriggerResult result = managedRuns.triggerAs(
                    trigger, prepared.getExecutionUserId(), request.getOperatorUserType());
            mapper.updateRunBinding(request.getTenantId(), prepared.getTemporalRunId(), "SUBMITTED",
                    null, result.getRunId(), result.getTaskId(), now());
            return prepared.toBuilder().status("SUBMITTED").managedRunId(result.getRunId())
                    .taskId(result.getTaskId()).build();
        });
    }

    @Override
    public TemporalManagedRunState reject(TemporalManagedRunRequest request,
                                          TemporalManagedRunState prepared) {
        return TenantUtils.execute(request.getTenantId(), () -> {
            mapper.updateRunBinding(request.getTenantId(), prepared.getTemporalRunId(), "REJECTED",
                    "BPM_REJECTED", null, null, now());
            return prepared.toBuilder().status("REJECTED").errorCode("BPM_REJECTED").build();
        });
    }

    private TemporalManagedRunState prepareInTenant(TemporalManagedRunRequest request,
                                                    String workflowId, String temporalRunId) {
        ManagedSkillTaskWorkflowView workflow = workflows.requireWorkflowAs(
                request.getSkillId(), request.getSkillVersion(),
                request.getOperatorUserId(), request.getOperatorUserType());
        if (!Boolean.TRUE.equals(workflow.getApprovalRequired())) {
            ManagedSkillTaskTriggerResult result = managedRuns.triggerAs(
                    triggerRequest(request, temporalRunId),
                    request.getOperatorUserId(), request.getOperatorUserType());
            TemporalManagedRunState state = TemporalManagedRunState.builder()
                    .status("SUBMITTED").temporalWorkflowId(workflowId).temporalRunId(temporalRunId)
                    .executionUserId(request.getOperatorUserId())
                    .managedRunId(result.getRunId()).taskId(result.getTaskId()).build();
            persist(request, state);
            return state;
        }
        require(request.getRoleCode() != null && request.getActionCode() != null,
                "Approval-bound schedule is missing role/action policy identity");
        TemporalApprovalPolicyRecord approvalPolicy = requireApprovalPolicy(request.getTenantId());
        Long executionUserId = approvalPolicy.getRequesterUserId();
        String suffix = DigestUtil.sha256Hex(temporalRunId).substring(0, 24);
        String workOrderId = "twr-" + suffix;
        String approvalId = "tap-" + suffix;
        AgentControlResult workOrder = agentCommands.execute(AgentControlCommand.builder()
                .operation(AgentControlOperation.CREATE_WORK_ORDER)
                .idempotencyKey("temporal:create-work-order:" + temporalRunId)
                .occurredAt(Instant.now())
                .workOrder(AgentControlCommand.WorkOrderDefinition.builder()
                        .workOrderId(workOrderId).roleCode(request.getRoleCode())
                        .actionCode(request.getActionCode())
                        .title("Temporal 定时托管：" + request.getSkillId())
                        .businessContextJson(request.getInputJson()).build())
                .build(), executionUserId);
        require("WAITING_APPROVAL".equals(workOrder.getStatus()),
                "Approval-bound scheduled work order did not enter WAITING_APPROVAL");
        agentCommands.execute(AgentControlCommand.builder()
                .operation(AgentControlOperation.REQUEST_APPROVAL)
                .idempotencyKey("temporal:request-approval:" + temporalRunId)
                .occurredAt(Instant.now())
                .approval(AgentControlCommand.ApprovalDefinition.builder()
                        .approvalId(approvalId).workOrderId(workOrderId)
                        .reasonCode("TEMPORAL_SCHEDULED_WRITE")
                        .workOrderExpectedVersion(workOrder.getAggregateVersion()).build())
                .build(), executionUserId);
        AgentBusinessCardView approvalCard = agentQueries.listBusinessCards(
                        request.getRoleCode(), "APPROVAL", "PENDING", 100).stream()
                .filter(card -> approvalId.equals(card.getCardId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Approval card was not materialized for scheduled Agent run"));
        Instant validFrom = Instant.now().minus(1, ChronoUnit.MINUTES);
        authorityGovernance.executeAuthorityGovernance(AgentAuthorityCommand.builder()
                .operation(AgentAuthorityOperation.GRANT_APPROVER)
                .idempotencyKey("temporal:grant-approver:" + temporalRunId)
                .occurredAt(Instant.now())
                .approvalGrant(AgentAuthorityCommand.ApprovalGrantDefinition.builder()
                        .grantId("tag-" + suffix)
                        .approverUserId(approvalPolicy.getApproverUserId())
                        .approvalId(approvalId)
                        .roleCode(approvalCard.getRoleCode())
                        .actionCode(approvalCard.getActionCode())
                        .riskLevel(approvalCard.getRiskLevel())
                        .scopeHash(approvalCard.getScopeHash())
                        .validFrom(validFrom)
                        .validUntil(validFrom.plus(7, ChronoUnit.DAYS))
                        .build())
                .build(), approvalPolicy.getGovernanceUserId());
        TemporalManagedRunState state = TemporalManagedRunState.builder()
                .status("WAITING_APPROVAL").temporalWorkflowId(workflowId)
                .temporalRunId(temporalRunId).executionUserId(executionUserId)
                .workOrderId(workOrderId).approvalId(approvalId).build();
        persist(request, state);
        return state;
    }

    private ManagedSkillTaskTriggerReqVO triggerRequest(TemporalManagedRunRequest request, String temporalRunId) {
        ManagedSkillTaskTriggerReqVO trigger = new ManagedSkillTaskTriggerReqVO();
        String suffix = DigestUtil.sha256Hex(temporalRunId).substring(0, 24);
        trigger.setRunId("tsr-" + suffix);
        trigger.setClientRequestKey("temporal/" + request.getScheduleId() + "/" + suffix);
        trigger.setSkillId(request.getSkillId());
        trigger.setSkillVersion(request.getSkillVersion());
        trigger.setInputJson(request.getInputJson());
        return trigger;
    }

    private void persist(TemporalManagedRunRequest request, TemporalManagedRunState state) {
        LocalDateTime now = now();
        mapper.upsertRunBinding(new TemporalRunBindingRecord()
                .setTenantId(request.getTenantId()).setTemporalRunId(state.getTemporalRunId())
                .setTemporalWorkflowId(state.getTemporalWorkflowId()).setScheduleId(request.getScheduleId())
                .setWorkOrderId(state.getWorkOrderId()).setApprovalId(state.getApprovalId())
                .setManagedRunId(state.getManagedRunId()).setSkillTaskId(state.getTaskId())
                .setStatus(state.getStatus()).setErrorCode(state.getErrorCode())
                .setCreatedAt(now).setUpdatedAt(now));
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private TemporalApprovalPolicyRecord requireApprovalPolicy(Long tenantId) {
        TemporalApprovalPolicyRecord policy = mapper.selectApprovalPolicy(tenantId);
        require(policy != null, "Approval-bound schedule has no active tenant approval policy");
        require(policy.getRequesterUserId() != null && policy.getRequesterUserId() > 0,
                "Approval policy requester is missing");
        require(policy.getApproverUserId() != null && policy.getApproverUserId() > 0,
                "Approval policy approver is missing");
        require(policy.getGovernanceUserId() != null && policy.getGovernanceUserId() > 0,
                "Approval policy governance actor is missing");
        require(!policy.getRequesterUserId().equals(policy.getApproverUserId()),
                "Approval requester and approver must be different users");
        require(!policy.getGovernanceUserId().equals(policy.getRequesterUserId())
                        && !policy.getGovernanceUserId().equals(policy.getApproverUserId()),
                "Approval governance actor must be independent from requester and approver");
        return policy;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
