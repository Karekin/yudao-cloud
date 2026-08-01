package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.request.WorkflowRegistryApprovalRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.request.WorkflowRegistryEvaluationRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.request.WorkflowRegistryRetirementRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.request.WorkflowRegistryValidationStartRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.view.WorkflowRegistryGovernanceStatusView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Workflow Registry Governance")
@RestController
@RequestMapping("/cloudmold/ai-operations/workflow-registry/governance")
@RequiredArgsConstructor
public class WorkflowRegistryGovernanceController {

    private final WorkflowRegistryGovernanceService service;

    @PostMapping("/validation-requests")
    @Operation(summary = "请求 CloudMold 独立验证器开始验证当前候选")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:manage')")
    public CommonResult<WorkflowRegistryGovernanceStatusView> startValidation(
            @Valid @RequestBody WorkflowRegistryValidationStartRequest request) {
        return success(service.startValidation(request));
    }

    @PostMapping("/evaluations")
    @Operation(summary = "提交工作流候选评估结果并驱动 E1/E2/E3 治理闭环")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:evaluate')")
    public CommonResult<WorkflowRegistryGovernanceStatusView> recordEvaluation(
            @Valid @RequestBody WorkflowRegistryEvaluationRequest request) {
        return success(service.recordEvaluation(request));
    }

    @PostMapping("/approvals")
    @Operation(summary = "以独立审批人批准或拒绝 E2/E3 工作流候选")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:approve')")
    public CommonResult<WorkflowRegistryGovernanceStatusView> recordApproval(
            @Valid @RequestBody WorkflowRegistryApprovalRequest request) {
        return success(service.recordApproval(request));
    }

    @PostMapping("/retirements")
    @Operation(summary = "执行 candidate kill switch 或 active rollback")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:release')")
    public CommonResult<WorkflowRegistryGovernanceStatusView> retire(
            @Valid @RequestBody WorkflowRegistryRetirementRequest request) {
        return success(service.retire(request));
    }

    @GetMapping("/workflows/{skillId}")
    @Operation(summary = "查询工作流治理状态摘要")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:query')")
    public CommonResult<WorkflowRegistryGovernanceStatusView> getStatus(@PathVariable String skillId) {
        return success(service.getStatus(skillId));
    }

    @GetMapping("/workflows")
    @Operation(summary = "列出租户内受治理的工作流")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:query')")
    public CommonResult<List<WorkflowRegistryGovernanceStatusView>> listStatuses(
            @RequestParam(defaultValue = "100") int limit) {
        return success(service.listStatuses(limit));
    }
}
