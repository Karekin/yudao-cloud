package cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo.AiWorkflowRunPageReqVO;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.query.AiWorkflowRunPageItem;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.query.AiWorkflowRunQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical AI Workflow Run Query")
@RestController
@RequestMapping("/cloudmold/ai-operations")
public class AiWorkflowRunQueryController {

    @Resource
    private AiWorkflowRunQueryService aiWorkflowRunQueryService;

    @GetMapping("/runs/page")
    @Operation(summary = "分页查询规范 AI 工作流运行，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:query')")
    public CommonResult<PageResult<AiWorkflowRunPageItem>> getPage(@Valid AiWorkflowRunPageReqVO request) {
        return success(aiWorkflowRunQueryService.getPage(request));
    }
}
