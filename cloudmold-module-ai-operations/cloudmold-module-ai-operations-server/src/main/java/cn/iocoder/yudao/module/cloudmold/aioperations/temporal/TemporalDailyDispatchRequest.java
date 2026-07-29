package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemporalDailyDispatchRequest implements Serializable {
    private Long tenantId;
    private String scheduleId;
    private String skillId;
    private String skillVersion;
    private String inputStrategy;
    private Integer maxFanOut;
    private Long operatorUserId;
    private Integer operatorUserType;
    private String roleCode;
    private String actionCode;
    private Long approvalTimeoutSeconds;
    private Long businessEventTimeoutSeconds;
    private String timeZone;
}
