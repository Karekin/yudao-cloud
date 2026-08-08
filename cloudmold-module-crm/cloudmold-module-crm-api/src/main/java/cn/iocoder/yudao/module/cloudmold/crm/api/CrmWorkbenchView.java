package cn.iocoder.yudao.module.cloudmold.crm.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmWorkbenchView implements Serializable {
    private String ownerPrincipalId;
    private long ownedCustomerCount;
    private long poolCustomerCount;
    private long ownedLeadCount;
    private long openOpportunityCount;
    private long dueFollowUpCount;
    private long overdueFollowUpCount;
    private List<CrmFollowUpView> upcomingFollowUps;
}
