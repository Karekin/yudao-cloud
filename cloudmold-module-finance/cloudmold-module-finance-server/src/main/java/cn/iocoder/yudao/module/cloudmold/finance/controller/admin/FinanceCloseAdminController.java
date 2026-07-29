package cn.iocoder.yudao.module.cloudmold.finance.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.finance.api.ChannelStatementView;
import cn.iocoder.yudao.module.cloudmold.finance.api.FinanceCloseCommand;
import cn.iocoder.yudao.module.cloudmold.finance.api.FinanceCloseCommandApi;
import cn.iocoder.yudao.module.cloudmold.finance.api.FinanceCloseResult;
import cn.iocoder.yudao.module.cloudmold.finance.api.FinanceCloseView;
import cn.iocoder.yudao.module.cloudmold.finance.service.actor.FinanceActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.finance.service.query.FinanceCloseQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CloudMold - Finance Close")
@RestController
@RequestMapping("/cloudmold/finance-close")
public class FinanceCloseAdminController {
    @Resource
    private FinanceCloseCommandApi commandApi;
    @Resource
    private FinanceCloseQueryService queryService;
    @Resource
    private FinanceActorPrincipalPort actorPrincipalPort;

    @PostMapping("/command")
    @Operation(summary = "执行规范财务对账、结算与关账命令")
    @PreAuthorize("@ss.hasPermission('cloudmold:finance-close:command')")
    public CommonResult<FinanceCloseResult> execute(@RequestBody FinanceCloseCommand command) {
        return success(commandApi.execute(command, actorPrincipalPort.resolveSystemAdmin(getLoginUserId())));
    }

    @GetMapping("/period/{periodId}")
    @Operation(summary = "查询会计期间关账终态")
    @PreAuthorize("@ss.hasPermission('cloudmold:finance-close:query')")
    public CommonResult<FinanceCloseView> period(@PathVariable("periodId") String periodId) {
        return success(queryService.requirePeriod(periodId));
    }

    @GetMapping("/statement/{statementId}")
    @Operation(summary = "查询渠道账单、差异与结算状态")
    @PreAuthorize("@ss.hasPermission('cloudmold:finance-close:query')")
    public CommonResult<ChannelStatementView> statement(
            @PathVariable("statementId") String statementId) {
        return success(queryService.requireStatement(statementId));
    }
}
