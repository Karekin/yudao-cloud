package cn.iocoder.yudao.module.cloudmold.risk.api;

import lombok.*;
import lombok.experimental.Accessors;

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
