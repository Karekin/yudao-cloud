package cn.iocoder.yudao.module.cloudmold.tokenplatform.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_token_platform_operation")
@Data
@Accessors(chain = true)
public class TokenPlatformOperationDO {
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
