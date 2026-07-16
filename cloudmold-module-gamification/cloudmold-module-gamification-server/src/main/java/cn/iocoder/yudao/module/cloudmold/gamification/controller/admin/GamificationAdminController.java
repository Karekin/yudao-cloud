package cn.iocoder.yudao.module.cloudmold.gamification.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.gamification.api.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Governed Gamification")
@RestController
@RequestMapping("/cloudmold/gamification")
public class GamificationAdminController {
    @Resource private GamificationCommandApi commandApi;
    @Resource private GamificationQueryApi queryApi;

    @PostMapping("/command")
    @Operation(summary = "Execute an idempotent governed-gamification command")
    @PreAuthorize("@ss.hasPermission('cloudmold:gamification:command')")
    public CommonResult<GamificationView> execute(@RequestBody GamificationCommand command) {
        return success(commandApi.execute(command));
    }

    @GetMapping("/game/get")
    @PreAuthorize("@ss.hasPermission('cloudmold:gamification:query')")
    public CommonResult<GamificationView> getGame(@RequestParam("gameId") String gameId) {
        return success(queryApi.getGame(gameId));
    }

    @GetMapping("/account/get")
    @PreAuthorize("@ss.hasPermission('cloudmold:gamification:query')")
    public CommonResult<GamificationView> getPlayerAccount(@RequestParam("gameId") String gameId,
                                                            @RequestParam("principalId") String principalId) {
        return success(queryApi.getPlayerAccount(gameId, principalId));
    }

    @GetMapping("/session/get")
    @PreAuthorize("@ss.hasPermission('cloudmold:gamification:query')")
    public CommonResult<GamificationView> getSession(@RequestParam("sessionId") String sessionId) {
        return success(queryApi.getSession(sessionId));
    }

    @GetMapping("/task-progress/get")
    @PreAuthorize("@ss.hasPermission('cloudmold:gamification:query')")
    public CommonResult<GamificationView> getTaskProgress(@RequestParam("taskDefinitionId") String taskDefinitionId,
                                                          @RequestParam("taskVersion") Long taskVersion,
                                                          @RequestParam("principalId") String principalId) {
        return success(queryApi.getTaskProgress(taskDefinitionId, taskVersion, principalId));
    }

    @GetMapping("/fragment/get")
    @PreAuthorize("@ss.hasPermission('cloudmold:gamification:query')")
    public CommonResult<GamificationView> getFragmentBalance(@RequestParam("gameId") String gameId,
                                                             @RequestParam("principalId") String principalId,
                                                             @RequestParam("fragmentCode") String fragmentCode) {
        return success(queryApi.getFragmentBalance(gameId, principalId, fragmentCode));
    }
}
