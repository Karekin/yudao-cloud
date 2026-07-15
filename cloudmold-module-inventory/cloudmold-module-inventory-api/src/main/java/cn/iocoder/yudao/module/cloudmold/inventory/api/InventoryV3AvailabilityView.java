package cn.iocoder.yudao.module.cloudmold.inventory.api;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class InventoryV3AvailabilityView {
    private String balanceId;
    private String ownerType;
    private String ownerId;
    private String canonicalSkuId;
    private String warehouseId;
    private String locationId;
    private String lotId;
    private String stockStatus;
    private String qualityStatus;
    private String baseUomCode;
    private BigDecimal onHandQuantity;
    private BigDecimal reservedQuantity;
    private BigDecimal inTransitQuantity;
    private BigDecimal unreservedQuantity;
    private BigDecimal allocatableQuantity;
    private String allocationEligibility;
    private Long aggregateVersion;
    private Instant eligibilityAt;
}
