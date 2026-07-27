package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * Tenant-scoped separation of duties for approval-bound Agent runs.
 */
@Data
@Accessors(chain = true)
public class TemporalApprovalPolicyRecord {

    private Long tenantId;
    private Long requesterUserId;
    private Long approverUserId;
    private Long governanceUserId;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
