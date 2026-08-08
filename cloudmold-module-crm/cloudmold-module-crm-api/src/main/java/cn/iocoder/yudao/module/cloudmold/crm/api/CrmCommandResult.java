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
public class CrmCommandResult implements Serializable {
    private Long operationId;
    private boolean duplicate;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private String currentStatus;
    private String ownerPrincipalId;
    private String poolStatus;
    private LocalDateTime nextFollowUpAt;
}
