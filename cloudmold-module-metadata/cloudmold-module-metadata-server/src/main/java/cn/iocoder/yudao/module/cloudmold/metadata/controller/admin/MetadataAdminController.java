package cn.iocoder.yudao.module.cloudmold.metadata.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.metadata.api.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Governed Metadata Control Plane")
@RestController
@RequestMapping("/cloudmold/metadata")
public class MetadataAdminController {
    @Resource private MetadataCommandApi commandApi;
    @Resource private MetadataQueryApi queryApi;

    @PostMapping("/command")
    @Operation(summary = "Publish governed definitions or append exact runtime evidence")
    @PreAuthorize("@ss.hasPermission('cloudmold:metadata:command')")
    public CommonResult<MetadataView> execute(@RequestBody MetadataCommand command) {
        return success(commandApi.execute(command));
    }

    @GetMapping("/definition/get")
    @PreAuthorize("@ss.hasPermission('cloudmold:metadata:query')")
    public CommonResult<MetadataView> getDefinition(@RequestParam("definitionId") String definitionId) {
        return success(queryApi.getDefinition(definitionId));
    }

    @GetMapping("/task-run/get")
    @PreAuthorize("@ss.hasPermission('cloudmold:metadata:query')")
    public CommonResult<MetadataView> getTaskRun(@RequestParam("runId") String runId) {
        return success(queryApi.getTaskRun(runId));
    }

    @GetMapping("/dqc-result/get")
    @PreAuthorize("@ss.hasPermission('cloudmold:metadata:query')")
    public CommonResult<MetadataView> getDqcResult(@RequestParam("resultId") String resultId) {
        return success(queryApi.getDqcResult(resultId));
    }
}
