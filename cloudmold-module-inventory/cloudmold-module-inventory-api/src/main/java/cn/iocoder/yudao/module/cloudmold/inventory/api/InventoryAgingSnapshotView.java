package cn.iocoder.yudao.module.cloudmold.inventory.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAgingSnapshotView {
    private String snapshotId;
    private String snapshotCode;
    private String status;
    private Long snapshotVersion;
    private String ownerType;
    private String ownerId;
    private String warehouseId;
    private String bucketPolicyCode;
    private String bucketPolicyVersion;
    private String bucketPolicyHash;
    private Integer ageFreshMaxDays;
    private Integer ageAgingMaxDays;
    private Integer ageStaleMaxDays;
    private Integer expiryWarningMaxDays;
    private Integer expiryCriticalMaxDays;
    private String ledgerWatermarkRef;
    private LocalDateTime ledgerWatermarkOccurredAt;
    private LocalDate snapshotDate;
    private Integer lineCount;
    private Integer unknownAgeCount;
    private Integer unknownExpiryCount;
    private LocalDateTime createdAt;
    private List<InventoryAgingSnapshotLineView> lines;
}
