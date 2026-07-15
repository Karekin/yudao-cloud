package cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_promotion_operation")
public class PromotionOperationDO {
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
