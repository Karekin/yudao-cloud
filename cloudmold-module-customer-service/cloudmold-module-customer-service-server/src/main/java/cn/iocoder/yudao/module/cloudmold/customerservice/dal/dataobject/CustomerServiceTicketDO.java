package cn.iocoder.yudao.module.cloudmold.customerservice.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_customer_service_ticket")
@Data
@Accessors(chain = true)
public class CustomerServiceTicketDO {
    @TableId(type = IdType.INPUT)
    private String ticketId;
    private Long tenantId;
    private String ticketNo;
    private String runId;
    private String customerPrincipalId;
    private String channelCode;
    private String priority;
    private String categoryCode;
    private String slaPolicyCode;
    private Integer slaPolicyVersion;
    private LocalDateTime resolutionDeadlineAt;
    private Integer fcrWindowHours;
    private String assignedAgentPrincipalId;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
