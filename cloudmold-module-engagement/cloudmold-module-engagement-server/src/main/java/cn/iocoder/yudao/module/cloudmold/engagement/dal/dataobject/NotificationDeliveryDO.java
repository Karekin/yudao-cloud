package cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_engagement_notification_delivery")
public class NotificationDeliveryDO {
    @TableId(type = IdType.INPUT)
    private String deliveryId;
    private Long tenantId;
    private String deliveryKey;
    private String campaignId;
    private String principalId;
    private String channel;
    private String destinationToken;
    private String status;
    private Integer attemptCount;
    private Integer receiptCount;
    private Long version;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
