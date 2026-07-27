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
public class ManagedSkillTaskDetailView implements Serializable {

    private ManagedSkillTaskRunView task;
    private List<ManagedSkillTaskBusinessPhaseView> businessPhases;
    private List<ManagedSkillTaskStepView> steps;
}
