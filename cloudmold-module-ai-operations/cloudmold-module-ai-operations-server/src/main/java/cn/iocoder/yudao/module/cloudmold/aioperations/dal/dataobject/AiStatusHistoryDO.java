package cn.iocoder.yudao.module.cloudmold.aioperations.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_ai_ops_status_history")
public class AiStatusHistoryDO {
    @TableId(type = IdType.INPUT)
    private String historyId;
    private Long tenantId;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private Long operationId;
    private String operationType;
    private String previousStatus;
    private String currentStatus;
    private String errorCode;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
