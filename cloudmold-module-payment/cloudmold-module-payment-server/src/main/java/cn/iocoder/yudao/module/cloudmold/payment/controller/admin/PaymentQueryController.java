package cn.iocoder.yudao.module.cloudmold.payment.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.payment.controller.admin.vo.PaymentPageReqVO;
import cn.iocoder.yudao.module.cloudmold.payment.service.query.PaymentPageItem;
import cn.iocoder.yudao.module.cloudmold.payment.service.query.PaymentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Payment Query")
@RestController
@RequestMapping("/cloudmold/payment")
public class PaymentQueryController {

    @Resource
    private PaymentQueryService paymentQueryService;

    @GetMapping("/page")
    @Operation(summary = "分页查询规范 Payment，只返回当前租户 CloudMold 权威支付数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:payment:query')")
    public CommonResult<PageResult<PaymentPageItem>> getPage(@Valid PaymentPageReqVO request) {
        return success(paymentQueryService.getPage(request));
    }
}
