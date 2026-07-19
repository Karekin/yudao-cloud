package cn.iocoder.yudao.module.cloudmold.commercebehavior.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.controller.admin.vo.CommerceBehaviorEventPageReqVO;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.service.query.BehaviorEventPageItem;
import cn.iocoder.yudao.module.cloudmold.commercebehavior.service.query.CommerceBehaviorEventQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Commerce Behavior Query")
@RestController
@RequestMapping("/cloudmold/commerce-behavior")
public class CommerceBehaviorEventQueryController {

    @Resource
    private CommerceBehaviorEventQueryService commerceBehaviorEventQueryService;

    @GetMapping("/event/page")
    @Operation(summary = "分页查询规范交易行为事件，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:commerce-behavior:query')")
    public CommonResult<PageResult<BehaviorEventPageItem>> getPage(@Valid CommerceBehaviorEventPageReqVO request) {
        return success(commerceBehaviorEventQueryService.getPage(request));
    }
}
