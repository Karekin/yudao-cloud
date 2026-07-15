package cn.iocoder.yudao.module.cloudmold.customerservice.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_customer_service_compensation_entry")
@Data
@Accessors(chain = true)
public class CustomerServiceCompensationEntryDO {
    @TableId(type = IdType.INPUT)
    private String compensationEntryId;
    private Long tenantId;
    private String claimId;
    private String ticketId;
    private String entryType;
    private Long amountMinor;
    private String currencyCode;
    private String operationIdempotencyKey;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
