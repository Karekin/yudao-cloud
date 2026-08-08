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
public class CrmContactView implements Serializable {
    private String contactId;
    private String customerId;
    private String contactName;
    private String roleTitle;
    private String contactChannelRef;
    private String maskedContact;
    private Boolean isPrimary;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
