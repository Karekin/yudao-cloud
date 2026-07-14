package cn.iocoder.yudao.module.cloudmold.fulfillment.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Fulfillment")
@RestController
@RequestMapping("/cloudmold/fulfillment")
public class FulfillmentCommandController {
    @Resource
    private FulfillmentCommandApi fulfillmentCommandApi;

    @PostMapping("/command")
    @Operation(summary = "Execute one canonical fulfillment command")
    @PreAuthorize("@ss.hasPermission('cloudmold:fulfillment:command')")
    public CommonResult<FulfillmentCommandResult> execute(@RequestBody FulfillmentCommand command) {
        return success(fulfillmentCommandApi.execute(command));
    }
}
