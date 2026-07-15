package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryV3AvailabilityDO {
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
    private Long aggregateVersion;
}
