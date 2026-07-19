package cn.iocoder.yudao.module.cloudmold.catalog.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.catalog.controller.admin.vo.CatalogSkuPageReqVO;
import cn.iocoder.yudao.module.cloudmold.catalog.service.query.CatalogQueryService;
import cn.iocoder.yudao.module.cloudmold.catalog.service.query.CatalogSkuDetailVO;
import cn.iocoder.yudao.module.cloudmold.catalog.service.query.CatalogSkuPageItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Catalog Query")
@RestController
@RequestMapping("/cloudmold/catalog")
public class CatalogQueryController {

    @Resource
    private CatalogQueryService catalogQueryService;

    @GetMapping("/skus/page")
    @Operation(summary = "分页查询规范 SKU，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:catalog:query')")
    public CommonResult<PageResult<CatalogSkuPageItem>> getSkuPage(@Valid CatalogSkuPageReqVO request) {
        return success(catalogQueryService.getSkuPage(request));
    }

    @GetMapping("/skus/get")
    @Operation(summary = "查询单个规范 SKU 详情，含条码列表，只返回当前租户权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:catalog:query')")
    @Parameter(name = "skuId", description = "规范 SKU ID", required = true)
    public CommonResult<CatalogSkuDetailVO> getSkuDetail(@RequestParam("skuId") String skuId) {
        return success(catalogQueryService.getSkuDetail(skuId));
    }
}
