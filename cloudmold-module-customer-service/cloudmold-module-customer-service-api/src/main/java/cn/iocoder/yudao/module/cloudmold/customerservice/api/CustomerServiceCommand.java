package cn.iocoder.yudao.module.cloudmold.customerservice.api;

import lombok.*;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class CustomerServiceCommand {
    private CustomerServiceOperation operation;
    private String idempotencyKey;
    private String runId;
    private String ticketId;
    private String ticketNo;
    private Long expectedVersion;
    private String customerPrincipalId;
    private String channelCode;
    private String priority;
    private String categoryCode;
    private String slaPolicyCode;
    private Integer slaPolicyVersion;
    private Instant resolutionDeadlineAt;
    private Integer fcrWindowHours;
    private String assignedAgentPrincipalId;
    private String referenceSourceSystem;
    private String referenceType;
    private String referenceId;
    private String direction;
    private String senderType;
    private String senderPrincipalId;
    private String messageType;
    private String contentToken;
    private String messageId;
    private String mediaType;
    private String objectToken;
    private String contentSha256;
    private Long sizeBytes;
    private String malwareScanStatus;
    private String touchpointCode;
    private String sentimentCode;
    private String commentToken;
    private String reviewerPrincipalId;
    private Integer scoreBasisPoints;
    private String outcomeCode;
    private String reasonCode;
    private String claimId;
    private String claimCode;
    private String claimType;
    private Long requestedAmountMinor;
    private Long approvedAmountMinor;
    private String currencyCode;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
}
