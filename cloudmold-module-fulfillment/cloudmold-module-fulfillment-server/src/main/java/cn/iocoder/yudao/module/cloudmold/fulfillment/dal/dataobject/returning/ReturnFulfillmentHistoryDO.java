package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.returning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_return_fulfillment_history")
public class ReturnFulfillmentHistoryDO {
    @TableId(type = IdType.AUTO)
    private Long historyId;
    private Long tenantId;
    private String returnFulfillmentId;
    private Long aggregateVersion;
    private String previousStatus;
    private String currentStatus;
    private Long operationId;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
