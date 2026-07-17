package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class LegacyTradeProductIdentityQualificationApprovalDO {
    private String approvalId;
    private Long tenantId;
    private String requestId;
    private String approvalRole;
    private Long approverId;
    private String scopeHash;
    private Long expectedRequestVersion;
    private String evidenceRef;
    private String idempotencyKey;
    private String requestHash;
    private String status;
    private Long version;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
}
