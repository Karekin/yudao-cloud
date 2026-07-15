package cn.iocoder.yudao.module.cloudmold.tokenplatform.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.tokenplatform.api.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Token Platform")
@RestController
@RequestMapping("/cloudmold/token-platform")
public class TokenPlatformAdminController {

    @Resource
    private TokenPlatformCommandApi commandApi;
    @Resource
    private TokenPlatformQueryApi queryApi;

    @PostMapping("/command")
    @Operation(summary = "Execute one canonical token platform command")
    @PreAuthorize("@ss.hasPermission('cloudmold:token-platform:command')")
    public CommonResult<TokenPlatformCommandResult> execute(@RequestBody TokenPlatformCommand command) {
        return success(commandApi.execute(command));
    }

    @GetMapping("/{aggregateType}/{aggregateId}")
    @Operation(summary = "Get one canonical token platform aggregate")
    @PreAuthorize("@ss.hasPermission('cloudmold:token-platform:query')")
    public CommonResult<TokenPlatformAggregateView> get(@PathVariable String aggregateType,
                                                        @PathVariable String aggregateId) {
        return success(queryApi.get(aggregateType, aggregateId));
    }
}
