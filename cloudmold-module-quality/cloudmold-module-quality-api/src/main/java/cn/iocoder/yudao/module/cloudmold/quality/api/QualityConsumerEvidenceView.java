package cn.iocoder.yudao.module.cloudmold.quality.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityConsumerEvidenceView {
    private String canonicalSkuId;
    private String status;
    private String inspectionTaskId;
    private String decision;
    private String evidenceToken;
    private LocalDateTime inspectedAt;
    private Long aggregateVersion;
}
