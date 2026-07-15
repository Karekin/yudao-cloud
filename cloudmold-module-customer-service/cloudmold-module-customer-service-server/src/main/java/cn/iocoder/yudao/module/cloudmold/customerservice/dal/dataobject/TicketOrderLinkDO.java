package cn.iocoder.yudao.module.cloudmold.customerservice.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_customer_service_ticket_order_link")
@Data
@Accessors(chain = true)
public class TicketOrderLinkDO {
    @TableId(type = IdType.INPUT)
    private String linkId;
    private Long tenantId;
    private String ticketId;
    private String referenceSourceSystem;
    private String referenceType;
    private String referenceId;
    private LocalDateTime createdAt;
}
