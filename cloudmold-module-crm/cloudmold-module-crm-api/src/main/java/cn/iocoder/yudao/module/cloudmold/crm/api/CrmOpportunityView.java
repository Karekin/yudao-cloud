package cn.iocoder.yudao.module.cloudmold.crm.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmOpportunityView implements Serializable {
    private String opportunityId;
    private String opportunityCode;
    private String customerId;
    private String opportunityName;
    private String stage;
    private Long expectedAmountMinor;
    private String currencyCode;
    private LocalDate expectedCloseDate;
    private String ownerPrincipalId;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
