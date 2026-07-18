package cn.iocoder.yudao.module.cloudmold.customerservice.api;

import lombok.*;
import lombok.experimental.Accessors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class CustomerServiceView {
    private Long operationId;
    private Boolean duplicate;
    private String ticketId;
    private String ticketNo;
    private String ticketStatus;
    private Long ticketVersion;
    private String assignedAgentPrincipalId;
    private String linkId;
    private String messageId;
    private String attachmentId;
    private String feedbackId;
    private String reviewId;
    private String claimId;
    private String claimCode;
    private String claimStatus;
    private Long claimVersion;
    private String compensationEntryId;
    private Long paidAmountMinor;
    private String currencyCode;
}
