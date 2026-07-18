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
public class DreamPlantCommand implements java.io.Serializable {
    private DreamPlantOperation operation;
    private String idempotencyKey;
    private String runTraceId;
    private Instant occurredAt;

    private String mapKey;
    private Long expectedVersion;
    private String schemaVersion;
    private String payloadJson;
    private String payloadSha256;
    private String sourceRef;
    private Boolean publiclyReadable;

    private String explorationRunId;
    private String intent;
    private String contextJson;
    private String requestedByPrincipalId;
    private String outcomeStatus;
    private String outcomeJson;
    private String evidenceRef;
}
