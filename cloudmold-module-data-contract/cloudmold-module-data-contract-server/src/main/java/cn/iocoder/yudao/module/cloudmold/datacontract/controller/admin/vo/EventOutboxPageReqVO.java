package cn.iocoder.yudao.module.cloudmold.datacontract.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 事件外发分页 Request")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EventOutboxPageReqVO extends PageParam {

    @Schema(description = "事件 ID")
    private String eventId;

    @Schema(description = "事件类型")
    private String eventType;

    @Schema(description = "状态 0=待处理/10=已认领/20=已发布/30=死信")
    private Integer status;

    @Schema(description = "聚合类型")
    private String aggregateType;

    @Schema(description = "聚合 ID")
    private String aggregateId;

    @Schema(description = "记录时间起")
    private LocalDateTime recordedAtFrom;

    @Schema(description = "记录时间止")
    private LocalDateTime recordedAtTo;
}
