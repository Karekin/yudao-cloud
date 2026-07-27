package cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo.ManagedSkillTaskTriggerReqVO;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.command.AiOperationsManagedRunCommandService;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.command.ManagedSkillTaskTriggerResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - AI Operations Managed Run Command")
@RestController
@RequestMapping("/cloudmold/ai-operations/managed-runs")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.agent-control", name = "enabled", havingValue = "true")
public class AiOperationsManagedRunCommandController {

    private final AiOperationsManagedRunCommandService managedRunCommandService;

    @PostMapping("/trigger")
    @Operation(summary = "由后台管理系统发起受管 SkillTask；R2/R3 由服务端兑换 BPM 执行票据")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:command')")
    public CommonResult<ManagedSkillTaskTriggerResult> triggerManagedRun(
            @Valid @RequestBody ManagedSkillTaskTriggerReqVO request) {
        return success(managedRunCommandService.trigger(request));
    }
}
