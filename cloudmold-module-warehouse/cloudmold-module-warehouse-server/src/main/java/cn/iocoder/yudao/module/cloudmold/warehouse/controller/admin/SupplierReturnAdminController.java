package cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.supplierreturn.*;
import cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo.SupplierReturnPageReqVO;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.actor.WarehouseActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.supplierreturn.SupplierReturnPageItem;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.supplierreturn.SupplierReturnQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CloudMold - 供应商退供")
@RestController
@RequestMapping("/cloudmold/warehouse/supplier-returns")
public class SupplierReturnAdminController {

    @Resource
    private SupplierReturnCommandApi supplierReturnCommandApi;
    @Resource
    private SupplierReturnQueryApi supplierReturnQueryApi;
    @Resource
    private SupplierReturnQueryService supplierReturnQueryService;
    @Resource
    private WarehouseActorPrincipalPort warehouseActorPrincipalPort;

    @PostMapping("/command")
    @Operation(summary = "执行规范供应商退供命令")
    @PreAuthorize("@ss.hasPermission('cloudmold:warehouse:supplier-return:command')")
    public CommonResult<SupplierReturnResult> execute(@RequestBody SupplierReturnCommand command) {
        String actorPrincipalId = warehouseActorPrincipalPort.resolveSystemAdmin(getLoginUserId());
        return success(supplierReturnCommandApi.execute(command, actorPrincipalId));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询规范供应商退供单")
    @PreAuthorize("@ss.hasPermission('cloudmold:warehouse:supplier-return:query')")
    public CommonResult<PageResult<SupplierReturnPageItem>> getPage(@Valid SupplierReturnPageReqVO request) {
        return success(supplierReturnQueryService.getPage(request));
    }

    @GetMapping("/get")
    @Operation(summary = "查询规范供应商退供详情")
    @PreAuthorize("@ss.hasPermission('cloudmold:warehouse:supplier-return:query')")
    public CommonResult<SupplierReturnView> get(@RequestParam("returnId") String returnId) {
        return success(supplierReturnQueryApi.requireByReturnId(returnId));
    }
}
