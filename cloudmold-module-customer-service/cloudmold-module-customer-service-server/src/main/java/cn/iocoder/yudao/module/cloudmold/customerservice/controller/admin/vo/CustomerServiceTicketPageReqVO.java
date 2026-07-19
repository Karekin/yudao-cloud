package cn.iocoder.yudao.module.cloudmold.customerservice.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 客服工单分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CustomerServiceTicketPageReqVO extends PageParam {

    @Schema(description = "工单 ID")
    private String ticketId;

    @Schema(description = "工单号")
    private String ticketNo;

    @Schema(description = "客户主体")
    private String customerPrincipalId;

    @Schema(description = "受理坐席主体")
    private String assignedAgentPrincipalId;

    @Schema(description = "渠道编码")
    private String channelCode;

    @Schema(description = "优先级")
    private String priority;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "业务分类码")
    private String categoryCode;

    @Schema(description = "创建时间起")
    private LocalDateTime createdAtFrom;

    @Schema(description = "创建时间止")
    private LocalDateTime createdAtTo;
}
