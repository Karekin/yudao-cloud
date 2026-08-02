package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_inventory_procurement_receipt_operation_v3")
public class InventoryProcurementReceiptOperationDO {
    @TableId(type = IdType.AUTO)
    private Long operationId;
    private Long tenantId;
    private String idempotencyKey;
    private String sourceEventId;
    private String receiptId;
    private String receiptLineId;
    private Long decisionVersion;
    private String disposition;
    private String operationType;
    private String requestHash;
    private String attemptToken;
    private Integer status;
    private Long ledgerTransactionId;
    private String sourceBalanceId;
    private Long sourceAggregateVersion;
    private String targetBalanceId;
    private Long targetAggregateVersion;
    private BigDecimal receivedQuantity;
    private BigDecimal pendingQuantity;
    private BigDecimal acceptedQuantity;
    private BigDecimal rejectedQuantity;
    private BigDecimal quarantinedQuantity;
    private BigDecimal returnedQuantity;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
