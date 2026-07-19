package cn.iocoder.yudao.module.cloudmold.fulfillment.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.fulfillment.controller.admin.vo.FulfillmentPageReqVO;
import cn.iocoder.yudao.module.cloudmold.fulfillment.service.query.FulfillmentPageItem;
import cn.iocoder.yudao.module.cloudmold.fulfillment.service.query.FulfillmentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Fulfillment Query")
@RestController
@RequestMapping("/cloudmold/fulfillment")
public class FulfillmentQueryController {

    @Resource
    private FulfillmentQueryService fulfillmentQueryService;

    @GetMapping("/page")
    @Operation(summary = "分页查询规范 Fulfillment，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:fulfillment:query')")
    public CommonResult<PageResult<FulfillmentPageItem>> getPage(@Valid FulfillmentPageReqVO request) {
        return success(fulfillmentQueryService.getPage(request));
    }
}
