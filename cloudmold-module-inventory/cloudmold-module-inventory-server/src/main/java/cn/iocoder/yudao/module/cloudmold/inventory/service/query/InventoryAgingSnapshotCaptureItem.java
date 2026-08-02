package cn.iocoder.yudao.module.cloudmold.inventory.service.query;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class InventoryAgingSnapshotCaptureItem {
    private String balanceId;
    private String ownerType;
    private String ownerId;
    private String canonicalSkuId;
    private String warehouseId;
    private String locationId;
    private String lotId;
    private String lotCode;
    private String stockStatus;
    private String qualityStatus;
    private String baseUomCode;
    private BigDecimal onHandQuantity;
    private BigDecimal reservedQuantity;
    private BigDecimal inTransitQuantity;
    private Long balanceVersion;
    private LocalDate manufacturedOn;
    private LocalDate expiresOn;
    private LocalDateTime lotReceivedAt;
    private LocalDateTime firstLedgerEntryAt;
}
