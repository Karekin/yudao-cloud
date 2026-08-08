package cn.iocoder.yudao.module.cloudmold.crm.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.crm.api.*;
import cn.iocoder.yudao.module.cloudmold.crm.service.actor.CrmActorPrincipalPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CloudMold - Canonical CRM")
@RestController
@RequestMapping("/cloudmold/crm")
public class CrmAdminController {

    @Resource
    private CrmAutomationCommandApi commandApi;
    @Resource
    private CrmQueryApi queryApi;
    @Resource
    private CrmActorPrincipalPort actorPrincipalPort;

    @PostMapping("/command")
    @Operation(summary = "执行 CRM 写命令")
    @PreAuthorize("@ss.hasPermission('cloudmold:crm:command')")
    public CommonResult<CrmCommandResult> execute(@RequestBody CrmCommand command) {
        return success(commandApi.execute(command));
    }

    @GetMapping("/workbench")
    @Operation(summary = "查询销售工作台")
    @PreAuthorize("@ss.hasPermission('cloudmold:crm:query')")
    public CommonResult<CrmWorkbenchView> getWorkbench(@RequestParam(required = false) String ownerPrincipalId) {
        String owner = ownerPrincipalId;
        if (owner == null || owner.isBlank()) {
            owner = actorPrincipalPort.resolveSystemAdmin(getLoginUserId());
        }
        return success(queryApi.getWorkbench(owner));
    }

    @GetMapping("/customers")
    @Operation(summary = "分页查询客户")
    @PreAuthorize("@ss.hasPermission('cloudmold:crm:query')")
    public CommonResult<PageResult<CrmCustomerView>> getCustomers(@Valid CrmCustomerPageQuery query) {
        return success(queryApi.getCustomers(query));
    }

    @GetMapping("/leads")
    @Operation(summary = "分页查询线索")
    @PreAuthorize("@ss.hasPermission('cloudmold:crm:query')")
    public CommonResult<PageResult<CrmLeadView>> getLeads(@Valid CrmLeadPageQuery query) {
        return success(queryApi.getLeads(query));
    }

    @GetMapping("/contacts")
    @Operation(summary = "分页查询联系人")
    @PreAuthorize("@ss.hasPermission('cloudmold:crm:query')")
    public CommonResult<PageResult<CrmContactView>> getContacts(@Valid CrmContactPageQuery query) {
        return success(queryApi.getContacts(query));
    }

    @GetMapping("/opportunities")
    @Operation(summary = "分页查询商机")
    @PreAuthorize("@ss.hasPermission('cloudmold:crm:query')")
    public CommonResult<PageResult<CrmOpportunityView>> getOpportunities(@Valid CrmOpportunityPageQuery query) {
        return success(queryApi.getOpportunities(query));
    }

    @GetMapping("/follow-ups")
    @Operation(summary = "分页查询跟进记录")
    @PreAuthorize("@ss.hasPermission('cloudmold:crm:query')")
    public CommonResult<PageResult<CrmFollowUpView>> getFollowUps(@Valid CrmFollowUpPageQuery query) {
        return success(queryApi.getFollowUps(query));
    }

    @GetMapping("/pool")
    @Operation(summary = "分页查询公海客户")
    @PreAuthorize("@ss.hasPermission('cloudmold:crm:query')")
    public CommonResult<PageResult<CrmCustomerView>> getPool(@Valid CrmPoolPageQuery query) {
        return success(queryApi.getPool(query));
    }
}
