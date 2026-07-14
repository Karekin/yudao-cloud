package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_tracking_event")
public class TrackingEventDO {
    @TableId(type = IdType.INPUT)
    private String trackingEventId;
    private Long tenantId;
    private String shipmentId;
    private String providerEventKey;
    private String trackingStatus;
    private String content;
    private LocalDateTime occurredAt;
    private LocalDateTime receivedAt;
    private LocalDateTime createdAt;
}
