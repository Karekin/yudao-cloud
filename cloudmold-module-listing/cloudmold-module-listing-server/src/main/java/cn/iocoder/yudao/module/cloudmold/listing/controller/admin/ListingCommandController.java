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

    @PostMapping("/channel-publish-receipt")
    @Operation(summary = "Record an idempotent real channel publish receipt for a canonical Listing")
    @PreAuthorize("@ss.hasPermission('cloudmold:listing:command')")
    public CommonResult<ListingChannelPublishReceiptResult> recordChannelPublishReceipt(
            @RequestBody ListingChannelPublishReceiptCommand command) {
        return success(listingCommandApi.recordChannelPublishReceipt(command));
    }

    @PostMapping("/offer/validate")
    @Operation(summary = "Validate the exact published Listing offer and CNY price snapshot")
    @PreAuthorize("@ss.hasPermission('cloudmold:listing:offer:query')")
    public CommonResult<PublishedListingOfferView> requirePublishedOffer(
            @RequestBody PublishedOfferValidationCommand command) {
        return success(listingQueryApi.requirePublishedOffer(command));
    }

    @PostMapping("/terminal-readback")
    @Operation(summary = "Read the governed terminal listing state without inventing channel confirmation")
    @PreAuthorize("@ss.hasPermission('cloudmold:listing:offer:query')")
    public CommonResult<ListingTerminalReadbackView> getListingTerminalReadback(
            @RequestBody ListingTerminalReadbackCommand command) {
        return success(listingQueryApi.getListingTerminalReadback(command));
    }
}
