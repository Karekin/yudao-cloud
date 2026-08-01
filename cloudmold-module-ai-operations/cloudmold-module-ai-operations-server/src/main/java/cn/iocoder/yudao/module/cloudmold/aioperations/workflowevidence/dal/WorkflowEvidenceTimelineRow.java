package cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class WorkflowEvidenceTimelineRow {
    private String recordType;
    private String recordId;
    private String lineageId;
    private String workflowId;
    private String workflowVersion;
    private String proposalId;
    private String sourceType;
    private String headline;
    private String detailText;
    private String modelSummary;
    private String evidenceRef;
    private String externalUrl;
    private String summarySourceRefsJson;
    private String corroboratingSourceTypesJson;
    private String metricsJson;
    private String severity;
    private String dqcStatus;
    private String recordStatus;
    private Boolean releaseEligible;
    private LocalDateTime freshUntil;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private LocalDateTime recordedAt;
    private String actorSubject;
    private String sourceClass;
    private BigDecimal confidence;
    private String region;
    private String applicability;
    private String contentHashSha256;
}
