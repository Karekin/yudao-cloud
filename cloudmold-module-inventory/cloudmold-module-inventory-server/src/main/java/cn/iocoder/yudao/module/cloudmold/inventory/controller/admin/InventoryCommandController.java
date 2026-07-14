package cn.iocoder.yudao.module.cloudmold.inventory.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryCommand;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryCommandApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryCommandResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Inventory")
@RestController
@RequestMapping("/cloudmold/inventory")
public class InventoryCommandController {

    @Resource
    private InventoryCommandApi inventoryCommandApi;

    @PostMapping("/command")
    @Operation(summary = "Execute one canonical inventory command")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:command')")
    public CommonResult<InventoryCommandResult> execute(@RequestBody InventoryCommand command) {
        return success(inventoryCommandApi.execute(command));
    }

}
