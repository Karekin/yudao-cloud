package cn.iocoder.yudao.module.cloudmold.skilltask.api;

import java.util.List;

public interface SkillTaskQueryApi {

    SkillTaskView get(String taskId);

    SkillTaskView getByRequestKey(String skillId, String clientRequestKey);

    List<SkillTaskStepView> listSteps(String taskId);
}
