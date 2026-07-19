package cn.iocoder.yudao.module.cloudmold.order.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.order.controller.admin.vo.OrderPageReqVO;
import cn.iocoder.yudao.module.cloudmold.order.service.query.OrderPageItem;
import cn.iocoder.yudao.module.cloudmold.order.service.query.OrderQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Order Query")
@RestController
@RequestMapping("/cloudmold/order")
public class OrderQueryController {

    @Resource
    private OrderQueryService orderQueryService;

    @GetMapping("/page")
    @Operation(summary = "分页查询规范订单，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:order:query')")
    public CommonResult<PageResult<OrderPageItem>> getOrderPage(@Valid OrderPageReqVO request) {
        return success(orderQueryService.getOrderPage(request));
    }
}
