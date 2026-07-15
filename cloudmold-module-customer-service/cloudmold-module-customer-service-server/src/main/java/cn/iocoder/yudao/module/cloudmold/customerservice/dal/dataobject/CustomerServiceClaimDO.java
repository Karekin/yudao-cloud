package cn.iocoder.yudao.module.cloudmold.customerservice.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_customer_service_claim")
@Data
@Accessors(chain = true)
public class CustomerServiceClaimDO {
    @TableId(type = IdType.INPUT)
    private String claimId;
    private Long tenantId;
    private String claimCode;
    private String ticketId;
    private String runId;
    private String claimType;
    private String orderRef;
    private String afterSaleRef;
    private String status;
    private Long requestedAmountMinor;
    private Long approvedAmountMinor;
    private Long paidAmountMinor;
    private String currencyCode;
    private String reasonCode;
    private String compensationEntryId;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
