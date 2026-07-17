package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class LegacyTradeProductIdentityQualificationRequestDO {
    private String requestId;
    private Long tenantId;
    private String idempotencyKey;
    private String requestHash;
    private String actionType;
    private String targetQualificationId;
    private String sourceMigrationRunId;
    private String itemEvidenceId;
    private Long legacyOrderItemId;
    private Long historicalSpuId;
    private Long historicalSkuId;
    private String sourceItemEvidenceHash;
    private String historicalProductSnapshotHash;
    private String sourceEvidenceUri;
    private String qualificationRef;
    private String scopeHash;
    private Long requesterId;
    private Integer approvalCount;
    private String status;
    private String qualificationId;
    private Long version;
    private LocalDateTime requestedAt;
    private LocalDateTime appliedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
