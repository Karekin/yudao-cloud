package cn.iocoder.yudao.module.cloudmold.inventory.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3Command;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3CommandApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3CommandResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Inventory v3")
@RestController
@RequestMapping("/cloudmold/inventory/v3")
public class InventoryV3CommandController {
    @Resource
    private InventoryV3CommandApi commandApi;

    @PostMapping("/command")
    @Operation(summary = "Execute one location/lot-aware canonical inventory command")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:command')")
    public CommonResult<InventoryV3CommandResult> execute(@RequestBody InventoryV3Command command) {
        return success(commandApi.execute(command));
    }
}
