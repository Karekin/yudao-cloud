package cn.iocoder.yudao.module.cloudmold.order.api.migration;

import lombok.Data;

import java.time.Instant;

@Data
public class LegacyTradeTargetReadinessCommand {
    private String idempotencyKey;
    private String sourceEventId;
    private String targetReadinessRunId;
    private String sourceMigrationRunId;
    private String policyVersion;
    private String evidenceRef;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
