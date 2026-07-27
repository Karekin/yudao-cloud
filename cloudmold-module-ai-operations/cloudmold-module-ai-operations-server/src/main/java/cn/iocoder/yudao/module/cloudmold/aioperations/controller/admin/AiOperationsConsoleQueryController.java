package cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo.AiArtifactPageReqVO;
import cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo.AiObservationPageReqVO;
import cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo.AiWorkflowPageReqVO;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.query.AiOperationsManagedRunQueryService;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.query.AiArtifactPageItem;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.query.AiObservationPageItem;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.query.AiOperationsConsoleQueryService;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.query.AiWorkflowPageItem;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.query.AiWorkflowRunDetailView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskDetailView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskRunPageRequest;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskRunView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - AI Operations Console Query")
@RestController
@RequestMapping("/cloudmold/ai-operations")
@Validated
public class AiOperationsConsoleQueryController {

    @Resource
    private AiOperationsConsoleQueryService consoleQueryService;
    @Resource
    private AiOperationsManagedRunQueryService managedRunQueryService;

    @GetMapping("/managed-workflows")
    @Operation(summary = "查询受管 SkillTask 工作流定义")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:query')")
    public CommonResult<List<ManagedSkillTaskWorkflowView>> listManagedWorkflows() {
        return success(managedRunQueryService.listManagedWorkflows());
    }

    @GetMapping("/managed-runs/page")
    @Operation(summary = "分页查询受管 SkillTask durable runs")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:query')")
    public CommonResult<PageResult<ManagedSkillTaskRunView>> getManagedRunPage(
            @Valid ManagedSkillTaskRunPageRequest request) {
        return success(managedRunQueryService.getManagedRunPage(request));
    }

    @GetMapping("/managed-runs/{taskId}")
    @Operation(summary = "查询受管 SkillTask durable run 详情")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:query')")
    public CommonResult<ManagedSkillTaskDetailView> getManagedRun(@PathVariable String taskId) {
        return success(managedRunQueryService.getManagedRun(taskId));
    }

    @GetMapping("/workflows/page")
    @Operation(summary = "分页查询规范 AI 工作流控制台视图")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:query')")
    public CommonResult<PageResult<AiWorkflowPageItem>> getWorkflowPage(@Valid AiWorkflowPageReqVO request) {
        return success(consoleQueryService.getWorkflowPage(request));
    }

    @GetMapping("/runs/{runId}")
    @Operation(summary = "查询规范 AI 工作流运行控制台详情")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:query')")
    public CommonResult<AiWorkflowRunDetailView> getRunDetail(@PathVariable String runId) {
        return success(consoleQueryService.getRunDetail(runId));
    }

    @GetMapping("/artifacts/page")
    @Operation(summary = "分页查询规范 AI 运行证据工件，只返回真实反馈证据引用")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:query')")
    public CommonResult<PageResult<AiArtifactPageItem>> getArtifactPage(@Valid AiArtifactPageReqVO request) {
        return success(consoleQueryService.getArtifactPage(request));
    }

    @GetMapping("/observations/page")
    @Operation(summary = "分页查询规范 AI 调用观测，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:query')")
    public CommonResult<PageResult<AiObservationPageItem>> getObservationPage(@Valid AiObservationPageReqVO request) {
        return success(consoleQueryService.getObservationPage(request));
    }
}
