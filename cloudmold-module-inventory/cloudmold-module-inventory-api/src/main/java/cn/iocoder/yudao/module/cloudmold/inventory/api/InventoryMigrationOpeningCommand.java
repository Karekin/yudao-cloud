package cn.iocoder.yudao.module.cloudmold.inventory.api;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@Accessors(chain = true)
public class InventoryMigrationOpeningCommand {
    private String idempotencyKey;
    private String sourceEventId;
    private String qualificationId;
    private Long expectedVersion;
    private String expectedSourceSnapshotHash;
    private String evidenceRef;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
