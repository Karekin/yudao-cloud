package cn.iocoder.yudao.module.cloudmold.listing.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.listing.api.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Listing")
@RestController
@RequestMapping("/cloudmold/listing")
public class ListingCommandController {
    @Resource
    private ListingCommandApi listingCommandApi;
    @Resource
    private ListingQueryApi listingQueryApi;

    @PostMapping("/command")
    @Operation(summary = "Execute one version-checked canonical Listing command")
    @PreAuthorize("@ss.hasPermission('cloudmold:listing:command')")
    public CommonResult<ListingCommandResult> execute(@RequestBody ListingCommand command) {
        return success(listingCommandApi.execute(command));
    }

    @PostMapping("/offer/validate")
    @Operation(summary = "Validate the exact published Listing offer and CNY price snapshot")
    @PreAuthorize("@ss.hasPermission('cloudmold:listing:offer:query')")
    public CommonResult<PublishedListingOfferView> requirePublishedOffer(
            @RequestBody PublishedOfferValidationCommand command) {
        return success(listingQueryApi.requirePublishedOffer(command));
    }
}
