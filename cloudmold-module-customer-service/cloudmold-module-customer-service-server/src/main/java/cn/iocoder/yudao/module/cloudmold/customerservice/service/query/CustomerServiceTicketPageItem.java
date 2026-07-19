package cn.iocoder.yudao.module.cloudmold.customerservice.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 客服工单行")
@Data
public class CustomerServiceTicketPageItem {

    private String ticketId;
    private String ticketNo;
    private String customerPrincipalId;
    private String channelCode;
    private String priority;
    private String categoryCode;
    private String assignedAgentPrincipalId;
    private String status;
    private LocalDateTime resolutionDeadlineAt;
    private Long aggregateVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
