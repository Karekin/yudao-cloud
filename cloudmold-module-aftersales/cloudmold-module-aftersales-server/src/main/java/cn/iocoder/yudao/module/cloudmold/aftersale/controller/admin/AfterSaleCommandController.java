package cn.iocoder.yudao.module.cloudmold.aftersale.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.aftersale.api.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical After Sale")
@RestController
@RequestMapping("/cloudmold/aftersale")
public class AfterSaleCommandController {
    @Resource
    private AfterSaleCommandApi commandApi;
    @Resource
    private AfterSaleQueryApi queryApi;

    @PostMapping("/command")
    @Operation(summary = "Execute one governed after-sale command")
    @PreAuthorize("@ss.hasPermission('cloudmold:aftersale:command')")
    public CommonResult<AfterSaleView> execute(@RequestBody AfterSaleCommand command) {
        return success(commandApi.execute(command));
    }

    @GetMapping("/get")
    @Operation(summary = "Get one after-sale case and resolution state")
    @PreAuthorize("@ss.hasPermission('cloudmold:aftersale:query')")
    public CommonResult<AfterSaleView> get(@RequestParam("afterSaleId") String afterSaleId) {
        return success(queryApi.get(afterSaleId));
    }

    @GetMapping("/get-by-order-item")
    @Operation(summary = "Resolve one after-sale case after an ambiguous request outcome")
    @PreAuthorize("@ss.hasPermission('cloudmold:aftersale:query')")
    public CommonResult<AfterSaleView> getByOrderItem(@RequestParam("orderId") String orderId,
                                                     @RequestParam("orderItemId") String orderItemId) {
        return success(queryApi.getByOrderItem(orderId, orderItemId));
    }
}
