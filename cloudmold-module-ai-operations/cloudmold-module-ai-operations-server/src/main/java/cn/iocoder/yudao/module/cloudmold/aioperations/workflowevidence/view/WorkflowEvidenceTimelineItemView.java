package cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.view;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Workflow evidence timeline item")
@Builder
public record WorkflowEvidenceTimelineItemView(
        String recordType,
        String recordId,
        String lineageId,
        String workflowId,
        String workflowVersion,
        String proposalId,
        String sourceType,
        String headline,
        String detailText,
        String modelSummary,
        String evidenceRef,
        String externalUrl,
        List<String> summarySourceRefs,
        List<String> corroboratingSourceTypes,
        JsonNode metrics,
        String severity,
        String dqcStatus,
        String status,
        boolean releaseEligible,
        LocalDateTime freshUntil,
        LocalDateTime windowStart,
        LocalDateTime windowEnd,
        LocalDateTime recordedAt,
        String actorSubject,
        String sourceClass,
        BigDecimal confidence,
        String region,
        String applicability,
        String contentHashSha256) {
}
