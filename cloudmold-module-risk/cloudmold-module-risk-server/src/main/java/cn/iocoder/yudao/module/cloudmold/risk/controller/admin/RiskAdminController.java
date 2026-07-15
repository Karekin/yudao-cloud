package cn.iocoder.yudao.module.cloudmold.risk.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.risk.api.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Explainable Risk Review")
@RestController
@RequestMapping("/cloudmold/risk")
public class RiskAdminController {
    @Resource private RiskCommandApi commandApi;
    @Resource private RiskQueryApi queryApi;

    @PostMapping("/command")
    @Operation(summary = "Execute an evidence-only risk command; no automatic enforcement")
    @PreAuthorize("@ss.hasPermission('cloudmold:risk:command')")
    public CommonResult<RiskView> execute(@RequestBody RiskCommand command) {
        return success(commandApi.execute(command));
    }

    @GetMapping("/policy/get")
    @PreAuthorize("@ss.hasPermission('cloudmold:risk:query')")
    public CommonResult<RiskView> getPolicy(@RequestParam("policyId") String policyId) {
        return success(queryApi.getPolicy(policyId));
    }

    @GetMapping("/cluster/get")
    @PreAuthorize("@ss.hasPermission('cloudmold:risk:query')")
    public CommonResult<RiskView> getCluster(@RequestParam("clusterId") String clusterId) {
        return success(queryApi.getCluster(clusterId));
    }

    @GetMapping("/review/get")
    @PreAuthorize("@ss.hasPermission('cloudmold:risk:query')")
    public CommonResult<RiskView> getReview(@RequestParam("caseId") String caseId) {
        return success(queryApi.getReviewCase(caseId));
    }
}
