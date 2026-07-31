package cn.iocoder.yudao.module.cloudmold.supplier.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierPerformanceReadinessView {
    private String supplierId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String status;
    private List<String> missingMetricCodes;
    private Map<String, Integer> evidenceCounts;
}
