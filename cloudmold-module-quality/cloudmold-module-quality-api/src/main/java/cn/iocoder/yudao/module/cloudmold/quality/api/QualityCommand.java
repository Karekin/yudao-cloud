package cn.iocoder.yudao.module.cloudmold.quality.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityCommand {
    private QualityOperation operation;
    private String idempotencyKey;
    private String runId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;
    private StandardDefinition standard;
    private CertificationDefinition certification;
    private InspectionTaskDefinition inspectionTask;
    private CapaDefinition capa;
    private RecallActionDefinition recallAction;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StandardDefinition {
        private String standardId;
        private String standardCode;
        private String categoryCode;
        private String brandCode;
        private String applicableSkuId;
        private String contentSha256;
        private Long expectedVersion;
        private String approverPrincipalId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CertificationDefinition {
        private String certificationId;
        private String authenticatorPrincipalId;
        private String standardId;
        private String certificationLevel;
        private LocalDate effectiveFrom;
        private LocalDate effectiveTo;
        private String evidenceSha256;
        private Long expectedVersion;
        private String reasonCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InspectionTaskDefinition {
        private String taskId;
        private String standardId;
        private String subjectType;
        private String subjectRef;
        private String canonicalSkuId;
        private String lotId;
        private String warehouseId;
        private String priority;
        private Long expectedVersion;
        private String authenticatorPrincipalId;
        private String decision;
        private String defectCode;
        private String evidenceRef;
        private String recheckReasonCode;
        private String secondaryAuthenticatorPrincipalId;
        private String adjudicatorPrincipalId;
        private String groundTruthDecision;
        private String groundTruthDefectCode;
        private String groundTruthEvidenceRef;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CapaDefinition {
        private String capaId;
        private String inspectionTaskId;
        private String rootCauseCode;
        private String ownerPrincipalId;
        private LocalDate dueDate;
        private Long expectedVersion;
        private String effectivenessEvidenceRef;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecallActionDefinition {
        private String recallActionId;
        private String inspectionTaskId;
        private String reasonCode;
        private String ownerPrincipalId;
        private Long expectedVersion;
        private String resolutionCode;
    }
}
