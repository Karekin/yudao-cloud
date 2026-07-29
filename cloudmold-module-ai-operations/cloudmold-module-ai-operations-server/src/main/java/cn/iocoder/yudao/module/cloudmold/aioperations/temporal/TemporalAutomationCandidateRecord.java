package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class TemporalAutomationCandidateRecord {
    private Long tenantId;
    private String candidateId;
    private String clientRequestKey;
    private String skillId;
    private String skillVersion;
    private String businessKey;
    private String inputJson;
    private LocalDateTime dueAt;
    private String status;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private String temporalWorkflowId;
    private LocalDateTime dispatchedAt;
    private String lastError;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
