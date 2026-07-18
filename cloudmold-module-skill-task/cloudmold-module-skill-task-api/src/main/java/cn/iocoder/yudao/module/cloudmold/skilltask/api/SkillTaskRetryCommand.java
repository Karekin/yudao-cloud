package cn.iocoder.yudao.module.cloudmold.skilltask.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillTaskRetryCommand implements java.io.Serializable {

    private String taskId;
    private Long expectedVersion;
    private String reason;
    private String approvalRef;
}
