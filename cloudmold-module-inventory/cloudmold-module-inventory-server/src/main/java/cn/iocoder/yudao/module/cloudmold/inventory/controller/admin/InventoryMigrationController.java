package cn.iocoder.yudao.module.cloudmold.inventory.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.cloudmold.inventory.api.*;
import cn.iocoder.yudao.module.cloudmold.inventory.service.InventoryMigrationEnvironmentGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CloudMold - Canonical Inventory Migration")
@RestController
@RequestMapping("/cloudmold/inventory/v3/migrations")
public class InventoryMigrationController {

    @Resource
    private InventoryMigrationAssessmentApi assessmentApi;
    @Resource
    private InventoryMigrationQueryApi queryApi;
    @Resource
    private InventoryMigrationQualificationApi qualificationApi;
    @Resource
    private InventoryMigrationPilotApi pilotApi;
    @Resource
    private InventoryMigrationShadowApi shadowApi;
    @Resource
    private InventoryMigrationEnvironmentGuard environmentGuard;

    @PostMapping("/assess-v1")
    @Operation(summary = "Assess all or one selected Inventory v1 balance without opening v3 stock")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:migration-assess')")
    public CommonResult<InventoryMigrationAssessmentResult> assessV1(
            @RequestBody InventoryMigrationAssessmentCommand command) {
        environmentGuard.requireAssessmentAllowed(command);
        return success(assessmentApi.assessV1(command));
    }

    @PostMapping("/qualify-v1")
    @Operation(summary = "Qualify one explicitly evidenced controlled migration canary")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:migration-qualify')")
    public CommonResult<InventoryMigrationQualificationResult> qualifyV1(
            @RequestBody InventoryMigrationQualificationCommand command) {
        environmentGuard.requireControlledCanaryMutationAllowed();
        return success(qualificationApi.qualify(command));
    }

    @PostMapping("/migrate-v1")
    @Operation(summary = "Open one qualified controlled canary through the sole v3 opening driver")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:migration-migrate')")
    public CommonResult<InventoryMigrationOpeningResult> migrateV1(
            @RequestBody InventoryMigrationOpeningCommand command) {
        environmentGuard.requireControlledCanaryMutationAllowed();
        return success(qualificationApi.migrate(command));
    }

    @GetMapping("/qualifications/{qualificationId}")
    @Operation(summary = "Read one Inventory migration qualification and opening state")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:migration-query')")
    public CommonResult<InventoryMigrationQualificationResult> requireQualification(
            @PathVariable String qualificationId) {
        return success(qualificationApi.requireQualification(qualificationId));
    }

    @GetMapping("/{migrationRunId}")
    @Operation(summary = "Read one immutable Inventory migration assessment run")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:migration-query')")
    public CommonResult<InventoryMigrationAssessmentResult> requireRun(@PathVariable String migrationRunId) {
        return success(queryApi.requireRun(migrationRunId));
    }

    @GetMapping("/{migrationRunId}/candidates")
    @Operation(summary = "Read ordered Inventory migration assessment candidates")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:migration-query')")
    public CommonResult<List<InventoryMigrationCandidateView>> listCandidates(@PathVariable String migrationRunId) {
        return success(queryApi.listCandidates(migrationRunId));
    }

    @PostMapping("/pilot-batches/freeze")
    @Operation(summary = "Freeze one production-history pilot manifest without qualifying or opening stock")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:migration-pilot-prepare')")
    public CommonResult<InventoryMigrationPilotApi.PilotBatchResult> freezePilotBatch(
            @RequestBody InventoryMigrationPilotApi.FreezePilotBatchCommand command) {
        return success(pilotApi.freeze(command, getLoginUserId()));
    }

    @PostMapping("/pilot-batches/{batchId}/approvals")
    @Operation(summary = "Bind one authenticated DATA_OWNER or CHANGE_MANAGER approval to a frozen pilot manifest")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:migration-pilot-approve')")
    public CommonResult<InventoryMigrationPilotApi.PilotBatchResult> approvePilotBatch(
            @PathVariable String batchId,
            @RequestBody InventoryMigrationPilotApi.ApprovePilotBatchCommand command) {
        return success(pilotApi.approve(batchId, command, getLoginUserId()));
    }

    @PostMapping("/pilot-batches/{batchId}/admit")
    @Operation(summary = "Revalidate and admit a production pilot; admission never opens v3 stock")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:migration-pilot-admit')")
    public CommonResult<InventoryMigrationPilotApi.PilotBatchResult> admitPilotBatch(
            @PathVariable String batchId,
            @RequestBody InventoryMigrationPilotApi.AdmitPilotBatchCommand command) {
        return success(pilotApi.admit(batchId, command, getLoginUserId()));
    }

    @GetMapping("/pilot-batches/{batchId}")
    @Operation(summary = "Read one production pilot manifest, approvals, and admission state")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:migration-query')")
    public CommonResult<InventoryMigrationPilotApi.PilotBatchResult> requirePilotBatch(@PathVariable String batchId) {
        return success(pilotApi.requireBatch(batchId));
    }

    @PostMapping("/pilot-batches/{batchId}/shadow-windows")
    @Operation(summary = "Start an evidence-only continuous shadow window for one admitted production pilot")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:migration-shadow-collect')")
    public CommonResult<InventoryMigrationShadowApi.ShadowWindowResult> startShadowWindow(
            @PathVariable String batchId,
            @RequestBody InventoryMigrationShadowApi.StartShadowWindowCommand command) {
        return success(shadowApi.start(batchId, command, getLoginUserId()));
    }

    @PostMapping("/shadow-windows/{windowId}/rounds")
    @Operation(summary = "Atomically record one full-denominator read-only shadow comparison round")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:migration-shadow-collect')")
    public CommonResult<InventoryMigrationShadowApi.ShadowWindowResult> recordShadowRound(
            @PathVariable String windowId,
            @RequestBody InventoryMigrationShadowApi.RecordShadowRoundCommand command) {
        return success(shadowApi.recordRound(windowId, command, getLoginUserId()));
    }

    @PostMapping("/shadow-windows/{windowId}/finalize")
    @Operation(summary = "Verify a terminal shadow evidence result without authorizing execution or cutover")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:migration-shadow-verify')")
    public CommonResult<InventoryMigrationShadowApi.ShadowWindowResult> finalizeShadowWindow(
            @PathVariable String windowId,
            @RequestBody InventoryMigrationShadowApi.FinalizeShadowWindowCommand command) {
        return success(shadowApi.finalizeWindow(windowId, command, getLoginUserId()));
    }

    @GetMapping("/shadow-windows/{windowId}")
    @Operation(summary = "Read one continuous shadow evidence window and its completed rounds")
    @PreAuthorize("@ss.hasPermission('cloudmold:inventory:migration-query')")
    public CommonResult<InventoryMigrationShadowApi.ShadowWindowResult> requireShadowWindow(
            @PathVariable String windowId) {
        return success(shadowApi.requireWindow(windowId));
    }
}
