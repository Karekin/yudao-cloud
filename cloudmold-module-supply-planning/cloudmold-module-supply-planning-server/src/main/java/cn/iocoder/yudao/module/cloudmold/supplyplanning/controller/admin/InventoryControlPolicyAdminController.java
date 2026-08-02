package cn.iocoder.yudao.module.cloudmold.supplyplanning.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.InventoryHealthSnapshotCommand;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.InventoryHealthSnapshotCommandApi;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.InventoryHealthSnapshotResult;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.InventoryHealthSnapshotView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SafetyStockPolicyCommand;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SafetyStockPolicyCommandApi;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SafetyStockPolicyResult;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SafetyStockPolicyView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.controller.admin.vo.InventoryHealthSnapshotPageReqVO;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.controller.admin.vo.SafetyStockPolicyPageReqVO;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.service.InventoryControlPolicyServiceImpl;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.service.actor.SupplyPlanningActorPrincipalPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CloudMold - Inventory Control Policy")
@RestController
@RequestMapping("/cloudmold/supply-planning")
public class InventoryControlPolicyAdminController {
    @Resource
    private SafetyStockPolicyCommandApi safetyStockPolicyCommandApi;
    @Resource
    private InventoryHealthSnapshotCommandApi inventoryHealthSnapshotCommandApi;
    @Resource
    private InventoryControlPolicyServiceImpl queryService;
    @Resource
    private SupplyPlanningActorPrincipalPort actorPrincipalPort;

    @PostMapping("/safety-stock-policies/command")
    @Operation(summary = "执行安全库存与服务水平策略命令")
    @PreAuthorize("@ss.hasPermission('cloudmold:supply-planning:policy:command')")
    public CommonResult<SafetyStockPolicyResult> executePolicy(@RequestBody SafetyStockPolicyCommand command) {
        return success(safetyStockPolicyCommandApi.execute(command, actorPrincipal()));
    }

    @GetMapping("/safety-stock-policies/page")
    @Operation(summary = "分页查询安全库存策略")
    @PreAuthorize("@ss.hasPermission('cloudmold:supply-planning:policy:query')")
    public CommonResult<PageResult<SafetyStockPolicyView>> getPolicyPage(
            @Valid SafetyStockPolicyPageReqVO request) {
        return success(queryService.getPolicyPage(request));
    }

    @GetMapping("/safety-stock-policies/{policyId}")
    @Operation(summary = "查询安全库存策略详情")
    @PreAuthorize("@ss.hasPermission('cloudmold:supply-planning:policy:query')")
    public CommonResult<SafetyStockPolicyView> getPolicy(@PathVariable("policyId") String policyId) {
        return success(queryService.requirePolicy(policyId));
    }

    @PostMapping("/inventory-health-snapshots/command")
    @Operation(summary = "创建库存健康不可变快照")
    @PreAuthorize("@ss.hasPermission('cloudmold:supply-planning:health-snapshot:command')")
    public CommonResult<InventoryHealthSnapshotResult> captureSnapshot(
            @RequestBody InventoryHealthSnapshotCommand command) {
        return success(inventoryHealthSnapshotCommandApi.capture(command, actorPrincipal()));
    }

    @GetMapping("/inventory-health-snapshots/page")
    @Operation(summary = "分页查询库存健康快照")
    @PreAuthorize("@ss.hasPermission('cloudmold:supply-planning:health-snapshot:query')")
    public CommonResult<PageResult<InventoryHealthSnapshotView>> getSnapshotPage(
            @Valid InventoryHealthSnapshotPageReqVO request) {
        return success(queryService.getSnapshotPage(request));
    }

    @GetMapping("/inventory-health-snapshots/{snapshotId}")
    @Operation(summary = "查询库存健康快照详情")
    @PreAuthorize("@ss.hasPermission('cloudmold:supply-planning:health-snapshot:query')")
    public CommonResult<InventoryHealthSnapshotView> getSnapshot(
            @PathVariable("snapshotId") String snapshotId) {
        return success(queryService.requireSnapshot(snapshotId));
    }

    private String actorPrincipal() {
        return actorPrincipalPort.resolveSystemAdmin(getLoginUserId());
    }
}
