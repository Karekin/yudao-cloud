package cn.iocoder.yudao.module.cloudmold.procurement.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementOrderView;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementQueryApi;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.ProcurementOrder;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseOrderDeliverySchedule;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseOrderItem;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql.ProcurementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProcurementQueryService implements ProcurementQueryApi {
    private final ProcurementMapper mapper;

    @Override
    public ProcurementOrderView requireCurrent(String orderId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ProcurementOrder header = mapper.selectCurrentHeader(tenantId, orderId);
        if (header == null) {
            throw new IllegalArgumentException("procurement order not found");
        }
        return toView(tenantId, header);
    }

    @Override
    public ProcurementOrderView requireBySourceBusiness(String sourceBusinessType, String sourceBusinessRef) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ProcurementOrder header = mapper.selectCurrentHeaderBySourceBusiness(tenantId, sourceBusinessType, sourceBusinessRef);
        if (header == null) {
            throw new IllegalArgumentException("procurement order not found");
        }
        return toView(tenantId, header);
    }

    private ProcurementOrderView toView(Long tenantId, ProcurementOrder header) {
        List<PurchaseOrderItem> items = mapper.selectItems(tenantId, header.getOrderId());
        List<PurchaseOrderDeliverySchedule> schedules = mapper.selectSchedules(tenantId, header.getOrderId());

        Map<String, List<ProcurementOrderView.PurchaseOrderDeliveryScheduleView>> schedulesByItemId = new LinkedHashMap<>();
        for (PurchaseOrderDeliverySchedule schedule : schedules) {
            schedulesByItemId.computeIfAbsent(schedule.getItemId(), ignored -> new ArrayList<>())
                    .add(ProcurementOrderView.PurchaseOrderDeliveryScheduleView.builder()
                            .scheduleId(schedule.getScheduleId())
                            .scheduleNumber(schedule.getScheduleNumber())
                            .requiredDeliveryDate(schedule.getRequiredDeliveryDate())
                            .canonicalWarehouseId(schedule.getCanonicalWarehouseId())
                            .scheduledQuantity(schedule.getScheduledQuantity())
                            .build());
        }

        List<ProcurementOrderView.PurchaseOrderItemView> itemViews = new ArrayList<>(items.size());
        for (PurchaseOrderItem item : items) {
            List<ProcurementOrderView.PurchaseOrderDeliveryScheduleView> itemSchedules =
                    schedulesByItemId.getOrDefault(item.getItemId(), List.of());
            itemViews.add(ProcurementOrderView.PurchaseOrderItemView.builder()
                    .itemId(item.getItemId())
                    .lineNumber(item.getLineNumber())
                    .canonicalSkuId(item.getCanonicalSkuId())
                    .orderedQuantity(item.getOrderedQuantity())
                    .uomCode(item.getUomCode())
                    .taxCode(item.getTaxCode())
                    .taxRateBps(item.getTaxRateBps())
                    .unitNetPriceMinor(item.getUnitNetPriceMinor())
                    .lineNetAmountMinor(item.getLineNetAmountMinor())
                    .lineTaxAmountMinor(item.getLineTaxAmountMinor())
                    .lineGrossAmountMinor(item.getLineGrossAmountMinor())
                    .schedules(itemSchedules)
                    .build());
        }

        return ProcurementOrderView.builder()
                .orderId(header.getOrderId())
                .orderCode(header.getOrderCode())
                .sourceBusinessType(header.getSourceBusinessType())
                .sourceBusinessRef(header.getSourceBusinessRef())
                .supplierId(header.getSupplierId())
                .currencyCode(header.getCurrencyCode())
                .leadTimeDays(header.getLeadTimeDays())
                .headerNetAmountMinor(header.getHeaderNetAmountMinor())
                .headerTaxAmountMinor(header.getHeaderTaxAmountMinor())
                .headerGrossAmountMinor(header.getHeaderGrossAmountMinor())
                .taxCalculationPolicyCode(header.getTaxCalculationPolicyCode())
                .roundingPolicyCode(header.getRoundingPolicyCode())
                .status(header.getStatus())
                .version(header.getVersion())
                .createdByPrincipalId(header.getCreatedByPrincipalId())
                .dispatchedByPrincipalId(header.getDispatchedByPrincipalId())
                .supplierConfirmedByPrincipalId(header.getSupplierConfirmedByPrincipalId())
                .cancelledByPrincipalId(header.getCancelledByPrincipalId())
                .closedByPrincipalId(header.getClosedByPrincipalId())
                .reasonCode(header.getReasonCode())
                .remark(header.getRemark())
                .createdAt(header.getCreatedAt())
                .updatedAt(header.getUpdatedAt())
                .dispatchedAt(header.getDispatchedAt())
                .supplierConfirmedAt(header.getSupplierConfirmedAt())
                .cancelledAt(header.getCancelledAt())
                .closedAt(header.getClosedAt())
                .items(itemViews)
                .build();
    }
}
