package cn.iocoder.yudao.module.cloudmold.customerservice.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.customerservice.controller.admin.vo.CustomerServiceTicketPageReqVO;
import cn.iocoder.yudao.module.cloudmold.customerservice.service.query.CustomerServiceTicketPageItem;
import cn.iocoder.yudao.module.cloudmold.customerservice.service.query.CustomerServiceTicketQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Customer Service Ticket Query")
@RestController
@RequestMapping("/cloudmold/customer-service")
public class CustomerServiceTicketQueryController {

    @Resource
    private CustomerServiceTicketQueryService customerServiceTicketQueryService;

    @GetMapping("/ticket/page")
    @Operation(summary = "分页查询规范客服工单，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:customer-service:query')")
    public CommonResult<PageResult<CustomerServiceTicketPageItem>> getPage(
            @Valid CustomerServiceTicketPageReqVO request) {
        return success(customerServiceTicketQueryService.getPage(request));
    }
}
