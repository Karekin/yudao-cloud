package cn.iocoder.yudao.module.cloudmold.inventory.api;

import lombok.Data;

import java.time.Instant;

@Data
public class InventoryMigrationAssessmentCommand {
    private String idempotencyKey;
    private String sourceEventId;
    private String migrationRunId;
    private String sourceBalanceId;
    private String policyVersion;
    private String evidenceRef;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
