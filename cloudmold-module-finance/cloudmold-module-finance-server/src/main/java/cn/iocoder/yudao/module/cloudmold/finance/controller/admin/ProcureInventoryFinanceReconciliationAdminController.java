package cn.iocoder.yudao.module.cloudmold.finance.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.ProcureInventoryFinanceReconciliationAdminVOs.*;
import cn.iocoder.yudao.module.cloudmold.finance.service.ProcureInventoryFinanceReconciliationResult;
import cn.iocoder.yudao.module.cloudmold.finance.service.ProcureInventoryFinanceReconciliationService;
import cn.iocoder.yudao.module.cloudmold.finance.service.actor.FinanceActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.finance.service.query.ProcureInventoryFinanceReconciliationQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CloudMold - Procure Inventory Finance Reconciliation")
@RestController
@RequestMapping("/cloudmold/finance/procure-to-pay/reconciliations")
public class ProcureInventoryFinanceReconciliationAdminController {
    @Resource private ProcureInventoryFinanceReconciliationService service;
    @Resource private ProcureInventoryFinanceReconciliationQueryService queryService;
    @Resource private FinanceActorPrincipalPort actorPort;

    @PostMapping("/runs")
    @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:reconciliation:command')")
    public CommonResult<RunCommandResult> createRun(@RequestBody CreateRunRequest request) {
        return success(admin(service.createRun(request, actor())));
    }

    @GetMapping("/runs/page")
    @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:reconciliation:query')")
    public CommonResult<PageResult<RunPageItem>> runs(RunPageRequest request) {
        return success(queryService.runs(request));
    }

    @GetMapping("/runs/{runId}")
    @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:reconciliation:query')")
    public CommonResult<RunDetail> run(@PathVariable String runId) {
        return success(queryService.run(runId));
    }

    @GetMapping("/lines/page")
    @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:reconciliation:query')")
    public CommonResult<PageResult<LinePageItem>> lines(LinePageRequest request) {
        return success(queryService.lines(request));
    }

    @GetMapping("/lines/{lineId}")
    @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:reconciliation:query')")
    public CommonResult<LineDetail> line(@PathVariable String lineId, @RequestParam String runId) {
        return success(queryService.line(runId, lineId));
    }

    private String actor() {
        return actorPort.resolveSystemAdmin(getLoginUserId());
    }

    private static RunCommandResult admin(ProcureInventoryFinanceReconciliationResult result) {
        RunCommandResult value = new RunCommandResult();
        value.setOperationId(String.valueOf(result.getOperationId()));
        value.setDuplicate(result.isDuplicate());
        value.setAggregateType(result.getAggregateType());
        value.setAggregateId(result.getAggregateId());
        value.setRunCode(result.getRunCode());
        value.setStatus(result.getStatus());
        value.setLineCount(result.getLineCount());
        value.setMatchedCount(result.getMatchedCount());
        value.setDifferentCount(result.getDifferentCount());
        value.setMissingCount(result.getMissingCount());
        value.setUncomparableCount(result.getUncomparableCount());
        return value;
    }
}
