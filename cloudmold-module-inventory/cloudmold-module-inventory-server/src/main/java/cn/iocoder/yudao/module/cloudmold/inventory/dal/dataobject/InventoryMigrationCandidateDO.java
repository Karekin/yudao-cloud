package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InventoryMigrationCandidateDO {
    private String candidateId;
    private Long tenantId;
    private String migrationRunId;
    private String legacyBalanceId;
    private Long legacyBalanceVersion;
    private String legacySnapshotHash;
    private String sourceSystem;
    private String sourceType;
    private String sourceId;
    private String sourceClassification;
    private LocalDateTime sourceUpdatedAt;
    private String legacyOwnerId;
    private String legacyCanonicalSkuId;
    private String legacyWarehouseId;
    private String stockStatus;
    private String qualityStatus;
    private String baseUomCode;
    private BigDecimal sourceOnHandQuantity;
    private BigDecimal sourceReservedQuantity;
    private BigDecimal sourceInTransitQuantity;
    private String initialBusinessType;
    private String initialSourceEventId;
    private Integer activeReservationCount;
    private BigDecimal activeReservationQuantity;
    private String lotTrackingPolicy;
    private String decisionStatus;
    private String reasonCodes;
    private String verificationRef;
    private Long version;
    private LocalDateTime assessedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
