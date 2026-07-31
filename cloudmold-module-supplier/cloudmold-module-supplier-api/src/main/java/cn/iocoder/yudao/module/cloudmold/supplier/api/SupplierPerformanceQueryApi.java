package cn.iocoder.yudao.module.cloudmold.supplier.api;

import java.time.LocalDate;

public interface SupplierPerformanceQueryApi {
    SupplierPerformanceScorecardView requireLatestScorecard(String supplierId);

    SupplierPerformanceReadinessView inspectReadiness(String supplierId, LocalDate periodStart, LocalDate periodEnd);
}
