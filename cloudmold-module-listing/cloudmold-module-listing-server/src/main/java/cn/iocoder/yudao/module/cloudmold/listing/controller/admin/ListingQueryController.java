package cn.iocoder.yudao.module.cloudmold.listing.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.listing.controller.admin.vo.ListingPageReqVO;
import cn.iocoder.yudao.module.cloudmold.listing.service.query.ListingPageItem;
import cn.iocoder.yudao.module.cloudmold.listing.service.query.ListingQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Listing Query")
@RestController
@RequestMapping("/cloudmold/listing")
public class ListingQueryController {

    @Resource
    private ListingQueryService listingQueryService;

    @GetMapping("/page")
    @Operation(summary = "分页查询当前租户规范 Listing")
    @PreAuthorize("@ss.hasPermission('cloudmold:listing:query')")
    public CommonResult<PageResult<ListingPageItem>> getListingPage(@Valid ListingPageReqVO request) {
        return success(listingQueryService.getListingPage(request));
    }
}
