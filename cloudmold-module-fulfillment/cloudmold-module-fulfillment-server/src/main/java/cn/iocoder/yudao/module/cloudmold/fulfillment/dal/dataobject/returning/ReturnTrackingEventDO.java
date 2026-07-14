package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.returning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_return_tracking_event")
public class ReturnTrackingEventDO {
    @TableId(type = IdType.AUTO)
    private Long trackingEventId;
    private Long tenantId;
    private String returnFulfillmentId;
    private String returnShipmentId;
    private String eventType;
    private String operatorId;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
