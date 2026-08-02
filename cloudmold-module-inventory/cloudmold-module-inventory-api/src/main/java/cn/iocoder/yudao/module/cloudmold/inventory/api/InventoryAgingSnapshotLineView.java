package cn.iocoder.yudao.module.cloudmold.inventory.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAgingSnapshotLineView {
    private Long lineId;
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
    private BigDecimal availableQuantity;
    private Long balanceVersion;
    private LocalDate manufacturedOn;
    private LocalDate expiresOn;
    private String ageBasisType;
    private LocalDateTime ageBasisAt;
    private Integer ageDays;
    private String ageBucket;
    private Integer expiryDaysRemaining;
    private String expiryStatus;
    private String expiryBucket;
    private String riskClassification;
}
