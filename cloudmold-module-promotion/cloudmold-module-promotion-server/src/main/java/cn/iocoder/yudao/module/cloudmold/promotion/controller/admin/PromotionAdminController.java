package cn.iocoder.yudao.module.cloudmold.promotion.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.promotion.api.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Promotion")
@RestController
@RequestMapping("/cloudmold/promotion")
public class PromotionAdminController {

    @Resource
    private PromotionCommandApi commandApi;
    @Resource
    private PromotionQueryApi queryApi;

    @PostMapping("/command")
    @Operation(summary = "Execute one canonical promotion command")
    @PreAuthorize("@ss.hasPermission('cloudmold:promotion:command')")
    public CommonResult<PromotionCommandResult> execute(@RequestBody PromotionCommand command) {
        return success(commandApi.execute(command));
    }

    @GetMapping("/{aggregateType}/{aggregateId}")
    @Operation(summary = "Get one canonical promotion aggregate")
    @PreAuthorize("@ss.hasPermission('cloudmold:promotion:query')")
    public CommonResult<PromotionAggregateView> get(@PathVariable String aggregateType,
                                                    @PathVariable String aggregateId) {
        return success(queryApi.get(aggregateType, aggregateId));
    }
}
