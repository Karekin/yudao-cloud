package cn.iocoder.yudao.module.cloudmold.customerservice.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_customer_service_status_history")
@Data
@Accessors(chain = true)
public class CustomerServiceStatusHistoryDO {
    @TableId(type = IdType.AUTO)
    private Long historyId;
    private Long tenantId;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private String previousStatus;
    private String currentStatus;
    private Long operationId;
    private String reasonCode;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
