package cn.iocoder.yudao.module.cloudmold.skilltask.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskDetailView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskRunPageRequest;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskRunView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;
import cn.iocoder.yudao.module.cloudmold.skilltask.service.query.ManagedSkillTaskQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Managed SkillTask AI Operations Query")
@RestController
@RequestMapping("/cloudmold/ai-operations")
@RequiredArgsConstructor
public class SkillTaskAiOperationsQueryController {

    private final ManagedSkillTaskQueryService managedSkillTaskQueryService;

    @GetMapping("/managed-workflows")
    @Operation(summary = "查询当前注册的受管 SkillTask 工作流定义")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:query')")
    public CommonResult<List<ManagedSkillTaskWorkflowView>> listManagedWorkflows() {
        return success(managedSkillTaskQueryService.listManagedWorkflows());
    }

    @GetMapping("/managed-runs/page")
    @Operation(summary = "分页查询当前租户受管 SkillTask durable runs")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:query')")
    public CommonResult<PageResult<ManagedSkillTaskRunView>> getManagedRunPage(
            @Valid ManagedSkillTaskRunPageRequest request) {
        return success(managedSkillTaskQueryService.getManagedRunPage(request));
    }

    @GetMapping("/managed-runs/{taskId}")
    @Operation(summary = "查询当前租户受管 SkillTask durable run 详情")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:query')")
    public CommonResult<ManagedSkillTaskDetailView> getManagedRun(@PathVariable String taskId) {
        return success(managedSkillTaskQueryService.getManagedRun(taskId));
    }
}
