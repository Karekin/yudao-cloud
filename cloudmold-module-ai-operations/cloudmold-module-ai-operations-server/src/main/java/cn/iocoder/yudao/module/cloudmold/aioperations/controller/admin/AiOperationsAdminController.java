package cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.aioperations.api.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical AI Operations")
@RestController
@RequestMapping("/cloudmold/ai-operations")
@Validated
public class AiOperationsAdminController {

    @Resource
    private AiOperationsCommandApi commandApi;
    @Resource
    private AiOperationsQueryApi queryApi;

    @PostMapping("/commands")
    @Operation(summary = "Execute one canonical AI operations command")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:command')")
    public CommonResult<AiOperationsCommandResult> execute(@RequestBody AiOperationsCommand command) {
        return success(commandApi.execute(command));
    }

    @GetMapping("/aggregates/{aggregateType}/{aggregateId}")
    @Operation(summary = "Get one canonical AI operations aggregate")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:query')")
    public CommonResult<AiOperationsAggregateView> get(@PathVariable String aggregateType,
                                                       @PathVariable String aggregateId) {
        return success(queryApi.get(aggregateType, aggregateId));
    }
}
