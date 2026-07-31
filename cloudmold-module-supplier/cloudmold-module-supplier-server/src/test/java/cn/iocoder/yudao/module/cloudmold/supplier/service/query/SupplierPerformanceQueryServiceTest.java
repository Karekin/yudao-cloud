package cn.iocoder.yudao.module.cloudmold.supplier.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierPerformanceReadinessView;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierPerformanceRecords.MetricEvidence;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.mysql.SupplierPerformanceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.time.LocalDate.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SupplierPerformanceQueryServiceTest {
    private final SupplierPerformanceMapper mapper = mock(SupplierPerformanceMapper.class);
    private final SupplierPerformanceQueryService service = new SupplierPerformanceQueryService(mapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(162L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void reportsNeedsDataInsteadOfPretendingAScorecardExists() {
        when(mapper.selectMetricEvidence(162L, "supplier-01", of(2026, 7, 1), of(2026, 7, 31)))
                .thenReturn(List.of(new MetricEvidence().setMetricCode("OTIF")));

        SupplierPerformanceReadinessView result = service.inspectReadiness("supplier-01",
                of(2026, 7, 1), of(2026, 7, 31));

        assertThat(result.getStatus()).isEqualTo("NEEDS_DATA");
        assertThat(result.getEvidenceCounts()).containsEntry("OTIF", 1);
        assertThat(result.getMissingMetricCodes()).containsExactly(
                "QUALITY_PASS_RATE", "CAPACITY_ATTAINMENT", "CAPA_EFFECTIVENESS");
    }

    @Test
    void reportsReadyToScoreOnlyAfterAllRequiredMetricFamiliesArePresent() {
        when(mapper.selectMetricEvidence(162L, "supplier-01", of(2026, 7, 1), of(2026, 7, 31)))
                .thenReturn(List.of(metric("OTIF"), metric("QUALITY_PASS_RATE"),
                        metric("CAPACITY_ATTAINMENT"), metric("CAPA_EFFECTIVENESS")));

        SupplierPerformanceReadinessView result = service.inspectReadiness("supplier-01",
                of(2026, 7, 1), of(2026, 7, 31));

        assertThat(result.getStatus()).isEqualTo("READY_TO_SCORE");
        assertThat(result.getMissingMetricCodes()).isEmpty();
    }

    private static MetricEvidence metric(String metricCode) {
        return new MetricEvidence().setMetricCode(metricCode);
    }
}
