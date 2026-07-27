package cn.iocoder.yudao.module.cloudmold.customerservice.api.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResolutionWorkflowResult {
    public enum Status { PREPARE, WAITING, RUNNING, SUCCEEDED, FAILED }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Artifact {
        private String type;
        private String id;
        private String status;
        private Long version;
        private String label;
    }

    private String workflowType;
    private String workflowInstanceKey;
    private String businessKey;
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
