package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_warehouse_procurement_putaway_line")
public class PutawayLineDO {
    private String putawayLineId;
    private Long tenantId;
    private String putawayId;
    private String receiptId;
    private String receiptLineId;
    private String warehouseId;
    private String sourceLocationId;
    private String targetLocationId;
    private String canonicalSkuId;
    private String ownerType;
    private String ownerId;
    private String lotId;
    private String baseUomCode;
    private BigDecimal putawayQuantity;
    private BigDecimal cumulativePutawayQuantity;
    private String status;
    private Long version;
    private Long inventoryOperationId;
    private Long inventoryLedgerTxId;
    private String inventoryMovementGroupId;
    private String inventoryTargetBalanceId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
