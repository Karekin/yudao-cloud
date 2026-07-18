package cn.iocoder.yudao.module.cloudmold.dreamplant.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DreamPlantExplorationView implements java.io.Serializable {
    private String explorationRunId;
    private String mapKey;
    private String intent;
    private String contextJson;
    private String requestedByPrincipalId;
    private String status;
    private Long version;
    private String outcomeJson;
    private String evidenceRef;
    private Instant createdAt;
    private Instant updatedAt;
}
