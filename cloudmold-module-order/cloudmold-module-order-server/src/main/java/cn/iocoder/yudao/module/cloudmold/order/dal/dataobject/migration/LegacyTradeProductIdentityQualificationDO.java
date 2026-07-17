package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class LegacyTradeProductIdentityQualificationDO {
    private String qualificationId;
    private Long tenantId;
    private String sourceMigrationRunId;
    private String itemEvidenceId;
    private Long legacyOrderItemId;
    private Long historicalSpuId;
    private Long historicalSkuId;
    private String sourceItemEvidenceHash;
    private String historicalProductSnapshotHash;
    private String sourceEvidenceUri;
    private String qualificationRef;
    private String requestId;
    private String approvalSetHash;
    private String revocationRequestId;
    private String revocationApprovalSetHash;
    private LocalDateTime revokedAt;
    private String qualifiedBy;
    private LocalDateTime qualifiedAt;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
