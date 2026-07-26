package cn.iocoder.yudao.module.cloudmold.listing.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.listing.controller.admin.vo.ListingPageReqVO;
import cn.iocoder.yudao.module.cloudmold.listing.service.query.ListingPageItem;
import cn.iocoder.yudao.module.cloudmold.listing.service.query.ListingQueryService;
import cn.iocoder.yudao.module.cloudmold.listing.service.query.SkuSellabilityQueryService;
import cn.iocoder.yudao.module.cloudmold.listing.service.query.SkuSellabilityView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Listing Query")
@RestController
@RequestMapping("/cloudmold/listing")
public class ListingQueryController {

    @Resource
    private ListingQueryService listingQueryService;

    @Resource
    private SkuSellabilityQueryService skuSellabilityQueryService;

    @GetMapping("/page")
    @Operation(summary = "分页查询当前租户规范 Listing")
    @PreAuthorize("@ss.hasPermission('cloudmold:listing:query')")
    public CommonResult<PageResult<ListingPageItem>> getListingPage(@Valid ListingPageReqVO request) {
        return success(listingQueryService.getListingPage(request));
    }

    @GetMapping("/sku-sellability")
    @Operation(summary = "查询规范 SKU 的质量、库存、渠道发布与综合可售状态")
    @PreAuthorize("@ss.hasPermission('cloudmold:catalog:query') and "
            + "@ss.hasPermission('cloudmold:listing:query')")
    public CommonResult<SkuSellabilityView> getSkuSellability(@RequestParam String canonicalSkuId) {
        return success(skuSellabilityQueryService.getBySku(canonicalSkuId));
    }
}
