package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_inventory_stock_count_adjustment_v3")
public class InventoryStockCountAdjustmentDO {
    private String adjustmentId;
    private Long tenantId;
    private String stockCountId;
    private String stockCountLineId;
    private String ownerType;
    private String ownerId;
    private String canonicalSkuId;
    private String warehouseId;
    private String locationId;
    private String lotId;
    private String stockStatus;
    private String qualityStatus;
    private String baseUomCode;
    private String balanceId;
    private BigDecimal bookOnHandQuantity;
    private BigDecimal countedOnHandQuantity;
    private BigDecimal adjustmentQuantity;
    private Long ledgerTransactionId;
    private Long aggregateVersion;
    private String status;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
