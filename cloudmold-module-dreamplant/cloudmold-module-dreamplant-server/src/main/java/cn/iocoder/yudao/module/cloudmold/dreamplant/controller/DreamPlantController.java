package cn.iocoder.yudao.module.cloudmold.dreamplant.controller;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.dreamplant.api.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - DreamPlant AI World Map")
@RestController
@RequestMapping("/cloudmold/dreamplant")
public class DreamPlantController {
    @Resource private DreamPlantCommandApi commandApi;
    @Resource private DreamPlantQueryApi queryApi;

    @GetMapping("/public/world-map/{mapKey}")
    @PermitAll
    @Operation(summary = "Read the explicitly published public world-map projection")
    public CommonResult<DreamPlantWorldMapSnapshot> getPublicWorldMap(@PathVariable("mapKey") String mapKey) {
        return success(queryApi.getPublicWorldMap(mapKey));
    }

    @GetMapping("/world-map/{mapKey}")
    @PreAuthorize("@ss.hasPermission('cloudmold:dreamplant:query')")
    public CommonResult<DreamPlantWorldMapSnapshot> getWorldMap(@PathVariable("mapKey") String mapKey) {
        return success(queryApi.getPublishedWorldMap(mapKey));
    }

    @PostMapping("/world-map/publish")
    @PreAuthorize("@ss.hasPermission('cloudmold:dreamplant:command')")
    public CommonResult<DreamPlantCommandResult> publish(@RequestBody DreamPlantCommand command) {
        command.setOperation(DreamPlantOperation.PUBLISH_WORLD_MAP);
        return success(commandApi.execute(command));
    }

    @PostMapping("/exploration/submit")
    @PreAuthorize("@ss.hasPermission('cloudmold:dreamplant:explore')")
    public CommonResult<DreamPlantCommandResult> submitExploration(@RequestBody DreamPlantCommand command) {
        command.setOperation(DreamPlantOperation.SUBMIT_EXPLORATION);
        return success(commandApi.execute(command));
    }

    @PostMapping("/exploration/outcome")
    @PreAuthorize("@ss.hasPermission('cloudmold:dreamplant:command')")
    public CommonResult<DreamPlantCommandResult> recordOutcome(@RequestBody DreamPlantCommand command) {
        command.setOperation(DreamPlantOperation.RECORD_EXPLORATION_OUTCOME);
        return success(commandApi.execute(command));
    }

    @GetMapping("/exploration/{explorationRunId}")
    @PreAuthorize("@ss.hasPermission('cloudmold:dreamplant:query')")
    public CommonResult<DreamPlantExplorationView> getExploration(
            @PathVariable("explorationRunId") String explorationRunId) {
        return success(queryApi.getExploration(explorationRunId));
    }

    @GetMapping("/explorations")
    @PreAuthorize("@ss.hasPermission('cloudmold:dreamplant:query')")
    public CommonResult<List<DreamPlantExplorationView>> listExplorations(
            @RequestParam("status") String status,
            @RequestParam(value = "limit", defaultValue = "50") Integer limit) {
        return success(queryApi.listExplorations(status, limit));
    }
}
