package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.cancellation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_order_cancellation_saga_item")
public class OrderCancellationSagaItemDO {
    @TableId(type = IdType.INPUT)
    private String sagaItemId;
    private Long tenantId;
    private String sagaId;
    private String orderItemId;
    private String reservationId;
    private String ownerId;
    private String canonicalSkuId;
    private String warehouseId;
    private String stockStatus;
    private String qualityStatus;
    private String uomCode;
    private BigDecimal quantity;
    private String releaseIdempotencyKey;
    private String status;
    private Integer attemptCount;
    private Long inventoryOperationId;
    private LocalDateTime occurredAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lastErrorCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lastErrorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime releasedAt;
}
