package cn.iocoder.yudao.module.cloudmold.quality.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityConsumerEvidenceView implements Serializable {
    private String canonicalSkuId;
    private String status;
    private String inspectionTaskId;
    private String inspectionStatus;
    private String decision;
    private String evidenceToken;
    private LocalDateTime inspectedAt;
    private LocalDateTime completedAt;
    private String standardId;
    private String standardCode;
    private Long standardVersion;
    private String standardVersionId;
    private Long aggregateVersion;
}
