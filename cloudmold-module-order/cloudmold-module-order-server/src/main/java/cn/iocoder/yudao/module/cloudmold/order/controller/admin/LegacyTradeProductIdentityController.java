package cn.iocoder.yudao.module.cloudmold.order.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.order.api.migration.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Legacy Trade Historical Product Identity")
@RestController
@RequestMapping("/cloudmold/order/migrations/legacy-trade-product-identity")
public class LegacyTradeProductIdentityController {

    @Resource
    private LegacyTradeProductIdentityApi identityApi;

    @PostMapping("/assess")
    @Operation(summary = "Assess historical SPU/SKU identity without treating current product rows as history")
    @PreAuthorize("@ss.hasPermission('cloudmold:order:migration-assess')")
    public CommonResult<LegacyTradeProductIdentityResult> assess(
            @RequestBody LegacyTradeProductIdentityCommand command) {
        return success(identityApi.assess(command));
    }

    @GetMapping("/{identityRunId}")
    @Operation(summary = "Read one immutable historical product-identity run")
    @PreAuthorize("@ss.hasPermission('cloudmold:order:migration-query')")
    public CommonResult<LegacyTradeProductIdentityResult> requireRun(@PathVariable String identityRunId) {
        return success(identityApi.requireRun(identityRunId));
    }

    @GetMapping("/{identityRunId}/items")
    @Operation(summary = "Read per-OrderItem product identity conflicts and historical evidence blockers")
    @PreAuthorize("@ss.hasPermission('cloudmold:order:migration-query')")
    public CommonResult<List<LegacyTradeProductIdentityItemView>> listItems(@PathVariable String identityRunId) {
        return success(identityApi.listItems(identityRunId));
    }
}
