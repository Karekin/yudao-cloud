package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InventoryMigrationPilotApprovalDO {
    private String approvalId;
    private Long tenantId;
    private String batchId;
    private String approvalRole;
    private Long approverId;
    private String scopeHash;
    private String policyHash;
    private String evidenceRef;
    private String idempotencyKey;
    private String requestHash;
    private String status;
    private Long version;
    private LocalDateTime approvedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
