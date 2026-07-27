package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class TemporalScheduleView {
    private String scheduleId;
    private String displayName;
    private String description;
    private String skillId;
    private String skillVersion;
    private Long intervalSeconds;
    private String timeZone;
    private String status;
    private boolean paused;
    private String overlapPolicy;
    private String temporalNamespace;
    private String temporalTaskQueue;
    private Instant nextActionAt;
    private Instant lastActionAt;
}
