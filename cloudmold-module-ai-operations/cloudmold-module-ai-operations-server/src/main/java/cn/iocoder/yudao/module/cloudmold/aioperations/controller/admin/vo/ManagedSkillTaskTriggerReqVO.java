package cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ManagedSkillTaskTriggerReqVO {

    @NotBlank
    private String skillId;
    @NotBlank
    private String skillVersion;
    private String runId;
    @NotBlank
    private String clientRequestKey;
    @NotBlank
    private String inputJson;
    @Valid
    private ApprovalContext approval;

    @Data
    public static class ApprovalContext {
        @NotBlank
        private String workOrderId;
        @NotBlank
        private String approvalId;
        private Long workOrderExpectedVersion;
        private Long validForSeconds;
        private String missionRunId;
        private String leaseOwner;
        private String leaseToken;
        private Long fencingToken;
    }
}
