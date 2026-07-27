package cn.iocoder.yudao.module.cloudmold.supplyplanning.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoWarehouseInboundQueryApi;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementOrderView;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementQueryApi;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentBusinessStageView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentExecutionView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SupplyPlanningQueryApi;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.controller.admin.vo.SupplyPlanningPageReqVO;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.mysql.SupplyPlanningMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SupplyPlanningQueryService implements SupplyPlanningQueryApi {
    private final SupplyPlanningMapper mapper;
    private final ProcurementQueryApi procurementQueryApi;
    private final YudaoWarehouseInboundQueryApi warehouseInboundQueryApi;

    public PageResult<SupplyPlanningWorkItem> getPage(SupplyPlanningPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String itemType = upper(request.getItemType());
        String status = upper(request.getStatus());
        long total = mapper.countWorkItems(tenantId, itemType, status);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        return new PageResult<>(
                mapper.selectWorkItems(tenantId, itemType, status, offset, request.getPageSize()),
                total);
    }

    @Override
    public ReplenishmentExecutionView requireReplenishmentExecution(String recommendationId) {
        ReplenishmentExecutionView view =
                mapper.selectReplenishmentExecution(TenantContextHolder.getRequiredTenantId(), recommendationId);
        if (view == null) {
            throw new IllegalArgumentException("replenishment recommendation not found");
        }
        return view;
    }

    @Override
    public ReplenishmentBusinessStageView requireReplenishmentBusinessStage(String recommendationId) {
        ReplenishmentExecutionView execution = requireReplenishmentExecution(recommendationId);
        ReplenishmentBusinessStageView.ReplenishmentBusinessStageViewBuilder builder =
                ReplenishmentBusinessStageView.builder()
                        .recommendationId(execution.getRecommendationId())
                        .planId(execution.getPlanId())
                        .recommendationStatus(execution.getRecommendationStatus())
                        .targetType(execution.getTargetType())
                        .projectionSourceSystem(execution.getSourceSystem())
                        .projectionDocumentType(execution.getDocumentType())
                        .projectionExternalDocumentId(execution.getExternalDocumentId())
                        .projectionExternalDocumentNo(execution.getExternalDocumentNo())
                        .projectionDocumentStatus(execution.getDocumentStatus())
                        .nextWaitingEventCode(execution.getNextWaitingEventCode())
                        .nextWaitingEventLabel(execution.getNextWaitingEventLabel());
        if ("PURCHASE_REQUEST".equals(execution.getTargetType())) {
            ProcurementOrderView order =
                    procurementQueryApi.requireBySourceBusiness("REPLENISHMENT", recommendationId);
            builder.procurementOrderId(order.getOrderId())
                    .procurementOrderNo(order.getOrderCode())
                    .procurementOrderStatus(order.getStatus())
                    .projectionSourceSystem(order.getProjectionSourceSystem())
                    .projectionDocumentType(order.getProjectionDocumentType())
                    .projectionExternalDocumentId(order.getProjectionExternalDocumentId())
                    .projectionExternalDocumentNo(order.getProjectionExternalDocumentNo())
                    .projectionDocumentStatus(order.getProjectionDocumentStatus());
            if ("CREATED".equals(order.getStatus())) {
                builder.supplierConfirmationStatus("WAITING_PO_DISPATCH")
                        .asnStatus("WAITING_PO_DISPATCH")
                        .receiptStatus("WAITING_PO_DISPATCH")
                        .qualityStatus("WAITING_PO_DISPATCH")
                        .putawayStatus("WAITING_PO_DISPATCH")
                        .nextWaitingEventCode("PROCUREMENT_DISPATCHED")
                        .nextWaitingEventLabel("等待采购单正式下发");
            } else if ("DISPATCHED".equals(order.getStatus())) {
                builder.supplierConfirmationStatus("WAITING_SUPPLIER_CONFIRMATION")
                        .asnStatus("WAITING_SUPPLIER_CONFIRMATION")
                        .receiptStatus("WAITING_SUPPLIER_CONFIRMATION")
                        .qualityStatus("WAITING_SUPPLIER_CONFIRMATION")
                        .putawayStatus("WAITING_SUPPLIER_CONFIRMATION")
                        .nextWaitingEventCode("SUPPLIER_CONFIRMATION")
                        .nextWaitingEventLabel("等待供应商确认采购单");
            } else if ("SUPPLIER_CONFIRMED".equals(order.getStatus()) || "CLOSED".equals(order.getStatus())) {
                builder.supplierConfirmationStatus("COMPLETED");
                YudaoWarehouseInboundQueryApi.PurchaseInboundTerminalView inbound =
                        warehouseInboundQueryApi.getPurchaseInboundTerminal(
                                new YudaoWarehouseInboundQueryApi.PurchaseInboundTerminalQuery(
                                        "PROCUREMENT_ORDER", order.getOrderId(), order.getOrderCode(), null));
                builder.asnStatus(inbound.asnStatus())
                        .receiptStatus(inbound.receiptStatus())
                        .qualityStatus(inbound.qualityStatus())
                        .putawayStatus(inbound.putawayStatus())
                        .nextWaitingEventCode(inbound.nextWaitingEventCode())
                        .nextWaitingEventLabel(inbound.nextWaitingEventLabel())
                        .inventoryLedgerTransactionId(inbound.inventoryLedgerTransactionId())
                        .inventoryBalanceId(inbound.inventoryBalanceId());
            } else if ("CANCELLED".equals(order.getStatus())) {
                builder.supplierConfirmationStatus("CANCELLED")
                        .asnStatus("CANCELLED")
                        .receiptStatus("CANCELLED")
                        .qualityStatus("CANCELLED")
                        .putawayStatus("CANCELLED")
                        .nextWaitingEventCode("NONE")
                        .nextWaitingEventLabel("采购单已取消");
            }
            return builder.build();
        }
        builder.supplierConfirmationStatus("NOT_APPLICABLE")
                .asnStatus("NOT_APPLICABLE")
                .receiptStatus("NOT_APPLICABLE")
                .qualityStatus("NOT_APPLICABLE")
                .putawayStatus("NOT_APPLICABLE");
        return builder.build();
    }

    private static String upper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : null;
    }
}
