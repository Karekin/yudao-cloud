package cn.iocoder.yudao.module.cloudmold.datacontract.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("cloudmold_event_outbox")
@Data
public class CloudmoldEventOutboxDO {

    @TableId(type = IdType.INPUT)
    private String eventId;
    private String eventType;
    private Integer schemaVersion;
    private String sourceSystem;
    private Long tenantId;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private Short eventSequence;
    private LocalDateTime occurredAt;
    private LocalDateTime recordedAt;
    private String traceId;
    private String correlationId;
    private String causationId;
    private String idempotencyKey;
    private String payload;
    private String headers;
    private String payloadHash;
    private String destination;
    private Integer status;
    private LocalDateTime availableAt;
    private Integer attemptCount;
    private Integer maxAttempts;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private LocalDateTime publishedAt;
    private String lastErrorCode;
    private String lastErrorSummary;

}
