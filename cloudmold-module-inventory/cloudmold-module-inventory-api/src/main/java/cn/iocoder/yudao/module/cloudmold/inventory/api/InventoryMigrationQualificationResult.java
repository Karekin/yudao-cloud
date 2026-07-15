package cn.iocoder.yudao.module.cloudmold.inventory.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryMigrationQualificationResult {
    private Long operationId;
    private String qualificationId;
    private String migrationRunId;
    private String candidateId;
    private String sourceSnapshotHash;
    private String status;
    private String ownerType;
    private String ownerId;
    private String canonicalSkuId;
    private String warehouseSourceMappingId;
    private String warehouseId;
    private String locationId;
    private String lotTrackingPolicy;
    private String lotId;
    private String stockStatus;
    private String qualityStatus;
    private String baseUomCode;
    private BigDecimal sourceOnHandQuantity;
    private List<String> resolvedBlockerCodes;
    private Long openingOperationId;
    private String targetBalanceId;
    private Long ledgerTransactionId;
    private String bridgeId;
    private Long version;
    private Instant qualifiedAt;
    private Instant migratedAt;
    private boolean duplicate;
}
