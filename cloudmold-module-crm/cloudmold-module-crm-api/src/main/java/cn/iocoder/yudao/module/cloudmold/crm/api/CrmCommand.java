package cn.iocoder.yudao.module.cloudmold.crm.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmCommand implements Serializable {
    private CrmOperation operation;
    private String idempotencyKey;
    private String runId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    private CustomerDefinition customer;
    private LeadDefinition lead;
    private ContactDefinition contact;
    private OpportunityDefinition opportunity;
    private FollowUpDefinition followUp;
    private String reasonCode;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerDefinition implements Serializable {
        private String customerId;
        private String customerCode;
        private String customerName;
        private String levelCode;
        private String lifecycleStatus;
        private String poolStatus;
        private String ownerPrincipalId;
        private String sourceCode;
        private String industryCode;
        private String regionCode;
        private LocalDateTime nextFollowUpAt;
        private Long expectedVersion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeadDefinition implements Serializable {
        private String leadId;
        private String leadCode;
        private String leadName;
        private String sourceCode;
        private String status;
        private String ownerPrincipalId;
        private String contactChannelRef;
        private String maskedContact;
        private LocalDateTime nextFollowUpAt;
        private Long expectedVersion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContactDefinition implements Serializable {
        private String contactId;
        private String customerId;
        private String contactName;
        private String roleTitle;
        private String contactChannelRef;
        private String maskedContact;
        private Boolean isPrimary;
        private String status;
        private Long expectedVersion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpportunityDefinition implements Serializable {
        private String opportunityId;
        private String opportunityCode;
        private String customerId;
        private String opportunityName;
        private String stage;
        private Long expectedAmountMinor;
        private String currencyCode;
        private LocalDate expectedCloseDate;
        private String ownerPrincipalId;
        private Long expectedVersion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FollowUpDefinition implements Serializable {
        private String followUpId;
        private String subjectType;
        private String subjectId;
        private String methodCode;
        private String summary;
        private LocalDateTime nextFollowUpAt;
    }
}
