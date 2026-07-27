package cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo.TemporalScheduleCreateReqVO;
import cn.iocoder.yudao.module.cloudmold.aioperations.temporal.AiOperationsTemporalScheduleService;
import cn.iocoder.yudao.module.cloudmold.aioperations.temporal.TemporalScheduleView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - AI Operations Temporal Schedules")
@RestController
@RequestMapping("/cloudmold/ai-operations/temporal-schedules")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.ai-operations.temporal", name = "enabled", havingValue = "true")
public class AiOperationsTemporalController {

    private final AiOperationsTemporalScheduleService schedules;

    @GetMapping
    @Operation(summary = "查询当前租户由 Temporal 托管的定时工作流")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:query')")
    public CommonResult<List<TemporalScheduleView>> list() {
        return success(schedules.list());
    }

    @PostMapping
    @Operation(summary = "从后台管理系统创建 Temporal 定时工作流")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:command')")
    public CommonResult<TemporalScheduleView> create(@Valid @RequestBody TemporalScheduleCreateReqVO request) {
        return success(schedules.create(request));
    }

    @PostMapping("/{scheduleId}/trigger")
    @Operation(summary = "立即触发一次，不改变周期")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:command')")
    public CommonResult<Boolean> trigger(@PathVariable String scheduleId) {
        schedules.trigger(scheduleId);
        return success(true);
    }

    @PostMapping("/{scheduleId}/pause")
    @Operation(summary = "暂停 Temporal Schedule")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:command')")
    public CommonResult<Boolean> pause(@PathVariable String scheduleId) {
        schedules.pause(scheduleId);
        return success(true);
    }

    @PostMapping("/{scheduleId}/resume")
    @Operation(summary = "恢复 Temporal Schedule")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:command')")
    public CommonResult<Boolean> resume(@PathVariable String scheduleId) {
        schedules.resume(scheduleId);
        return success(true);
    }
}
