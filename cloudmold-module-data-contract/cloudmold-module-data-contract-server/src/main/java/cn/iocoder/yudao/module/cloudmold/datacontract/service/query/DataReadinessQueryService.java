package cn.iocoder.yudao.module.cloudmold.datacontract.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.controller.admin.vo.DataReadinessEvidenceSectionRespVO;
import cn.iocoder.yudao.module.cloudmold.datacontract.controller.admin.vo.DataReadinessOutboxOverviewRespVO;
import cn.iocoder.yudao.module.cloudmold.datacontract.controller.admin.vo.DataReadinessOverviewRespVO;
import cn.iocoder.yudao.module.cloudmold.datacontract.dal.mysql.DataReadinessQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DataReadinessQueryService {

    static final String UNKNOWN = "UNKNOWN";
    static final String NOT_CONNECTED = "NOT_CONNECTED";

    private static final String OUTBOX_BOUNDARY = "Live tenant-scoped view over cloudmold_event_outbox only; "
            + "payloads, headers, and publish errors are intentionally excluded. Zero counts do not imply healthy "
            + "CDC, DQC, ADS, or source graduation.";
    private static final String CDC_BOUNDARY = "First slice does not query Flink jobs, CDC control planes, or "
            + "connector state from the application backend.";
    private static final String DQC_BOUNDARY = "First slice does not execute or read external DQC suites from the "
            + "application backend.";
    private static final String ADS_BOUNDARY = "First slice does not query StarRocks, ADS tables, or lakehouse "
            + "projections from the application backend.";
    private static final String SOURCE_GRADUATION_BOUNDARY = "First slice does not read external source-admission "
            + "or graduation evidence from the application backend.";

    private final DataReadinessQueryMapper dataReadinessQueryMapper;

    public DataReadinessOverviewRespVO getOverview() {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime generatedAt = LocalDateTime.now();
        DataReadinessOutboxOverviewRow row = dataReadinessQueryMapper.selectOutboxOverview(tenantId);
        if (row == null) {
            row = new DataReadinessOutboxOverviewRow();
        }

        DataReadinessOverviewRespVO response = new DataReadinessOverviewRespVO();
        response.setGeneratedAt(generatedAt);
        response.setOutbox(toOutboxOverview(generatedAt, row));
        response.setCdc(unknownSection(generatedAt, CDC_BOUNDARY));
        response.setDqc(unknownSection(generatedAt, DQC_BOUNDARY));
        response.setAds(unknownSection(generatedAt, ADS_BOUNDARY));
        response.setSourceGraduation(unknownSection(generatedAt, SOURCE_GRADUATION_BOUNDARY));
        return response;
    }

    private static DataReadinessOutboxOverviewRespVO toOutboxOverview(LocalDateTime generatedAt,
                                                                      DataReadinessOutboxOverviewRow row) {
        DataReadinessOutboxOverviewRespVO response = new DataReadinessOutboxOverviewRespVO();
        response.setGeneratedAt(generatedAt);
        response.setPendingCount(defaultZero(row.getPendingCount()));
        response.setClaimedCount(defaultZero(row.getClaimedCount()));
        response.setPublishedCount(defaultZero(row.getPublishedCount()));
        response.setDeadCount(defaultZero(row.getDeadCount()));
        response.setOldestPendingRecordedAt(row.getOldestPendingRecordedAt());
        response.setLatestRecordedAt(row.getLatestRecordedAt());
        response.setLatestPublishedAt(row.getLatestPublishedAt());
        response.setBoundary(OUTBOX_BOUNDARY);
        return response;
    }

    private static DataReadinessEvidenceSectionRespVO unknownSection(LocalDateTime generatedAt, String boundary) {
        DataReadinessEvidenceSectionRespVO response = new DataReadinessEvidenceSectionRespVO();
        response.setStatus(UNKNOWN);
        response.setConnectionStatus(NOT_CONNECTED);
        response.setGeneratedAt(generatedAt);
        response.setBoundary(boundary);
        return response;
    }

    private static Long defaultZero(Long value) {
        return value == null ? 0L : value;
    }
}
