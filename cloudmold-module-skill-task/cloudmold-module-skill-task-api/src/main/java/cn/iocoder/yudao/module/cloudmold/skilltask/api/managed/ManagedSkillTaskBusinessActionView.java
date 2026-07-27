package cn.iocoder.yudao.module.cloudmold.skilltask.api.managed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagedSkillTaskBusinessActionView implements Serializable {

    private String taskId;
    private String stepCode;
    private String displayName;
    private String resultSummary;
    private Integer actionOrder;
    private String actionType;
    private String operationType;
    private String status;
    private Integer attemptCount;
    private List<ManagedSkillTaskOutcomeObjectView> businessObjects;
    private String evidenceSha256;
    private String errorCode;
    private Instant startedAt;
    private Instant completedAt;
}
