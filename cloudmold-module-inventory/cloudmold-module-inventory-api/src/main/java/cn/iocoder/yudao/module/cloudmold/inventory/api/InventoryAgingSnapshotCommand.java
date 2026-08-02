package cn.iocoder.yudao.module.cloudmold.inventory.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAgingSnapshotCommand {
    private String idempotencyKey;
    private String sourceEventId;
    private String correlationId;
    private Instant occurredAt;
    private String ownerType;
    private String ownerId;
    private String warehouseId;
    private String bucketPolicyCode;
    private String bucketPolicyVersion;
    private Integer ageFreshMaxDays;
    private Integer ageAgingMaxDays;
    private Integer ageStaleMaxDays;
    private Integer expiryWarningMaxDays;
    private Integer expiryCriticalMaxDays;
}
