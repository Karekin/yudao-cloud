package cn.iocoder.yudao.module.cloudmold.fulfillment.api.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FulfillmentExceptionCommand {
    private FulfillmentExceptionOperation operation;
    private String idempotencyKey;
    private String runId;
    private String exceptionId;
    private String fulfillmentId;
    private String orderId;
    private FulfillmentExceptionType exceptionType;
    private Long expectedVersion;
    private FulfillmentExceptionAction action;
    private String actionDescription;
    private String evidenceRef;
    private String approvalRef;
    private String reason;
    private String resolutionSummary;
    private Instant occurredAt;
    private String correlationId;
    private String causationId;
}
