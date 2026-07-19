package cn.iocoder.yudao.module.cloudmold.promotion.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.promotion.controller.admin.vo.PromotionCampaignPageReqVO;
import cn.iocoder.yudao.module.cloudmold.promotion.service.query.PromotionCampaignPageItem;
import cn.iocoder.yudao.module.cloudmold.promotion.service.query.PromotionCampaignQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Promotion Campaign Query")
@RestController
@RequestMapping("/cloudmold/promotion")
public class PromotionCampaignQueryController {

    @Resource
    private PromotionCampaignQueryService promotionCampaignQueryService;

    @GetMapping("/campaign/page")
    @Operation(summary = "分页查询规范营销活动，只返回当前租户 CloudMold 权威数据")
    @PreAuthorize("@ss.hasPermission('cloudmold:promotion:query')")
    public CommonResult<PageResult<PromotionCampaignPageItem>> getPage(@Valid PromotionCampaignPageReqVO request) {
        return success(promotionCampaignQueryService.getPage(request));
    }
}
