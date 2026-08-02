package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_stock_transfer_status_history")
public class StockTransferStatusHistoryDO {
    private String historyId;
    private Long tenantId;
    private Long operationId;
    private String businessObjectType;
    private String businessObjectId;
    private String status;
    private Long statusVersion;
    private String stageCode;
    private String stageLabel;
    private String remark;
    private LocalDateTime changedAt;
    private LocalDateTime createdAt;
}
