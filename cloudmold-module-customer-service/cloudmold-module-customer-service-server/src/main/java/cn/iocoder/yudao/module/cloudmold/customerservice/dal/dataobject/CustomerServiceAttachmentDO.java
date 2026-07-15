package cn.iocoder.yudao.module.cloudmold.customerservice.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_customer_service_attachment")
@Data
@Accessors(chain = true)
public class CustomerServiceAttachmentDO {
    @TableId(type = IdType.INPUT)
    private String attachmentId;
    private Long tenantId;
    private String ticketId;
    private String messageId;
    private String runId;
    private String mediaType;
    private String objectToken;
    private String contentSha256;
    private Long sizeBytes;
    private String malwareScanStatus;
    private LocalDateTime createdAt;
}
