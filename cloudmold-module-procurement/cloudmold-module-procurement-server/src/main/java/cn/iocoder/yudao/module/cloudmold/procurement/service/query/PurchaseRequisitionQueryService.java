package cn.iocoder.yudao.module.cloudmold.procurement.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.procurement.api.PurchaseRequisitionQueryApi;
import cn.iocoder.yudao.module.cloudmold.procurement.api.PurchaseRequisitionView;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseRequisition;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseRequisitionDeliverySchedule;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseRequisitionLine;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql.ProcurementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PurchaseRequisitionQueryService implements PurchaseRequisitionQueryApi {
    private final ProcurementMapper mapper;

    @Override
    public PurchaseRequisitionView requireBySourceBusiness(String sourceBusinessType, String sourceBusinessRef) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        PurchaseRequisition header = mapper.selectPurchaseRequisitionBySourceBusiness(
                tenantId, sourceBusinessType, sourceBusinessRef);
        if (header == null) {
            throw new IllegalArgumentException("purchase requisition not found");
        }
        List<PurchaseRequisitionLine> lines = mapper.selectPurchaseRequisitionLines(tenantId, header.getRequisitionId());
        List<PurchaseRequisitionDeliverySchedule> schedules = mapper.selectPurchaseRequisitionSchedules(
                tenantId, header.getRequisitionId());
        Map<String, List<PurchaseRequisitionView.DeliveryScheduleView>> schedulesByLine = new LinkedHashMap<>();
        for (PurchaseRequisitionDeliverySchedule schedule : schedules) {
            schedulesByLine.computeIfAbsent(schedule.getLineId(), ignored -> new ArrayList<>()).add(
                    PurchaseRequisitionView.DeliveryScheduleView.builder()
                            .scheduleId(schedule.getScheduleId()).scheduleNumber(schedule.getScheduleNumber())
                            .canonicalWarehouseId(schedule.getCanonicalWarehouseId())
                            .requiredDeliveryDate(schedule.getRequiredDeliveryDate())
                            .scheduledQuantity(schedule.getScheduledQuantity()).build());
        }
        List<PurchaseRequisitionView.LineView> lineViews = lines.stream().map(line ->
                PurchaseRequisitionView.LineView.builder()
                        .lineId(line.getLineId()).lineNumber(line.getLineNumber())
                        .canonicalSkuId(line.getCanonicalSkuId()).requestedQuantity(line.getRequestedQuantity())
                        .uomCode(line.getUomCode())
                        .valuationPolicyId(line.getValuationPolicyId())
                        .valuationPolicyVersion(line.getValuationPolicyVersion())
                        .valuationPolicyHash(line.getValuationPolicyHash())
                        .schedules(schedulesByLine.getOrDefault(line.getLineId(), List.of())).build()).toList();
        return PurchaseRequisitionView.builder()
                .requisitionId(header.getRequisitionId()).requisitionCode(header.getRequisitionCode())
                .sourceBusinessType(header.getSourceBusinessType()).sourceBusinessRef(header.getSourceBusinessRef())
                .legalEntityId(header.getLegalEntityId())
                .taxCalculationPolicyCode(header.getTaxCalculationPolicyCode())
                .roundingPolicyCode(header.getRoundingPolicyCode())
                .status(header.getStatus()).version(header.getVersion()).reasonCode(header.getReasonCode())
                .remark(header.getRemark()).lines(lineViews).build();
    }
}
