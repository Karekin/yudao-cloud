package cn.iocoder.yudao.module.cloudmold.agentcontrol.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionMetricObservationCommand implements Serializable {
    private String observationId;
    private Long tenantId;
    private String metricId;
    private String metricVersion;
    private String dimensionHash;
    private BigDecimal value;
    private String unitCode;
    private String sourceQueryId;
    private String evidenceSha256;
    private Instant observedAt;
}
