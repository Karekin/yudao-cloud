package cn.iocoder.yudao.module.cloudmold.inventory.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryAgingSnapshotApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryAgingSnapshotCommand;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryAgingSnapshotQueryApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryAgingSnapshotResult;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryAgingSnapshotView;
import cn.iocoder.yudao.module.cloudmold.inventory.controller.admin.vo.InventoryAgingSnapshotPageReqVO;
import cn.iocoder.yudao.module.cloudmold.inventory.service.InventoryAgingSnapshotServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Inventory Aging Snapshot")
@RestController
@RequestMapping("/cloudmold/inventory/v3/aging-snapshots")
public class InventoryAgingSnapshotController {

    @Resource
    private InventoryAgingSnapshotApi snapshotApi;
    @Resource
    private InventoryAgingSnapshotQueryApi queryApi;
    @Resource
    private InventoryAgingSnapshotServiceImpl service;

    @PostMapping("/command")
    @Operation(summary = "Capture one canonical inventory aging/expiry snapshot")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:aging-snapshot:command')")
    public CommonResult<InventoryAgingSnapshotResult> capture(@RequestBody InventoryAgingSnapshotCommand command) {
        return success(snapshotApi.capture(command));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询库龄/效期快照")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:aging-snapshot:query')")
    public CommonResult<PageResult<InventoryAgingSnapshotView>> getPage(
            @Valid InventoryAgingSnapshotPageReqVO request) {
        return success(service.getSnapshotPage(request));
    }

    @GetMapping("/{snapshotId}")
    @Operation(summary = "读取库龄/效期快照详情")
    @Parameter(name = "snapshotId", description = "快照 ID", required = true)
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:aging-snapshot:query')")
    public CommonResult<InventoryAgingSnapshotView> requireSnapshot(@PathVariable String snapshotId) {
        return success(queryApi.requireSnapshot(snapshotId));
    }
}
