package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_inventory_stock_transfer_v3")
public class InventoryStockTransferDO {
    @TableId(type = IdType.INPUT)
    private String movementGroupId;
    private Long tenantId;
    private String ownerType;
    private String ownerId;
    private String canonicalSkuId;
    private String sourceWarehouseId;
    private String sourceLocationId;
    private String targetWarehouseId;
    private String targetLocationId;
    private String lotId;
    private String stockStatus;
    private String qualityStatus;
    private String baseUomCode;
    private BigDecimal dispatchedQuantity;
    private BigDecimal receivedQuantity;
    private Long version;
    private Long createdOperationId;
    private Long lastOperationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
