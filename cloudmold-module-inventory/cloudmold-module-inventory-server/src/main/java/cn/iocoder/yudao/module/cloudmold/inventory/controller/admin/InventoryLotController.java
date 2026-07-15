package cn.iocoder.yudao.module.cloudmold.inventory.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.inventory.api.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CloudMold - Canonical Inventory Lot")
@RestController
@RequestMapping("/cloudmold/inventory/v3/lots")
public class InventoryLotController {

    @Resource
    private InventoryLotCommandApi commandApi;
    @Resource
    private InventoryLotQueryApi queryApi;
    @Resource
    private InventoryV3AvailabilityQueryApi availabilityQueryApi;

    @PostMapping("/command")
    @Operation(summary = "Execute one canonical Lot lifecycle or source-mapping command")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:lot-command')")
    public CommonResult<InventoryLotResult> execute(@RequestBody InventoryLotCommand command) {
        return success(commandApi.execute(command));
    }

    @GetMapping("/{lotId}")
    @Operation(summary = "Read current canonical Lot metadata and allocation eligibility")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:lot-query')")
    public CommonResult<InventoryLotView> requireCurrent(
            @PathVariable String lotId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant eligibilityAt) {
        return success(queryApi.requireCurrent(lotId, eligibilityAt));
    }

    @GetMapping("/resolve-source")
    @Operation(summary = "Resolve a qualified source identity at an effective instant")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:lot-query')")
    public CommonResult<InventoryLotView> requireBySource(
            @RequestParam String sourceSystem, @RequestParam String sourceType, @RequestParam String sourceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant effectiveAt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant eligibilityAt) {
        return success(queryApi.requireBySource(sourceSystem, sourceType, sourceId, effectiveAt, eligibilityAt));
    }

    @GetMapping("/{lotId}/availability")
    @Operation(summary = "Read exact-grain balances and allocatable quantity for one Lot")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:lot-query')")
    public CommonResult<List<InventoryV3AvailabilityView>> listAvailability(
            @PathVariable String lotId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant eligibilityAt) {
        return success(availabilityQueryApi.listByLot(lotId, eligibilityAt));
    }
}
