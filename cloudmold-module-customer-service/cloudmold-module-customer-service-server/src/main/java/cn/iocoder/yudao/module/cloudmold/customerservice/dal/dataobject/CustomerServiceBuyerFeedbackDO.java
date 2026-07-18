package cn.iocoder.yudao.module.cloudmold.customerservice.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("cloudmold_customer_service_buyer_feedback")
public class CustomerServiceBuyerFeedbackDO {

    private String feedbackId;
    private Long tenantId;
    private String ticketId;
    private String runId;
    private String customerPrincipalId;
    private String touchpointCode;
    private String sentimentCode;
    private Integer scoreBasisPoints;
    private String reasonCode;
    private String commentToken;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
