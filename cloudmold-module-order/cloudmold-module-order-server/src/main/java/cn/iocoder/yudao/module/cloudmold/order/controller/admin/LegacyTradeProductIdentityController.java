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
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CloudMold - Legacy Trade Historical Product Identity")
@RestController
@RequestMapping("/cloudmold/order/migrations/legacy-trade-product-identity")
public class LegacyTradeProductIdentityController {

    @Resource
    private LegacyTradeProductIdentityApi identityApi;

    @Resource
    private LegacyTradeProductIdentityQualificationApi qualificationApi;

    @PostMapping("/assess")
    @Operation(summary = "Assess historical SPU/SKU identity without treating current product rows as history")
    @PreAuthorize("@ss.hasPermission('cloudmold:order:migration-assess')")
    public CommonResult<LegacyTradeProductIdentityResult> assess(
            @RequestBody LegacyTradeProductIdentityCommand command) {
        return success(identityApi.assess(command));
    }

    @GetMapping("/{identityRunId}")
    @Operation(summary = "Read one immutable historical product-identity run")
    @PreAuthorize("@ss.hasPermission('cloudmold:order:migration-query')")
    public CommonResult<LegacyTradeProductIdentityResult> requireRun(@PathVariable String identityRunId) {
        return success(identityApi.requireRun(identityRunId));
    }

    @GetMapping("/{identityRunId}/items")
    @Operation(summary = "Read per-OrderItem product identity conflicts and historical evidence blockers")
    @PreAuthorize("@ss.hasPermission('cloudmold:order:migration-query')")
    public CommonResult<List<LegacyTradeProductIdentityItemView>> listItems(@PathVariable String identityRunId) {
        return success(identityApi.listItems(identityRunId));
    }

    @PostMapping("/qualification-requests")
    @Operation(summary = "Freeze one exact historical SPU/SKU evidence request for independent review")
    @PreAuthorize("@ss.hasPermission('cloudmold:order:migration-qualify-prepare')")
    public CommonResult<LegacyTradeProductIdentityQualificationApi.QualificationRequestResult>
    requestQualification(
            @RequestBody LegacyTradeProductIdentityQualificationApi.QualificationRequestCommand command) {
        return success(qualificationApi.request(command, getLoginUserId()));
    }

    @PostMapping("/qualification-requests/{requestId}/approvals")
    @Operation(summary = "Bind one authenticated DATA_OWNER or CHANGE_MANAGER review to frozen evidence")
    @PreAuthorize("(#command.approvalRole != null and #command.approvalRole.toUpperCase() == 'DATA_OWNER' " +
            "and @ss.hasPermission('cloudmold:order:migration-qualify-approve-data-owner')) or " +
            "(#command.approvalRole != null and #command.approvalRole.toUpperCase() == 'CHANGE_MANAGER' " +
            "and @ss.hasPermission('cloudmold:order:migration-qualify-approve-change-manager'))")
    public CommonResult<LegacyTradeProductIdentityQualificationApi.QualificationRequestResult>
    approveQualification(
            @PathVariable String requestId,
            @RequestBody LegacyTradeProductIdentityQualificationApi.QualificationApprovalCommand command) {
        return success(qualificationApi.approve(requestId, command, getLoginUserId()));
    }

    @GetMapping("/qualification-requests/{requestId}")
    @Operation(summary = "Read one immutable-scope qualification request and its independent approvals")
    @PreAuthorize("@ss.hasPermission('cloudmold:order:migration-query')")
    public CommonResult<LegacyTradeProductIdentityQualificationApi.QualificationRequestResult>
    requireQualificationRequest(@PathVariable String requestId) {
        return success(qualificationApi.requireRequest(requestId));
    }
}
