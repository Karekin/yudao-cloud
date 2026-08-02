package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_supplier_return_dispatch_line")
public class SupplierReturnDispatchLineDO {
    private String executionLineId;
    private Long tenantId;
    private String batchId;
    private String returnId;
    private String returnLineId;
    private Integer lineNumber;
    private String sourceDisposition;
    private BigDecimal dispatchedQuantity;
    private BigDecimal cumulativeDispatchedQuantity;
    private BigDecimal outstandingQuantity;
    private Long inventoryOperationId;
    private Long ledgerTransactionId;
    private String sourceBalanceId;
    private Long inventoryAggregateVersion;
    private String status;
    private String remark;
    private Long version;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
