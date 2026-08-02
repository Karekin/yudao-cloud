package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_stock_transfer_execution_line")
public class StockTransferExecutionLineDO {
    private String executionLineId;
    private Long tenantId;
    private String batchId;
    private String orderId;
    private String orderLineId;
    private Integer lineNumber;
    private String outboundExecutionLineId;
    private String canonicalSkuId;
    private String movementGroupId;
    private BigDecimal executedQuantity;
    private BigDecimal receivedQuantity;
    private BigDecimal cumulativeDispatchedQuantity;
    private BigDecimal cumulativeReceivedQuantity;
    private BigDecimal outstandingQuantity;
    private String status;
    private Long version;
    private String lotId;
    private String sourceLocationId;
    private String sourceStockStatus;
    private String sourceQualityStatus;
    private String targetLocationId;
    private String targetStockStatus;
    private String targetQualityStatus;
    private Long dispatchLedgerTransactionId;
    private Long receiveLedgerTransactionId;
    private String remark;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
