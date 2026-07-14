package cn.iocoder.yudao.module.cloudmold.catalog.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogCommandApi;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogLifecycleCommand;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogLifecycleResult;
import cn.iocoder.yudao.module.cloudmold.catalog.api.DefineCatalogSkuCommand;
import cn.iocoder.yudao.module.cloudmold.catalog.api.DefineCatalogSkuResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Catalog")
@RestController
@RequestMapping("/cloudmold/catalog")
public class CatalogCommandController {
    @Resource
    private CatalogCommandApi catalogCommandApi;

    @PostMapping("/sku/define")
    @Operation(summary = "Define one canonical apparel SKU and its Style/SPU dimensions")
    @PreAuthorize("@ss.hasPermission('cloudmold:catalog:sku:define')")
    public CommonResult<DefineCatalogSkuResult> defineSku(@RequestBody DefineCatalogSkuCommand command) {
        return success(catalogCommandApi.defineSku(command));
    }

    @PostMapping("/lifecycle")
    @Operation(summary = "Execute one version-checked canonical Catalog lifecycle transition")
    @PreAuthorize("@ss.hasPermission('cloudmold:catalog:lifecycle')")
    public CommonResult<CatalogLifecycleResult> changeStatus(@RequestBody CatalogLifecycleCommand command) {
        return success(catalogCommandApi.changeStatus(command));
    }
}
