package cn.iocoder.yudao.module.cloudmold.customerservice.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_customer_service_quality_review")
@Data
@Accessors(chain = true)
public class CustomerServiceQualityReviewDO {
    @TableId(type = IdType.INPUT)
    private String reviewId;
    private Long tenantId;
    private String ticketId;
    private String runId;
    private String reviewerPrincipalId;
    private Integer scoreBasisPoints;
    private String outcomeCode;
    private String reasonCode;
    private LocalDateTime createdAt;
}
