package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.request.WorkflowProposalRegistryRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.view.WorkflowProposalRegistryStatusView;
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
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Workflow Proposal Registry")
@RestController
@RequestMapping("/cloudmold/ai-operations/workflow-registry")
@RequiredArgsConstructor
public class WorkflowProposalRegistryController {

    private final WorkflowProposalRegistryService service;

    @PostMapping("/proposals")
    @Operation(summary = "以 CAS 提交不可变工作流候选提案")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:manage')")
    public CommonResult<WorkflowProposalRegistryStatusView> submit(
            @Valid @RequestBody WorkflowProposalRegistryRequest request) {
        return success(service.submit(request));
    }

    @GetMapping("/workflows/{skillId}")
    @Operation(summary = "查询工作流 stable/candidate 指针")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:query')")
    public CommonResult<WorkflowProposalRegistryStatusView> getStatus(@PathVariable String skillId) {
        return success(service.getStatus(skillId));
    }
}
