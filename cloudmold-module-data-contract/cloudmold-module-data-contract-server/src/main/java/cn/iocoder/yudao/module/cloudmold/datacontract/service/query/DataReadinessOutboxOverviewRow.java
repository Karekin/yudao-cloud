package cn.iocoder.yudao.module.cloudmold.datacontract.service.query;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DataReadinessOutboxOverviewRow {

    private Long pendingCount;
    private Long claimedCount;
    private Long publishedCount;
    private Long deadCount;
    private LocalDateTime oldestPendingRecordedAt;
    private LocalDateTime latestRecordedAt;
    private LocalDateTime latestPublishedAt;
}
