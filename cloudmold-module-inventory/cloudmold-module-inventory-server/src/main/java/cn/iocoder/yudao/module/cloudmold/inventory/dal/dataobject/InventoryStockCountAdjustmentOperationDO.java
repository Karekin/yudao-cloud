package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_inventory_stock_count_adjustment_operation")
public class InventoryStockCountAdjustmentOperationDO {
    @TableId(type = IdType.AUTO)
    private Long operationId;
    private Long tenantId;
    private String idempotencyKey;
    private String sourceEventId;
    private String stockCountLineId;
    private String requestHash;
    private String attemptToken;
    private Integer status;
    private String adjustmentId;
    private String resultJson;
    private Long ledgerTransactionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
