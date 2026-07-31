package cn.iocoder.yudao.module.cloudmold.supplier.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierProfileView;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierPerformanceCommand;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierPerformanceCommandApi;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierPerformanceReadinessView;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierPerformanceResult;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierPerformanceScorecardView;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierSourcingCommand;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierSourcingCommandApi;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierSourcingDecisionView;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierSourcingResult;
import cn.iocoder.yudao.module.cloudmold.supplier.service.actor.SupplierActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.supplier.service.query.SupplierSourcingQueryService;
import cn.iocoder.yudao.module.cloudmold.supplier.service.query.SupplierPerformanceQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

import java.time.LocalDate;

@Tag(name = "CloudMold - Supplier Sourcing")
@RestController
@RequestMapping("/cloudmold/supplier-sourcing")
public class SupplierSourcingAdminController {
    @Resource
    private SupplierSourcingCommandApi commandApi;
    @Resource
    private SupplierPerformanceCommandApi performanceCommandApi;
    @Resource
    private SupplierSourcingQueryService queryService;
    @Resource
    private SupplierPerformanceQueryService performanceQueryService;
    @Resource
    private SupplierActorPrincipalPort actorPrincipalPort;

    @PostMapping("/command")
    @Operation(summary = "执行规范供应商寻源命令")
    @PreAuthorize("@ss.hasPermission('cloudmold:supplier-sourcing:command')")
    public CommonResult<SupplierSourcingResult> execute(@RequestBody SupplierSourcingCommand command) {
        return success(commandApi.execute(command, actorPrincipalPort.resolveSystemAdmin(getLoginUserId())));
    }

    @PostMapping("/performance/command")
    @Operation(summary = "记录供应商绩效事实或生成评分卡")
    @PreAuthorize("@ss.hasPermission('cloudmold:supplier-performance:command')")
    public CommonResult<SupplierPerformanceResult> executePerformance(@RequestBody SupplierPerformanceCommand command) {
        return success(performanceCommandApi.execute(command,
                actorPrincipalPort.resolveSystemAdmin(getLoginUserId())));
    }

    @GetMapping("/performance/readiness")
    @Operation(summary = "查询供应商绩效评分就绪度")
    @PreAuthorize("@ss.hasPermission('cloudmold:supplier-performance:query')")
    public CommonResult<SupplierPerformanceReadinessView> readiness(@RequestParam("supplierId") String supplierId,
                                                                      @RequestParam("periodStart") LocalDate periodStart,
                                                                      @RequestParam("periodEnd") LocalDate periodEnd) {
        return success(performanceQueryService.inspectReadiness(supplierId, periodStart, periodEnd));
    }

    @GetMapping("/performance/latest/{supplierId}")
    @Operation(summary = "查询供应商最新绩效评分卡")
    @PreAuthorize("@ss.hasPermission('cloudmold:supplier-performance:query')")
    public CommonResult<SupplierPerformanceScorecardView> latestPerformance(
            @PathVariable("supplierId") String supplierId) {
        return success(performanceQueryService.requireLatestScorecard(supplierId));
    }

    @GetMapping("/supplier/{supplierId}")
    @Operation(summary = "查询供应商准入状态")
    @PreAuthorize("@ss.hasPermission('cloudmold:supplier-sourcing:query')")
    public CommonResult<SupplierProfileView> supplier(@PathVariable("supplierId") String supplierId) {
        return success(queryService.requireSupplier(supplierId));
    }

    @GetMapping("/decision/{sourcingCaseId}")
    @Operation(summary = "查询 RFQ、报价、样品和定标决策")
    @PreAuthorize("@ss.hasPermission('cloudmold:supplier-sourcing:query')")
    public CommonResult<SupplierSourcingDecisionView> decision(
            @PathVariable("sourcingCaseId") String sourcingCaseId) {
        return success(queryService.requireDecision(sourcingCaseId));
    }
}
