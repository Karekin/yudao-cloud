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

@Tag(name = "CloudMold - Legacy Trade Benefit Migration")
@RestController
@RequestMapping("/cloudmold/order/migrations/legacy-trade-benefits")
public class LegacyTradeBenefitMigrationController {

    @Resource
    private LegacyTradeBenefitMigrationApi migrationApi;

    @PostMapping("/assess")
    @Operation(summary = "Assess the complete local legacy Trade benefit denominator without importing it")
    @PreAuthorize("@ss.hasPermission('cloudmold:order:migration-assess')")
    public CommonResult<LegacyTradeBenefitAssessmentResult> assess(
            @RequestBody LegacyTradeBenefitAssessmentCommand command) {
        return success(migrationApi.assess(command));
    }

    @GetMapping("/{migrationRunId}")
    @Operation(summary = "Read one immutable legacy Trade benefit assessment run")
    @PreAuthorize("@ss.hasPermission('cloudmold:order:migration-query')")
    public CommonResult<LegacyTradeBenefitAssessmentResult> requireRun(@PathVariable String migrationRunId) {
        return success(migrationApi.requireRun(migrationRunId));
    }

    @GetMapping("/{migrationRunId}/candidates")
    @Operation(summary = "Read every assessed legacy Trade Order candidate")
    @PreAuthorize("@ss.hasPermission('cloudmold:order:migration-query')")
    public CommonResult<List<LegacyTradeBenefitCandidateView>> listCandidates(@PathVariable String migrationRunId) {
        return success(migrationApi.listCandidates(migrationRunId));
    }

    @GetMapping("/{migrationRunId}/components")
    @Operation(summary = "Read every nonzero legacy Trade benefit component and unresolved evidence state")
    @PreAuthorize("@ss.hasPermission('cloudmold:order:migration-query')")
    public CommonResult<List<LegacyTradeBenefitComponentView>> listComponents(@PathVariable String migrationRunId) {
        return success(migrationApi.listComponents(migrationRunId));
    }

    @GetMapping("/{migrationRunId}/items")
    @Operation(summary = "Read the immutable legacy Trade Order Item denominator for exact mapping review")
    @PreAuthorize("@ss.hasPermission('cloudmold:order:migration-query')")
    public CommonResult<List<LegacyTradeBenefitItemView>> listItems(@PathVariable String migrationRunId) {
        return success(migrationApi.listItems(migrationRunId));
    }
}
