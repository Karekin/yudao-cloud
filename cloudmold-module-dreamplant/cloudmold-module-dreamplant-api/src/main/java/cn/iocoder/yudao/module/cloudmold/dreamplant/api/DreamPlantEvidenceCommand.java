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
public class DreamPlantEvidenceCommand implements java.io.Serializable {
    private String mapKey;
    private String evidenceId;
    private String idempotencyKey;
    private String runTraceId;
    private Long expectedVersion;
    private String subjectType;
    private String subjectId;
    private String evidenceType;
    private String title;
    private String summary;
    private String contentRef;
    private String contentSha256;
    private String detailsJson;
    private String detailsSha256;
    private String sourceRef;
    private Instant observedAt;
    private Instant capturedAt;
    private Instant occurredAt;
}
