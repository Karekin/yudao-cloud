package cn.iocoder.yudao.module.cloudmold.finance.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.ProcureToPayResult;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.SupplierReturnReversalCommandApi;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.SupplierReturnReversalCommands;
import cn.iocoder.yudao.module.cloudmold.finance.service.actor.FinanceActorPrincipalPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CloudMold - Supplier Return Finance Reversal")
@RestController
@RequestMapping("/cloudmold/finance/procure-to-pay/supplier-return-reversals")
public class SupplierReturnReversalAdminController {

    @Resource
    private SupplierReturnReversalCommandApi supplierReturnReversalCommandApi;
    @Resource
    private FinanceActorPrincipalPort financeActorPrincipalPort;

    @PostMapping("/command")
    @Operation(summary = "Atomically post one finance-owned supplier return AP and valuation reversal")
    @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:command')")
    public CommonResult<ProcureToPayResult> post(@RequestBody SupplierReturnReversalCommands.Post command) {
        return success(supplierReturnReversalCommandApi.post(
                command, financeActorPrincipalPort.resolveSystemAdmin(getLoginUserId())));
    }
}
