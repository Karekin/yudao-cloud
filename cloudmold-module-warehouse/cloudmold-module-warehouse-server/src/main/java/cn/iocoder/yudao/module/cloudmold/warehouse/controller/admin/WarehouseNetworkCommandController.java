package cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Warehouse Network")
@RestController
@RequestMapping("/cloudmold/warehouse")
public class WarehouseNetworkCommandController {
    @Resource
    private WarehouseNetworkCommandApi commandApi;
    @Resource
    private WarehouseSourceMappingQueryApi queryApi;

    @PostMapping("/command")
    @Operation(summary = "Execute one canonical warehouse network command")
    @PreAuthorize("@ss.hasPermission('cloudmold:warehouse:command')")
    public CommonResult<WarehouseNetworkCommandResult> execute(@RequestBody WarehouseNetworkCommand command) {
        return success(commandApi.execute(command));
    }

    @PostMapping("/source/resolve-network")
    @Operation(summary = "Resolve one active source warehouse to its ready canonical network")
    @PreAuthorize("@ss.hasPermission('cloudmold:warehouse:query')")
    public CommonResult<WarehouseNetworkView> resolveNetwork(
            @RequestBody WarehouseSourceReference reference) {
        return success(queryApi.resolveReadyNetwork(reference, Instant.now()));
    }
}
