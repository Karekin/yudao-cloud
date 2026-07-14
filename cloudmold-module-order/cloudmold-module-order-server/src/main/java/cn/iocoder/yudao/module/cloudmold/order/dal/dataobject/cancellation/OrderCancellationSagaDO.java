package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.cancellation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_order_cancellation_saga")
public class OrderCancellationSagaDO {
    @TableId(type = IdType.INPUT)
    private String sagaId;
    private String cancellationMode;
    private Long tenantId;
    private String idempotencyKey;
    private String requestHash;
    private String runId;
    private String orderId;
    private String orderNo;
    private String orderStatusAtRequest;
    private Long orderVersionAtRequest;
    private String status;
    private String activeStep;
    private Integer expectedReservationCount;
    private Integer releasedReservationCount;
    private String paymentId;
    private Long paymentVersionAtRequest;
    private Long paymentRefundTransactionId;
    private String paymentStatus;
    private Integer expectedFulfillmentCount;
    private Integer cancelledFulfillmentCount;
    private Integer attemptCount;
    private Integer maxAttempts;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime nextRetryAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String leaseOwner;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime leaseUntil;
    private Long version;
    private String reason;
    private String correlationId;
    private String causationId;
    private LocalDateTime occurredAt;
    private LocalDateTime finalizeOccurredAt;
    private LocalDateTime paymentRefundOccurredAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lastErrorCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lastErrorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime completedAt;
}
