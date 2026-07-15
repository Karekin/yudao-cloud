package cn.iocoder.yudao.module.cloudmold.merchant.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.merchant.api.deposit.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Merchant Deposit")
@RestController
@RequestMapping("/cloudmold/merchant/deposit")
public class MerchantDepositController {

    @Resource
    private MerchantDepositCommandApi commandApi;
    @Resource
    private MerchantDepositQueryApi queryApi;

    @PostMapping("/command")
    @Operation(summary = "Append one canonical Merchant deposit ledger command")
    @PreAuthorize("@ss.hasPermission('cloudmold:merchant:deposit-command')")
    public CommonResult<MerchantDepositResult> execute(@RequestBody MerchantDepositCommand command) {
        return success(commandApi.execute(command));
    }

    @GetMapping("/current")
    @Operation(summary = "Get the current canonical Merchant deposit account")
    @PreAuthorize("@ss.hasPermission('cloudmold:merchant:deposit-query')")
    public CommonResult<MerchantDepositView> current(@RequestParam String merchantId,
                                                     @RequestParam(defaultValue = "CNY") String currency) {
        return success(queryApi.requireCurrent(merchantId, currency));
    }
}
