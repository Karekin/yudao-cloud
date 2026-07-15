package cn.iocoder.yudao.module.cloudmold.merchant.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantCommand;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantCommandApi;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantCommandResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Merchant")
@RestController
@RequestMapping("/cloudmold/merchant")
public class MerchantCommandController {

    @Resource
    private MerchantCommandApi merchantCommandApi;

    @PostMapping("/command")
    @Operation(summary = "Execute one canonical merchant onboarding or lifecycle command")
    @PreAuthorize("@ss.hasPermission('cloudmold:merchant:command')")
    public CommonResult<MerchantCommandResult> execute(@RequestBody MerchantCommand command) {
        return success(merchantCommandApi.execute(command));
    }
}
