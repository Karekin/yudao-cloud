package cn.iocoder.yudao.module.cloudmold.agentcontrol.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

import java.util.List;

@Tag(name = "CloudMold - Agent Control Plane")
@RestController
@RequestMapping("/cloudmold/agent-control")
@ConditionalOnProperty(prefix = "cloudmold.agent-control", name = "enabled", havingValue = "true")
public class AgentControlAdminController {
    @Resource private AgentControlCommandApi commandApi;
    @Resource private AgentControlQueryApi queryApi;
    @Resource private AgentExecutionTicketApi executionTicketApi;

    @PostMapping("/commands")
    @Operation(summary = "Persist role work, handoff, approval and business-result commands")
    @PreAuthorize("@ss.hasPermission('cloudmold:agent-control:command')")
    public CommonResult<AgentControlResult> executeWork(@RequestBody AgentControlCommand command) {
        requireGovernanceOperation(command, false);
        return success(commandApi.execute(command, getLoginUserId()));
    }

    @PostMapping("/governance/commands")
    @Operation(summary = "Define tenant role contracts and exact server-side action policies")
    @PreAuthorize("@ss.hasPermission('cloudmold:agent-control:govern')")
    public CommonResult<AgentControlResult> executeGovernance(@RequestBody AgentControlCommand command) {
        requireGovernanceOperation(command, true);
        return success(commandApi.execute(command, getLoginUserId()));
    }

    @PostMapping("/execution-tickets")
    @Operation(summary = "Issue one short-lived cma1 SkillTask execution ticket from an approved READY work order")
    @PreAuthorize("@ss.hasPermission('cloudmold:agent-control:command')")
    public CommonResult<AgentExecutionTicketResult> issueExecutionTicket(
            @RequestBody AgentExecutionTicketCommand command) {
        return success(executionTicketApi.issue(command, getLoginUserId()));
    }

    @GetMapping("/roles/{roleCode}")
    @PreAuthorize("@ss.hasPermission('cloudmold:agent-control:query')")
    public CommonResult<AgentControlResult> getRole(@PathVariable String roleCode) {
        return success(queryApi.getRole(roleCode));
    }

    @GetMapping("/work-orders/{workOrderId}")
    @PreAuthorize("@ss.hasPermission('cloudmold:agent-control:query')")
    public CommonResult<AgentControlResult> getWorkOrder(@PathVariable String workOrderId) {
        return success(queryApi.getWorkOrder(workOrderId));
    }

    @GetMapping("/handoffs/{handoffId}")
    @PreAuthorize("@ss.hasPermission('cloudmold:agent-control:query')")
    public CommonResult<AgentControlResult> getHandoff(@PathVariable String handoffId) {
        return success(queryApi.getHandoff(handoffId));
    }

    @GetMapping("/approvals/{approvalId}")
    @PreAuthorize("@ss.hasPermission('cloudmold:agent-control:query')")
    public CommonResult<AgentControlResult> getApproval(@PathVariable String approvalId) {
        return success(queryApi.getApproval(approvalId));
    }

    @GetMapping("/approvals/{approvalId}/detail")
    @Operation(summary = "查询 Agent 审批冻结的业务事项与影响范围")
    @PreAuthorize("@ss.hasPermission('cloudmold:agent-control:query')")
    public CommonResult<AgentApprovalDetailView> getApprovalDetail(@PathVariable String approvalId) {
        return success(queryApi.getApprovalDetail(approvalId));
    }

    @GetMapping("/approval-board-stats")
    @Operation(summary = "查询 AI 工作流审批门禁分层统计")
    @PreAuthorize("@ss.hasPermission('cloudmold:agent-control:query')")
    public CommonResult<AgentApprovalBoardStatsView> getApprovalBoardStats() {
        return success(queryApi.getApprovalBoardStats());
    }

    @GetMapping("/business-results/{resultId}")
    @PreAuthorize("@ss.hasPermission('cloudmold:agent-control:query')")
    public CommonResult<AgentControlResult> getBusinessResult(@PathVariable String resultId) {
        return success(queryApi.getBusinessResult(resultId));
    }

    @GetMapping("/business-cards")
    @Operation(summary = "查询岗位审批、交接和业务结果卡片")
    @PreAuthorize("@ss.hasPermission('cloudmold:agent-control:query')")
    public CommonResult<List<AgentBusinessCardView>> listBusinessCards(
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) String cardType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit) {
        return success(queryApi.listBusinessCards(roleCode, cardType, status, limit));
    }

    private void requireGovernanceOperation(AgentControlCommand command, boolean expected) {
        boolean governance = command != null && (command.getOperation() == AgentControlOperation.DEFINE_ROLE
                || command.getOperation() == AgentControlOperation.SET_ACTION_POLICY);
        if (governance != expected) {
            throw new IllegalArgumentException(expected
                    ? "governance endpoint only accepts role or action-policy commands"
                    : "role and action-policy commands require the governance endpoint");
        }
    }
}
