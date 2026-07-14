package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.cancellation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_order_cancellation_saga_fulfillment")
public class OrderCancellationSagaFulfillmentDO {
    @TableId(type = IdType.INPUT)
    private String sagaFulfillmentId;
    private Long tenantId;
    private String sagaId;
    private String fulfillmentId;
    private Long fulfillmentVersionAtRequest;
    private String statusAtRequest;
    private String requestIdempotencyKey;
    private String finalizeIdempotencyKey;
    private String status;
    private Integer attemptCount;
    private Long fulfillmentOperationId;
    private LocalDateTime occurredAt;
    private String lastErrorCode;
    private String lastErrorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime cancelledAt;
}
