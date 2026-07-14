package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.returning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_return_fulfillment_operation")
public class ReturnFulfillmentOperationDO {
    @TableId(type = IdType.AUTO)
    private Long operationId;
    private Long tenantId;
    private String idempotencyKey;
    private String commandType;
    private String requestHash;
    private String attemptToken;
    private Integer status;
    private String returnFulfillmentId;
    private String resultJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
