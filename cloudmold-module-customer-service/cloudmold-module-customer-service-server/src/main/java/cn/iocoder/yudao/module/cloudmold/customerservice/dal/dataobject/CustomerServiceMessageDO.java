package cn.iocoder.yudao.module.cloudmold.customerservice.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_customer_service_message")
@Data
@Accessors(chain = true)
public class CustomerServiceMessageDO {
    @TableId(type = IdType.INPUT)
    private String messageId;
    private Long tenantId;
    private String ticketId;
    private String runId;
    private String direction;
    private String senderType;
    private String senderPrincipalId;
    private String messageType;
    private String contentToken;
    private Integer attachmentCount;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
