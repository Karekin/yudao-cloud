package cn.iocoder.yudao.module.cloudmold.listing.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.listing.api.unpublish.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Listing Sales Eligibility Enforcement Saga")
@RestController
@RequestMapping("/cloudmold/listing-unpublish-saga")
public class ListingUnpublishSagaController {
    @Resource
    private ListingUnpublishSagaCommandApi commandApi;
    @Resource
    private ListingUnpublishSagaQueryApi queryApi;

    @PostMapping("/retry")
    @Operation(summary = "Manually retry one exhausted Listing unpublish Saga")
    @PreAuthorize("@ss.hasPermission('cloudmold:listing-unpublish-saga:retry')")
    public CommonResult<ListingUnpublishSagaView> retry(@RequestBody ListingUnpublishSagaCommand command) {
        if (command != null) command.setOperation(ListingUnpublishSagaOperation.RETRY);
        return success(commandApi.execute(command));
    }

    @GetMapping("/get")
    @Operation(summary = "Read one Listing unpublish Saga")
    @PreAuthorize("@ss.hasPermission('cloudmold:listing-unpublish-saga:query')")
    public CommonResult<ListingUnpublishSagaView> get(@RequestParam("sagaId") String sagaId) {
        return success(queryApi.get(sagaId));
    }
}
