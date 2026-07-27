package cn.iocoder.yudao.module.cloudmold.procurement.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.procurement.api.*;
import cn.iocoder.yudao.module.cloudmold.procurement.service.actor.ProcurementActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.procurement.service.query.ProcurementQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CloudMold - Procurement")
@RestController
@RequestMapping("/cloudmold/procurement")
public class ProcurementAdminController {
    @Resource
    private ProcurementCommandApi commandApi;
    @Resource
    private ProcurementQueryService queryService;
    @Resource
    private ProcurementActorPrincipalPort actorPrincipalPort;

    @PostMapping("/command")
    @Operation(summary = "执行规范采购单命令")
    @PreAuthorize("@ss.hasPermission('cloudmold:procurement:command')")
    public CommonResult<ProcurementResult> execute(@RequestBody ProcurementCommand command) {
        return success(commandApi.execute(command, actorPrincipalPort.resolveSystemAdmin(getLoginUserId())));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "查询规范采购单当前状态")
    @PreAuthorize("@ss.hasPermission('cloudmold:procurement:query')")
    public CommonResult<ProcurementOrderView> get(@PathVariable("orderId") String orderId) {
        return success(queryService.requireCurrent(orderId));
    }
}
