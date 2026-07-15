package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class InventoryMigrationShadowComparisonDO {
    private String comparisonId;
    private Long tenantId;
    private String windowId;
    private String roundId;
    private Integer roundNumber;
    private String pilotItemId;
    private Integer manifestOrdinal;
    private String itemScopeHash;
    private String canonicalGrainHash;
    private String sourceWatermarkHash;
    private String targetWatermarkHash;
    private String sourceId;
    private Long sourceVersion;
    private LocalDateTime sourceUpdatedAt;
    private String sourceSnapshotHash;
    private BigDecimal sourceOnHandQuantity;
    private BigDecimal sourceReservedQuantity;
    private BigDecimal sourceInTransitQuantity;
    private Integer activeReservationCount;
    private BigDecimal activeReservationQuantity;
    private String sourceEvidenceRef;
    private Boolean targetAvailable;
    private Long targetRecordVersion;
    private String targetCanonicalGrainHash;
    private BigDecimal targetOnHandQuantity;
    private BigDecimal targetReservedQuantity;
    private BigDecimal targetInTransitQuantity;
    private String targetProjectionHash;
    private String targetEvidenceRef;
    private Boolean comparable;
    private String comparisonResult;
    private String differenceFields;
    private String reasonCodes;
    private Long aggregateVersion;
    private LocalDateTime createdAt;
}
