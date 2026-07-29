package cn.iocoder.yudao.module.cloudmold.partnermarketing.api;

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
public class PartnerMarketingWorkflowView implements Serializable {

    public enum Status { PREPARE, WAITING, RUNNING, SUCCEEDED, FAILED }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Artifact implements Serializable {
        private String type;
        private String id;
        private String status;
        private Long version;
        private String label;
    }

    private String workflowType;
    private String workflowInstanceKey;
    private String businessKey;
    private String currentStatus;
    private Status status;
    private String phase;
    private Boolean terminal;
    private Boolean actionRequired;
    private String summary;
    private Long aggregateVersion;
    private List<String> blockers;
    private List<String> nextActions;
    private List<Artifact> artifacts;
}
