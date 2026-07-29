package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class TemporalAutomationCandidateView {
    private String candidateId;
    private String clientRequestKey;
    private String skillId;
    private String skillVersion;
    private String businessKey;
    private String status;
    private Instant dueAt;
    private boolean created;
}
