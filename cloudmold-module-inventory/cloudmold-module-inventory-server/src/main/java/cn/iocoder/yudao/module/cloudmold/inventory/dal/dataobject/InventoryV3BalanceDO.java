package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_inventory_balance_v3")
public class InventoryV3BalanceDO {
    @TableId(type = IdType.INPUT)
    private String balanceId;
    private Long tenantId;
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
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
