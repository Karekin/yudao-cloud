package cn.iocoder.yudao.module.cloudmold.quality.dal.dataobject;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class QualityRecords {
    private QualityRecords() {
    }

    @Data
    @Accessors(chain = true)
    public static class Operation {
        private Long operationId;
        private Long tenantId;
        private String idempotencyKey;
        private String commandType;
        private String requestHash;
        private String attemptToken;
        private Integer status;
        private String aggregateType;
        private String aggregateId;
        private String resultJson;
    }

    @Data
    @Accessors(chain = true)
    public static class Standard {
        private String standardId;
        private Long tenantId;
        private String standardCode;
        private String categoryCode;
        private String brandCode;
        private String applicableSkuId;
        private String draftContentSha256;
        private String status;
        private Long currentVersion;
        private Long aggregateVersion;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class StandardVersion {
        private String standardVersionId;
        private Long tenantId;
        private String standardId;
        private Long standardVersion;
        private String contentSha256;
        private String approverPrincipalId;
        private LocalDateTime effectiveAt;
        private LocalDateTime createdAt;
    }

    @Data
    @Accessors(chain = true)
    public static class Certification {
        private String certificationId;
        private Long tenantId;
        private String authenticatorPrincipalId;
        private String standardId;
        private String certificationLevel;
        private LocalDate effectiveFrom;
        private LocalDate effectiveTo;
        private String evidenceSha256;
        private String status;
        private Long version;
        private LocalDateTime revokedAt;
        private String revokeReasonCode;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class InspectionTask {
        private String taskId;
        private Long tenantId;
        private String standardId;
        private Long standardVersion;
        private String standardVersionId;
        private String subjectType;
        private String subjectRef;
        private String canonicalSkuId;
        private String lotId;
        private String warehouseId;
        private String priority;
        private String status;
        private String authenticatorPrincipalId;
        private String decision;
        private String defectCode;
        private String evidenceRef;
        private String recheckReasonCode;
        private String secondaryAuthenticatorPrincipalId;
        private String secondaryDecision;
        private String secondaryDefectCode;
        private String secondaryEvidenceRef;
        private String adjudicatorPrincipalId;
        private String groundTruthDecision;
        private String groundTruthDefectCode;
        private String groundTruthEvidenceRef;
        private Long version;
        private LocalDateTime assignedAt;
        private LocalDateTime startedAt;
        private LocalDateTime decidedAt;
        private LocalDateTime recheckedAt;
        private LocalDateTime adjudicatedAt;
        private LocalDateTime completedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class TaskHistory {
        private Long tenantId;
        private String taskId;
        private Long taskVersion;
        private String previousStatus;
        private String currentStatus;
        private String actorPrincipalId;
        private String reasonCode;
        private Long operationId;
        private LocalDateTime occurredAt;
        private LocalDateTime createdAt;
    }

    @Data
    @Accessors(chain = true)
    public static class Capa {
        private String capaId;
        private Long tenantId;
        private String inspectionTaskId;
        private String rootCauseCode;
        private String ownerPrincipalId;
        private LocalDate dueDate;
        private String status;
        private String effectivenessEvidenceRef;
        private Long version;
        private LocalDateTime openedAt;
        private LocalDateTime resolvedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Accessors(chain = true)
    public static class RecallAction {
        private String recallActionId;
        private Long tenantId;
        private String inspectionTaskId;
        private String canonicalSkuId;
        private String lotId;
        private String warehouseId;
        private String reasonCode;
        private String status;
        private String ownerPrincipalId;
        private String resolutionCode;
        private Long version;
        private LocalDateTime openedAt;
        private LocalDateTime acknowledgedAt;
        private LocalDateTime resolvedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
