package cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_engagement_notification_receipt")
public class NotificationDeliveryReceiptDO {
    @TableId(type = IdType.INPUT)
    private String receiptId;
    private Long tenantId;
    private String deliveryId;
    private String externalReceiptId;
    private String receiptStatus;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
