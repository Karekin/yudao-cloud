package cn.iocoder.yudao.module.cloudmold.aioperations.service.command;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentExecutionTicketApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentExecutionTicketCommand;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentExecutionTicketResult;
import cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo.ManagedSkillTaskTriggerReqVO;
import cn.iocoder.yudao.module.cloudmold.rpc.CloudMoldRpcCallContext;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskCommandApi;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskSubmitCommand;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.agent-control", name = "enabled", havingValue = "true")
public class AiOperationsManagedRunCommandService {

    private final AiOperationsManagedRunQueryServiceFacade workflowQuery;
    private final AgentExecutionTicketApi executionTicketApi;
    private final SkillTaskCommandApi skillTaskCommandApi;

    public ManagedSkillTaskTriggerResult trigger(ManagedSkillTaskTriggerReqVO request) {
        Objects.requireNonNull(request, "request");
        LoginUser loginUser = Objects.requireNonNull(SecurityFrameworkUtils.getLoginUser(),
                "Login user is required for AI Operations managed trigger");
        ManagedSkillTaskWorkflowView workflow = workflowQuery.requireWorkflow(
                request.getSkillId(), request.getSkillVersion());
        return triggerAs(request, loginUser.getId(), loginUser.getUserType(), workflow);
    }

    /**
     * Executes a scheduled run as the frozen tenant operator stored with the Temporal Schedule.
     * The caller must already have entered that tenant context.
     */
    public ManagedSkillTaskTriggerResult triggerAs(ManagedSkillTaskTriggerReqVO request,
                                                   Long operatorUserId, Integer operatorUserType) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(operatorUserId, "operatorUserId");
        Objects.requireNonNull(operatorUserType, "operatorUserType");
        ManagedSkillTaskWorkflowView workflow = workflowQuery.requireWorkflowAs(
                request.getSkillId(), request.getSkillVersion(), operatorUserId, operatorUserType);
        return triggerAs(request, operatorUserId, operatorUserType, workflow);
    }

    private ManagedSkillTaskTriggerResult triggerAs(ManagedSkillTaskTriggerReqVO request,
                                                    Long operatorUserId, Integer operatorUserType,
                                                    ManagedSkillTaskWorkflowView workflow) {
        AgentExecutionTicketResult ticket = null;
        if (Boolean.TRUE.equals(workflow.getApprovalRequired())) {
            ManagedSkillTaskTriggerReqVO.ApprovalContext approval = Objects.requireNonNull(
                    request.getApproval(), workflow.getRiskLevel() + " workflow requires BPM approval context");
            ticket = executionTicketApi.issue(AgentExecutionTicketCommand.builder()
                    .workOrderId(approval.getWorkOrderId())
                    .approvalId(approval.getApprovalId())
                    .workOrderExpectedVersion(approval.getWorkOrderExpectedVersion())
                    .validForSeconds(approval.getValidForSeconds())
                    .missionRunId(approval.getMissionRunId())
                    .leaseOwner(approval.getLeaseOwner())
                    .leaseToken(approval.getLeaseToken())
                    .fencingToken(approval.getFencingToken())
                    .build(), operatorUserId);
        } else if (request.getApproval() != null) {
            throw new IllegalArgumentException("R1 workflow must not carry an approval context");
        }

        AgentExecutionTicketResult issuedTicket = ticket;
        SkillTaskView task = invokeSkillTask(request.getRunId(), operatorUserId, operatorUserType, () ->
                skillTaskCommandApi.submit(SkillTaskSubmitCommand.builder()
                        .skillId(workflow.getSkillId())
                        .skillVersion(workflow.getSkillVersion())
                        .runId(request.getRunId())
                        .clientRequestKey(request.getClientRequestKey())
                        .inputJson(request.getInputJson())
                        .riskLevel(workflow.getRiskLevel())
                        .approvalRef(issuedTicket == null ? null : issuedTicket.getApprovalRef())
                        .build()));
        return ManagedSkillTaskTriggerResult.builder()
                .taskId(task.getTaskId())
                .runId(task.getRunId())
                .skillId(task.getSkillId())
                .skillVersion(task.getSkillVersion())
                .riskLevel(task.getRiskLevel())
                .status(task.getStatus())
                .currentStepCode(task.getCurrentStepCode())
                .inputSha256(task.getInputSha256())
                .definitionClosureSha256(task.getDefinitionClosureSha256())
                .approvalRefSha256(issuedTicket == null ? null : issuedTicket.getApprovalRefSha256())
                .approvalExpiresAt(issuedTicket == null ? null : issuedTicket.getExpiresAt())
                .createdAt(task.getCreatedAt())
                .build();
    }

    private <T> T invokeSkillTask(String runId, Long operatorUserId, Integer operatorUserType,
                                  Supplier<T> action) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        CloudMoldRpcCallContext context = new CloudMoldRpcCallContext(tenantId, operatorUserId,
                operatorUserType, AiOperationsManagedRunQueryServiceFacade.CONSOLE_SKILL_ID,
                "console:trigger:" + normalize(runId));
        SecurityContext previousSecurityContext = SecurityContextHolder.getContext();
        try (CloudMoldRpcCallContext.Scope ignored = CloudMoldRpcCallContext.open(context)) {
            SecurityContextHolder.clearContext();
            return action.get();
        } finally {
            SecurityContextHolder.setContext(previousSecurityContext);
        }
    }

    private static String normalize(String value) {
        return value == null ? "generated" : value.trim().replace(' ', '-');
    }
}
