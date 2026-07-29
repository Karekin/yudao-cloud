package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class TemporalDispatchObservationRecord {

    private Long tenantId;
    private String scheduleId;
    private String temporalRunId;
    private String businessDate;
    private String outcomeCode;
    private Integer candidateCount;
    private Integer dispatchedCount;
    private Integer failedCount;
    private String workflowIdsJson;
    private LocalDateTime observedAt;
}
