package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

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
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskQueryApi;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskTerminalProofView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskView;
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
    private final SkillTaskQueryApi skillTaskQueries;
    private final AiOperationsTemporalMapper mapper;

    @Override
    public TemporalManagedRunState prepare(TemporalManagedRunRequest request, String workflowId, String runId) {
        return TenantUtils.execute(request.getTenantId(), () -> prepareInTenant(request, workflowId, runId));
    }

    @Override
    public TemporalManagedRunState resumeApproved(TemporalManagedRunRequest request,
                                                  TemporalManagedRunState prepared) {
        return TenantUtils.execute(request.getTenantId(), () -> {
            ManagedSkillTaskTriggerReqVO trigger = triggerRequest(request, prepared.getTemporalRunId());
            if (prepared.getWorkOrderId() != null) {
                AgentControlResult workOrder = agentQueries.getWorkOrder(prepared.getWorkOrderId());
                require("READY".equals(workOrder.getStatus()), "BPM approval did not leave the work order READY");
                ManagedSkillTaskTriggerReqVO.ApprovalContext approval =
                        new ManagedSkillTaskTriggerReqVO.ApprovalContext();
                approval.setWorkOrderId(prepared.getWorkOrderId());
                approval.setApprovalId(prepared.getApprovalId());
                approval.setWorkOrderExpectedVersion(workOrder.getAggregateVersion());
                approval.setValidForSeconds(300L);
                trigger.setApproval(approval);
            }
            ManagedSkillTaskTriggerResult result = managedRuns.triggerAs(
                    trigger, prepared.getExecutionUserId(), request.getOperatorUserType());
            mapper.updateRunBinding(request.getTenantId(), prepared.getTemporalRunId(), "RUNNING",
                    null, result.getRunId(), result.getTaskId(), now());
            return prepared.toBuilder().status("RUNNING").phase("SKILL_TASK")
                    .waitingOn("SKILL_TASK_RESULT").approvalDecision("APPROVE")
                    .managedRunId(result.getRunId()).taskId(result.getTaskId())
                    .businessResult(TemporalManagedBusinessResult.builder()
                            .outcomeCode("SKILL_TASK_SUBMITTED")
                            .summary("SkillTask 已提交，等待真实业务终态回读")
                            .domainObjectType("SKILL_TASK")
                            .domainObjectId(result.getTaskId())
                            .evidenceRef(result.getRunId())
                            .build())
                    .build();
        });
    }

    @Override
    public TemporalManagedRunState reject(TemporalManagedRunRequest request,
                                          TemporalManagedRunState prepared) {
        return TenantUtils.execute(request.getTenantId(), () -> {
            mapper.updateRunBinding(request.getTenantId(), prepared.getTemporalRunId(), "REJECTED",
                    "BPM_REJECTED", null, null, now());
            return prepared.toBuilder().status("REJECTED").phase("COMPLETED")
                    .approvalDecision("REJECT").waitingOn(null)
                    .errorCode("BPM_REJECTED")
                    .businessResult(TemporalManagedBusinessResult.builder()
                            .outcomeCode("APPROVAL_REJECTED")
                            .summary("运营主体未放行该高风险动作")
                            .domainObjectType("APPROVAL")
                            .domainObjectId(prepared.getApprovalId())
                            .evidenceRef(prepared.getWorkOrderId())
                            .build())
                    .build();
        });
    }

    @Override
    public TemporalManagedRunState pause(TemporalManagedRunRequest request,
                                         TemporalManagedRunState current,
                                         String reason) {
        return TenantUtils.execute(request.getTenantId(), () -> {
            mapper.updateRunBinding(request.getTenantId(), current.getTemporalRunId(), "PAUSED",
                    "MANUAL_PAUSE", current.getManagedRunId(), current.getTaskId(), now());
            return current.toBuilder().status("PAUSED").phase("MANUAL_CONTROL")
                    .waitingOn("RESUME_OR_CANCEL")
                    .resumableStatus(current.getStatus())
                    .pauseReason(reason)
                    .errorCode("MANUAL_PAUSE")
                    .build();
        });
    }

    @Override
    public TemporalManagedRunState resume(TemporalManagedRunRequest request,
                                          TemporalManagedRunState current,
                                          String reason) {
        return TenantUtils.execute(request.getTenantId(), () -> {
            String resumedStatus = current.getResumableStatus() == null ? "WAITING_APPROVAL"
                    : current.getResumableStatus();
            mapper.updateRunBinding(request.getTenantId(), current.getTemporalRunId(), resumedStatus,
                    null, current.getManagedRunId(), current.getTaskId(), now());
            return current.toBuilder().status(resumedStatus)
                    .phase("WAITING_APPROVAL".equals(resumedStatus) ? "APPROVAL_GATE" : current.getPhase())
                    .waitingOn("WAITING_APPROVAL".equals(resumedStatus) ? "BPM_APPROVAL" : current.getWaitingOn())
                    .pauseReason(reason)
                    .errorCode(null)
                    .build();
        });
    }

    @Override
    public TemporalManagedRunState cancel(TemporalManagedRunRequest request,
                                          TemporalManagedRunState current,
                                          String reason) {
        return TenantUtils.execute(request.getTenantId(), () -> {
            mapper.updateRunBinding(request.getTenantId(), current.getTemporalRunId(), "CANCELLED",
                    "MANUAL_CANCELLED", current.getManagedRunId(), current.getTaskId(), now());
            return current.toBuilder().status("CANCELLED").phase("COMPLETED")
                    .waitingOn(null).cancelReason(reason).errorCode("MANUAL_CANCELLED")
                    .businessResult(TemporalManagedBusinessResult.builder()
                            .outcomeCode("MANUAL_CANCELLED")
                            .summary("运行在提交 SkillTask 前被人工取消")
                            .domainObjectType("WORK_ORDER")
                            .domainObjectId(current.getWorkOrderId())
                            .evidenceRef(current.getTemporalRunId())
                            .build())
                    .build();
        });
    }

    @Override
    public TemporalManagedRunState timeout(TemporalManagedRunRequest request,
                                           TemporalManagedRunState current) {
        return TenantUtils.execute(request.getTenantId(), () -> {
            mapper.updateRunBinding(request.getTenantId(), current.getTemporalRunId(), "TIMED_OUT",
                    "APPROVAL_TIMEOUT", current.getManagedRunId(), current.getTaskId(), now());
            return current.toBuilder().status("TIMED_OUT").phase("COMPLETED")
                    .waitingOn(null).errorCode("APPROVAL_TIMEOUT")
                    .businessResult(TemporalManagedBusinessResult.builder()
                            .outcomeCode("APPROVAL_TIMEOUT")
                            .summary("审批在有效期内未放行，运行进入超时终态")
                            .domainObjectType("APPROVAL")
                            .domainObjectId(current.getApprovalId())
                            .evidenceRef(current.getWorkOrderId())
                            .build())
                    .build();
        });
    }

    @Override
    public TemporalManagedRunState refreshSkillTask(TemporalManagedRunRequest request,
                                                    TemporalManagedRunState current) {
        return TenantUtils.execute(request.getTenantId(), () -> {
            SkillTaskView task = skillTaskQueries.get(current.getTaskId());
            if (task == null || task.getStatus() == null) {
                return current;
            }
            String status = task.getStatus();
            if ("QUEUED".equals(status) || "RUNNING".equals(status) || "WAITING".equals(status)) {
                return current.toBuilder().status("RUNNING").phase("SKILL_TASK")
                        .waitingOn("SKILL_TASK_RESULT")
                        .businessResult(TemporalManagedBusinessResult.builder()
                                .outcomeCode("SKILL_TASK_RUNNING")
                                .summary("SkillTask 正在执行，等待终态")
                                .domainObjectType("SKILL_TASK")
                                .domainObjectId(task.getTaskId())
                                .evidenceRef(task.getRunId())
                                .build())
                        .build();
            }
            SkillTaskTerminalProofView proof = skillTaskQueries.getTerminalProof(current.getTaskId());
            String runStatus = "SUCCEEDED".equals(status) ? "SUCCEEDED" : "NEEDS_REVIEW";
            String errorCode = "SUCCEEDED".equals(status) ? null : status;
            mapper.updateRunBinding(request.getTenantId(), current.getTemporalRunId(), runStatus,
                    errorCode, current.getManagedRunId(), current.getTaskId(), now());
            return current.toBuilder().status(runStatus).phase("COMPLETED")
                    .waitingOn(null)
                    .errorCode(errorCode)
                    .businessResult(TemporalManagedBusinessResult.builder()
                            .outcomeCode("SUCCEEDED".equals(status) ? "SKILL_TASK_SUCCEEDED"
                                    : "SKILL_TASK_NEEDS_REVIEW")
                            .summary("SUCCEEDED".equals(status)
                                    ? "SkillTask 已完成并返回终态证明"
                                    : "SkillTask 未自动闭环，已关闭到人工复核")
                            .domainObjectType("SKILL_TASK")
                            .domainObjectId(task.getTaskId())
                            .evidenceRef(proof == null ? task.getRunId() : proof.getTerminalResultSha256())
                            .build())
                    .build();
        });
    }

    private TemporalManagedRunState prepareInTenant(TemporalManagedRunRequest request,
                                                    String workflowId, String temporalRunId) {
        ManagedSkillTaskWorkflowView workflow = workflows.requireWorkflowAs(
                request.getSkillId(), request.getSkillVersion(),
                request.getOperatorUserId(), request.getOperatorUserType());
        if (!Boolean.TRUE.equals(workflow.getApprovalRequired())) {
            TemporalManagedRunState state = TemporalManagedRunState.builder()
                    .status("READY_FOR_SUBMISSION").phase("SKILL_TASK")
                    .waitingOn("SKILL_TASK_SUBMISSION")
                    .temporalWorkflowId(workflowId).temporalRunId(temporalRunId)
                    .executionUserId(request.getOperatorUserId())
                    .businessResult(TemporalManagedBusinessResult.builder()
                            .outcomeCode("READY_FOR_SUBMISSION")
                            .summary("无需审批，等待 SkillTask 子工作流提交")
                            .domainObjectType("TEMPORAL_WORKFLOW")
                            .domainObjectId(temporalRunId)
                            .evidenceRef(workflowId)
                            .build())
                    .build();
            persist(request, state);
            return state;
        }
        require(request.getRoleCode() != null && request.getActionCode() != null,
                "Approval-bound schedule is missing role/action policy identity");
        TemporalApprovalPolicyRecord approvalPolicy = requireApprovalPolicy(request.getTenantId());
        Long executionUserId = approvalPolicy.getRequesterUserId();
        String workOrderId = TemporalManagedWorkflowIds.workOrderId(temporalRunId);
        String approvalId = TemporalManagedWorkflowIds.approvalId(temporalRunId);
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
                        .grantId("tag-" + TemporalManagedWorkflowIds.stableSuffix(temporalRunId))
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
                .status("WAITING_APPROVAL").phase("APPROVAL_GATE")
                .waitingOn("BPM_APPROVAL").resumableStatus("WAITING_APPROVAL")
                .temporalWorkflowId(workflowId)
                .temporalRunId(temporalRunId).executionUserId(executionUserId)
                .workOrderId(workOrderId).approvalId(approvalId)
                .businessResult(TemporalManagedBusinessResult.builder()
                        .outcomeCode("WAITING_APPROVAL")
                        .summary("高风险动作已生成工单并等待运营主体审批")
                        .domainObjectType("APPROVAL")
                        .domainObjectId(approvalId)
                        .evidenceRef(workOrderId)
                        .build())
                .build();
        persist(request, state);
        return state;
    }

    private ManagedSkillTaskTriggerReqVO triggerRequest(TemporalManagedRunRequest request, String temporalRunId) {
        ManagedSkillTaskTriggerReqVO trigger = new ManagedSkillTaskTriggerReqVO();
        trigger.setRunId(TemporalManagedWorkflowIds.managedRunId(temporalRunId));
        trigger.setClientRequestKey(TemporalManagedWorkflowIds.clientRequestKey(request, temporalRunId));
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
