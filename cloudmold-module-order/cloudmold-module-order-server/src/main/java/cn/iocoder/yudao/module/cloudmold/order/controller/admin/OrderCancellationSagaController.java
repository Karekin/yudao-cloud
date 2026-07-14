package cn.iocoder.yudao.module.cloudmold.order.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.order.api.cancellation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Durable Order Cancellation Saga")
@RestController
@RequestMapping("/cloudmold/order-cancellation-saga")
public class OrderCancellationSagaController {
    @Resource
    private OrderCancellationSagaCommandApi commandApi;
    @Resource
    private OrderCancellationSagaQueryApi queryApi;

    @PostMapping("/command")
    @Operation(summary = "Start or manually retry one durable cancellation Saga")
    @PreAuthorize("@ss.hasPermission('cloudmold:order-cancellation-saga:command')")
    public CommonResult<OrderCancellationSagaView> execute(@RequestBody OrderCancellationSagaCommand command) {
        return success(commandApi.execute(command));
    }

    @GetMapping("/get")
    @Operation(summary = "Read one durable cancellation Saga")
    @PreAuthorize("@ss.hasPermission('cloudmold:order-cancellation-saga:query')")
    public CommonResult<OrderCancellationSagaView> get(@RequestParam("sagaId") String sagaId) {
        return success(queryApi.get(sagaId));
    }
}
