package cn.iocoder.yudao.module.cloudmold.quality.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.quality.api.QualityCommand;
import cn.iocoder.yudao.module.cloudmold.quality.api.QualityCommandApi;
import cn.iocoder.yudao.module.cloudmold.quality.api.QualityResult;
import cn.iocoder.yudao.module.cloudmold.quality.controller.admin.vo.QualityPageReqVO;
import cn.iocoder.yudao.module.cloudmold.quality.service.actor.QualityActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.quality.service.query.QualityQueryService;
import cn.iocoder.yudao.module.cloudmold.quality.service.query.QualityWorkItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CloudMold - Authentication and Quality")
@RestController
@RequestMapping("/cloudmold/quality")
public class QualityAdminController {
    @Resource
    private QualityCommandApi commandApi;
    @Resource
    private QualityQueryService queryService;
    @Resource
    private QualityActorPrincipalPort actorPrincipalPort;

    @PostMapping("/command")
    @Operation(summary = "执行标准、鉴别师资质、质检任务和 CAPA 命令")
    @PreAuthorize("@ss.hasPermission('cloudmold:quality:command')")
    public CommonResult<QualityResult> execute(@RequestBody QualityCommand command) {
        String actorPrincipalId = actorPrincipalPort.resolveSystemAdmin(getLoginUserId());
        return success(commandApi.execute(command, actorPrincipalId));
    }

    @GetMapping("/work-item/page")
    @Operation(summary = "分页查询当前租户鉴别质检工作项")
    @PreAuthorize("@ss.hasPermission('cloudmold:quality:query')")
    public CommonResult<PageResult<QualityWorkItem>> getPage(@Valid QualityPageReqVO request) {
        return success(queryService.getPage(request));
    }
}
