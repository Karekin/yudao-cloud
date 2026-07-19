package cn.iocoder.yudao.module.cloudmold.datacontract.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 事件外发分页项（不含 payload/headers/error 大字段）")
@Data
public class EventOutboxPageItem {

    private String eventId;
    private String eventType;
    private Integer schemaVersion;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private Integer status;
    private String destination;
    private Integer attemptCount;
    private LocalDateTime occurredAt;
    private LocalDateTime recordedAt;
    private LocalDateTime publishedAt;
}
