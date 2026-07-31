package cn.iocoder.yudao.module.cloudmold.supplier.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierPerformanceQueryApi;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierPerformanceReadinessView;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierPerformanceScorecardView;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierPerformanceRecords.MetricEvidence;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.mysql.SupplierPerformanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SupplierPerformanceQueryService implements SupplierPerformanceQueryApi {
    private static final List<String> REQUIRED_METRIC_CODES = List.of(
            "OTIF", "QUALITY_PASS_RATE", "CAPACITY_ATTAINMENT", "CAPA_EFFECTIVENESS");

    private final SupplierPerformanceMapper mapper;

    @Override
    public SupplierPerformanceScorecardView requireLatestScorecard(String supplierId) {
        SupplierPerformanceScorecardView result = mapper.selectLatestScorecard(
                TenantContextHolder.getRequiredTenantId(), supplierId);
        if (result == null) {
            throw new IllegalArgumentException("supplier performance scorecard not found");
        }
        return result;
    }

    @Override
    public SupplierPerformanceReadinessView inspectReadiness(String supplierId, LocalDate periodStart,
                                                             LocalDate periodEnd) {
        if (supplierId == null || supplierId.isBlank()) {
            throw new IllegalArgumentException("supplierId is required");
        }
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("performance period is invalid");
        }
        List<MetricEvidence> evidence = mapper.selectMetricEvidence(TenantContextHolder.getRequiredTenantId(),
                supplierId, periodStart, periodEnd);
        Map<String, Integer> counts = new LinkedHashMap<>();
        evidence.forEach(item -> counts.merge(item.getMetricCode(), 1, Integer::sum));
        List<String> missing = REQUIRED_METRIC_CODES.stream().filter(code -> !counts.containsKey(code)).toList();
        return SupplierPerformanceReadinessView.builder().supplierId(supplierId).periodStart(periodStart)
                .periodEnd(periodEnd).status(missing.isEmpty() ? "READY_TO_SCORE" : "NEEDS_DATA")
                .missingMetricCodes(missing).evidenceCounts(Map.copyOf(counts)).build();
    }
}
