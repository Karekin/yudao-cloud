package cn.iocoder.yudao.module.cloudmold.payment.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.payment.api.PaymentCommand;
import cn.iocoder.yudao.module.cloudmold.payment.api.PaymentCommandApi;
import cn.iocoder.yudao.module.cloudmold.payment.api.PaymentCommandResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Payment")
@RestController
@RequestMapping("/cloudmold/payment")
public class PaymentCommandController {
    @Resource
    private PaymentCommandApi paymentCommandApi;

    @PostMapping("/command")
    @Operation(summary = "Execute one canonical test payment command")
    @PreAuthorize("@ss.hasPermission('cloudmold:payment:command')")
    public CommonResult<PaymentCommandResult> execute(@RequestBody PaymentCommand command) {
        return success(paymentCommandApi.execute(command));
    }
}
