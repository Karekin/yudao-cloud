package cn.iocoder.yudao.module.cloudmold.catalog.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssortmentPlanningCommandResult implements Serializable {
    private Long operationId;
    private Boolean duplicate;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private String status;
    private String waveId;
    private String candidateId;
    private Integer candidateCount;
    private Integer evaluatedCandidateCount;
    private Integer selectedStyleCount;
    private List<String> selectedCandidateIds;
    private String decisionSummary;
}
