package cn.iocoder.yudao.module.cloudmold.order.api.migration;

import lombok.Data;

import java.time.Instant;

@Data
public class LegacyTradeBenefitAssessmentCommand {
    private String idempotencyKey;
    private String sourceEventId;
    private String migrationRunId;
    private String policyVersion;
    private String evidenceRef;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
