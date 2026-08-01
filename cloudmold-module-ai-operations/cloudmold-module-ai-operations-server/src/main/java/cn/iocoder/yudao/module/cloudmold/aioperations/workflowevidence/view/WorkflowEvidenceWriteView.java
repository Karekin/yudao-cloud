package cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.view;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Workflow evidence write result")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowEvidenceWriteView {

    private String aggregateType;
    private String aggregateId;
    private String lineageId;
    private String workflowId;
    private String workflowVersion;
    private String proposalId;
    private List<String> externalSnapshotIds;
    private String actorSubject;
    private LocalDateTime storedAt;
    private boolean duplicate;
}
