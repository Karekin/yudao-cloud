package cn.iocoder.yudao.module.cloudmold.operationsintelligence.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.controller.admin.vo.OperationsAlertPageReqVO;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.service.query.OperationsAlertPageItem;
import cn.iocoder.yudao.module.cloudmold.operationsintelligence.service.query.OperationsAlertQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Operations Alert Query")
@RestController
@RequestMapping("/cloudmold/operations-intelligence")
public class OperationsAlertQueryController {

    @Resource
    private OperationsAlertQueryService operationsAlertQueryService;

    @GetMapping("/alert/page")
    @Operation(summary = "分页查询规范运营告警工单，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:operations-intelligence:query')")
    public CommonResult<PageResult<OperationsAlertPageItem>> getPage(@Valid OperationsAlertPageReqVO request) {
        return success(operationsAlertQueryService.getPage(request));
    }
}
