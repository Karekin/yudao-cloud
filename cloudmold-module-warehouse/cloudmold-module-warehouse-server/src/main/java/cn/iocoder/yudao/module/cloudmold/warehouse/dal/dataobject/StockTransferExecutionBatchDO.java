package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_stock_transfer_execution_batch")
public class StockTransferExecutionBatchDO {
    private String batchId;
    private Long tenantId;
    private String orderId;
    private String batchNo;
    private String batchType;
    private String status;
    private Long version;
    private String remark;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
