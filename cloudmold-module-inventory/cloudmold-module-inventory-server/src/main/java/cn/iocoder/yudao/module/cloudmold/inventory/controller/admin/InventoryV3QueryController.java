package cn.iocoder.yudao.module.cloudmold.inventory.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.inventory.controller.admin.vo.InventoryV3BalancePageReqVO;
import cn.iocoder.yudao.module.cloudmold.inventory.controller.admin.vo.InventoryV3LedgerPageReqVO;
import cn.iocoder.yudao.module.cloudmold.inventory.controller.admin.vo.InventoryV3ReservationPageReqVO;
import cn.iocoder.yudao.module.cloudmold.inventory.service.query.InventoryV3BalancePageItem;
import cn.iocoder.yudao.module.cloudmold.inventory.service.query.InventoryV3LedgerPageItem;
import cn.iocoder.yudao.module.cloudmold.inventory.service.query.InventoryV3QueryService;
import cn.iocoder.yudao.module.cloudmold.inventory.service.query.InventoryV3ReservationPageItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Inventory v3 Query")
@RestController
@RequestMapping("/cloudmold/inventory/v3")
public class InventoryV3QueryController {

    @Resource
    private InventoryV3QueryService inventoryV3QueryService;

    @GetMapping("/balances/page")
    @Operation(summary = "分页查询规范 Inventory v3 余额，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:query')")
    public CommonResult<PageResult<InventoryV3BalancePageItem>> getBalancePage(
            @Valid InventoryV3BalancePageReqVO request) {
        return success(inventoryV3QueryService.getBalancePage(request));
    }

    @GetMapping("/reservations/page")
    @Operation(summary = "分页查询规范 Inventory v3 预占，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:query')")
    public CommonResult<PageResult<InventoryV3ReservationPageItem>> getReservationPage(
            @Valid InventoryV3ReservationPageReqVO request) {
        return success(inventoryV3QueryService.getReservationPage(request));
    }

    @GetMapping("/ledger/page")
    @Operation(summary = "分页查询规范 Inventory v3 账本，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:query')")
    public CommonResult<PageResult<InventoryV3LedgerPageItem>> getLedgerPage(
            @Valid InventoryV3LedgerPageReqVO request) {
        return success(inventoryV3QueryService.getLedgerPage(request));
    }
}
