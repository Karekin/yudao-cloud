package cn.iocoder.yudao.module.cloudmold.order.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.order.api.migration.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Legacy Trade Target Readiness")
@RestController
@RequestMapping("/cloudmold/order/migrations/legacy-trade-target-readiness")
public class LegacyTradeTargetReadinessController {

    @Resource
    private LegacyTradeTargetReadinessApi readinessApi;

    @PostMapping("/assess")
    @Operation(summary = "Freeze explicit buyer, Catalog, Order and OrderItem mapping readiness without importing")
    @PreAuthorize("@ss.hasPermission('cloudmold:order:migration-assess')")
    public CommonResult<LegacyTradeTargetReadinessResult> assess(
            @RequestBody LegacyTradeTargetReadinessCommand command) {
        return success(readinessApi.assess(command));
    }

    @GetMapping("/{targetReadinessRunId}")
    @Operation(summary = "Read one immutable target-readiness run")
    @PreAuthorize("@ss.hasPermission('cloudmold:order:migration-query')")
    public CommonResult<LegacyTradeTargetReadinessResult> requireRun(
            @PathVariable String targetReadinessRunId) {
        return success(readinessApi.requireRun(targetReadinessRunId));
    }

    @GetMapping("/{targetReadinessRunId}/orders")
    @Operation(summary = "Read per-Order target mapping blockers")
    @PreAuthorize("@ss.hasPermission('cloudmold:order:migration-query')")
    public CommonResult<List<LegacyTradeTargetReadinessOrderView>> listOrders(
            @PathVariable String targetReadinessRunId) {
        return success(readinessApi.listOrders(targetReadinessRunId));
    }

    @GetMapping("/{targetReadinessRunId}/items")
    @Operation(summary = "Read per-OrderItem Catalog and target-id mapping blockers")
    @PreAuthorize("@ss.hasPermission('cloudmold:order:migration-query')")
    public CommonResult<List<LegacyTradeTargetReadinessItemView>> listItems(
            @PathVariable String targetReadinessRunId) {
        return success(readinessApi.listItems(targetReadinessRunId));
    }
}
