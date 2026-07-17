package cn.iocoder.yudao.module.cloudmold.order.api.migration;

import lombok.Data;

import java.time.Instant;

@Data
public class LegacyTradeBenefitGovernanceCommand {
    private String idempotencyKey;
    private String sourceEventId;
    private String governanceRunId;
    private String sourceMigrationRunId;
    private String policyVersion;
    private String evidenceRef;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
