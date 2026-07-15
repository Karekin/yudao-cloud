package cn.iocoder.yudao.module.cloudmold.inventory.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@Accessors(chain = true)
public class InventoryMigrationQualificationCommand {
    private String idempotencyKey;
    private String sourceEventId;
    private String migrationRunId;
    private String candidateId;
    private String expectedSourceSnapshotHash;
    private String warehouseId;
    private String locationId;
    private String lotTrackingPolicy;
    private String lotId;
    private String policyVersion;
    private String evidenceRef;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
