package cn.iocoder.yudao.module.cloudmold.integration.yudao.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Yudao Anti-Corruption Projections")
@RestController
@RequestMapping("/cloudmold/integration/yudao/catalog")
public class YudaoCatalogProjectionController {

    @Resource
    private YudaoCatalogProjectionApi projectionApi;

    @PostMapping("/plan")
    @Operation(summary = "Plan replayable Mall, ERP, and WMS projections from one ACTIVE canonical SKU")
    @PreAuthorize("@ss.hasPermission('cloudmold:integration:yudao:catalog:plan')")
    public CommonResult<LegacyCatalogProjectionResult> plan(
            @RequestBody PlanLegacyCatalogProjectionCommand command) {
        return success(projectionApi.plan(command));
    }

}
