package cn.iocoder.yudao.module.cloudmold.inventory.api;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
public class InventoryMigrationCandidateView {
    private String candidateId;
    private String migrationRunId;
    private String legacyBalanceId;
    private Long legacyBalanceVersion;
    private String legacySnapshotHash;
    private String sourceSystem;
    private String sourceType;
    private String sourceId;
    private String sourceClassification;
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
    private List<String> reasonCodes;
    private String verificationRef;
    private Instant assessedAt;
}
