package cn.iocoder.yudao.module.cloudmold.datacontract.controller.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DataReadinessOutboxOverviewRespVO {

    private LocalDateTime generatedAt;
    private Long pendingCount;
    private Long claimedCount;
    private Long publishedCount;
    private Long deadCount;
    private LocalDateTime oldestPendingRecordedAt;
    private LocalDateTime latestRecordedAt;
    private LocalDateTime latestPublishedAt;
    private String boundary;
}
