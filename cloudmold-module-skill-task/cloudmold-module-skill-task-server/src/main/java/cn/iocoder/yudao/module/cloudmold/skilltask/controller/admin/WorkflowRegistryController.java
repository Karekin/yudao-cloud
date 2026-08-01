package cn.iocoder.yudao.module.cloudmold.skilltask.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.skilltask.workflowregistry.WorkflowRegistryDefinitionView;
import cn.iocoder.yudao.module.cloudmold.skilltask.workflowregistry.WorkflowRegistryQueryService;
import cn.iocoder.yudao.module.cloudmold.skilltask.workflowregistry.WorkflowRegistrySubjectView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Workflow Registry")
@RestController
@RequestMapping("/cloudmold/workflow-registry")
@RequiredArgsConstructor
public class WorkflowRegistryController {

    private final WorkflowRegistryQueryService workflowRegistryQueryService;

    @GetMapping("/subject")
    @Operation(summary = "查询当前 bearer token 在 CloudMold 认证后的主体")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:query')")
    public CommonResult<WorkflowRegistrySubjectView> getCurrentSubject() {
        return success(workflowRegistryQueryService.getCurrentSubject());
    }

    @GetMapping("/definitions/{skillId}/{skillVersion}")
    @Operation(summary = "查询当前登录主体绑定的 SkillTask 工作流定义及 HMAC attestation")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:query')")
    public CommonResult<WorkflowRegistryDefinitionView> getDefinition(
            @PathVariable String skillId,
            @PathVariable String skillVersion) {
        return success(workflowRegistryQueryService.getDefinition(skillId, skillVersion));
    }
}
