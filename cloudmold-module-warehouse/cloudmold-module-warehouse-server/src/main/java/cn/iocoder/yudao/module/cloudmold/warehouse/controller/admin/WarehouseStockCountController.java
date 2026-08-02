package cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockCountCommand;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockCountCommandApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockCountResult;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockCountView;
import cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo.StockCountPageReqVO;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.actor.WarehouseActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.query.StockCountPageItem;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.query.StockCountQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CloudMold - Canonical Warehouse Stock Count")
@RestController
@RequestMapping("/cloudmold/warehouse/stock-counts")
public class WarehouseStockCountController {

    @Resource
    private StockCountCommandApi commandApi;

    @Resource
    private StockCountQueryService queryService;

    @Resource
    private WarehouseActorPrincipalPort actorPrincipalPort;

    @PostMapping("/command")
    @Operation(summary = "执行 CloudMold 权威库存盘点命令")
    @PreAuthorize("@ss.hasPermission('cloudmold:warehouse:command')")
    public CommonResult<StockCountResult> execute(@RequestBody StockCountCommand command) {
        command.setActorPrincipalId(actorPrincipalPort.resolveSystemAdmin(getLoginUserId()));
        return success(commandApi.execute(command));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询 CloudMold 权威库存盘点单据")
    @PreAuthorize("@ss.hasPermission('cloudmold:warehouse:query')")
    public CommonResult<PageResult<StockCountPageItem>> getPage(@Valid StockCountPageReqVO request) {
        return success(queryService.getPage(request));
    }

    @GetMapping("/get")
    @Operation(summary = "查询 CloudMold 权威库存盘点详情")
    @PreAuthorize("@ss.hasPermission('cloudmold:warehouse:query')")
    public CommonResult<StockCountView> get(@RequestParam("stockCountId") String stockCountId) {
        return success(queryService.requireByStockCountId(stockCountId));
    }
}
