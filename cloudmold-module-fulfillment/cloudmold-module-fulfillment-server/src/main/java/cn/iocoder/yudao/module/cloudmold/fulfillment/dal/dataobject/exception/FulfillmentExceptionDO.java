package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.exception;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_fulfillment_exception")
public class FulfillmentExceptionDO {
    @TableId(type = IdType.INPUT)
    private String exceptionId;
    private Long tenantId;
    private String exceptionNo;
    private String runId;
    private String fulfillmentId;
    private String orderId;
    private String exceptionType;
    private String status;
    private String actionCode;
    private String actionDescription;
    private String planEvidenceRef;
    private String approvalRef;
    private String resolutionEvidenceRef;
    private String reason;
    private String resolutionSummary;
    private Long version;
    private LocalDateTime occurredAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
