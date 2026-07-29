package cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo.TemporalAutomationCandidateCreateReqVO;
import cn.iocoder.yudao.module.cloudmold.aioperations.temporal.TemporalAutomationCandidateService;
import cn.iocoder.yudao.module.cloudmold.aioperations.temporal.TemporalAutomationCandidateView;
import cn.iocoder.yudao.module.cloudmold.aioperations.temporal.TemporalAutomationOverviewService;
import cn.iocoder.yudao.module.cloudmold.aioperations.temporal.TemporalAutomationOverviewView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - AI Operations Temporal Automation")
@RestController
@RequestMapping("/cloudmold/ai-operations/temporal-automation")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.ai-operations.temporal", name = "enabled", havingValue = "true")
public class AiOperationsTemporalAutomationController {

    private final TemporalAutomationCandidateService candidates;
    private final TemporalAutomationOverviewService overview;

    @GetMapping("/overview")
    @Operation(summary = "查询每日 Temporal 调度、候选来源和业务自治实证")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:query')")
    public CommonResult<TemporalAutomationOverviewView> overview() {
        return success(overview.get());
    }

    @PostMapping("/candidates")
    @Operation(summary = "物化一个由每日 Temporal 发现流程受控分发的业务候选")
    @PreAuthorize("@ss.hasPermission('cloudmold:ai-operations:command')")
    public CommonResult<TemporalAutomationCandidateView> enqueue(
            @Valid @RequestBody TemporalAutomationCandidateCreateReqVO request) {
        return success(candidates.enqueue(request));
    }
}
