package cn.iocoder.yudao.module.cloudmold.order.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.order.api.migration.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Legacy Trade Benefit Governance")
@RestController
@RequestMapping("/cloudmold/order/migrations/legacy-trade-benefit-governance")
public class LegacyTradeBenefitGovernanceController {

    @Resource
    private LegacyTradeBenefitGovernanceApi governanceApi;

    @PostMapping("/assess")
    @Operation(summary = "Freeze historical benefit, named funding, and quarantine readiness without importing")
    @PreAuthorize("@ss.hasPermission('cloudmold:order:migration-assess')")
    public CommonResult<LegacyTradeBenefitGovernanceResult> assess(
            @RequestBody LegacyTradeBenefitGovernanceCommand command) {
        return success(governanceApi.assess(command));
    }

    @GetMapping("/{governanceRunId}")
    @Operation(summary = "Read one immutable benefit-governance run")
    @PreAuthorize("@ss.hasPermission('cloudmold:order:migration-query')")
    public CommonResult<LegacyTradeBenefitGovernanceResult> requireRun(
            @PathVariable String governanceRunId) {
        return success(governanceApi.requireRun(governanceRunId));
    }

    @GetMapping("/{governanceRunId}/components")
    @Operation(summary = "Read per-component historical identity and named-funding blockers")
    @PreAuthorize("@ss.hasPermission('cloudmold:order:migration-query')")
    public CommonResult<List<LegacyTradeBenefitGovernanceComponentView>> listComponents(
            @PathVariable String governanceRunId) {
        return success(governanceApi.listComponents(governanceRunId));
    }

    @GetMapping("/{governanceRunId}/quarantines")
    @Operation(summary = "Read immutable quarantine cases and explicit decision status")
    @PreAuthorize("@ss.hasPermission('cloudmold:order:migration-query')")
    public CommonResult<List<LegacyTradeBenefitGovernanceQuarantineView>> listQuarantines(
            @PathVariable String governanceRunId) {
        return success(governanceApi.listQuarantines(governanceRunId));
    }
}
