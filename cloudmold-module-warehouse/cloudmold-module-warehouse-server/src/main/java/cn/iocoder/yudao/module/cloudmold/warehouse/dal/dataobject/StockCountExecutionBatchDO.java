package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_stock_count_execution_batch")
public class StockCountExecutionBatchDO {
    private String batchId;
    private Long tenantId;
    private String stockCountId;
    private String batchNo;
    private String status;
    private Integer lineCount;
    private String countedByPrincipalId;
    private String remark;
    private Long version;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
