package cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo.WarehouseLocationPageReqVO;
import cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo.WarehousePageReqVO;
import cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo.WarehouseZonePageReqVO;
import cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo.StockTransferPageReqVO;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockTransferView;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.query.StockTransferPageItem;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.query.StockTransferQueryService;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.query.WarehouseLocationPageItem;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.query.WarehousePageItem;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.query.WarehouseQueryService;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.query.WarehouseZonePageItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * CloudMold 规范仓网只读查询。
 * 仅读取 cloudmold_warehouse / cloudmold_warehouse_zone / cloudmold_warehouse_location 权威表，
 * 不读取 yudao 旧 WMS 业务表；按当前租户隔离。
 */
@Tag(name = "CloudMold - Canonical Warehouse Query")
@RestController
@RequestMapping("/cloudmold/warehouse")
public class WarehouseQueryController {

    @Resource
    private WarehouseQueryService warehouseQueryService;

    @Resource
    private StockTransferQueryService stockTransferQueryService;

    @GetMapping("/stock-transfers/page")
    @Operation(summary = "分页查询 Warehouse 权威库存调拨单据")
    @PreAuthorize("@ss.hasPermission('cloudmold:warehouse:query')")
    public CommonResult<PageResult<StockTransferPageItem>> getStockTransferPage(
            @Valid StockTransferPageReqVO request) {
        return success(stockTransferQueryService.getPage(request));
    }

    @GetMapping("/stock-transfers/get")
    @Operation(summary = "查询 Warehouse 权威库存调拨详情")
    @PreAuthorize("@ss.hasPermission('cloudmold:warehouse:query')")
    public CommonResult<StockTransferView> getStockTransfer(String requestId) {
        return success(stockTransferQueryService.requireByRequestId(requestId));
    }

    @GetMapping("/warehouses/page")
    @Operation(summary = "分页查询规范仓库，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:warehouse:query')")
    public CommonResult<PageResult<WarehousePageItem>> getWarehousePage(
            @Valid WarehousePageReqVO request) {
        return success(warehouseQueryService.getWarehousePage(request));
    }

    @GetMapping("/zones/page")
    @Operation(summary = "分页查询规范库区，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:warehouse:query')")
    public CommonResult<PageResult<WarehouseZonePageItem>> getZonePage(
            @Valid WarehouseZonePageReqVO request) {
        return success(warehouseQueryService.getZonePage(request));
    }

    @GetMapping("/locations/page")
    @Operation(summary = "分页查询规范库位，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:warehouse:query')")
    public CommonResult<PageResult<WarehouseLocationPageItem>> getLocationPage(
            @Valid WarehouseLocationPageReqVO request) {
        return success(warehouseQueryService.getLocationPage(request));
    }
}
