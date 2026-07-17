package cn.iocoder.yudao.module.cloudmold.order.api.migration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

public interface LegacyTradeProductIdentityQualificationApi {

    QualificationRequestResult request(QualificationRequestCommand command, Long requesterId);

    QualificationRequestResult approve(String requestId, QualificationApprovalCommand command, Long approverId);

    QualificationRequestResult requireRequest(String requestId);

    @Data
    public static class QualificationRequestCommand {
        private String idempotencyKey;
        private String actionType;
        private String targetQualificationId;
        private String sourceMigrationRunId;
        private String itemEvidenceId;
        private Long historicalSpuId;
        private Long historicalSkuId;
        private String sourceItemEvidenceHash;
        private String historicalProductSnapshotHash;
        private String sourceEvidenceUri;
        private String qualificationRef;
        private String correlationId;
        private String causationId;
        private Instant occurredAt;
    }

    @Data
    public static class QualificationApprovalCommand {
        private String idempotencyKey;
        private String approvalRole;
        private Long expectedVersion;
        private String evidenceRef;
        private String correlationId;
        private String causationId;
        private Instant occurredAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualificationRequestResult {
        private String requestId;
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
        private String evidenceVerificationStatus;
        private String evidenceVerifierVersion;
        private Long evidenceContentLength;
        private Instant evidenceVerifiedAt;
        private String qualificationRef;
        private String scopeHash;
        private Long requesterId;
        private Integer approvalCount;
        private String status;
        private String qualificationId;
        private String qualificationStatus;
        private Long version;
        private List<QualificationApprovalResult> approvals;
        private boolean duplicate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualificationApprovalResult {
        private String approvalId;
        private String approvalRole;
        private Long approverId;
        private String evidenceRef;
        private Long expectedRequestVersion;
        private String status;
    }
}
