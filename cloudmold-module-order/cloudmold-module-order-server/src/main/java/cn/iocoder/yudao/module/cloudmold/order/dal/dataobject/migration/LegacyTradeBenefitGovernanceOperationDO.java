package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LegacyTradeBenefitGovernanceOperationDO {
    private Long operationId;
    private Long tenantId;
    private String idempotencyKey;
    private String sourceEventId;
    private String requestHash;
    private String attemptToken;
    private Integer status;
    private String governanceRunId;
    private String resultJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
