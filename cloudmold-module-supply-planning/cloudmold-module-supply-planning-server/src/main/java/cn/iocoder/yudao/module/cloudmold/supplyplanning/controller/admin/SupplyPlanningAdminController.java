package cn.iocoder.yudao.module.cloudmold.supplyplanning.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentBusinessStageView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentExecutionView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SupplyPlanningCommand;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SupplyPlanningCommandApi;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SupplyPlanningResult;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.controller.admin.vo.SupplyPlanningPageReqVO;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.service.actor.SupplyPlanningActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.service.query.SupplyPlanningQueryService;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.service.query.SupplyPlanningWorkItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CloudMold - Supply Planning")
@RestController
@RequestMapping("/cloudmold/supply-planning")
public class SupplyPlanningAdminController {
    @Resource
    private SupplyPlanningCommandApi commandApi;
    @Resource
    private SupplyPlanningQueryService queryService;
    @Resource
    private SupplyPlanningActorPrincipalPort actorPrincipalPort;

    @PostMapping("/command")
    @Operation(summary = "执行需求预测、S&OP、补货和库存健康命令")
    @PreAuthorize("@ss.hasPermission('cloudmold:supply-planning:command')")
    public CommonResult<SupplyPlanningResult> execute(@RequestBody SupplyPlanningCommand command) {
        String actorPrincipalId = actorPrincipalPort.resolveSystemAdmin(getLoginUserId());
        return success(commandApi.execute(command, actorPrincipalId));
    }

    @GetMapping("/work-item/page")
    @Operation(summary = "分页查询当前租户供应链计划工作项")
    @PreAuthorize("@ss.hasPermission('cloudmold:supply-planning:query')")
    public CommonResult<PageResult<SupplyPlanningWorkItem>> getPage(
            @Valid SupplyPlanningPageReqVO request) {
        return success(queryService.getPage(request));
    }

    @GetMapping("/replenishment/{recommendationId}/execution")
    @Operation(summary = "查询补货转换结果与下一等待事件")
    @PreAuthorize("@ss.hasPermission('cloudmold:supply-planning:query')")
    public CommonResult<ReplenishmentExecutionView> getReplenishmentExecution(
            @PathVariable("recommendationId") String recommendationId) {
        return success(queryService.requireReplenishmentExecution(recommendationId));
    }

    @GetMapping("/replenishment/{recommendationId}/business-stage")
    @Operation(summary = "查询补货业务阶段聚合状态")
    @PreAuthorize("@ss.hasPermission('cloudmold:supply-planning:query')")
    public CommonResult<ReplenishmentBusinessStageView> getReplenishmentBusinessStage(
            @PathVariable("recommendationId") String recommendationId) {
        return success(queryService.requireReplenishmentBusinessStage(recommendationId));
    }
}
