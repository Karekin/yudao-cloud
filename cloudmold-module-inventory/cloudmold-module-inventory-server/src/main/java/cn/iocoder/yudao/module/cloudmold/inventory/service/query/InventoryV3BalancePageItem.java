package cn.iocoder.yudao.module.cloudmold.inventory.service.query;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 规范 Inventory v3 余额分页项")
@Data
public class InventoryV3BalancePageItem {

    private String balanceId;
    private String canonicalSkuId;
    private String skuCode;
    private String spuCode;
    private String warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private String locationId;
    private String locationCode;
    private String locationName;
    private String lotId;
    private String lotCode;
    private String ownerType;
    private String ownerId;
    private String stockStatus;
    private String qualityStatus;
    private String baseUomCode;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal onHandQuantity;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal reservedQuantity;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal inTransitQuantity;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal availableQuantity;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal allocatableQuantity;
    private String allocationEligibility;
    private Long aggregateVersion;
    private LocalDateTime updatedAt;
}
