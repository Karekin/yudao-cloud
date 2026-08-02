package cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.*;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.actor.WarehouseActorPrincipalPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CloudMold - Canonical Warehouse Network")
@RestController
@RequestMapping("/cloudmold/warehouse")
public class WarehouseNetworkCommandController {
    @Resource
    private WarehouseNetworkCommandApi commandApi;
    @Resource
    private WarehouseSourceMappingQueryApi queryApi;
    @Resource
    private InboundCommandApi inboundCommandApi;
    @Resource
    private InboundQueryApi inboundQueryApi;
    @Resource
    private WarehouseActorPrincipalPort actorPrincipalPort;

    @PostMapping("/command")
    @Operation(summary = "Execute one canonical warehouse network command")
    @PreAuthorize("@ss.hasPermission('cloudmold:warehouse:command')")
    public CommonResult<WarehouseNetworkCommandResult> execute(@RequestBody WarehouseNetworkCommand command) {
        return success(commandApi.execute(command));
    }

    @PostMapping("/inbound/asns/command")
    @Operation(summary = "Execute one authoritative procurement ASN command")
    @PreAuthorize("@ss.hasPermission('cloudmold:warehouse:command')")
    public CommonResult<InboundCommandResult> executeAsn(@RequestBody InboundCommand command) {
        return success(inboundCommandApi.execute(command, actorPrincipalPort.resolveSystemAdmin(getLoginUserId())));
    }

    @PostMapping("/inbound/receipts/partial-receive")
    @Operation(summary = "Record one Warehouse partial procurement receipt batch")
    @PreAuthorize("@ss.hasPermission('cloudmold:warehouse:command')")
    public CommonResult<InboundCommandResult> partialReceive(@RequestBody InboundCommand command) {
        return success(inboundCommandApi.execute(command, actorPrincipalPort.resolveSystemAdmin(getLoginUserId())));
    }

    @PostMapping("/inbound/putaways/command")
    @Operation(summary = "Execute one authoritative multi-line procurement putaway command")
    @PreAuthorize("@ss.hasPermission('cloudmold:warehouse:command')")
    public CommonResult<InboundCommandResult> executePutaway(@RequestBody InboundCommand command) {
        return success(inboundCommandApi.execute(command, actorPrincipalPort.resolveSystemAdmin(getLoginUserId())));
    }

    @GetMapping("/inbound/stage")
    @Operation(summary = "Get the canonical inbound stage for one procurement order")
    @PreAuthorize("@ss.hasPermission('cloudmold:warehouse:query')")
    public CommonResult<InboundQueryApi.InboundStageView> getInboundStage(
            @RequestParam("procurementOrderId") String procurementOrderId) {
        return success(inboundQueryApi.requireInboundStage(procurementOrderId));
    }

    @GetMapping("/inbound/receipts/progress")
    @Operation(summary = "Get the canonical receipt progress for one procurement order")
    @PreAuthorize("@ss.hasPermission('cloudmold:warehouse:query')")
    public CommonResult<InboundReceiptProgressView> getReceiptProgress(
            @RequestParam("procurementOrderId") String procurementOrderId) {
        return success(inboundQueryApi.requireReceiptProgress(procurementOrderId));
    }

    @PostMapping("/source/resolve-network")
    @Operation(summary = "Resolve one active source warehouse to its ready canonical network")
    @PreAuthorize("@ss.hasPermission('cloudmold:warehouse:query')")
    public CommonResult<WarehouseNetworkView> resolveNetwork(
            @RequestBody WarehouseSourceReference reference) {
        return success(queryApi.resolveReadyNetwork(reference, Instant.now()));
    }
}
