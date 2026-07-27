package cn.iocoder.yudao.module.cloudmold.skilltask.api.managed;

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
public class ManagedSkillTaskBusinessOutcomeView implements Serializable {

    private String outcomeType;
    private String headline;
    private String summary;
    private List<ManagedSkillTaskOutcomeMetricView> metrics;
    private List<ManagedSkillTaskOutcomeObjectView> businessObjects;
    private String evidenceSha256;
}
