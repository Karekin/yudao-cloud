package cn.iocoder.yudao.module.cloudmold.operationsintelligence.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.api.*;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.api.workflow.BusinessControlWorkflowResult;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.api.workflow.DailyBusinessControlQueryPort;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.api.workflow.WeeklyBusinessReviewQueryPort;
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
    @Resource private DailyBusinessControlQueryPort dailyBusinessControlQueryPort;
    @Resource private WeeklyBusinessReviewQueryPort weeklyBusinessReviewQueryPort;

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

    @GetMapping("/clue-source-version/get")
    @PreAuthorize("@ss.hasPermission('cloudmold:operations-intelligence:query')")
    public CommonResult<OperationsIntelligenceResult> getClueSourceVersion(@RequestParam String sourceVersionId) {
        return success(queryApi.getClueSourceVersion(sourceVersionId));
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

    @GetMapping("/workflow/daily-business-control")
    @Operation(summary = "Get the tenant-scoped daily business control read model without writing Mission state")
    @PreAuthorize("@ss.hasPermission('cloudmold:operations-intelligence:query')")
    public CommonResult<BusinessControlWorkflowResult> getDailyBusinessControl() {
        return success(dailyBusinessControlQueryPort.inspectDaily());
    }

    @GetMapping("/workflow/weekly-business-review")
    @Operation(summary = "Get the tenant-scoped weekly business review read model without writing Mission state")
    @PreAuthorize("@ss.hasPermission('cloudmold:operations-intelligence:query')")
    public CommonResult<BusinessControlWorkflowResult> getWeeklyBusinessReview() {
        return success(weeklyBusinessReviewQueryPort.inspectWeekly());
    }
}
