package cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TemporalAutomationCandidateCreateReqVO {

    @NotBlank
    @Size(max = 191)
    private String clientRequestKey;

    @NotBlank
    @Size(max = 191)
    private String skillId;

    @NotBlank
    @Size(max = 64)
    private String skillVersion;

    @NotBlank
    @Size(max = 191)
    private String businessKey;

    @NotBlank
    private String inputJson;

    private LocalDateTime dueAt;
}
