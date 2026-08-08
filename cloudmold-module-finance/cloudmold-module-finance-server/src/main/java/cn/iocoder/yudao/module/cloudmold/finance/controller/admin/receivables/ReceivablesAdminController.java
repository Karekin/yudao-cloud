package cn.iocoder.yudao.module.cloudmold.finance.controller.admin.receivables;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.finance.api.receivables.ReceivablesCommandPort;
import cn.iocoder.yudao.module.cloudmold.finance.api.receivables.ReceivablesCommandResult;
import cn.iocoder.yudao.module.cloudmold.finance.api.receivables.ReceivablesCommands;
import cn.iocoder.yudao.module.cloudmold.finance.api.receivables.ReceivablesSummaryView;
import cn.iocoder.yudao.module.cloudmold.finance.service.actor.FinanceActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.finance.service.query.receivables.ReceivablesQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CloudMold - Finance Receivables")
@RestController
@RequestMapping("/cloudmold/finance/receivables")
public class ReceivablesAdminController {

    @Resource
    private ReceivablesCommandPort receivablesCommandApi;
    @Resource
    private ReceivablesQueryService receivablesQueryService;
    @Resource
    private FinanceActorPrincipalPort financeActorPrincipalPort;

    @GetMapping("/customers/{customerId}/summary")
    @PreAuthorize("@ss.hasPermission('cloudmold:finance:receivables:query')")
    public CommonResult<List<ReceivablesSummaryView>> summarizeByCustomer(@PathVariable("customerId") String customerId) {
        return success(receivablesQueryService.summarizeByCustomer(customerId));
    }

    @GetMapping("/sales-contracts/{salesContractId}/summary")
    @PreAuthorize("@ss.hasPermission('cloudmold:finance:receivables:query')")
    public CommonResult<List<ReceivablesSummaryView>> summarizeBySalesContract(
            @PathVariable("salesContractId") String salesContractId) {
        return success(receivablesQueryService.summarizeBySalesContract(salesContractId));
    }

    @PostMapping("/plans/register")
    @PreAuthorize("@ss.hasPermission('cloudmold:finance:receivables:command')")
    public CommonResult<ReceivablesCommandResult> registerPlan(@RequestBody ReceivablesCommands.RegisterPlan command) {
        return success(receivablesCommandApi.registerPlan(command, actorPrincipalId()));
    }

    @PostMapping("/receipts/record")
    @PreAuthorize("@ss.hasPermission('cloudmold:finance:receivables:command')")
    public CommonResult<ReceivablesCommandResult> recordReceipt(@RequestBody ReceivablesCommands.RecordReceipt command) {
        return success(receivablesCommandApi.recordReceipt(command, actorPrincipalId()));
    }

    @PostMapping("/receipts/allocate")
    @PreAuthorize("@ss.hasPermission('cloudmold:finance:receivables:command')")
    public CommonResult<ReceivablesCommandResult> allocateReceipt(
            @RequestBody ReceivablesCommands.AllocateReceipt command) {
        return success(receivablesCommandApi.allocateReceipt(command, actorPrincipalId()));
    }

    private String actorPrincipalId() {
        return financeActorPrincipalPort.resolveSystemAdmin(getLoginUserId());
    }
}
