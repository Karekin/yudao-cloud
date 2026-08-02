package cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.inventoryscrap.InventoryScrapCommand;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.inventoryscrap.InventoryScrapCommandApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.inventoryscrap.InventoryScrapResult;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.actor.WarehouseActorPrincipalPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CloudMold - Canonical Inventory Scrap Command")
@RestController
@RequestMapping("/cloudmold/warehouse/inventory-scraps")
public class InventoryScrapCommandController {

    @Resource
    private InventoryScrapCommandApi commandApi;
    @Resource
    private WarehouseActorPrincipalPort actorPrincipalPort;

    @PostMapping("/command")
    @Operation(summary = "执行一条权威库存报废命令")
    @PreAuthorize("@ss.hasPermission('cloudmold:warehouse:inventory-scrap:command')")
    public CommonResult<InventoryScrapResult> execute(@RequestBody InventoryScrapCommand command) {
        command.setActorPrincipalId(actorPrincipalPort.resolveSystemAdmin(getLoginUserId()));
        return success(commandApi.execute(command));
    }
}
