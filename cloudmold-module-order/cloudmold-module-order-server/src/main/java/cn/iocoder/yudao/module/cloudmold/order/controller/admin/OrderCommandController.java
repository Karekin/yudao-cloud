package cn.iocoder.yudao.module.cloudmold.order.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderCommand;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderCommandApi;
import cn.iocoder.yudao.module.cloudmold.order.api.OrderCommandResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Order")
@RestController
@RequestMapping("/cloudmold/order")
public class OrderCommandController {
    @Resource
    private OrderCommandApi orderCommandApi;

    @PostMapping("/command")
    @Operation(summary = "Execute one canonical order command")
    @PreAuthorize("@ss.hasPermission('cloudmold:order:command')")
    public CommonResult<OrderCommandResult> execute(@RequestBody OrderCommand command) {
        return success(orderCommandApi.execute(command));
    }
}
