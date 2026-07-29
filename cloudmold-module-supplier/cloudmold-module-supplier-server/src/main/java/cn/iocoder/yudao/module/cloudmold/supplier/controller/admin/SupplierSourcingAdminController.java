package cn.iocoder.yudao.module.cloudmold.supplier.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierProfileView;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierSourcingCommand;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierSourcingCommandApi;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierSourcingDecisionView;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierSourcingResult;
import cn.iocoder.yudao.module.cloudmold.supplier.service.actor.SupplierActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.supplier.service.query.SupplierSourcingQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CloudMold - Supplier Sourcing")
@RestController
@RequestMapping("/cloudmold/supplier-sourcing")
public class SupplierSourcingAdminController {
    @Resource
    private SupplierSourcingCommandApi commandApi;
    @Resource
    private SupplierSourcingQueryService queryService;
    @Resource
    private SupplierActorPrincipalPort actorPrincipalPort;

    @PostMapping("/command")
    @Operation(summary = "执行规范供应商寻源命令")
    @PreAuthorize("@ss.hasPermission('cloudmold:supplier-sourcing:command')")
    public CommonResult<SupplierSourcingResult> execute(@RequestBody SupplierSourcingCommand command) {
        return success(commandApi.execute(command, actorPrincipalPort.resolveSystemAdmin(getLoginUserId())));
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
