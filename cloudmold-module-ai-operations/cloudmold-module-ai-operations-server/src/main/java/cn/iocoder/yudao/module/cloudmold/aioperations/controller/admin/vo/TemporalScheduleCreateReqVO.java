package cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TemporalScheduleCreateReqVO {

    @NotBlank
    @Size(max = 96)
    private String scheduleId;
    @NotBlank
    @Size(max = 128)
    private String displayName;
    @NotBlank
    @Size(max = 500)
    private String description;
    @NotBlank
    @Size(max = 191)
    private String skillId;
    @NotBlank
    @Size(max = 64)
    private String skillVersion;
    @NotBlank
    private String inputJson;
    @Min(60)
    @Max(31_536_000)
    private long intervalSeconds = 3600;
    @Size(max = 64)
    private String timeZone = "Asia/Shanghai";
    @Size(max = 64)
    private String roleCode;
    @Size(max = 128)
    private String actionCode;
    private boolean paused;
}
