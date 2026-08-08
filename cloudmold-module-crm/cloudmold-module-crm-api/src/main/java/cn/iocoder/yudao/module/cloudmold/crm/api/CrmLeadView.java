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
public class CrmLeadView implements Serializable {
    private String leadId;
    private String leadCode;
    private String leadName;
    private String sourceCode;
    private String status;
    private String ownerPrincipalId;
    private String contactChannelRef;
    private String maskedContact;
    private LocalDateTime nextFollowUpAt;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
