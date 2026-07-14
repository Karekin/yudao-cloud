package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_fulfillment_order")
public class FulfillmentOrderDO {
    @TableId(type = IdType.INPUT)
    private String fulfillmentId;
    private Long tenantId;
    private String fulfillmentNo;
    private String runId;
    private String orderId;
    private String orderNo;
    private String sellerId;
    private String warehouseId;
    private String status;
    private String cancellationSagaId;
    private String preCancellationStatus;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
