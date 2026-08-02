package cn.iocoder.yudao.module.cloudmold.supplier.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierPerformanceCommand;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierPerformanceCommandApi;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierPerformanceReadinessView;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierPerformanceResult;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierPerformanceScorecardView;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierProfileCommand;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierProfileCommandApi;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierProfileQueryApi;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierProfileResult;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierProfileView;
import cn.iocoder.yudao.module.cloudmold.supplier.service.actor.SupplierActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.supplier.service.query.SupplierPerformanceQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CloudMold - Supplier Master Data")
@RestController
@RequestMapping("/cloudmold/suppliers")
public class SupplierAdminController {
    @Resource
    private SupplierProfileCommandApi profileCommandApi;
    @Resource
    private SupplierProfileQueryApi profileQueryApi;
    @Resource
    private SupplierPerformanceCommandApi performanceCommandApi;
    @Resource
    private SupplierPerformanceQueryService performanceQueryService;
    @Resource
    private SupplierActorPrincipalPort actorPrincipalPort;

    @PostMapping("/profile/command")
    @Operation(summary = "执行供应商主档或准入命令")
    @PreAuthorize("@ss.hasPermission('cloudmold:supplier-profile:command')")
    public CommonResult<SupplierProfileResult> executeProfile(@RequestBody SupplierProfileCommand command) {
        return success(profileCommandApi.execute(command,
                actorPrincipalPort.resolveSystemAdmin(getLoginUserId())));
    }

    @GetMapping("/profile/{supplierId}")
    @Operation(summary = "查询供应商主档与准入状态")
    @PreAuthorize("@ss.hasPermission('cloudmold:supplier-profile:query')")
    public CommonResult<SupplierProfileView> profile(@PathVariable("supplierId") String supplierId) {
        return success(profileQueryApi.requireSupplier(supplierId));
    }

    @PostMapping("/performance/command")
    @Operation(summary = "记录供应商绩效事实或生成评分卡")
    @PreAuthorize("@ss.hasPermission('cloudmold:supplier-performance:command')")
    public CommonResult<SupplierPerformanceResult> executePerformance(
            @RequestBody SupplierPerformanceCommand command) {
        return success(performanceCommandApi.execute(command,
                actorPrincipalPort.resolveSystemAdmin(getLoginUserId())));
    }

    @GetMapping("/performance/readiness")
    @Operation(summary = "查询供应商绩效评分就绪度")
    @PreAuthorize("@ss.hasPermission('cloudmold:supplier-performance:query')")
    public CommonResult<SupplierPerformanceReadinessView> readiness(
            @RequestParam("supplierId") String supplierId,
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
}
