package cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_engagement_notification_attempt")
public class NotificationDeliveryAttemptDO {
    @TableId(type = IdType.INPUT)
    private String attemptId;
    private Long tenantId;
    private String deliveryId;
    private Integer attemptNo;
    private String providerCode;
    private String providerReference;
    private String outcome;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
