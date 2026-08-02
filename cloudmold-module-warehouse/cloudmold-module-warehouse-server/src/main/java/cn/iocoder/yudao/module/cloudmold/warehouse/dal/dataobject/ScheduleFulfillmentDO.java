package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_warehouse_procurement_schedule_fulfillment")
public class ScheduleFulfillmentDO {
    private String scheduleFulfillmentId;
    private Long tenantId;
    private String procurementOrderId;
    private String procurementOrderItemId;
    private String deliveryScheduleId;
    private BigDecimal orderedQuantity;
    private BigDecimal cancelledQuantity;
    private BigDecimal allowedOverReceiptQuantity;
    private BigDecimal receivedQuantity;
    private BigDecimal pendingQualityQuantity;
    private BigDecimal acceptedQuantity;
    private BigDecimal rejectedQuantity;
    private BigDecimal quarantinedQuantity;
    private BigDecimal returnedQuantity;
    private String tolerancePolicyVersion;
    private String tolerancePolicyHash;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
