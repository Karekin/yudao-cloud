package cn.iocoder.yudao.module.cloudmold.dreamplant.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DreamPlantMetricCommand implements java.io.Serializable {
    private String mapKey;
    private String metricId;
    private String idempotencyKey;
    private String runTraceId;
    private Long expectedVersion;
    private String subjectType;
    private String subjectId;
    private String metricCode;
    private String metricName;
    private BigDecimal metricValue;
    private String metricUnit;
    private String status;
    private String dimensionJson;
    private String detailsJson;
    private String detailsSha256;
    private String sourceRef;
    private String evidenceRef;
    private Instant windowStartAt;
    private Instant windowEndAt;
    private Instant measuredAt;
    private Instant occurredAt;
}
