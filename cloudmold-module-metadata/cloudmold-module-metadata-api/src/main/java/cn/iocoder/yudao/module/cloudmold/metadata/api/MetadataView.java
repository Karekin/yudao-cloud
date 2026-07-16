package cn.iocoder.yudao.module.cloudmold.metadata.api;

import lombok.*;
import lombok.experimental.Accessors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class MetadataView {
    private Long operationId;
    private Boolean duplicate;
    private String definitionId;
    private String definitionKind;
    private Long definitionVersion;
    private String definitionStatus;
    private String runId;
    private Integer attempt;
    private Long observationSequence;
    private String runStatus;
    private String resultId;
    private String resultStatus;
}
