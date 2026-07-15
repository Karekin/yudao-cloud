package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("cloudmold_inventory_migration_qualification")
public class InventoryMigrationQualificationDO {
    @TableId
    private String qualificationId;
    private Long tenantId;
    private String migrationRunId;
    private String candidateId;
    private Long qualificationOperationId;
    private String sourceSystem;
    private String sourceType;
    private String sourceId;
    private String sourceSnapshotHash;
    private Long sourceVersion;
    private LocalDateTime sourceUpdatedAt;
    private BigDecimal sourceOnHandQuantity;
    private BigDecimal sourceReservedQuantity;
    private BigDecimal sourceInTransitQuantity;
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
    private String resolvedBlockerCodes;
    private String policyVersion;
    private String verificationRef;
    private String status;
    private Long openingOperationId;
    private String targetBalanceId;
    private Long ledgerTransactionId;
    private String bridgeId;
    private Long version;
    private LocalDateTime qualifiedAt;
    private LocalDateTime migratedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
