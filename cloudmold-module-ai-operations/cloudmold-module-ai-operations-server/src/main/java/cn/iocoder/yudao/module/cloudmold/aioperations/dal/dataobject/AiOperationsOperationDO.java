package cn.iocoder.yudao.module.cloudmold.aioperations.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_ai_ops_operation")
public class AiOperationsOperationDO {
    @TableId(type = IdType.AUTO)
    private Long operationId;
    private Long tenantId;
    private String idempotencyKey;
    private String operationType;
    private String requestHash;
    private String attemptToken;
    private Integer status;
    private String aggregateType;
    private String aggregateId;
    private String resultJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
