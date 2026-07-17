package cn.iocoder.yudao.module.cloudmold.operationsintelligence.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.api.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Operations Intelligence")
@RestController
@RequestMapping("/cloudmold/operations-intelligence")
public class OperationsIntelligenceAdminController {
    @Resource private OperationsIntelligenceCommandApi commandApi;
    @Resource private OperationsIntelligenceQueryApi queryApi;

    @PostMapping("/command")
    @Operation(summary = "Record governed observations, reviewed clues and alert lifecycle changes")
    @PreAuthorize("@ss.hasPermission('cloudmold:operations-intelligence:command')")
    public CommonResult<OperationsIntelligenceResult> execute(@RequestBody OperationsIntelligenceCommand command) {
        return success(commandApi.execute(command));
    }

    @GetMapping("/observation/get")
    @PreAuthorize("@ss.hasPermission('cloudmold:operations-intelligence:query')")
    public CommonResult<OperationsIntelligenceResult> getObservation(@RequestParam String observationId) {
        return success(queryApi.getObservation(observationId));
    }

    @GetMapping("/clue/get")
    @PreAuthorize("@ss.hasPermission('cloudmold:operations-intelligence:query')")
    public CommonResult<OperationsIntelligenceResult> getClue(@RequestParam String clueId) {
        return success(queryApi.getClue(clueId));
    }

    @GetMapping("/alert/get")
    @PreAuthorize("@ss.hasPermission('cloudmold:operations-intelligence:query')")
    public CommonResult<OperationsIntelligenceResult> getAlert(@RequestParam String alertId) {
        return success(queryApi.getAlert(alertId));
    }
}
