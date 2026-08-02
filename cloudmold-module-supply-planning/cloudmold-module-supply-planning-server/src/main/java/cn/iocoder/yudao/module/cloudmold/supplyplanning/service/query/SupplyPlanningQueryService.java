package cn.iocoder.yudao.module.cloudmold.supplyplanning.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.procurement.api.PurchaseRequisitionQueryApi;
import cn.iocoder.yudao.module.cloudmold.procurement.api.PurchaseRequisitionView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentBusinessStageView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentExecutionProposalView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentExecutionView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SupplyPlanningQueryApi;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.controller.admin.vo.SupplyPlanningPageReqVO;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.mysql.SupplyPlanningMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockTransferQueryApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockTransferView;
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
    private final PurchaseRequisitionQueryApi purchaseRequisitionQueryApi;
    private final StockTransferQueryApi stockTransferQueryApi;

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
                        .targetType(execution.getTargetType());
        if ("PURCHASE_REQUEST".equals(execution.getTargetType())) {
            PurchaseRequisitionView requisition = purchaseRequisitionQueryApi
                    .requireBySourceBusiness("REPLENISHMENT", recommendationId);
            return builder.purchaseRequisitionId(requisition.getRequisitionId())
                    .purchaseRequisitionNo(requisition.getRequisitionCode())
                    .purchaseRequisitionStatus(requisition.getStatus())
                    .supplierConfirmationStatus("WAITING_SOURCING")
                    .asnStatus("WAITING_PURCHASE_ORDER")
                    .receiptStatus("WAITING_PURCHASE_ORDER")
                    .qualityStatus("WAITING_PURCHASE_ORDER")
                    .putawayStatus("WAITING_PURCHASE_ORDER")
                    .nextWaitingEventCode("PROCUREMENT_SOURCING")
                    .nextWaitingEventLabel("等待采购寻源与定标")
                    .build();
        }
        if ("TRANSFER_REQUEST".equals(execution.getTargetType())) {
            StockTransferView transfer = stockTransferQueryApi.requireBySourceBusiness(
                    "REPLENISHMENT", recommendationId);
            builder.stockTransferId(transfer.getOrderId())
                    .stockTransferNo(transfer.getOrderCode())
                    .stockTransferStatus(transfer.getOrderStatus())
                    .nextWaitingEventCode(transfer.getCurrentStageCode())
                    .nextWaitingEventLabel(transfer.getCurrentStageLabel());
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
