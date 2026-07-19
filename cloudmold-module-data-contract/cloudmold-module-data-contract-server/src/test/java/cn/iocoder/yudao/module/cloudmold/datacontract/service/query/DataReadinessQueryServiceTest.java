package cn.iocoder.yudao.module.cloudmold.datacontract.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.controller.admin.vo.DataReadinessOverviewRespVO;
import cn.iocoder.yudao.module.cloudmold.datacontract.dal.mysql.DataReadinessQueryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DataReadinessQueryServiceTest {

    private final DataReadinessQueryMapper dataReadinessQueryMapper = mock(DataReadinessQueryMapper.class);
    private final DataReadinessQueryService service = new DataReadinessQueryService(dataReadinessQueryMapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(17L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldBuildLiveOutboxOverviewAndExplicitUnknownExternalSections() {
        DataReadinessOutboxOverviewRow row = new DataReadinessOutboxOverviewRow();
        row.setPendingCount(2L);
        row.setClaimedCount(1L);
        row.setPublishedCount(5L);
        row.setDeadCount(0L);
        row.setOldestPendingRecordedAt(LocalDateTime.of(2026, 7, 19, 8, 0));
        row.setLatestRecordedAt(LocalDateTime.of(2026, 7, 19, 10, 0));
        row.setLatestPublishedAt(LocalDateTime.of(2026, 7, 19, 10, 30));
        when(dataReadinessQueryMapper.selectOutboxOverview(17L)).thenReturn(row);

        DataReadinessOverviewRespVO result = service.getOverview();

        assertThat(result.getGeneratedAt()).isNotNull();
        assertThat(result.getOutbox().getGeneratedAt()).isEqualTo(result.getGeneratedAt());
        assertThat(result.getOutbox().getPendingCount()).isEqualTo(2L);
        assertThat(result.getOutbox().getClaimedCount()).isEqualTo(1L);
        assertThat(result.getOutbox().getPublishedCount()).isEqualTo(5L);
        assertThat(result.getOutbox().getDeadCount()).isZero();
        assertThat(result.getOutbox().getLatestPublishedAt()).isEqualTo(LocalDateTime.of(2026, 7, 19, 10, 30));
        assertThat(result.getOutbox().getBoundary()).contains("payloads, headers, and publish errors");
        assertThat(result.getCdc().getStatus()).isEqualTo(DataReadinessQueryService.UNKNOWN);
        assertThat(result.getCdc().getConnectionStatus()).isEqualTo(DataReadinessQueryService.NOT_CONNECTED);
        assertThat(result.getDqc().getBoundary()).contains("does not execute or read external DQC suites");
        assertThat(result.getAds().getBoundary()).contains("does not query StarRocks");
        assertThat(result.getSourceGraduation().getBoundary()).contains("does not read external source-admission");
        verify(dataReadinessQueryMapper).selectOutboxOverview(17L);
    }

    @Test
    void shouldDefaultMissingAggregateToZeroCountsWithoutCallingItHealthy() {
        when(dataReadinessQueryMapper.selectOutboxOverview(17L)).thenReturn(null);

        DataReadinessOverviewRespVO result = service.getOverview();

        assertThat(result.getOutbox().getPendingCount()).isZero();
        assertThat(result.getOutbox().getClaimedCount()).isZero();
        assertThat(result.getOutbox().getPublishedCount()).isZero();
        assertThat(result.getOutbox().getDeadCount()).isZero();
        assertThat(result.getOutbox().getOldestPendingRecordedAt()).isNull();
        assertThat(result.getOutbox().getLatestRecordedAt()).isNull();
        assertThat(result.getOutbox().getLatestPublishedAt()).isNull();
        assertThat(result.getOutbox().getBoundary()).contains("Zero counts do not imply healthy");
    }
}
