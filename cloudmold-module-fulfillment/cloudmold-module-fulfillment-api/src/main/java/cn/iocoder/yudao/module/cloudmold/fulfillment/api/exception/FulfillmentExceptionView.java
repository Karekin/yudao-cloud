package cn.iocoder.yudao.module.cloudmold.fulfillment.api.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FulfillmentExceptionView {
    private Long operationId;
    private String exceptionId;
    private String exceptionNo;
    private String runId;
    private String fulfillmentId;
    private String orderId;
    private FulfillmentExceptionType exceptionType;
    private FulfillmentExceptionStatus status;
    private FulfillmentExceptionAction action;
    private String actionDescription;
    private String planEvidenceRef;
    private String approvalRef;
    private String resolutionEvidenceRef;
    private String reason;
    private String resolutionSummary;
    private Long aggregateVersion;
    private Boolean duplicate;
}
