package cn.iocoder.yudao.module.cloudmold.integration.yudao.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoProcurementPromiseApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Yudao Procurement Promise")
@RestController
@RequestMapping("/cloudmold/integration/yudao/procurement/promise")
public class YudaoProcurementPromiseController {

    @Resource
    private YudaoProcurementPromiseApi procurementPromiseApi;

    @PostMapping("/save")
    @Operation(summary = "Freeze a governed purchase-order-line receipt promise")
    @PreAuthorize("@ss.hasPermission('cloudmold:integration:yudao:procurement:promise:save')")
    public CommonResult<YudaoProcurementPromiseApi.PurchasePromiseView> save(
            @RequestBody YudaoProcurementPromiseApi.PurchasePromiseCommand command) {
        return success(procurementPromiseApi.savePurchasePromise(command));
    }

    @GetMapping("/line/{purchaseOrderLineId}")
    @Operation(summary = "Get the governed purchase promise by ERP purchase order line")
    @PreAuthorize("@ss.hasPermission('cloudmold:integration:yudao:procurement:promise:query')")
    public CommonResult<YudaoProcurementPromiseApi.PurchasePromiseView> get(
            @PathVariable("purchaseOrderLineId") Long purchaseOrderLineId) {
        return success(procurementPromiseApi.getPurchasePromise(purchaseOrderLineId));
    }
}
