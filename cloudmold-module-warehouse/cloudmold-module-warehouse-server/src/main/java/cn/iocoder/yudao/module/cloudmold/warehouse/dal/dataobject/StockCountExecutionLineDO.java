package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_stock_count_execution_line")
public class StockCountExecutionLineDO {
    private String executionLineId;
    private Long tenantId;
    private String batchId;
    private String stockCountId;
    private String stockCountLineId;
    private BigDecimal countedOnHandQuantity;
    private BigDecimal differenceQuantity;
    private String remark;
    private LocalDateTime createdAt;
}
