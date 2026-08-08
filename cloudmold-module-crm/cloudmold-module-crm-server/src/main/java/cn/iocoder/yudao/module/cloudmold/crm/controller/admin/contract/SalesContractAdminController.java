package cn.iocoder.yudao.module.cloudmold.crm.controller.admin.contract;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractCommand;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractCommandPort;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractCommandResult;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractQueryApi;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractView;
import cn.iocoder.yudao.module.cloudmold.crm.service.contract.SalesContractActorPrincipalPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CloudMold - CRM Sales Contracts")
@RestController
@RequestMapping("/cloudmold/crm/sales-contracts")
public class SalesContractAdminController {

    @Resource
    private SalesContractCommandPort commandApi;
    @Resource
    private SalesContractQueryApi queryApi;
    @Resource
    private SalesContractActorPrincipalPort actorPrincipalPort;

    @PostMapping("/command")
    @Operation(summary = "执行销售合同命令")
    @PreAuthorize("@ss.hasPermission('cloudmold:crm:sales-contract:command')")
    public CommonResult<SalesContractCommandResult> execute(@RequestBody SalesContractCommand command) {
        Long loginUserId = getLoginUserId();
        String actorPrincipalId = actorPrincipalPort.resolveSystemAdmin(loginUserId);
        return success(commandApi.execute(command, actorPrincipalId, loginUserId));
    }

    @GetMapping("/{salesContractId}")
    @Operation(summary = "查询销售合同")
    @PreAuthorize("@ss.hasPermission('cloudmold:crm:sales-contract:query')")
    public CommonResult<SalesContractView> get(@PathVariable String salesContractId) {
        return success(queryApi.get(salesContractId));
    }

    @GetMapping("/page")
    @Operation(summary = "查询销售合同列表")
    @PreAuthorize("@ss.hasPermission('cloudmold:crm:sales-contract:query')")
    public CommonResult<List<SalesContractView>> list() {
        return success(queryApi.list());
    }
}
