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
public class DreamPlantDriftView implements java.io.Serializable {
    private String mapKey;
    private String driftId;
    private Long version;
    private String subjectType;
    private String subjectId;
    private String driftType;
    private String severity;
    private String status;
    private String baselineRef;
    private String observedRef;
    private String detailsJson;
    private String detailsSha256;
    private String sourceRef;
    private String evidenceRef;
    private Instant detectedAt;
    private Instant resolvedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
