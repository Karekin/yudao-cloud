package cn.iocoder.yudao.module.cloudmold.risk.api;

import lombok.*;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class RiskView {
    private Long operationId;
    private Boolean duplicate;
    private String policyId;
    private Long policyVersion;
    private String policyStatus;
    private String taxonomyId;
    private Long taxonomyVersion;
    private String taxonomyStatus;
    private String taxonomyVersionId;
    private Long taxonomyDefinitionVersion;
    private String eventCode;
    private List<String> intelligenceLevels;
    /** ISO-8601 text is used so an idempotent result snapshot cannot reinterpret epoch milliseconds as seconds. */
    private String retiredAt;
    private String signalId;
    private String relationId;
    private String clusterId;
    private Long clusterVersion;
    private String clusterStatus;
    private Integer memberCount;
    private Integer edgeCount;
    private String caseId;
    private Long caseVersion;
    private String reviewStatus;
    private String decisionId;
    private String feedbackId;
}
