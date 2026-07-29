package cn.iocoder.yudao.module.cloudmold.supplyplanning.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoLegacyOperationsQueryApi;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoWarehouseInboundQueryApi;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementOrderView;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementQueryApi;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentBusinessStageView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentExecutionProposalView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentExecutionView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SupplyPlanningQueryApi;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.controller.admin.vo.SupplyPlanningPageReqVO;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.mysql.SupplyPlanningMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SupplyPlanningQueryService implements SupplyPlanningQueryApi {
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");

    private final SupplyPlanningMapper mapper;
    private final ProcurementQueryApi procurementQueryApi;
    private final YudaoWarehouseInboundQueryApi warehouseInboundQueryApi;
    private final YudaoLegacyOperationsQueryApi legacyOperationsQueryApi;

    @Override
    public List<ReplenishmentExecutionProposalView> listReadyReplenishmentExecutionProposals(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        return mapper.selectReadyReplenishmentExecutionProposals(
                TenantContextHolder.getRequiredTenantId(), limit);
    }

    @Override
    public ReplenishmentExecutionProposalView requireReadyReplenishmentExecutionProposal(
            String proposalId) {
        if (!StringUtils.hasText(proposalId) || proposalId.length() > 128
                || !SAFE_REF.matcher(proposalId).matches()) {
            throw new IllegalArgumentException("proposalId must be a safe opaque reference");
        }
        ReplenishmentExecutionProposalView proposal =
                mapper.selectReadyReplenishmentExecutionProposal(
                        TenantContextHolder.getRequiredTenantId(), proposalId);
        if (proposal == null) {
            throw new IllegalArgumentException(
                    "ready replenishment execution proposal not found");
        }
        return proposal;
    }

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
        if ("TRANSFER_REQUEST".equals(execution.getTargetType())
                && "YUDAO_WMS".equals(execution.getSourceSystem())
                && "MOVEMENT_ORDER".equals(execution.getDocumentType())) {
            YudaoLegacyOperationsQueryApi.LegacyDocumentView movementOrder =
                    legacyOperationsQueryApi.getMovementOrder(
                            requirePositiveLong(execution.getExternalDocumentId(),
                                    "movement order external document id"));
            builder.projectionExternalDocumentId(String.valueOf(movementOrder.documentId()))
                    .projectionExternalDocumentNo(movementOrder.documentNo());
            if (Integer.valueOf(0).equals(movementOrder.status())) {
                builder.projectionDocumentStatus("PREPARE")
                        .nextWaitingEventCode("TRANSFER_OUTBOUND")
                        .nextWaitingEventLabel("等待调拨出库");
            } else if (Integer.valueOf(4).equals(movementOrder.status())) {
                builder.projectionDocumentStatus("FINISHED")
                        .nextWaitingEventCode("NONE")
                        .nextWaitingEventLabel("调拨已完成");
            } else if (Integer.valueOf(5).equals(movementOrder.status())) {
                builder.projectionDocumentStatus("CANCELED")
                        .nextWaitingEventCode("NONE")
                        .nextWaitingEventLabel("调拨单已取消");
            } else {
                builder.projectionDocumentStatus("UNKNOWN_" + movementOrder.status())
                        .nextWaitingEventCode("MANUAL_RECONCILIATION")
                        .nextWaitingEventLabel("调拨单状态需要人工复核");
            }
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

    private static Long requirePositiveLong(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(fieldName + " must be positive");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be a positive integer", ex);
        }
    }
}
