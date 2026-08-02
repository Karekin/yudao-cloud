package cn.iocoder.yudao.module.cloudmold.quality.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.quality.api.ProcurementReceiptInspectionCommand;
import cn.iocoder.yudao.module.cloudmold.quality.api.ProcurementReceiptInspectionCommandApi;
import cn.iocoder.yudao.module.cloudmold.quality.api.ProcurementReceiptInspectionQueryApi;
import cn.iocoder.yudao.module.cloudmold.quality.api.ProcurementReceiptInspectionResult;
import cn.iocoder.yudao.module.cloudmold.quality.api.ProcurementReceiptInspectionView;
import cn.iocoder.yudao.module.cloudmold.quality.controller.admin.vo.ProcurementReceiptInspectionPageReqVO;
import cn.iocoder.yudao.module.cloudmold.quality.service.ProcurementReceiptInspectionPageItem;
import cn.iocoder.yudao.module.cloudmold.quality.service.ProcurementReceiptInspectionQueryService;
import cn.iocoder.yudao.module.cloudmold.quality.service.actor.QualityActorPrincipalPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "CloudMold - 采购收货质检")
@RestController
@RequestMapping("/cloudmold/quality/procurement-receipt-inspections")
public class ProcurementReceiptInspectionAdminController {
    @Resource
    private ProcurementReceiptInspectionCommandApi commandApi;
    @Resource
    private ProcurementReceiptInspectionQueryApi queryApi;
    @Resource
    private ProcurementReceiptInspectionQueryService queryService;
    @Resource
    private QualityActorPrincipalPort actorPrincipalPort;

    @PostMapping("/command")
    @Operation(summary = "执行采购收货质检命令")
    @PreAuthorize("@ss.hasPermission('cloudmold:quality:procurement-receipt-inspection:command')")
    public CommonResult<ProcurementReceiptInspectionResult> execute(
            @RequestBody ProcurementReceiptInspectionCommand command) {
        String actorPrincipalId = actorPrincipalPort.resolveSystemAdmin(getLoginUserId());
        return success(commandApi.execute(command, actorPrincipalId));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询采购收货质检单")
    @PreAuthorize("@ss.hasPermission('cloudmold:quality:procurement-receipt-inspection:query')")
    public CommonResult<PageResult<ProcurementReceiptInspectionPageItem>> getPage(
            @Valid ProcurementReceiptInspectionPageReqVO request) {
        return success(queryService.getPage(request));
    }

    @GetMapping("/{inspectionId}")
    @Operation(summary = "查询采购收货质检完整事实")
    @PreAuthorize("@ss.hasPermission('cloudmold:quality:procurement-receipt-inspection:query')")
    public CommonResult<ProcurementReceiptInspectionView> get(@PathVariable String inspectionId) {
        return success(queryApi.get(inspectionId));
    }
}
