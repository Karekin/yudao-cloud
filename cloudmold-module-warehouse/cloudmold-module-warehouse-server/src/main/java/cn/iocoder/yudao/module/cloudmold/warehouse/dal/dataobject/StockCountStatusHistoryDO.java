package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_stock_count_status_history")
public class StockCountStatusHistoryDO {
    private String historyId;
    private Long tenantId;
    private Long operationId;
    private String stockCountId;
    private String status;
    private Long statusVersion;
    private String stageCode;
    private String stageLabel;
    private String changedByPrincipalId;
    private String remark;
    private LocalDateTime changedAt;
    private LocalDateTime createdAt;
}
