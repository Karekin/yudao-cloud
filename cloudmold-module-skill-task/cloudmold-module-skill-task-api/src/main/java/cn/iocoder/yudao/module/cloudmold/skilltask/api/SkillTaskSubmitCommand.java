package cn.iocoder.yudao.module.cloudmold.skilltask.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillTaskSubmitCommand implements java.io.Serializable {

    private String skillId;
    private String skillVersion;
    private String clientRequestKey;
    private String inputJson;
    private String riskLevel;
    private String approvalRef;
}
