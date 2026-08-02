package cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.inventoryscrap.InventoryScrapQueryApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.inventoryscrap.InventoryScrapView;
import cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo.InventoryScrapPageReqVO;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.inventoryscrap.InventoryScrapQueryService;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.inventoryscrap.InventoryScrapQueryService.InventoryScrapPageItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Inventory Scrap Query")
@RestController
@RequestMapping("/cloudmold/warehouse/inventory-scraps")
public class InventoryScrapQueryController {

    @Resource
    private InventoryScrapQueryService inventoryScrapQueryService;
    @Resource
    private InventoryScrapQueryApi inventoryScrapQueryApi;

    @GetMapping("/page")
    @Operation(summary = "分页查询权威库存报废单据")
    @PreAuthorize("@ss.hasPermission('cloudmold:warehouse:inventory-scrap:query')")
    public CommonResult<PageResult<InventoryScrapPageItem>> page(@Valid InventoryScrapPageReqVO request) {
        return success(inventoryScrapQueryService.getPage(request));
    }

    @GetMapping("/get")
    @Operation(summary = "查询权威库存报废详情")
    @PreAuthorize("@ss.hasPermission('cloudmold:warehouse:inventory-scrap:query')")
    public CommonResult<InventoryScrapView> get(@RequestParam("scrapId") String scrapId) {
        return success(inventoryScrapQueryApi.requireByScrapId(scrapId));
    }
}
