package cn.iocoder.yudao.module.cloudmold.agentcontrol.api.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionLifecycleGoalView implements Serializable {

    private String goalId;
    private String goalCode;
    private String title;
    private String status;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}
