package cn.iocoder.yudao.module.cloudmold.crm.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmCustomerView implements Serializable {
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
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
