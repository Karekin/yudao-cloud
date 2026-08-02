package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_inventory_stock_transfer_operation_v3")
public class InventoryStockTransferOperationDO {
    @TableId(type = IdType.AUTO)
    private Long operationId;
    private Long tenantId;
    private String idempotencyKey;
    private String sourceEventId;
    private String movementGroupId;
    private String operationType;
    private String requestHash;
    private String attemptToken;
    private Integer status;
    private Long ledgerTransactionId;
    private String sourceBalanceId;
    private Long sourceAggregateVersion;
    private BigDecimal sourceOnHandQuantity;
    private BigDecimal sourceInTransitQuantity;
    private String targetBalanceId;
    private Long targetAggregateVersion;
    private BigDecimal targetOnHandQuantity;
    private BigDecimal targetInTransitQuantity;
    private BigDecimal cumulativeDispatchedQuantity;
    private BigDecimal cumulativeReceivedQuantity;
    private BigDecimal outstandingQuantity;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
