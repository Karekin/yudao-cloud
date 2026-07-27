package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class TemporalScheduleRecord {
    private Long tenantId;
    private String scheduleId;
    private String displayName;
    private String description;
    private String skillId;
    private String skillVersion;
    private String inputJson;
    private Long intervalSeconds;
    private String timeZone;
    private String overlapPolicy;
    private Long operatorUserId;
    private Integer operatorUserType;
    private String roleCode;
    private String actionCode;
    private String status;
    private String temporalNamespace;
    private String temporalTaskQueue;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
